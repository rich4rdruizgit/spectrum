package co.doubler.spectrum.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.doubler.spectrum.domain.model.EcholocationState
import co.doubler.spectrum.ui.theme.BluetoothAccent
import co.doubler.spectrum.ui.theme.BluetoothNodeConnected
import co.doubler.spectrum.ui.theme.DataFontFamily
import co.doubler.spectrum.ui.theme.NearBlack

@Composable
fun EcholocationOverlay(
    state: EcholocationState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tracker = (state as? EcholocationState.Active)?.tracker
    val isFast = (state as? EcholocationState.Active)?.rotationTooFast ?: false
    val heading: Float? = when (state) {
        is EcholocationState.Active -> state.tracker.getHybridHeading()
        is EcholocationState.Result -> state.bestHeading
        else -> return
    }
    val confidence: Float = when (state) {
        is EcholocationState.Active -> state.tracker.getHybridConfidence()
        is EcholocationState.Result -> state.confidence
        else -> return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NearBlack.copy(alpha = 0.85f))
    ) {
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Text("✕", color = Color.White, fontSize = 20.sp)
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            val instruction = if (state is EcholocationState.Result) "Dirección estimada"
                              else "Rotá lentamente 360°"
            Text(
                text = instruction,
                fontFamily = DataFontFamily,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )

            if (isFast) {
                Text(
                    text = "Más despacio",
                    color = Color(0xFFFF8C00),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Canvas(modifier = Modifier.size(200.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.width / 2f - 20.dp.toPx()
                val strokeWidth = 8.dp.toPx()

                drawCircle(
                    color = Color.White.copy(alpha = 0.1f),
                    radius = radius,
                    center = center,
                    style = Stroke(strokeWidth)
                )

                val buckets = tracker?.getBucketStates() ?: BooleanArray(36) { true }
                buckets.forEachIndexed { i, isFilled ->
                    drawArc(
                        color = if (isFilled) BluetoothAccent else BluetoothAccent.copy(alpha = 0.15f),
                        startAngle = i * 10f - 90f,
                        sweepAngle = 9f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(strokeWidth)
                    )
                }

                if (heading != null) {
                    rotate(heading, pivot = center) {
                        val arrowLength = radius - 20.dp.toPx()
                        drawLine(
                            color = BluetoothNodeConnected,
                            start = center,
                            end = Offset(center.x, center.y - arrowLength),
                            strokeWidth = 4.dp.toPx()
                        )
                        val tipY = center.y - arrowLength
                        val headSize = 12.dp.toPx()
                        drawLine(
                            color = BluetoothNodeConnected,
                            start = Offset(center.x, tipY),
                            end = Offset(center.x - headSize * 0.6f, tipY + headSize),
                            strokeWidth = 4.dp.toPx()
                        )
                        drawLine(
                            color = BluetoothNodeConnected,
                            start = Offset(center.x, tipY),
                            end = Offset(center.x + headSize * 0.6f, tipY + headSize),
                            strokeWidth = 4.dp.toPx()
                        )
                    }
                }
            }

            Text(
                text = "CONFIANZA: ${(confidence * 100).toInt()}%",
                fontFamily = DataFontFamily,
                color = if (confidence >= 0.35f) BluetoothAccent else Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp
            )
        }
    }
}
