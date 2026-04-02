package co.doubler.spectrum.rendering.bluetooth

import android.content.Context
import android.opengl.GLES30
import android.opengl.Matrix
import androidx.compose.ui.geometry.Offset
import co.doubler.spectrum.R
import co.doubler.spectrum.presentation.model.BluetoothDeviceNode
import co.doubler.spectrum.rendering.pipeline.OverlayRenderer
import co.doubler.spectrum.rendering.shader.ShaderProgram
import co.doubler.spectrum.util.Constants
import com.google.ar.core.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.cos
import kotlin.math.sin

/**
 * GL-side renderer for Bluetooth Vision mode.
 *
 * Draws a fullscreen shader overlay for each detected Bluetooth device, rendering:
 * - A glowing circle node at the projected NDC position of each device
 * - An expanding proximity ring per device
 * - An animated dashed connection line from the screen center to each connected device
 *
 * Thread model:
 * - All GL methods ([onSurfaceCreated], [onDrawFrame], [cleanup]) run on the GL thread.
 * - [bluetoothDevicesRef] written by ViewModel on Main, read here on GL — AtomicReference.
 * - [screenPositionsRef] written here on GL, read by ViewModel on Main — AtomicReference.
 */
class BluetoothOverlayRenderer(
    private val context: Context,
    private val bluetoothDevicesRef: AtomicReference<List<BluetoothDeviceNode>>,
    private val screenPositionsRef: AtomicReference<Map<String, Offset>>
) : OverlayRenderer {

    private var shaderProgram: ShaderProgram? = null
    private var vertexVbo: Int = 0
    private var texCoordVbo: Int = 0
    private var startTimeNanos: Long = 0L
    private var viewportWidth: Int = 0
    private var viewportHeight: Int = 0
    private var aspectRatio: Float = 1f

    // Cached uniform locations (populated in onSurfaceCreated)
    private var uNodeCentersLoc: Int = -1
    private var uNodeColorsLoc: Int = -1
    private var uNodeFlagsLoc: Int = -1
    private var uNodeCountLoc: Int = -1
    private var uTimeLoc: Int = -1
    private var uAspectRatioLoc: Int = -1
    private var uNodeRadiusLoc: Int = -1
    private var uRingWidthLoc: Int = -1
    private var uLineWidthLoc: Int = -1
    private var uLineIntensityLoc: Int = -1
    private var uMaxRingRadiusLoc: Int = -1

    // Pre-allocated arrays — avoid per-frame GC pressure
    private val nodeCentersArray = FloatArray(Constants.BT_MAX_DEVICES * 4)
    private val nodeColorsArray = FloatArray(Constants.BT_MAX_DEVICES * 4)
    private val nodeFlagsArray = FloatArray(Constants.BT_MAX_DEVICES * 4)

    // Scratch arrays for matrix math
    private val worldPointHomogeneous = FloatArray(4)
    private val viewPoint = FloatArray(4)
    private val clipPoint = FloatArray(4)

    override fun onSurfaceCreated(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        aspectRatio = width.toFloat() / height.toFloat()
        startTimeNanos = System.nanoTime()

        // Compile bluetooth shaders
        shaderProgram = ShaderProgram.create(
            context,
            R.raw.bluetooth_vertex,
            R.raw.bluetooth_fragment
        )

        // Cache uniform locations
        shaderProgram?.let { sp ->
            uNodeCentersLoc  = sp.getUniformLocation("u_NodeCenters")
            uNodeColorsLoc   = sp.getUniformLocation("u_NodeColors")
            uNodeFlagsLoc    = sp.getUniformLocation("u_NodeFlags")
            uNodeCountLoc    = sp.getUniformLocation("u_NodeCount")
            uTimeLoc         = sp.getUniformLocation("u_Time")
            uAspectRatioLoc  = sp.getUniformLocation("u_AspectRatio")
            uNodeRadiusLoc   = sp.getUniformLocation("u_NodeRadius")
            uRingWidthLoc    = sp.getUniformLocation("u_RingWidth")
            uLineWidthLoc    = sp.getUniformLocation("u_LineWidth")
            uLineIntensityLoc = sp.getUniformLocation("u_LineIntensity")
            uMaxRingRadiusLoc = sp.getUniformLocation("u_MaxRingRadius")
        }

        // Create VBOs for fullscreen quad (same pattern as CameraBackgroundRenderer / GhostOverlayRenderer)
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
        val devices = bluetoothDevicesRef.get()
        if (devices.isEmpty()) return

        val count = minOf(devices.size, Constants.BT_MAX_DEVICES)
        val elapsedSec = (System.nanoTime() - startTimeNanos) / 1_000_000_000f

        // ── 1. Project each device to NDC and pack uniform arrays ──

        val screenPositions = mutableMapOf<String, Offset>()

        for (i in 0 until count) {
            val device = devices[i]
            val worldPoint = azimuthToWorldPoint(device.azimuthDeg, device.estimatedDistance)
            val ndc = projectToNDC(worldPoint, projectionMatrix, viewMatrix)
            val behindCamera = ndc == null

            val idx = i * 4

            nodeCentersArray[idx + 0] = ndc?.get(0) ?: 0f   // x NDC
            nodeCentersArray[idx + 1] = ndc?.get(1) ?: 0f   // y NDC
            nodeCentersArray[idx + 2] = if (behindCamera) 0f else device.signalStrength  // intensity
            nodeCentersArray[idx + 3] = (device.address.hashCode() and 0xFF) / 255f * TAU  // phase

            // Unpack color from Long ARGB
            val color = device.color
            nodeColorsArray[idx + 0] = ((color shr 16) and 0xFF) / 255f  // R
            nodeColorsArray[idx + 1] = ((color shr 8) and 0xFF) / 255f   // G
            nodeColorsArray[idx + 2] = (color and 0xFF) / 255f           // B
            nodeColorsArray[idx + 3] = ((color shr 24) and 0xFF) / 255f  // A

            // Flags: x = isConnected
            nodeFlagsArray[idx + 0] = if (device.isConnected) 1f else 0f
            nodeFlagsArray[idx + 1] = 0f
            nodeFlagsArray[idx + 2] = 0f
            nodeFlagsArray[idx + 3] = 0f

            // Compute screen positions for Compose AR labels
            if (!behindCamera && ndc != null) {
                val screenX = (ndc[0] + 1f) / 2f * viewportWidth
                val screenY = (1f - ndc[1]) / 2f * viewportHeight  // flip Y for screen coords
                screenPositions[device.address] = Offset(screenX, screenY)
            }
        }

        // Zero out unused slots (intensity = 0 causes shader to skip them)
        for (i in count until Constants.BT_MAX_DEVICES) {
            nodeCentersArray[i * 4 + 2] = 0f
        }

        // Write screen positions for Compose labels (atomic swap, lock-free)
        screenPositionsRef.set(screenPositions)

        // ── 2. GL state for transparent blending over camera feed ──

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)

        // ── 3. Use shader and set uniforms ──

        val program = shaderProgram ?: return
        program.use()

        GLES30.glUniform1i(uNodeCountLoc, count)
        GLES30.glUniform1f(uTimeLoc, elapsedSec)
        GLES30.glUniform1f(uAspectRatioLoc, aspectRatio)
        GLES30.glUniform1f(uNodeRadiusLoc, Constants.BT_NODE_RADIUS)
        GLES30.glUniform1f(uRingWidthLoc, Constants.BT_RING_WIDTH)
        GLES30.glUniform1f(uLineWidthLoc, Constants.BT_LINE_WIDTH)
        GLES30.glUniform1f(uLineIntensityLoc, Constants.BT_LINE_INTENSITY)
        GLES30.glUniform1f(uMaxRingRadiusLoc, Constants.BT_MAX_RING_RADIUS)
        GLES30.glUniform4fv(uNodeCentersLoc, count, nodeCentersArray, 0)
        GLES30.glUniform4fv(uNodeColorsLoc, count, nodeColorsArray, 0)
        GLES30.glUniform4fv(uNodeFlagsLoc, count, nodeFlagsArray, 0)

        // ── 4. Bind VBOs and draw fullscreen quad ──

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vertexVbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(
            0, Constants.COORDS_PER_VERTEX, GLES30.GL_FLOAT, false, 0, 0
        )

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, texCoordVbo)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(
            1, Constants.TEX_COORDS_PER_VERTEX, GLES30.GL_FLOAT, false, 0, 0
        )

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        // ── 5. Restore GL state ──

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

    // ── Projection helpers ──────────────────────────────────────────

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
        if (w <= 0f) return null  // Behind camera

        return floatArrayOf(clipPoint[0] / w, clipPoint[1] / w)
    }

    /**
     * Convert azimuth angle + distance to a world-space point.
     *
     * Azimuth 0 = directly ahead (−Z), 90 = right (+X), 180 = behind (+Z), 270 = left (−X).
     * Y is always 0 (same height as camera).
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

        const val TAU = 6.2831853f  // 2 * PI

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
