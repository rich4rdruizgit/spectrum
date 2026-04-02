package co.doubler.spectrum.ar

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Lifecycle-aware wrapper around ARCore [Session].
 *
 * Scoped to [ActivityRetainedComponent] — survives configuration changes,
 * destroyed when the Activity finishes.
 *
 * **Thread contract:**
 * - [checkAvailability], [requestInstall], [resume], [pause], [destroy] → Main thread
 * - [update], [setCameraTextureName] → GL thread ONLY
 * - [sessionState] → safe to collect from any thread
 */
@ActivityRetainedScoped
class ArSessionManager @Inject constructor() {

    companion object {
        private const val TAG = "ArSessionManager"
    }

    private val _sessionState = MutableStateFlow<ArSessionState>(ArSessionState.Checking)
    val sessionState: StateFlow<ArSessionState> = _sessionState.asStateFlow()

    private var session: Session? = null
    private var userRequestedInstall = true

    // ── Availability & Installation ─────────────────────────────────

    /**
     * Check ARCore availability and transition state accordingly.
     * Call from Activity.onCreate after CAMERA permission is granted.
     */
    fun checkAvailability(activity: Activity) {
        try {
            val availability = ArCoreApk.getInstance().checkAvailability(activity)

            if (availability.isTransient) {
                // Re-check — the query is still resolving
                @Suppress("DEPRECATION")
                activity.window.decorView.postDelayed({ checkAvailability(activity) }, 200)
                return
            }

            when {
                availability.isSupported -> {
                    // Supported — attempt session creation
                    createSession(activity)
                }
                else -> {
                    Log.w(TAG, "ARCore not supported on this device: $availability")
                    _sessionState.value = ArSessionState.NotSupported
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "ARCore availability check failed", e)
            _sessionState.value = ArSessionState.Error(e)
        }
    }

    /**
     * Request ARCore installation when state is [ArSessionState.NeedsInstall].
     * Call from Activity.onResume or in response to user action.
     *
     * @return true if install was requested (activity result pending),
     *         false if no install was needed.
     */
    fun requestInstall(activity: Activity): Boolean {
        return try {
            val installStatus = ArCoreApk.getInstance()
                .requestInstall(activity, userRequestedInstall)

            when (installStatus) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    // User will be prompted; flip flag so subsequent calls
                    // don't show the prompt again if the user dismissed it.
                    userRequestedInstall = false
                    true
                }
                ArCoreApk.InstallStatus.INSTALLED -> {
                    // Already installed — proceed to session creation
                    createSession(activity)
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "ARCore install request failed", e)
            _sessionState.value = ArSessionState.Error(e)
            false
        }
    }

    // ── Session Lifecycle ───────────────────────────────────────────

    /**
     * Resume the ARCore session. Call from Activity.onResume.
     * Only operates when state is [ArSessionState.Ready] or [ArSessionState.Paused].
     */
    fun resume() {
        val currentSession = session ?: return
        val state = _sessionState.value

        if (state !is ArSessionState.Ready && state !is ArSessionState.Paused) {
            Log.w(TAG, "resume() called in invalid state: $state")
            return
        }

        try {
            currentSession.resume()
            _sessionState.value = ArSessionState.Ready(currentSession)
            Log.d(TAG, "Session resumed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume session", e)
            _sessionState.value = ArSessionState.Error(e)
        }
    }

    /**
     * Pause the ARCore session. Call from Activity.onPause.
     * Emits [ArSessionState.Paused].
     */
    fun pause() {
        val currentSession = session ?: return
        val state = _sessionState.value

        if (state !is ArSessionState.Ready) {
            Log.d(TAG, "pause() called in non-Ready state: $state — skipping")
            return
        }

        try {
            currentSession.pause()
            _sessionState.value = ArSessionState.Paused
            Log.d(TAG, "Session paused")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause session", e)
            _sessionState.value = ArSessionState.Error(e)
        }
    }

    /**
     * Destroy the ARCore session and release all resources.
     * Call from Activity.onDestroy. The session cannot be reused after this.
     */
    fun destroy() {
        try {
            session?.close()
            Log.d(TAG, "Session destroyed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing session", e)
        } finally {
            session = null
        }
    }

    // ── GL Thread Operations ────────────────────────────────────────

    /**
     * Update the session and get the latest frame.
     *
     * **MUST be called from the GL thread only** (inside `onDrawFrame`).
     *
     * @return the current [Frame], or null if the session is not in [ArSessionState.Ready] state.
     */
    fun update(): Frame? {
        val state = _sessionState.value
        if (state !is ArSessionState.Ready) return null

        return try {
            state.session.update()
        } catch (e: Exception) {
            Log.e(TAG, "session.update() failed", e)
            null
        }
    }

    /**
     * Bind the camera OES texture to the ARCore session.
     *
     * **MUST be called from the GL thread only** (inside `onSurfaceCreated`).
     */
    fun setCameraTextureName(textureId: Int) {
        session?.setCameraTextureName(textureId)
    }

    // ── Private ─────────────────────────────────────────────────────

    /**
     * Create and configure the ARCore session.
     * Configures plane detection (horizontal + vertical),
     * focus mode AUTO, and update mode LATEST_CAMERA_IMAGE.
     */
    private fun createSession(context: Context) {
        try {
            val newSession = Session(context)

            val config = Config(newSession).apply {
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                focusMode = Config.FocusMode.AUTO
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            }
            newSession.configure(config)

            session = newSession
            _sessionState.value = ArSessionState.Ready(newSession)
            Log.d(TAG, "Session created and configured")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create ARCore session", e)
            _sessionState.value = ArSessionState.Error(e)
        }
    }
}
