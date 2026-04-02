package co.doubler.spectrum.rendering.pipeline

import com.google.ar.core.Frame

/**
 * Contract for mode-specific AR overlay renderers.
 *
 * Each scan mode (Ghost, Compete, Bluetooth, MagField) implements this
 * to draw its visualization on top of the camera background.
 *
 * **All methods are called on the GL thread.**
 */
interface OverlayRenderer {

    /**
     * Initialize GL resources (shaders, textures, VBOs).
     * Called when the GL surface is created or recreated.
     *
     * @param width Viewport width in pixels
     * @param height Viewport height in pixels
     */
    fun onSurfaceCreated(width: Int, height: Int)

    /**
     * Draw one frame of the overlay visualization.
     *
     * @param frame The current ARCore frame with tracking data
     * @param projectionMatrix 4x4 projection matrix from camera
     * @param viewMatrix 4x4 view matrix from camera pose
     */
    fun onDrawFrame(frame: Frame, projectionMatrix: FloatArray, viewMatrix: FloatArray)

    /**
     * Release all GL resources.
     * Called when the overlay is being removed or the GL context is destroyed.
     */
    fun cleanup()
}
