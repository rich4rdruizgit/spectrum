package co.doubler.spectrum.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val hudStyling = LocalHudStyling.current

    Box(modifier = modifier.fillMaxSize()) {
        // Corner brackets
        CornerBrackets(color = HudBracketColor, strokeWidth = hudStyling.strokeWidth.value)

        // Top status bar
        StatusBar(
            scanMode = scanMode,
            isScanning = isScanning,
            modifier = Modifier.align(Alignment.TopCenter)
        )

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
    modifier: Modifier = Modifier
) {
    val hudStyling = LocalHudStyling.current
    val modeColor = Color(scanMode.primaryColor)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(NearBlack.copy(alpha = hudStyling.backgroundAlpha))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = scanMode.displayName.uppercase(),
            color = modeColor,
            fontSize = hudStyling.textSize
        )

        Spacer(modifier = Modifier.weight(1f))

        if (isScanning) {
            ScanningIndicator(color = modeColor)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "SCANNING",
                color = HudTextGlow,
                fontSize = hudStyling.textSize
            )
        }
    }
}

// ── Scanning Pulse Indicator ─────────────────────────────────────

@Composable
private fun ScanningIndicator(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanning_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
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
