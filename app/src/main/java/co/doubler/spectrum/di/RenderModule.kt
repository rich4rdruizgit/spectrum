package co.doubler.spectrum.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent

/**
 * Hilt module for AR rendering dependencies.
 *
 * Installed in [ActivityRetainedComponent] because:
 * - [ArSessionManager] is @ActivityRetainedScoped with @Inject constructor,
 *   so Hilt discovers and scopes it automatically (no @Provides needed).
 * - ARCore Session must survive config changes but NOT outlive the Activity.
 *
 * NOT provided via DI (GL-context-scoped, created in ArGLRenderer.onSurfaceCreated):
 * - CameraBackgroundRenderer — tied to OES texture lifecycle
 * - RenderPipeline — orchestrates GL draw calls, recreated on context loss
 *
 * Future: add @Binds for OverlayRenderer implementations when scan modes are built.
 */
@Module
@InstallIn(ActivityRetainedComponent::class)
object RenderModule
