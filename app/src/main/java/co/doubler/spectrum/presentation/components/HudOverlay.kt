package co.doubler.spectrum.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import co.doubler.spectrum.domain.model.ScanMode
import co.doubler.spectrum.ui.theme.HudBracketColor
import co.doubler.spectrum.ui.theme.HudScanningPulse
import co.doubler.spectrum.ui.theme.HudTextGlow
import co.doubler.spectrum.ui.theme.LocalHudStyling
import co.doubler.spectrum.ui.theme.NearBlack

@Composable
fun HudOverlay(
    scanMode: ScanMode,
    isScanning: Boolean,
    isAnomalyActive: Boolean = false,
    subtitle: String? = null,
    iconEmoji: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val hudStyling = LocalHudStyling.current
    val bracketsVisible = remember { MutableTransitionState(false).apply { targetState = true } }
    val statusBarVisible = remember { MutableTransitionState(false).apply { targetState = true } }

    Box(modifier = modifier.fillMaxSize()) {
        // Corner brackets — entrance expand animation
        AnimatedVisibility(
            visibleState = bracketsVisible,
            enter = expandIn(animationSpec = tween(400), expandFrom = androidx.compose.ui.Alignment.Center) + fadeIn(tween(400)),
            exit = shrinkOut()
        ) {
            CornerBrackets(color = HudBracketColor, strokeWidth = hudStyling.strokeWidth.value)
        }

        // Top status bar — entrance slide-down animation
        AnimatedVisibility(
            visibleState = statusBarVisible,
            enter = slideInVertically(tween(300)) { -it } + fadeIn(tween(300)),
            exit = shrinkOut()
        ) {
            StatusBar(
                scanMode = scanMode,
                isScanning = isScanning,
                isAnomalyActive = isAnomalyActive,
                subtitle = subtitle,
                iconEmoji = iconEmoji,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        // Mode-specific overlay content
        content()
    }
}

// ── Corner Brackets ──────────────────────────────────────────────

@Composable
private fun CornerBrackets(
    color: Color,
    strokeWidth: Float,
    bracketLength: Float = 40f
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val len = bracketLength
        val sw = strokeWidth

        // Top-left
        drawLine(color, Offset(0f, len), Offset(0f, 0f), sw)
        drawLine(color, Offset(0f, 0f), Offset(len, 0f), sw)

        // Top-right
        drawLine(color, Offset(w, len), Offset(w, 0f), sw)
        drawLine(color, Offset(w, 0f), Offset(w - len, 0f), sw)

        // Bottom-left
        drawLine(color, Offset(0f, h - len), Offset(0f, h), sw)
        drawLine(color, Offset(0f, h), Offset(len, h), sw)

        // Bottom-right
        drawLine(color, Offset(w, h - len), Offset(w, h), sw)
        drawLine(color, Offset(w, h), Offset(w - len, h), sw)
    }
}

// ── Status Bar ───────────────────────────────────────────────────

@Composable
private fun StatusBar(
    scanMode: ScanMode,
    isScanning: Boolean,
    isAnomalyActive: Boolean = false,
    subtitle: String? = null,
    iconEmoji: String? = null,
    modifier: Modifier = Modifier
) {
    val hudStyling = LocalHudStyling.current
    val modeColor = Color(scanMode.primaryColor)

    // Anomaly text color pulse — only when active
    val modeTextColor = if (isAnomalyActive) {
        val infiniteTransition = rememberInfiniteTransition(label = "anomaly_text_pulse")
        infiniteTransition.animateColor(
            initialValue = modeColor,
            targetValue = Color.White,
            animationSpec = infiniteRepeatable(
                animation = tween(400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "anomaly_text_color"
        ).value
    } else {
        modeColor
    }

    if (subtitle != null) {
        // Two-row layout: icon + mode name / scanning dot on row 1, subtitle on row 2
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(NearBlack.copy(alpha = hudStyling.backgroundAlpha))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (iconEmoji != null) {
                    Text(text = iconEmoji, fontSize = hudStyling.textSize)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = scanMode.displayName.uppercase(),
                    color = modeTextColor,
                    fontSize = hudStyling.textSize,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                if (isScanning) {
                    ScanningIndicator(
                        color = modeTextColor,
                        pulseDurationMs = if (isAnomalyActive) 400 else 800
                    )
                }
            }
            val fullSubtitle = if (isScanning) "$subtitle • ESCANEANDO..." else subtitle
            Text(
                text = fullSubtitle,
                color = HudTextGlow.copy(alpha = 0.7f),
                fontSize = hudStyling.textSize
            )
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(NearBlack.copy(alpha = hudStyling.backgroundAlpha))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = scanMode.displayName.uppercase(),
                color = modeTextColor,
                fontSize = hudStyling.textSize
            )

            Spacer(modifier = Modifier.weight(1f))

            if (isScanning) {
                ScanningIndicator(
                    color = modeTextColor,
                    pulseDurationMs = if (isAnomalyActive) 400 else 800
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SCANNING",
                    color = HudTextGlow,
                    fontSize = hudStyling.textSize
                )
            }
        }
    }
}

// ── Scanning Pulse Indicator ─────────────────────────────────────

@Composable
private fun ScanningIndicator(color: Color, pulseDurationMs: Int = 800) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanning_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = pulseDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}
