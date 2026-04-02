package co.doubler.spectrum.rendering.pipeline

import com.google.ar.core.Camera
import com.google.ar.core.Frame
import com.google.ar.core.Session
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Orchestrates the per-frame rendering sequence.
 *
 * Draw order is guaranteed: [CameraBackgroundRenderer] always draws first
 * (the camera feed as background), followed by each registered [OverlayRenderer]
 * in registration order.
 *
 * Thread safety: overlay list uses [CopyOnWriteArrayList] so that [addOverlay]
 * and [removeOverlay] can be called from any thread while [render] runs on
 * the GL thread.
 *
 * All GL-touching methods ([onSurfaceCreated], [onSurfaceChanged], [render],
 * [cleanup]) MUST be called on the GL thread.
 */
class RenderPipeline(
    private val cameraBackgroundRenderer: CameraBackgroundRenderer
) {

    private val overlays = CopyOnWriteArrayList<OverlayRenderer>()
    private var viewportWidth: Int = 0
    private var viewportHeight: Int = 0
    private var surfaceCreated: Boolean = false

    /**
     * Initialize the pipeline. Creates camera renderer resources and
     * initializes all currently registered overlays.
     *
     * @param session The active ARCore session
     * @param width Viewport width in pixels
     * @param height Viewport height in pixels
     */
    fun onSurfaceCreated(session: Session, width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        surfaceCreated = true

        cameraBackgroundRenderer.onSurfaceCreated(session)

        for (overlay in overlays) {
            overlay.onSurfaceCreated(width, height)
        }
    }

    /**
     * Handle viewport size change.
     *
     * @param width New viewport width in pixels
     * @param height New viewport height in pixels
     */
    fun onSurfaceChanged(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
    }

    /**
     * Render one complete frame.
     *
     * 1. [CameraBackgroundRenderer] draws camera feed (depth test disabled)
     * 2. Extract projection and view matrices from [Camera]
     * 3. Each [OverlayRenderer] draws its visualization with the matrices
     *
     * @param frame The current ARCore frame
     * @param camera The camera from the current frame
     */
    fun render(frame: Frame, camera: Camera) {
        // 1. Camera background — ALWAYS first
        cameraBackgroundRenderer.draw(frame)

        // 2. Extract matrices for overlays
        if (overlays.isNotEmpty()) {
            val projectionMatrix = FloatArray(16)
            camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100f)

            val viewMatrix = FloatArray(16)
            camera.getViewMatrix(viewMatrix, 0)

            // 3. Draw each overlay in registration order
            for (overlay in overlays) {
                overlay.onDrawFrame(frame, projectionMatrix, viewMatrix)
            }
        }
    }

    /**
     * Register an overlay renderer.
     *
     * If the GL surface has already been created, the overlay's
     * [OverlayRenderer.onSurfaceCreated] will be called on the next frame.
     * Thread-safe — can be called from any thread.
     *
     * @param overlay The overlay renderer to add
     */
    fun addOverlay(overlay: OverlayRenderer) {
        overlays.add(overlay)
        if (surfaceCreated) {
            overlay.onSurfaceCreated(viewportWidth, viewportHeight)
        }
    }

    /**
     * Remove an overlay renderer and release its GL resources.
     * Thread-safe — can be called from any thread.
     *
     * @param overlay The overlay renderer to remove
     */
    fun removeOverlay(overlay: OverlayRenderer) {
        if (overlays.remove(overlay)) {
            overlay.cleanup()
        }
    }

    /**
     * Remove and clean up all overlay renderers.
     * Thread-safe — can be called from any thread.
     */
    fun clearOverlays() {
        val snapshot = ArrayList(overlays)
        overlays.clear()
        for (overlay in snapshot) {
            overlay.cleanup()
        }
    }

    /**
     * Release all GL resources — camera renderer and all overlays.
     */
    fun cleanup() {
        clearOverlays()
        cameraBackgroundRenderer.cleanup()
        surfaceCreated = false
    }
}
