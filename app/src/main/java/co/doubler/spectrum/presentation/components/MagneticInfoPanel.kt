package co.doubler.spectrum.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.doubler.spectrum.presentation.model.MagColorBand
import co.doubler.spectrum.presentation.model.MagneticUiState
import co.doubler.spectrum.ui.theme.AmberWarning
import co.doubler.spectrum.ui.theme.DataFontFamily
import co.doubler.spectrum.ui.theme.MagneticAccent
import co.doubler.spectrum.util.Constants
import kotlin.math.roundToInt

private val PanelBackground = Color(0xCC0A0A1A)

@Composable
fun MagneticInfoPanel(
    state: MagneticUiState,
    modifier: Modifier = Modifier
) {
    val panelVisible = remember { MutableTransitionState(false).apply { targetState = true } }
    val icnirpPercent = (state.currentMagnitude / Constants.MAG_SAFE_LIMIT_UT.toFloat())
        .coerceIn(0f, 1f)

    val magnitudeColor by animateColorAsState(
        targetValue = when (state.colorBand) {
            MagColorBand.LOW    -> Color(0xFF0091EA)
            MagColorBand.MEDIUM -> MagneticAccent
            MagColorBand.HIGH   -> Color(0xFFFF1744)
        },
        animationSpec = tween(300),
        label = "magnitudeColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "anomalyPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    AnimatedVisibility(
        visibleState = panelVisible,
        enter = slideInVertically(tween(350)) { it } + fadeIn(tween(350))
    ) {
    Column(
        modifier = modifier
            .width(210.dp)
            .background(PanelBackground, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Header
        Text(
            text = "MAGNETIC FIELD",
            fontFamily = DataFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MagneticAccent,
            letterSpacing = 1.sp
        )

        HorizontalDivider(color = Color(0x33FFFFFF), thickness = 0.5.dp)

        // Magnitude
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Total", color = Color(0xAAFFFFFF), fontFamily = DataFontFamily, fontSize = 11.sp)
            Text(
                text = "${"%.1f".format(state.currentMagnitude)} µT",
                color = magnitudeColor,
                fontFamily = DataFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // XYZ components
        listOf(
            Triple("X", state.x, Color(0xFF7799FF)),
            Triple("Y", state.y, Color(0xFF77FF99)),
            Triple("Z", state.z, Color(0xFFFF7799))
        ).forEach { (axis, value, color) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(axis, color = Color(0x77FFFFFF), fontFamily = DataFontFamily, fontSize = 10.sp)
                val prefix = if (value >= 0f) "+" else ""
                Text(
                    text = "$prefix${"%.1f".format(value)} µT",
                    color = color,
                    fontFamily = DataFontFamily,
                    fontSize = 10.sp
                )
            }
        }

        HorizontalDivider(color = Color(0x33FFFFFF), thickness = 0.5.dp)

        // ICNIRP bar
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ICNIRP",
                    color = Color(0xAAFFFFFF),
                    fontFamily = DataFontFamily,
                    fontSize = 10.sp
                )
                Text(
                    text = "${(icnirpPercent * 100).roundToInt()}%",
                    color = icnirpBarColor(icnirpPercent),
                    fontFamily = DataFontFamily,
                    fontSize = 10.sp
                )
            }
            LinearProgressIndicator(
                progress = { icnirpPercent },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = icnirpBarColor(icnirpPercent),
                trackColor = Color(0x33FFFFFF)
            )
            Text(
                text = "Reference level (ICNIRP 200 µT)",
                color = Color(0x55FFFFFF),
                fontFamily = DataFontFamily,
                fontSize = 8.sp
            )
        }

        // Anomaly indicator — only visible when active
        if (state.isAnomaly) {
            HorizontalDivider(color = Color(0x33FFFFFF), thickness = 0.5.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "⚠",
                    color = AmberWarning.copy(alpha = pulseAlpha),
                    fontSize = 13.sp
                )
                Text(
                    text = "ANOMALY",
                    color = AmberWarning.copy(alpha = pulseAlpha),
                    fontFamily = DataFontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
    } // AnimatedVisibility
}

@Composable
private fun icnirpBarColor(percent: Float): Color = when {
    percent < 0.4f -> Color(0xFF44FF88)   // green — safe range
    percent < 0.7f -> Color(0xFFFFAA22)   // amber — caution
    else           -> Color(0xFFFF2222)   // red — approaching/above limit
}
