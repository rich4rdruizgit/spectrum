package co.doubler.spectrum.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Dark Color Scheme (dark-only, no light variant) ──────────

private val SpectrumColorScheme = darkColorScheme(
    primary = NeonCyan,
    secondary = ElectricPurple,
    tertiary = SignalGreen,
    background = NearBlack,
    surface = DarkSurface,
    surfaceVariant = DarkSurface,
    error = HotRed,
    onPrimary = OnPrimary,
    onSecondary = OnSecondary,
    onTertiary = NearBlack,
    onBackground = OnDark,
    onSurface = OnDark,
    onSurfaceVariant = TextSecondary,
    onError = OnDark,
    outline = TextSecondary,
    outlineVariant = TextDisabled
)

// ── HUD Styling Constants ────────────────────────────────────

@Immutable
data class HudStyling(
    val strokeWidth: Dp = 1.5.dp,
    val cornerRadius: Dp = 4.dp,
    val textSize: TextUnit = 12.sp,
    val backgroundAlpha: Float = 0.5f,
    val animationDuration: Int = 300
)

val LocalHudStyling = staticCompositionLocalOf { HudStyling() }

// ── Sharp Corners (HUD aesthetic) ────────────────────────────

private val SpectrumShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp)
)

// ── Theme Composable ─────────────────────────────────────────

@Composable
fun SpectrumTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalHudStyling provides HudStyling()
    ) {
        MaterialTheme(
            colorScheme = SpectrumColorScheme,
            typography = Typography,
            shapes = SpectrumShapes,
            content = content
        )
    }
}
