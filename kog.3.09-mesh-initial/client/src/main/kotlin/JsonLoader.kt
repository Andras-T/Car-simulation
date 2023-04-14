import org.w3c.xhr.XMLHttpRequest
import org.w3c.dom.events.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import vision.gears.webglmath.Geometry

@Serializable
data class JsonMesh(
	val vertices : Array<Float>,
	val normals : Array<Float>,
	val texturecoords : Array<Array<Float>>,
	val faces : Array<Array<Short>>)

@Serializable
data class JsonModel(
	val meshes : Array<JsonMesh>)

class JsonLoader(){
  fun loadModel(jsonModelFileUrl : String) : JsonModel {
    val request = XMLHttpRequest()
    request.overrideMimeType("application/json")
    request.open("GET", jsonModelFileUrl, false)
    var jsonModel : dynamic = null
    request.onloadend = { 
      val json = Json { ignoreUnknownKeys=true }
      jsonModel = json.decodeFromString(
        JsonModel.serializer(),
        request.responseText
        ) 
      Unit
    }
    request.send()
    return jsonModel ?: throw Error("Could not load JSON model from file ${jsonModelFileUrl}.")
  }
  fun loadGeometries(gl : WebGL2RenderingContext,
      jsonModelFileUrl : String) : Array<Geometry> {
    return loadModel(jsonModelFileUrl).meshes.map{ SubmeshGeometry(gl, it) }.toTypedArray()
  }
  fun loadMeshes(
      gl : WebGL2RenderingContext,
      jsonModelFileUrl : String,
      vararg materials : Material) : Array<Mesh>{
    val geometries = loadGeometries(gl, jsonModelFileUrl)
    return (materials zip geometries).map{(m, g) -> Mesh(m, g)}.toTypedArray()
  }
}