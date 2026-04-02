package co.doubler.spectrum.ar

import com.google.ar.core.Session

/**
 * Sealed state machine for ARCore session lifecycle.
 *
 * Represents every possible state the AR session can be in,
 * enabling exhaustive `when` handling in the UI layer.
 */
sealed interface ArSessionState {

    /** Initial state - checking ARCore availability */
    data object Checking : ArSessionState

    /** Device does not support ARCore */
    data object NotSupported : ArSessionState

    /** ARCore is supported but not installed - trigger install prompt */
    data object NeedsInstall : ArSessionState

    /** ARCore session is active and ready */
    data class Ready(val session: Session) : ArSessionState

    /** Session is paused (Activity.onPause) */
    data object Paused : ArSessionState

    /** Unrecoverable error */
    data class Error(val throwable: Throwable) : ArSessionState
}
