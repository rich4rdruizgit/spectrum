package co.doubler.spectrum.rendering.compete

import android.content.Context
import android.opengl.GLES30
import android.opengl.Matrix
import co.doubler.spectrum.R
import co.doubler.spectrum.presentation.model.CompeteAp
import co.doubler.spectrum.rendering.pipeline.OverlayRenderer
import co.doubler.spectrum.rendering.shader.ShaderProgram
import co.doubler.spectrum.util.Constants
import co.doubler.spectrum.util.Constants.COMPETE_BORDER_PULSE_SPEED
import co.doubler.spectrum.util.Constants.COMPETE_BORDER_THRESHOLD
import co.doubler.spectrum.util.Constants.COMPETE_MAX_APS
import co.doubler.spectrum.util.Constants.COMPETE_TERRITORY_ALPHA
import com.google.ar.core.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.cos
import kotlin.math.sin

/**
 * GL-side renderer for Compete mode coverage territory visualization.
 *
 * Draws a fullscreen screen-space Voronoi overlay where each detected AP
 * "owns" the pixels where its weighted signal is dominant. Borders between
 * territories pulse white to indicate handover zones.
 *
 * Thread model:
 * - All GL methods ([onSurfaceCreated], [onDrawFrame], [cleanup]) run on the GL thread.
 * - [accessPointsRef] is written by CompeteViewModel on Main, read here on GL — AtomicReference.
 */
class CompeteOverlayRenderer(
    private val context: Context,
    private val accessPointsRef: AtomicReference<List<CompeteAp>>
) : OverlayRenderer {

    private var shaderProgram: ShaderProgram? = null
    private var vertexVbo: Int = 0
    private var texCoordVbo: Int = 0
    private var startTimeNanos: Long = 0L
    private var viewportWidth: Int = 0
    private var viewportHeight: Int = 0
    private var aspectRatio: Float = 1f

    // Cached uniform locations (populated in onSurfaceCreated)
    private var uApCentersLoc: Int = -1
    private var uApColorsLoc: Int = -1
    private var uApCountLoc: Int = -1
    private var uTimeLoc: Int = -1
    private var uAspectRatioLoc: Int = -1
    private var uBorderThresholdLoc: Int = -1
    private var uTerritoryAlphaLoc: Int = -1
    private var uBorderPulseSpeedLoc: Int = -1

    // Pre-allocated arrays to avoid per-frame allocation
    private val apCentersArray = FloatArray(COMPETE_MAX_APS * 4)
    private val apColorsArray = FloatArray(COMPETE_MAX_APS * 4)

    // Scratch arrays for matrix math
    private val worldPointHomogeneous = FloatArray(4)
    private val viewPoint = FloatArray(4)
    private val clipPoint = FloatArray(4)

    override fun onSurfaceCreated(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        aspectRatio = width.toFloat() / height.toFloat()
        startTimeNanos = System.nanoTime()

        shaderProgram = ShaderProgram.create(
            context,
            R.raw.compete_vertex,
            R.raw.compete_fragment
        )

        shaderProgram?.let { sp ->
            uApCentersLoc = sp.getUniformLocation("u_ApCenters")
            uApColorsLoc = sp.getUniformLocation("u_ApColors")
            uApCountLoc = sp.getUniformLocation("u_ApCount")
            uTimeLoc = sp.getUniformLocation("u_Time")
            uAspectRatioLoc = sp.getUniformLocation("u_AspectRatio")
            uBorderThresholdLoc = sp.getUniformLocation("u_BorderThreshold")
            uTerritoryAlphaLoc = sp.getUniformLocation("u_TerritoryAlpha")
            uBorderPulseSpeedLoc = sp.getUniformLocation("u_BorderPulseSpeed")
        }

        // VBOs for fullscreen quad (same pattern as GhostOverlayRenderer)
        val vbos = IntArray(2)
        GLES30.glGenBuffers(2, vbos, 0)
        vertexVbo = vbos[0]
        texCoordVbo = vbos[1]

        val quadVertexBuffer = createFloatBuffer(Constants.FULLSCREEN_QUAD_COORDS)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vertexVbo)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            Constants.FULLSCREEN_QUAD_COORDS.size * Constants.FLOAT_SIZE_BYTES,
            quadVertexBuffer,
            GLES30.GL_STATIC_DRAW
        )

        val texCoordBuffer = createFloatBuffer(Constants.FULLSCREEN_QUAD_TEX_COORDS)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, texCoordVbo)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            Constants.FULLSCREEN_QUAD_TEX_COORDS.size * Constants.FLOAT_SIZE_BYTES,
            texCoordBuffer,
            GLES30.GL_STATIC_DRAW
        )

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    override fun onDrawFrame(frame: Frame, projectionMatrix: FloatArray, viewMatrix: FloatArray) {
        val aps = accessPointsRef.get()
        if (aps.isEmpty()) return

        val count = minOf(aps.size, COMPETE_MAX_APS)
        val elapsedSec = (System.nanoTime() - startTimeNanos) / 1_000_000_000f

        // ── 1. Project each AP to NDC and pack uniform arrays ─────────────────

        for (i in 0 until count) {
            val ap = aps[i]
            val worldPoint = azimuthToWorldPoint(ap.azimuthDeg, ap.estimatedDistance)
            val ndc = projectToNDC(worldPoint, projectionMatrix, viewMatrix)

            val idx = i * 4
            apCentersArray[idx + 0] = ndc?.get(0) ?: 0f  // x NDC
            apCentersArray[idx + 1] = ndc?.get(1) ?: 0f  // y NDC
            apCentersArray[idx + 2] = if (ndc == null) 0f else ap.signalWeight // weight (0 = invisible)
            apCentersArray[idx + 3] = 0f                                        // unused

            // Unpack ARGB Long → normalized rgba floats
            val color = ap.color
            apColorsArray[idx + 0] = ((color shr 16) and 0xFF) / 255f  // R
            apColorsArray[idx + 1] = ((color shr 8) and 0xFF) / 255f   // G
            apColorsArray[idx + 2] = (color and 0xFF) / 255f           // B
            apColorsArray[idx + 3] = ((color shr 24) and 0xFF) / 255f  // A
        }

        // Zero out unused slots (weight = 0 disables AP in shader)
        for (i in count until COMPETE_MAX_APS) {
            apCentersArray[i * 4 + 2] = 0f
        }

        // ── 2. GL state ────────────────────────────────────────────────────────

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)

        // ── 3. Shader + uniforms ───────────────────────────────────────────────

        val program = shaderProgram ?: return
        program.use()

        GLES30.glUniform1i(uApCountLoc, count)
        GLES30.glUniform1f(uTimeLoc, elapsedSec)
        GLES30.glUniform1f(uAspectRatioLoc, aspectRatio)
        GLES30.glUniform1f(uBorderThresholdLoc, COMPETE_BORDER_THRESHOLD)
        GLES30.glUniform1f(uTerritoryAlphaLoc, COMPETE_TERRITORY_ALPHA)
        GLES30.glUniform1f(uBorderPulseSpeedLoc, COMPETE_BORDER_PULSE_SPEED)
        GLES30.glUniform4fv(uApCentersLoc, COMPETE_MAX_APS, apCentersArray, 0)
        GLES30.glUniform4fv(uApColorsLoc, COMPETE_MAX_APS, apColorsArray, 0)

        // ── 4. Bind VBOs and draw fullscreen quad ─────────────────────────────

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vertexVbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(
            0,
            Constants.COORDS_PER_VERTEX,
            GLES30.GL_FLOAT,
            false,
            0,
            0
        )

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, texCoordVbo)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(
            1,
            Constants.TEX_COORDS_PER_VERTEX,
            GLES30.GL_FLOAT,
            false,
            0,
            0
        )

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        // ── 5. Cleanup ────────────────────────────────────────────────────────

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
        vertexVbo = 0
        texCoordVbo = 0
    }

    // ── Projection helpers ────────────────────────────────────────────────────

    /**
     * Project a world-space point to NDC via projection * view * worldPoint.
     *
     * @return NDC [x, y] or null if the point is behind the camera (clip.w <= 0).
     */
    private fun projectToNDC(
        worldPoint: FloatArray,
        projectionMatrix: FloatArray,
        viewMatrix: FloatArray
    ): FloatArray? {
        worldPointHomogeneous[0] = worldPoint[0]
        worldPointHomogeneous[1] = worldPoint[1]
        worldPointHomogeneous[2] = worldPoint[2]
        worldPointHomogeneous[3] = 1f

        Matrix.multiplyMV(viewPoint, 0, viewMatrix, 0, worldPointHomogeneous, 0)
        Matrix.multiplyMV(clipPoint, 0, projectionMatrix, 0, viewPoint, 0)

        val w = clipPoint[3]
        if (w <= 0f) return null // Behind camera

        return floatArrayOf(clipPoint[0] / w, clipPoint[1] / w)
    }

    /**
     * Convert azimuth angle + distance to a world-space point.
     * Azimuth 0 = directly ahead (-Z), 90 = right (+X).
     */
    private fun azimuthToWorldPoint(azimuthDeg: Float, distance: Float): FloatArray {
        val rad = Math.toRadians(azimuthDeg.toDouble())
        return floatArrayOf(
            (distance * sin(rad)).toFloat(),
            0f,
            -(distance * cos(rad)).toFloat()
        )
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
