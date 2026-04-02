package co.doubler.spectrum.rendering.magnetic

import android.content.Context
import android.opengl.GLES30
import co.doubler.spectrum.R
import co.doubler.spectrum.presentation.model.MagneticRenderData
import co.doubler.spectrum.rendering.pipeline.OverlayRenderer
import co.doubler.spectrum.rendering.shader.ShaderProgram
import co.doubler.spectrum.util.Constants
import com.google.ar.core.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicReference

/**
 * GL-side renderer for Magnetic Field mode.
 *
 * Draws a fullscreen shader overlay that visualizes the magnetic field as:
 * - Animated flow lines running in the field's XY direction
 * - Particles flowing along the field lines
 * - An expanding anomaly ring when a field spike is detected
 *
 * Thread model:
 * - All GL methods run on the GL thread.
 * - [magneticDataRef] is written by MagneticViewModel on the main thread,
 *   read here on the GL thread — AtomicReference (lock-free).
 */
class MagneticOverlayRenderer(
    private val context: Context,
    private val magneticDataRef: AtomicReference<MagneticRenderData>
) : OverlayRenderer {

    private var shaderProgram: ShaderProgram? = null
    private var vertexVbo: Int = 0
    private var texCoordVbo: Int = 0
    private var startTimeNanos: Long = 0L
    private var aspectRatio: Float = 1f

    // Cached uniform locations
    private var uTimeLoc: Int = -1
    private var uAspectRatioLoc: Int = -1
    private var uFieldXLoc: Int = -1
    private var uFieldYLoc: Int = -1
    private var uMagnitudeLoc: Int = -1
    private var uIsAnomalyLoc: Int = -1
    private var uAnomalyIntensityLoc: Int = -1
    private var uColorLoc: Int = -1

    override fun onSurfaceCreated(width: Int, height: Int) {
        aspectRatio = width.toFloat() / height.toFloat()
        startTimeNanos = System.nanoTime()

        shaderProgram = ShaderProgram.create(
            context,
            R.raw.magnetic_vertex,
            R.raw.magnetic_fragment
        )

        shaderProgram?.let { sp ->
            uTimeLoc            = sp.getUniformLocation("u_Time")
            uAspectRatioLoc     = sp.getUniformLocation("u_AspectRatio")
            uFieldXLoc          = sp.getUniformLocation("u_FieldX")
            uFieldYLoc          = sp.getUniformLocation("u_FieldY")
            uMagnitudeLoc       = sp.getUniformLocation("u_Magnitude")
            uIsAnomalyLoc       = sp.getUniformLocation("u_IsAnomaly")
            uAnomalyIntensityLoc = sp.getUniformLocation("u_AnomalyIntensity")
            uColorLoc           = sp.getUniformLocation("u_Color")
        }

        // Fullscreen quad VBOs — same pattern as BluetoothOverlayRenderer
        val vbos = IntArray(2)
        GLES30.glGenBuffers(2, vbos, 0)
        vertexVbo   = vbos[0]
        texCoordVbo = vbos[1]

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vertexVbo)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            Constants.FULLSCREEN_QUAD_COORDS.size * Constants.FLOAT_SIZE_BYTES,
            createFloatBuffer(Constants.FULLSCREEN_QUAD_COORDS),
            GLES30.GL_STATIC_DRAW
        )

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, texCoordVbo)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            Constants.FULLSCREEN_QUAD_TEX_COORDS.size * Constants.FLOAT_SIZE_BYTES,
            createFloatBuffer(Constants.FULLSCREEN_QUAD_TEX_COORDS),
            GLES30.GL_STATIC_DRAW
        )

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    override fun onDrawFrame(frame: Frame, projectionMatrix: FloatArray, viewMatrix: FloatArray) {
        val data = magneticDataRef.get()
        val elapsedSec = (System.nanoTime() - startTimeNanos) / 1_000_000_000f

        val program = shaderProgram ?: return

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)

        program.use()

        GLES30.glUniform1f(uTimeLoc,             elapsedSec)
        GLES30.glUniform1f(uAspectRatioLoc,      aspectRatio)
        GLES30.glUniform1f(uFieldXLoc,           data.fieldX)
        GLES30.glUniform1f(uFieldYLoc,           data.fieldY)
        GLES30.glUniform1f(uMagnitudeLoc,        data.magnitude)
        GLES30.glUniform1i(uIsAnomalyLoc,        if (data.isAnomaly) 1 else 0)
        GLES30.glUniform1f(uAnomalyIntensityLoc, data.anomalyIntensity)
        GLES30.glUniform3f(uColorLoc,            data.colorR, data.colorG, data.colorB)

        // Bind vertex positions
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vertexVbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, Constants.COORDS_PER_VERTEX, GLES30.GL_FLOAT, false, 0, 0)

        // Bind texture coordinates
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, texCoordVbo)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, Constants.TEX_COORDS_PER_VERTEX, GLES30.GL_FLOAT, false, 0, 0)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        // Restore GL state
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDepthMask(true)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    override fun cleanup() {
        shaderProgram?.delete()
        shaderProgram = null

        val vbos = intArrayOf(vertexVbo, texCoordVbo).filter { it != 0 }.toIntArray()
        if (vbos.isNotEmpty()) {
            GLES30.glDeleteBuffers(vbos.size, vbos, 0)
        }
        vertexVbo   = 0
        texCoordVbo = 0
    }

    private companion object {
        fun createFloatBuffer(data: FloatArray): FloatBuffer {
            return ByteBuffer.allocateDirect(data.size * Constants.FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(data)
                    position(0)
                }
        }
    }
}
