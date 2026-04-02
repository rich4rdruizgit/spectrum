package co.doubler.spectrum.presentation.screen

import android.annotation.SuppressLint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.doubler.spectrum.domain.model.ScanMode
import co.doubler.spectrum.presentation.components.ArSceneView
import co.doubler.spectrum.presentation.model.AnomalyEvent
import co.doubler.spectrum.presentation.viewmodel.MagneticViewModel
import co.doubler.spectrum.rendering.magnetic.MagneticOverlayRenderer
import co.doubler.spectrum.ui.theme.DataFontFamily
import co.doubler.spectrum.ui.theme.MagneticAccent
import co.doubler.spectrum.ui.theme.NearBlack
import co.doubler.spectrum.ui.theme.TextSecondary
import co.doubler.spectrum.util.Constants
import co.doubler.spectrum.util.PermissionGroups
import co.doubler.spectrum.util.rememberPermissionState

@Composable
fun MagFieldScreen(
    viewModel: MagneticViewModel = hiltViewModel()
) {
    val permissionState = rememberPermissionState(
        PermissionGroups.forMode(ScanMode.MAGNETIC)
    )

    if (!permissionState.allGranted) {
        MagneticPermissionRequestContent(
            deniedPermissions = permissionState.deniedPermissions,
            onRequestPermissions = permissionState.requestPermissions
        )
    } else {
        MagneticArContent(viewModel = viewModel)
    }
}

// ── AR Content ───────────────────────────────────────────────────────

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun MagneticArContent(viewModel: MagneticViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val magneticRenderer = remember(viewModel) {
        MagneticOverlayRenderer(
            context = context,
            magneticDataRef = viewModel.magneticDataRef
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ArSceneView(
            sessionManager = viewModel.sessionManager,
            scanMode = ScanMode.MAGNETIC,
            isScanning = uiState.isScanning,
            overlayRenderer = magneticRenderer,
            isAnomalyActive = uiState.isAnomaly,
            subtitle = "CAMPOS MAGNÉTICOS",
            iconEmoji = "🧲",
            hudContent = {
                // ── Anomaly nodes positioned across screen ──
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    uiState.anomalyEvents.forEach { event ->
                        val nodeX = (event.screenX * constraints.maxWidth).toInt()
                        val nodeY = (event.screenY * constraints.maxHeight).toInt()
                        AnomalyNode(
                            event = event,
                            modifier = Modifier.offset {
                                IntOffset(nodeX - 30.dp.roundToPx(), nodeY - 30.dp.roundToPx())
                            }
                        )
                    }
                }

                // ── Stats bar at bottom ──
                MagFieldStatsBar(
                    averageMagnitude = uiState.averageMagnitude,
                    isSafe = uiState.isSafe,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        )

        if (uiState.isScanning) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MagneticAccent,
                strokeWidth = 2.dp
            )
        }
    }
}

// ── Anomaly Node ─────────────────────────────────────────────────────

@Composable
private fun AnomalyNode(
    event: AnomalyEvent,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "node_pulse_${event.id}")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val nodeColor = magnitudeNodeColor(event.magnitude)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // µT value
        Text(
            text = "${"%.1f".format(event.magnitude)} µT",
            fontFamily = DataFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = nodeColor
        )

        // Pulsing circle
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(nodeColor.copy(alpha = pulseAlpha * 0.2f))
                .border(1.5.dp, nodeColor.copy(alpha = pulseAlpha), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(nodeColor)
            )
        }

        // Signature label
        Text(
            text = event.signature,
            fontFamily = DataFontFamily,
            fontSize = 9.sp,
            color = nodeColor.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

// ── Stats Bar ────────────────────────────────────────────────────────

@Composable
private fun MagFieldStatsBar(
    averageMagnitude: Float,
    isSafe: Boolean,
    modifier: Modifier = Modifier
) {
    val safeColor = if (isSafe) Color(0xFF44FF88) else Color(0xFFFF2222)
    val safeText = if (isSafe)
        "✓ DENTRO DE LÍMITES SEGUROS (< ${Constants.MAG_SAFE_LIMIT_UT} µT)"
    else
        "⚠ POR ENCIMA DEL LÍMITE ICNIRP (${Constants.MAG_SAFE_LIMIT_UT} µT)"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(NearBlack.copy(alpha = 0.88f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "🧲 ",
                fontSize = 13.sp
            )
            Text(
                text = "CAMPO PROMEDIO: ${"%.1f".format(averageMagnitude)} µT",
                fontFamily = DataFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MagneticAccent
            )
        }
        Text(
            text = safeText,
            fontFamily = DataFontFamily,
            fontSize = 10.sp,
            color = safeColor
        )
    }
}

// ── Helpers ──────────────────────────────────────────────────────────

private fun magnitudeNodeColor(magnitude: Float): Color = when {
    magnitude > 100f -> Color(0xFFFF2222)   // red — high
    magnitude > 50f  -> Color(0xFFFF8C00)   // orange — medium
    else             -> Color(0xFFE040FB)   // purple — magnetic accent / low
}

// ── Permission Request UI ────────────────────────────────────────────

@Composable
private fun MagneticPermissionRequestContent(
    deniedPermissions: List<String>,
    onRequestPermissions: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NearBlack),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "MAG FIELD",
                fontFamily = DataFontFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MagneticAccent
            )
            Text(
                text = "Se necesita permiso de cámara para visualizar el campo magnético en AR.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Faltan: ${deniedPermissions.size} permiso(s)",
                fontFamily = DataFontFamily,
                fontSize = 11.sp,
                color = TextSecondary.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onRequestPermissions,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MagneticAccent,
                    contentColor = NearBlack
                )
            ) {
                Text(text = "Conceder permiso", fontWeight = FontWeight.Bold)
            }
        }
    }
}
