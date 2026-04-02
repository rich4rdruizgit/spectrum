package co.doubler.spectrum.rendering

import android.content.Context
import android.opengl.GLSurfaceView

/**
 * Custom [GLSurfaceView] configured for ARCore rendering.
 *
 * Configuration:
 * - OpenGL ES 3.0 context ([setEGLContextClientVersion])
 * - RGBA_8888 surface with 16-bit depth buffer ([setEGLConfigChooser])
 * - [preserveEGLContextOnPause] = true to minimize GL context loss on backgrounding
 * - [RENDERMODE_CONTINUOUSLY] because ARCore needs continuous frame updates
 *
 * The renderer is set externally after construction via [setRenderer].
 */
class GLArSurfaceView(context: Context) : GLSurfaceView(context) {

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0) // RGBA8888 + 16-bit depth, no stencil
        preserveEGLContextOnPause = true
        // Renderer set externally — GLSurfaceView enforces setRenderer() before use
    }
}
