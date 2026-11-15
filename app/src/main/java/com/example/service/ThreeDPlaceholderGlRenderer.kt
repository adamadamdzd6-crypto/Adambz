package com.example.service

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

enum class PlaceholderMeshShape {
    CUBE,
    CRYSTAL,
    CHARACTER_RIG
}

/**
 * Hardware-accelerated OpenGL ES 2.0 3D rendering surface placeholder.
 * Renders a perspective spatial grid, XYZ coordinate axes, and an orbitable
 * placeholder geometry mesh where processed 3D models will be displayed.
 */
class ThreeDPlaceholderGlRenderer : GLSurfaceView.Renderer {

    @Volatile
    var yawDegrees: Float = 32f

    @Volatile
    var pitchDegrees: Float = 22f

    @Volatile
    var zoomDistance: Float = 4.6f

    @Volatile
    var autoRotate: Boolean = true

    @Volatile
    var showGridAndAxes: Boolean = true

    @Volatile
    var wireframeOnly: Boolean = false

    @Volatile
    var meshShape: PlaceholderMeshShape = PlaceholderMeshShape.CHARACTER_RIG

    @Volatile
    var accentRed: Float = 0.024f

    @Volatile
    var accentGreen: Float = 0.714f

    @Volatile
    var accentBlue: Float = 0.831f

    @Volatile
    var isDarkSurface: Boolean = true

    private var programId: Int = 0
    private var positionHandle: Int = 0
    private var colorHandle: Int = 0
    private var mvpMatrixHandle: Int = 0

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private lateinit var gridBuffer: FloatBuffer
    private var gridVertexCount: Int = 0

    private lateinit var axesBuffer: FloatBuffer
    private var axesVertexCount: Int = 0

    private lateinit var cubeTrianglesBuffer: FloatBuffer
    private var cubeTriangleVertexCount: Int = 0
    private lateinit var cubeWireBuffer: FloatBuffer
    private var cubeWireVertexCount: Int = 0

    private lateinit var crystalTrianglesBuffer: FloatBuffer
    private var crystalTriangleVertexCount: Int = 0
    private lateinit var crystalWireBuffer: FloatBuffer
    private var crystalWireVertexCount: Int = 0

    private lateinit var rigTrianglesBuffer: FloatBuffer
    private var rigTriangleVertexCount: Int = 0
    private lateinit var rigWireBuffer: FloatBuffer
    private var rigWireVertexCount: Int = 0

    private var lastFrameNanos: Long = 0L

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glLineWidth(2.0f)

        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE)

        programId = GLES20.glCreateProgram().also { program ->
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)
        }

        positionHandle = GLES20.glGetAttribLocation(programId, "aPosition")
        colorHandle = GLES20.glGetAttribLocation(programId, "aColor")
        mvpMatrixHandle = GLES20.glGetUniformLocation(programId, "uMvpMatrix")

        buildStaticGeometry()
        lastFrameNanos = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        val safeHeight = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, width, safeHeight)
        val aspect = width.toFloat() / safeHeight.toFloat()
        Matrix.perspectiveM(projectionMatrix, 0, 45f, aspect, 0.1f, 50f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val nowNanos = System.nanoTime()
        val deltaSeconds = if (lastFrameNanos == 0L) {
            0.016f
        } else {
            ((nowNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
        }
        lastFrameNanos = nowNanos

        if (autoRotate) {
            yawDegrees = (yawDegrees + deltaSeconds * 28f) % 360f
        }

        if (isDarkSurface) {
            GLES20.glClearColor(0.024f, 0.039f, 0.078f, 1.0f)
        } else {
            GLES20.glClearColor(0.925f, 0.945f, 0.976f, 1.0f)
        }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        GLES20.glUseProgram(programId)

        val clampedZoom = zoomDistance.coerceIn(2.2f, 10.0f)
        val clampedPitch = pitchDegrees.coerceIn(-75f, 75f)
        val yawRad = Math.toRadians(yawDegrees.toDouble())
        val pitchRad = Math.toRadians(clampedPitch.toDouble())

        val eyeX = (clampedZoom * cos(pitchRad) * sin(yawRad)).toFloat()
        val eyeY = (clampedZoom * sin(pitchRad)).toFloat()
        val eyeZ = (clampedZoom * cos(pitchRad) * cos(yawRad)).toFloat()

        Matrix.setLookAtM(
            viewMatrix, 0,
            eyeX, eyeY, eyeZ,
            0f, 0f, 0f,
            0f, 1f, 0f
        )
        Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        if (showGridAndAxes) {
            drawVertexBuffer(gridBuffer, gridVertexCount, GLES20.GL_LINES)
            drawVertexBuffer(axesBuffer, axesVertexCount, GLES20.GL_LINES)
        }

        when (meshShape) {
            PlaceholderMeshShape.CUBE -> {
                if (!wireframeOnly) {
                    drawVertexBuffer(cubeTrianglesBuffer, cubeTriangleVertexCount, GLES20.GL_TRIANGLES)
                }
                drawVertexBuffer(cubeWireBuffer, cubeWireVertexCount, GLES20.GL_LINES)
            }
            PlaceholderMeshShape.CRYSTAL -> {
                if (!wireframeOnly) {
                    drawVertexBuffer(crystalTrianglesBuffer, crystalTriangleVertexCount, GLES20.GL_TRIANGLES)
                }
                drawVertexBuffer(crystalWireBuffer, crystalWireVertexCount, GLES20.GL_LINES)
            }
            PlaceholderMeshShape.CHARACTER_RIG -> {
                if (!wireframeOnly) {
                    drawVertexBuffer(rigTrianglesBuffer, rigTriangleVertexCount, GLES20.GL_TRIANGLES)
                }
                drawVertexBuffer(rigWireBuffer, rigWireVertexCount, GLES20.GL_LINES)
            }
        }
    }

    private fun drawVertexBuffer(buffer: FloatBuffer, vertexCount: Int, mode: Int) {
        if (vertexCount <= 0) return
        buffer.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(
            positionHandle,
            3,
            GLES20.GL_FLOAT,
            false,
            STRIDE_BYTES,
            buffer
        )

        buffer.position(3)
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glVertexAttribPointer(
            colorHandle,
            3,
            GLES20.GL_FLOAT,
            false,
            STRIDE_BYTES,
            buffer
        )

        GLES20.glDrawArrays(mode, 0, vertexCount)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }

    private fun buildStaticGeometry() {
        // 1. Spatial reference grid on Y = -1.0f
        val gridVerts = ArrayList<Float>()
        val gridY = -1.0f
        val extent = 3.0f
        val step = 0.5f
        var p = -extent
        while (p <= extent + 0.001f) {
            val isMajor = kotlin.math.abs(p) < 0.01f
            val c = if (isMajor) 0.34f else 0.19f
            val b = if (isMajor) 0.45f else 0.28f
            // Z-parallel line
            addLine(gridVerts, p, gridY, -extent, p, gridY, extent, c, c + 0.04f, b)
            // X-parallel line
            addLine(gridVerts, -extent, gridY, p, extent, gridY, p, c, c + 0.04f, b)
            p += step
        }
        gridVertexCount = gridVerts.size / FLOATS_PER_VERTEX
        gridBuffer = toFloatBuffer(gridVerts)

        // 2. Coordinate axes
        val axesVerts = ArrayList<Float>()
        addLine(axesVerts, 0f, gridY + 0.01f, 0f, 1.4f, gridY + 0.01f, 0f, 0.96f, 0.25f, 0.37f) // X Red
        addLine(axesVerts, 0f, gridY + 0.01f, 0f, 0f, gridY + 1.4f, 0f, 0.06f, 0.73f, 0.51f)  // Y Green
        addLine(axesVerts, 0f, gridY + 0.01f, 0f, 0f, gridY + 0.01f, 1.4f, 0.22f, 0.74f, 0.97f) // Z Blue
        axesVertexCount = axesVerts.size / FLOATS_PER_VERTEX
        axesBuffer = toFloatBuffer(axesVerts)

        // 3. Cube triangles + wireframe
        val cubeTri = ArrayList<Float>()
        val cubeWire = ArrayList<Float>()
        appendBoxGeometry(
            triOut = cubeTri,
            wireOut = cubeWire,
            cx = 0f,
            cy = 0f,
            cz = 0f,
            hx = 0.72f,
            hy = 0.72f,
            hz = 0.72f,
            r = 0.04f,
            g = 0.72f,
            b = 0.85f
        )
        cubeTriangleVertexCount = cubeTri.size / FLOATS_PER_VERTEX
        cubeTrianglesBuffer = toFloatBuffer(cubeTri)
        cubeWireVertexCount = cubeWire.size / FLOATS_PER_VERTEX
        cubeWireBuffer = toFloatBuffer(cubeWire)

        // 4. Crystal octahedron
        val crysTri = ArrayList<Float>()
        val crysWire = ArrayList<Float>()
        val top = floatArrayOf(0f, 1.05f, 0f)
        val bottom = floatArrayOf(0f, -0.95f, 0f)
        val ring = arrayOf(
            floatArrayOf(0f, 0.05f, 0.82f),
            floatArrayOf(0.82f, 0.05f, 0f),
            floatArrayOf(0f, 0.05f, -0.82f),
            floatArrayOf(-0.82f, 0.05f, 0f)
        )
        for (i in 0 until 4) {
            val next = (i + 1) % 4
            val shadeTop = 0.72f + i * 0.07f
            addTriangle(crysTri, top, ring[i], ring[next], 0.54f * shadeTop, 0.36f * shadeTop, 0.96f * shadeTop)
            val shadeBot = 0.55f + i * 0.06f
            addTriangle(crysTri, bottom, ring[next], ring[i], 0.04f * shadeBot, 0.72f * shadeBot, 0.85f * shadeBot)

            addLine(crysWire, top[0], top[1], top[2], ring[i][0], ring[i][1], ring[i][2], 0.40f, 0.91f, 0.98f)
            addLine(crysWire, bottom[0], bottom[1], bottom[2], ring[i][0], ring[i][1], ring[i][2], 0.40f, 0.91f, 0.98f)
            addLine(crysWire, ring[i][0], ring[i][1], ring[i][2], ring[next][0], ring[next][1], ring[next][2], 0.65f, 0.85f, 1.0f)
        }
        crystalTriangleVertexCount = crysTri.size / FLOATS_PER_VERTEX
        crystalTrianglesBuffer = toFloatBuffer(crysTri)
        crystalWireVertexCount = crysWire.size / FLOATS_PER_VERTEX
        crystalWireBuffer = toFloatBuffer(crysWire)

        // 5. Character Rig bounding silhouette (head, torso, arms, pedestal)
        val rigTri = ArrayList<Float>()
        val rigWire = ArrayList<Float>()
        // Pedestal plate
        appendBoxGeometry(rigTri, rigWire, 0f, -0.92f, 0f, 0.68f, 0.07f, 0.68f, 0.14f, 0.25f, 0.40f)
        // Legs / lower stance volume
        appendBoxGeometry(rigTri, rigWire, -0.18f, -0.45f, 0f, 0.12f, 0.38f, 0.14f, 0.04f, 0.65f, 0.80f)
        appendBoxGeometry(rigTri, rigWire, 0.18f, -0.45f, 0f, 0.12f, 0.38f, 0.14f, 0.04f, 0.65f, 0.80f)
        // Torso
        appendBoxGeometry(rigTri, rigWire, 0f, 0.22f, 0f, 0.32f, 0.36f, 0.18f, 0.54f, 0.36f, 0.96f)
        // Arms
        appendBoxGeometry(rigTri, rigWire, -0.46f, 0.20f, 0f, 0.10f, 0.32f, 0.11f, 0.06f, 0.72f, 0.51f)
        appendBoxGeometry(rigTri, rigWire, 0.46f, 0.20f, 0f, 0.10f, 0.32f, 0.11f, 0.06f, 0.72f, 0.51f)
        // Head
        appendBoxGeometry(rigTri, rigWire, 0f, 0.82f, 0f, 0.22f, 0.22f, 0.22f, 0.02f, 0.78f, 0.90f)

        rigTriangleVertexCount = rigTri.size / FLOATS_PER_VERTEX
        rigTrianglesBuffer = toFloatBuffer(rigTri)
        rigWireVertexCount = rigWire.size / FLOATS_PER_VERTEX
        rigWireBuffer = toFloatBuffer(rigWire)
    }

    private fun appendBoxGeometry(
        triOut: MutableList<Float>,
        wireOut: MutableList<Float>,
        cx: Float,
        cy: Float,
        cz: Float,
        hx: Float,
        hy: Float,
        hz: Float,
        r: Float,
        g: Float,
        b: Float
    ) {
        val x0 = cx - hx
        val x1 = cx + hx
        val y0 = cy - hy
        val y1 = cy + hy
        val z0 = cz - hz
        val z1 = cz + hz

        val p000 = floatArrayOf(x0, y0, z0)
        val p100 = floatArrayOf(x1, y0, z0)
        val p110 = floatArrayOf(x1, y1, z0)
        val p010 = floatArrayOf(x0, y1, z0)
        val p001 = floatArrayOf(x0, y0, z1)
        val p101 = floatArrayOf(x1, y0, z1)
        val p111 = floatArrayOf(x1, y1, z1)
        val p011 = floatArrayOf(x0, y1, z1)

        // Front (+Z)
        addQuad(triOut, p001, p101, p111, p011, r * 0.95f, g * 0.95f, b * 0.95f)
        // Back (-Z)
        addQuad(triOut, p100, p000, p010, p110, r * 0.65f, g * 0.65f, b * 0.65f)
        // Left (-X)
        addQuad(triOut, p000, p001, p011, p010, r * 0.78f, g * 0.78f, b * 0.78f)
        // Right (+X)
        addQuad(triOut, p101, p100, p110, p111, r * 0.84f, g * 0.84f, b * 0.84f)
        // Top (+Y)
        addQuad(triOut, p011, p111, p110, p010, (r * 1.1f).coerceAtMost(1f), (g * 1.1f).coerceAtMost(1f), (b * 1.1f).coerceAtMost(1f))
        // Bottom (-Y)
        addQuad(triOut, p000, p100, p101, p001, r * 0.50f, g * 0.50f, b * 0.50f)

        val wr = 0.55f
        val wg = 0.90f
        val wb = 1.0f
        // Wireframe edges
        addLine(wireOut, p000[0], p000[1], p000[2], p100[0], p100[1], p100[2], wr, wg, wb)
        addLine(wireOut, p100[0], p100[1], p100[2], p110[0], p110[1], p110[2], wr, wg, wb)
        addLine(wireOut, p110[0], p110[1], p110[2], p010[0], p010[1], p010[2], wr, wg, wb)
        addLine(wireOut, p010[0], p010[1], p010[2], p000[0], p000[1], p000[2], wr, wg, wb)

        addLine(wireOut, p001[0], p001[1], p001[2], p101[0], p101[1], p101[2], wr, wg, wb)
        addLine(wireOut, p101[0], p101[1], p101[2], p111[0], p111[1], p111[2], wr, wg, wb)
        addLine(wireOut, p111[0], p111[1], p111[2], p011[0], p011[1], p011[2], wr, wg, wb)
        addLine(wireOut, p011[0], p011[1], p011[2], p001[0], p001[1], p001[2], wr, wg, wb)

        addLine(wireOut, p000[0], p000[1], p000[2], p001[0], p001[1], p001[2], wr, wg, wb)
        addLine(wireOut, p100[0], p100[1], p100[2], p101[0], p101[1], p101[2], wr, wg, wb)
        addLine(wireOut, p110[0], p110[1], p110[2], p111[0], p111[1], p111[2], wr, wg, wb)
        addLine(wireOut, p010[0], p010[1], p010[2], p011[0], p011[1], p011[2], wr, wg, wb)
    }

    private fun addQuad(
        out: MutableList<Float>,
        a: FloatArray,
        b: FloatArray,
        c: FloatArray,
        d: FloatArray,
        r: Float,
        g: Float,
        bl: Float
    ) {
        addTriangle(out, a, b, c, r, g, bl)
        addTriangle(out, a, c, d, r, g, bl)
    }

    private fun addTriangle(
        out: MutableList<Float>,
        a: FloatArray,
        b: FloatArray,
        c: FloatArray,
        r: Float,
        g: Float,
        bl: Float
    ) {
        addVertex(out, a[0], a[1], a[2], r, g, bl)
        addVertex(out, b[0], b[1], b[2], r, g, bl)
        addVertex(out, c[0], c[1], c[2], r, g, bl)
    }

    private fun addLine(
        out: MutableList<Float>,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        r: Float, g: Float, b: Float
    ) {
        addVertex(out, x1, y1, z1, r, g, b)
        addVertex(out, x2, y2, z2, r, g, b)
    }

    private fun addVertex(
        out: MutableList<Float>,
        x: Float, y: Float, z: Float,
        r: Float, g: Float, b: Float
    ) {
        out.add(x)
        out.add(y)
        out.add(z)
        out.add(r)
        out.add(g)
        out.add(b)
    }

    private fun toFloatBuffer(values: List<Float>): FloatBuffer {
        return ByteBuffer.allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                values.forEach { put(it) }
                position(0)
            }
    }

    private fun compileShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }

    companion object {
        private const val FLOATS_PER_VERTEX = 6
        private const val STRIDE_BYTES = FLOATS_PER_VERTEX * 4

        private const val VERTEX_SHADER_CODE = """
            uniform mat4 uMvpMatrix;
            attribute vec4 aPosition;
            attribute vec4 aColor;
            varying vec4 vColor;
            void main() {
                vColor = aColor;
                gl_Position = uMvpMatrix * aPosition;
            }
        """

        private const val FRAGMENT_SHADER_CODE = """
            precision mediump float;
            varying vec4 vColor;
            void main() {
                gl_FragColor = vColor;
            }
        """
    }
}
