package co.doubler.spectrum.rendering

import android.app.Activity
import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.Display
import co.doubler.spectrum.ar.ArSessionManager
import co.doubler.spectrum.ar.ArSessionState
import co.doubler.spectrum.rendering.pipeline.CameraBackgroundRenderer
import co.doubler.spectrum.rendering.pipeline.RenderPipeline
import com.google.ar.core.TrackingState
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * [GLSurfaceView.Renderer] that drives the AR render loop.
 *
 * Ties the ARCore session (via [ArSessionManager]) to the GL render pipeline.
 * Creates [CameraBackgroundRenderer] and [RenderPipeline] on GL surface creation
 * because they hold GL resources that are bound to the EGL context — they CANNOT
 * be provided via DI since the GL context can be destroyed and recreated
 * independently of the Activity lifecycle.
 *
 * **Per-frame flow (onDrawFrame):**
 * 1. Clear color + depth buffers
 * 2. [ArSessionManager.update] to get the current [com.google.ar.core.Frame]
 * 3. Check [TrackingState] — skip rendering if not TRACKING
 * 4. [RenderPipeline.render] draws camera background + overlay renderers
 *
 * **Thread contract:** All methods are called on the GL render thread by [GLSurfaceView].
 */
class ArGLRenderer(
    private val context: Context,
    private val sessionManager: ArSessionManager,
) : GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "ArGLRenderer"
    }

    private var renderPipeline: RenderPipeline? = null
    private var viewportWidth: Int = 0
    private var viewportHeight: Int = 0
    private var pipelineInitialized: Boolean = false

    // ── GLSurfaceView.Renderer ─────────────────────────────────────

    /**
     * Called when the GL surface is created (or recreated after context loss).
     *
     * All GL resource IDs are invalidated on context loss, so we rebuild
     * everything from scratch: camera background renderer, render pipeline,
     * and re-bind the OES texture to the ARCore session.
     */
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Dark background — visible briefly before first camera frame
        GLES30.glClearColor(0.04f, 0.04f, 0.06f, 1.0f)

        // Enable depth test for overlay rendering
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)

        val cameraBackgroundRenderer = CameraBackgroundRenderer(context)
        renderPipeline = RenderPipeline(cameraBackgroundRenderer)

        // If session is already Ready, initialize the pipeline immediately.
        // This handles both fresh start and GL context recreation scenarios.
        pipelineInitialized = false
        val state = sessionManager.sessionState.value
        if (state is ArSessionState.Ready) {
            renderPipeline?.onSurfaceCreated(state.session, viewportWidth, viewportHeight)
            pipelineInitialized = true
            Log.d(TAG, "Pipeline initialized with existing session on surface creation")
        } else {
            Log.d(TAG, "Surface created, session not yet Ready (state: $state) — deferring pipeline init")
        }
    }

    /**
     * Called when the GL surface size changes (including the first time after creation).
     *
     * Updates the GL viewport and notifies both the ARCore session (for correct
     * display geometry / UV coordinate transformation) and the render pipeline.
     */
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height

        GLES30.glViewport(0, 0, width, height)

        // Notify session of display geometry change for correct UV transforms
        val state = sessionManager.sessionState.value
        if (state is ArSessionState.Ready) {
            val displayRotation = getDisplayRotation()
            state.session.setDisplayGeometry(displayRotation, width, height)
        }

        renderPipeline?.onSurfaceChanged(width, height)
    }

    /**
     * Called every frame to render the AR scene.
     *
     * Skips rendering when:
     * - Session is not in Ready state ([ArSessionManager.update] returns null)
     * - Camera is not in [TrackingState.TRACKING] state
     */
    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        val pipeline = renderPipeline ?: return

        // Get frame from ARCore session (GL thread operation)
        val frame = sessionManager.update() ?: return
        val camera = frame.camera

        // Only render when camera is tracking — PAUSED or STOPPED means
        // ARCore doesn't have a reliable pose estimate yet
        if (camera.trackingState != TrackingState.TRACKING) return

        // Render: camera background → overlay renderers
        pipeline.render(frame, camera)
    }

    // ── Public API ─────────────────────────────────────────────────

    /**
     * Expose the pipeline for adding/removing overlay renderers from outside
     * the GL thread (e.g., when scan mode changes).
     *
     * @return the current [RenderPipeline], or null if the GL surface hasn't been created yet.
     */
    fun getRenderPipeline(): RenderPipeline? = renderPipeline

    /**
     * Release all GL resources held by the pipeline.
     * Call when the renderer is being permanently torn down.
     */
    fun cleanup() {
        renderPipeline?.cleanup()
        renderPipeline = null
    }

    /**
     * Notify the renderer that the session has become Ready.
     *
     * Must be called on the GL thread (via [GLSurfaceView.queueEvent]).
     * Handles the case where the session becomes Ready AFTER onSurfaceCreated
     * has already run (e.g., delayed ARCore availability check).
     */
    fun onSessionReady(state: ArSessionState.Ready) {
        if (pipelineInitialized) {
            Log.d(TAG, "onSessionReady: pipeline already initialized — skipping")
            return
        }
        renderPipeline?.onSurfaceCreated(state.session, viewportWidth, viewportHeight)
        pipelineInitialized = true
        Log.d(TAG, "Pipeline initialized after session became Ready")
    }

    // ── Private ────────────────────────────────────────────────────

    /**
     * Get the current display rotation for [com.google.ar.core.Session.setDisplayGeometry].
     *
     * Uses the Activity's display when available, falls back to the default display
     * via the window manager.
     */
    private fun getDisplayRotation(): Int {
        val display: Display? = if (context is Activity) {
            @Suppress("DEPRECATION")
            context.windowManager.defaultDisplay
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager)
                ?.defaultDisplay
        }
        return display?.rotation ?: 0
    }
}
