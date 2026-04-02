package co.doubler.spectrum.presentation.components

import android.opengl.GLSurfaceView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.doubler.spectrum.ar.ArSessionManager
import co.doubler.spectrum.ar.ArSessionState
import co.doubler.spectrum.domain.model.ScanMode
import co.doubler.spectrum.rendering.ArGLRenderer
import co.doubler.spectrum.rendering.GLArSurfaceView
import co.doubler.spectrum.rendering.pipeline.OverlayRenderer

/**
 * Compose wrapper that hosts the AR GL surface and layers the HUD on top.
 *
 * Architecture:
 * - Bottom layer: [GLArSurfaceView] via [AndroidView] — renders camera feed + overlays
 * - Top layer: [HudOverlay] — Compose-based scan mode status and corner brackets
 *
 * Lifecycle management:
 * - [DisposableEffect] observes the parent lifecycle to pause/resume both the
 *   [GLSurfaceView] and [ArSessionManager] in the correct order.
 * - Order on pause: GLSurfaceView.onPause() BEFORE session.pause()
 *   (prevents onDrawFrame from calling session.update() on a paused session).
 * - Order on resume: session.resume() BEFORE GLSurfaceView.onResume()
 *   (ensures the session is ready when onDrawFrame fires).
 *
 * Session state observation:
 * - Collects [ArSessionManager.sessionState] as Compose state.
 * - Only renders the GL surface when the session has been through the [ArSessionState.Ready]
 *   state at least once. Shows appropriate fallback UI for other states.
 *
 * Thread contract:
 * - All GL operations happen on the GL render thread (managed by [GLSurfaceView]).
 * - Compose rendering happens on the Main thread.
 * - [ArSessionState] bridges the two via [kotlinx.coroutines.flow.StateFlow].
 *
 * @param sessionManager The ARCore session lifecycle manager (Activity-scoped via Hilt).
 * @param scanMode The currently active scan mode — passed to [HudOverlay].
 * @param isScanning Whether the scanner is actively collecting data.
 * @param overlayRenderer Optional mode-specific GL overlay renderer. When provided, it is
 *   registered on the [co.doubler.spectrum.rendering.pipeline.RenderPipeline] via
 *   [GLSurfaceView.queueEvent] to ensure GL-thread safety. Automatically unregistered
 *   when the composable leaves composition or the renderer instance changes.
 * @param modifier Modifier applied to the root [Box].
 * @param hudContent Additional mode-specific content rendered inside [HudOverlay].
 */
@Composable
fun ArSceneView(
    sessionManager: ArSessionManager,
    scanMode: ScanMode,
    isScanning: Boolean,
    overlayRenderer: OverlayRenderer? = null,
    isAnomalyActive: Boolean = false,
    modifier: Modifier = Modifier,
    hudContent: @Composable BoxScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sessionState by sessionManager.sessionState.collectAsStateWithLifecycle()

    // Retain renderer across recompositions — tied to sessionManager identity.
    // ArGLRenderer creates its own RenderPipeline on GL surface creation because
    // pipeline + camera renderer hold GL resources bound to the EGL context.
    val renderer = remember(sessionManager) {
        ArGLRenderer(context, sessionManager)
    }

    // Hold a reference to the GLSurfaceView so the lifecycle observer can
    // call onPause/onResume. Using mutableStateOf (not mutableStateOf<GLArSurfaceView?>)
    // because we only need the reference — we never trigger recomposition from it.
    var glSurfaceView by remember { mutableStateOf<GLArSurfaceView?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        // ── GL Surface (bottom layer) ──────────────────────────────
        AndroidView(
            factory = { ctx ->
                GLArSurfaceView(ctx).also { view ->
                    view.setRenderer(renderer)
                    view.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                    glSurfaceView = view
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = {
                renderer.cleanup()
                glSurfaceView = null
            },
        )

        // ── HUD Overlay (top layer) ───────────────────────────────
        HudOverlay(
            scanMode = scanMode,
            isScanning = isScanning,
            isAnomalyActive = isAnomalyActive,
            content = hudContent,
        )
    }

    // ── Lifecycle observer: pause/resume GL + session ─────────────
    DisposableEffect(lifecycleOwner, glSurfaceView) {
        val observer = LifecycleEventObserver { _, event ->
            val view = glSurfaceView ?: return@LifecycleEventObserver

            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // Session first — must be Ready before GL thread calls update()
                    sessionManager.resume()
                    view.onResume()
                }

                Lifecycle.Event.ON_PAUSE -> {
                    // GL first — stops onDrawFrame before session.pause()
                    view.onPause()
                    sessionManager.pause()
                }

                else -> { /* no-op */ }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // ── Overlay renderer registration (GL thread) ────────────────
    // Keyed on both overlayRenderer and glSurfaceView so it re-runs when:
    // - The mode changes (new renderer instance) → old overlay removed, new one added
    // - The GL surface is recreated → overlay re-registered on the new pipeline
    DisposableEffect(overlayRenderer, glSurfaceView) {
        val overlay = overlayRenderer ?: return@DisposableEffect onDispose {}
        val view = glSurfaceView ?: return@DisposableEffect onDispose {}

        view.queueEvent {
            renderer.getRenderPipeline()?.addOverlay(overlay)
        }

        onDispose {
            view.queueEvent {
                renderer.getRenderPipeline()?.removeOverlay(overlay)
            }
        }
    }
}
