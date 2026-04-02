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
     * Draw only the camera background for the current frame.
     *
     * Call this every frame regardless of tracking state — the camera
     * feed should be visible even when ARCore is initializing or lost.
     */
    fun renderCameraBackground(frame: Frame) {
        cameraBackgroundRenderer.draw(frame)
    }

    /**
     * Draw all registered overlay renderers for the current frame.
     *
     * Only call this when [com.google.ar.core.TrackingState.TRACKING] —
     * overlays need a valid camera pose to produce correct projection/view matrices.
     *
     * @param frame The current ARCore frame
     * @param camera The camera from the current frame (must be TRACKING)
     */
    fun renderOverlays(frame: Frame, camera: Camera) {
        if (overlays.isEmpty()) return

        val projectionMatrix = FloatArray(16)
        camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100f)

        val viewMatrix = FloatArray(16)
        camera.getViewMatrix(viewMatrix, 0)

        for (overlay in overlays) {
            overlay.onDrawFrame(frame, projectionMatrix, viewMatrix)
        }
    }

    /**
     * Render one complete frame: camera background + overlays.
     *
     * Convenience wrapper — prefer calling [renderCameraBackground] and
     * [renderOverlays] separately when you need tracking-state gating.
     *
     * @param frame The current ARCore frame
     * @param camera The camera from the current frame
     */
    fun render(frame: Frame, camera: Camera) {
        renderCameraBackground(frame)
        renderOverlays(frame, camera)
    }

    fun setCameraPostFx(tintColor: FloatArray, trackingLost: Boolean) {
        cameraBackgroundRenderer.setPostFxParams(tintColor, trackingLost)
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
