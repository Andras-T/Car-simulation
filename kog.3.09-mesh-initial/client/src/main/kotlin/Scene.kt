import org.khronos.webgl.get
import org.w3c.dom.HTMLCanvasElement
import vision.gears.webglmath.Mat4
import org.khronos.webgl.WebGLRenderingContext as GL
import kotlin.js.Date
import vision.gears.webglmath.UniformProvider
import vision.gears.webglmath.Vec3
import kotlin.math.*

class Scene(
    val gl: WebGL2RenderingContext
) : UniformProvider("scene") {

    private val vsTextured = Shader(gl, GL.VERTEX_SHADER, "textured-vs.glsl")
    private val fsTextured = Shader(gl, GL.FRAGMENT_SHADER, "textured-fs.glsl")
    private val texturedProgram = Program(gl, vsTextured, fsTextured)

    private val vsShadow = Shader(gl, GL.VERTEX_SHADER, "shadow-vs.glsl")
    private val fsShadow = Shader(gl, GL.FRAGMENT_SHADER, "shadow-fs.glsl")
    private val shadowProgram = Program(gl, vsShadow, fsShadow)

    private val jsonLoader = JsonLoader()
    private val gameObjects = ArrayList<GameObject>()
    private val truckGameObject = GameObject()
    private val wheels = ArrayList<GameObject>()

    private var dir = Vec3(1f, 0f, 0f)
    private var freeCam = true
    private var distanceFromCar = 60f
    private var switchingPerspectiveDelay = 250f
    private var lastTimeSwitched = 0f

    private val shadowMatrix by Mat4()
    private val lightDirection = Vec3(0.5f, -1f, 1f).normalize()
    private val shadowMaterial = Material(shadowProgram)

    // Shadows
    init {
        val lx = lightDirection.x
        val lz = lightDirection.z
        val d = -lightDirection.y

        shadowMatrix.set(
            1f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f,
            lx / d, 0.01f, lz / d, 1f
        )
    }

    // Loading trees
    init {

        val treeJsonModel = jsonLoader.loadModel("media/json/tree.json")
        val treeSubmeshGeometry = SubmeshGeometry(gl, treeJsonModel.meshes[0])
        val treeMaterial = Material(texturedProgram).apply {
            this["colorTexture"]?.set(
                Texture2D(gl, "media/json/tree.png")
            )
        }
        val treeMesh = Mesh(treeMaterial, treeSubmeshGeometry)

        for (i in -18..0) {
            gameObjects += GameObject(treeMesh).apply {
                position.set(Vec3(i * 110f, 0f, 110f))
            }
            gameObjects += GameObject(treeMesh).apply {
                position.set(Vec3(i * 110f, 0f, -110f))
            }
        }
    }

    // Loading heli
    init {
        val heliJsonModel = jsonLoader.loadModel("media/json/heli/heli1.json")
        val heliSubmeshGeometry = SubmeshGeometry(gl, heliJsonModel.meshes[0])
        val heliMaterial = Material(texturedProgram).apply {
            this["colorTexture"]?.set(
                Texture2D(gl, "media/json/heli/heli.png")
            )
        }

        val mainRotorJsonModel = jsonLoader.loadModel("media/json/heli/mainrotor.json")
        val mainRotorSubmeshGeometry = SubmeshGeometry(gl, mainRotorJsonModel.meshes[0])
        val mainRotorMaterial = Material(texturedProgram).apply {
            this["colorTexture"]?.set(
                Texture2D(gl, "media/json/heli/heliait.png")
            )
        }

        val tailRotorJsonModel = jsonLoader.loadModel("media/json/heli/tailrotor.json")
        val tailRotorSubmeshGeometry = SubmeshGeometry(gl, tailRotorJsonModel.meshes[0])
        val tailRotorMaterial = Material(texturedProgram).apply {
            this["colorTexture"]?.set(
                Texture2D(gl, "media/json/heli/heliait.png")
            )
        }

        val heliMesh = Mesh(heliMaterial, heliSubmeshGeometry)
        val mainRotorMesh = Mesh(mainRotorMaterial, mainRotorSubmeshGeometry)
        val tailRotorMesh = Mesh(tailRotorMaterial, tailRotorSubmeshGeometry)

        val heliObject = GameObject(heliMesh).apply {
            position.set(Vec3(-50f, 5f, -110f))
        }
        val heliObject2 = GameObject(heliMesh).apply {
            position.set(Vec3(-18f * 110f + 50f, 5f, -110f))
        }

        gameObjects += heliObject
        gameObjects += heliObject2

        gameObjects += GameObject(mainRotorMesh).apply {
            position.set(Vec3(0f, 13.5f, 5f))
            parent = heliObject
            move = object : GameObject.Motion() {

                override operator fun invoke(
                    dt: Float, t: Float, keysPressed: Set<String>, gameObjects: List<GameObject>
                ): Boolean {
                    yaw += dt * 30f
                    return true
                }
            }
        }
        gameObjects += GameObject(mainRotorMesh).apply {
            position.set(Vec3(0f, 13.5f, 5f))
            parent = heliObject2
            move = object : GameObject.Motion() {

                override operator fun invoke(
                    dt: Float, t: Float, keysPressed: Set<String>, gameObjects: List<GameObject>
                ): Boolean {
                    yaw += dt * 30f
                    return true
                }
            }
        }

        gameObjects += GameObject(tailRotorMesh).apply {
            position.set(Vec3(1f, 10f, -35f))
            parent = heliObject
            move = object : GameObject.Motion() {

                override operator fun invoke(
                    dt: Float, t: Float, keysPressed: Set<String>, gameObjects: List<GameObject>
                ): Boolean {
                    pitch += dt * 35f
                    return true
                }
            }
        }
        gameObjects += GameObject(tailRotorMesh).apply {
            position.set(Vec3(1f, 10f, -35f))
            parent = heliObject2
            move = object : GameObject.Motion() {

                override operator fun invoke(
                    dt: Float, t: Float, keysPressed: Set<String>, gameObjects: List<GameObject>
                ): Boolean {
                    pitch += dt * 35f
                    return true
                }
            }
        }
    }

    // Loading chassis and calculating forces and movement
    init {
        val chassisJsonModel = jsonLoader.loadModel("media/json/chevy/chassis.json")
        val chassisSubmeshGeometry = SubmeshGeometry(gl, chassisJsonModel.meshes[0])
        val chassisMaterial = Material(texturedProgram).apply {
            this["colorTexture"]?.set(
                Texture2D(gl, "media/json/chevy/chevy.png")
            )
        }
        val chassisMesh = Mesh(chassisMaterial, chassisSubmeshGeometry)

        truckGameObject.apply {
            addComponentsAndGatherUniforms(chassisMesh)
            yaw = -PI.toFloat() / 2

            move = object : GameObject.Motion() {

                val invMass = 10f
                val c1 = 0.05f
                var velocity = Vec3(0f, 0f, 0f)
                var forwardVelocity = 0f
                var wheelRotation = 0f
                val L = 14f

                override operator fun invoke(
                    dt: Float, t: Float, keysPressed: Set<String>, gameObjects: List<GameObject>
                ): Boolean {
                    var forceM = Vec3(0f, 0f, 0f)

                    if (keysPressed.contains("ArrowUp")) {
                        forceM = forceM + Vec3(0f, 0f, 1000f) * dt
                    } else if (keysPressed.contains("ArrowDown")) {
                        forceM = forceM + Vec3(0f, 0f, -500f) * dt
                    }

                    if (keysPressed.contains("ArrowRight") && wheelRotation < PI.toFloat() / 6f) {
                        wheelRotation += dt * PI.toFloat() / 8f
                        forceM = forceM - forceM * dt
                    } else if (keysPressed.contains("ArrowLeft") && wheelRotation > -PI.toFloat() / 6f) {
                        wheelRotation += dt * -PI.toFloat() / 8f
                        forceM = forceM - forceM * dt
                    } else if (keysPressed.contains("Control") && abs(wheelRotation) < PI.toFloat() / 180f * 3f) {
                        wheelRotation = 0f
                    }

                    if (keysPressed.contains(" ")) {
                        forceM = forceM - forceM * 10f * dt
                        forwardVelocity += forwardVelocity * -0.5f * dt
                    }

                    val forwardAcceleration = forceM.z * invMass
                    forwardVelocity += forwardAcceleration * dt
                    forwardVelocity *= exp(-dt * c1 * invMass) * abs(cos(wheelRotation / 4f))
                    velocity = Vec3(sin(yaw) * forwardVelocity, 0f, cos(yaw) * forwardVelocity)

                    val velocityN = Vec3(velocity.x, velocity.y, velocity.z).normalize()
                    val fVector = Vec3(sin(yaw), 0f, cos(yaw))
                    val fVDot = fVector.dot(velocityN)

                    forward = velocity.length() * fVDot

                    val frontAxisPos = Vec3(position.x, 0f, position.z) + dir * L / 2f
                    val rearAxisPos = Vec3(position.x, 0f, position.z) - dir * L / 2f
                    val rearAxisNormal = Vec3(dir.x, 0f, dir.z)
                    val frontAxisNormal = rotate2DVectorAroundYAxis(rearAxisNormal, wheelRotation)
                    val frontAxisLine = createLine(frontAxisNormal, frontAxisPos)
                    val rearAxisLine = createLine(rearAxisNormal, rearAxisPos)

                    var intersection = findIntersection(frontAxisLine, rearAxisLine)

                    if (hasIntersection(intersection)) {
                        intersection = intersection as Vec3
                        val radius = (Vec3(position.x, 0f, position.z) - intersection).length()
                        val prevPos = Vec3(position.x, 0f, position.z)
                        position = position + velocity * dt
                        val circleIntersection =
                            findCircleLineIntersection(intersection, radius, Vec3(position.x, 0f, position.z))
                        position = Vec3(0f, position.y, 0f) + circleIntersection
                        val distance = (prevPos - Vec3(position.x, 0f, position.z)).length()

                        if ((wheelRotation < 0 && forward > 0) || (wheelRotation > 0 && forward < 0))
                            yaw += 2 * asin(distance / (2 * radius))
                        else if ((wheelRotation > 0 && forward > 0) || (wheelRotation < 0 && forward < 0))
                            yaw -= 2 * asin(distance / (2 * radius))

                    } else {
                        position = position + velocity * dt
                    }

                    return true
                }
            }
        }
    }

    // Loading and rotating wheels
    init {
        val wheelJsonModel = jsonLoader.loadModel("media/json/chevy/wheel.json")
        val wheelSubmeshGeometry = SubmeshGeometry(gl, wheelJsonModel.meshes[0])
        val wheelMaterial = Material(texturedProgram).apply {
            this["colorTexture"]?.set(
                Texture2D(gl, "media/json/chevy/chevy.png")
            )
        }
        val wheelMesh = Mesh(wheelMaterial, wheelSubmeshGeometry)

        for (i in 0..3) {
            val wheelGameObject = GameObject().apply {
                addComponentsAndGatherUniforms(wheelMesh)
                position.set(
                    if (i % 2 == 0) -7f else 7f,
                    -3f,
                    if (i < 2) 14f else -11f
                )
                pitch = i * PI.toFloat() / 6f

                move = object : GameObject.Motion() {
                    override operator fun invoke(
                        dt: Float, t: Float, keysPressed: Set<String>, gameObjects: List<GameObject>
                    ): Boolean {
                        if (i < 2) {
                            if (keysPressed.contains("ArrowLeft") && yaw < PI.toFloat() / 6f)
                                yaw += dt * PI.toFloat() / 6f
                            if (keysPressed.contains("ArrowRight") && yaw > -PI.toFloat() / 6f)
                                yaw += dt * -PI.toFloat() / 6
                        }

                        if (!truckGameObject.move.forward.isNaN()) {
                            pitch += truckGameObject.move.forward * dt * 0.2f
                        }

                        return true
                    }
                }
            }

            wheelGameObject.parent = truckGameObject
            wheels += wheelGameObject
            gameObjects += wheelGameObject
        }

        truckGameObject.position = Vec3(0f, 5.75f, 0f)
        gameObjects += truckGameObject
    }

    // Loading plane
    init {
        val planeGeometry = PlaneGeometry(gl)

        val roadMaterial = Material(texturedProgram).apply {
            this["colorTexture"]?.set(
                Texture2D(gl, "media/road.jpg")
            )
        }
        val roadMesh = Mesh(roadMaterial, planeGeometry)
        val grassMaterial = Material(texturedProgram).apply {
            this["colorTexture"]?.set(
                Texture2D(gl, "media/grass.png")
            )
        }
        val grassMesh = Mesh(grassMaterial, planeGeometry)
        for (i in -40..40) {
            gameObjects += GameObject(roadMesh).apply {
                this.needsShadow = false
                position = Vec3(i * 100f, 0f, 0f)
            }
            for (j in -10..10) {
                if (j != 0) gameObjects += GameObject(grassMesh).apply {
                    this.needsShadow = false
                    position = Vec3(i * 100f, 0f, j * 100f)
                }
            }
        }
    }

    val camera = PerspectiveCamera(*Program.all).apply {
        position.set(1f, 1f)
        update()
    }

    fun resize(canvas: HTMLCanvasElement) {
        gl.viewport(
            0, 0, canvas.width, canvas.height
        )
        camera.setAspectRatio(canvas.width.toFloat() / canvas.height)
    }

    fun hasIntersection(intersection: Vec3?): Boolean {
        return ((intersection != null) && (!intersection.x.isNaN()) && (!intersection.z.isNaN()))
    }

    fun findCircleLineIntersection(circleCenter: Vec3, radius: Float, pointPosition: Vec3): Vec3 {
        val lineDirection = pointPosition - circleCenter
        val lineLength = lineDirection.length()

        val directionScale = radius / lineLength
        val intersectionPoint = Vec3(
            circleCenter.x + lineDirection.x * directionScale,
            0f,
            circleCenter.z + lineDirection.z * directionScale
        )
        return intersectionPoint
    }

    fun rotate2DVectorAroundYAxis(vector: Vec3, angle: Float): Vec3 {
        val cosAngle = cos(angle)
        val sinAngle = sin(angle)
        val x = cosAngle * vector.x - sinAngle * vector.z
        val z = sinAngle * vector.x + cosAngle * vector.z
        return Vec3(x, 0f, z)
    }

    fun createLine(normal: Vec3, point: Vec3): Vec3 {
        val constant = (normal.x * point.x + normal.z * point.z)
        return Vec3(normal.x, normal.z, constant)
    }

    fun findIntersection(line1: Vec3, line2: Vec3): Vec3? {
        val det = line1.x * line2.y - line2.x * line1.y
        if (det == 0f) {
            return null
        }
        val x = (line2.y * line1.z - line1.y * line2.z) / det
        val z = (line1.x * line2.z - line2.x * line1.z) / det
        return Vec3(x, 0f, z)
    }

    val timeAtFirstFrame = Date().getTime()
    var timeAtLastFrame = timeAtFirstFrame

    init {
        gl.enable(GL.DEPTH_TEST)
        addComponentsAndGatherUniforms(*Program.all)
    }

    @Suppress("UNUSED_PARAMETER")
    fun update(keysPressed: Set<String>) {
        val timeAtThisFrame = Date().getTime()
        val dt = (timeAtThisFrame - timeAtLastFrame).toFloat() / 1000f
        val t = (timeAtThisFrame - timeAtFirstFrame).toFloat() / 1000f
        timeAtLastFrame = timeAtThisFrame

        dir = Vec3(
            wheels[0].modelMatrix.storage[3] - wheels[2].modelMatrix.storage[3],
            0f,
            wheels[0].modelMatrix.storage[11] - wheels[2].modelMatrix.storage[11]
        ).normalize()

        if (keysPressed.contains("r")) {
            freeCam = false
        } else if (keysPressed.contains("f")) {
            freeCam = true
        }

        if (!freeCam) {
            if (keysPressed.contains("v") && switchingPerspectiveDelay < timeAtThisFrame.toFloat() - lastTimeSwitched) {
                distanceFromCar = distanceFromCar + 15f

                if (distanceFromCar > 76f)
                    distanceFromCar = 45f

                lastTimeSwitched = timeAtThisFrame.toFloat()
            }

            camera.followObject(
                dt,
                dir,
                truckGameObject.position,
                1f * truckGameObject.yaw + PI.toFloat(),
                distanceFromCar
            )
        } else {
            camera.move(dt, keysPressed)
        }

        gl.clearColor(0.5294f, 0.8078f, 0.9216f, 1f)
        gl.clearDepth(1f)
        gl.clear(GL.COLOR_BUFFER_BIT or GL.DEPTH_BUFFER_BIT)
        gl.enable(GL.BLEND)
        gl.blendFunc(
            GL.SRC_ALPHA, GL.ONE_MINUS_SRC_ALPHA
        )

        gameObjects.forEach { it.move(dt, t, keysPressed, gameObjects) }
        gameObjects.forEach { it.update() }
        gameObjects.forEach { it.draw(this, camera) }
        gameObjects.forEach {
            if (it.needsShadow) { // ground needs no shadow
                it.using(shadowMaterial).draw(this, this.camera);
            }
        }
    }
}
