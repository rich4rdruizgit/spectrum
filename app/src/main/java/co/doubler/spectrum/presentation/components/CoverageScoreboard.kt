package co.doubler.spectrum.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.doubler.spectrum.presentation.model.CompeteScoreEntry
import co.doubler.spectrum.ui.theme.CompeteAccent
import co.doubler.spectrum.ui.theme.CompeteLabelBackground
import co.doubler.spectrum.ui.theme.DataFontFamily
import co.doubler.spectrum.ui.theme.NearBlack
import co.doubler.spectrum.ui.theme.TextPrimary
import co.doubler.spectrum.ui.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * Collapsible bottom panel showing AP coverage rankings in Compete mode.
 *
 * Collapsed by default — shows a compact handle bar with the title and AP count.
 * Tapping expands the full ranked list with coverage bars, dBm, and rank deltas.
 */
@Composable
fun CoverageScoreboard(
    entries: List<CompeteScoreEntry>,
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        // ── Expanded list ──────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(250)) + fadeIn(tween(250)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(200))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NearBlack.copy(alpha = 0.9f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                entries.forEach { entry -> ScoreRow(entry = entry) }
            }
        }

        // ── Handle bar — always visible, tappable ──────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CompeteLabelBackground)
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "✂",
                fontSize = 11.sp,
                color = CompeteAccent
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "RANKING DE COBERTURA",
                fontFamily = DataFontFamily,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = CompeteAccent,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${entries.size} AP${if (entries.size > 1) "s" else ""}",
                fontFamily = DataFontFamily,
                fontSize = 9.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                contentDescription = if (expanded) "Colapsar" else "Expandir",
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ── Score Row ──────────────────────────────────────────────────────────────────

@Composable
private fun ScoreRow(entry: CompeteScoreEntry) {
    val apColor = Color(entry.color)
    val coveragePct = (entry.coveragePercent * 100).roundToInt()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Rank number ────────────────────────────────────────────────────────
        Text(
            text = "${entry.rank}.",
            fontFamily = DataFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (entry.rank == 1) CompeteAccent else TextSecondary,
            modifier = Modifier.width(20.dp)
        )

        // ── Territory color swatch ─────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(apColor)
        )

        // ── SSID name ─────────────────────────────────────────────────────────
        Text(
            text = entry.ssid.ifEmpty { "Hidden" }.take(16),
            fontFamily = DataFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )

        // ── Coverage bar + percent ─────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LinearProgressIndicator(
                progress = { entry.coveragePercent },
                modifier = Modifier
                    .width(64.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = apColor,
                trackColor = apColor.copy(alpha = 0.2f)
            )
            Text(
                text = "$coveragePct%",
                fontFamily = DataFontFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = apColor,
                modifier = Modifier.width(30.dp)
            )
        }

        // ── dBm ───────────────────────────────────────────────────────────────
        Text(
            text = "${entry.rssi}dBm",
            fontFamily = DataFontFamily,
            fontSize = 9.sp,
            color = TextSecondary,
            modifier = Modifier.width(44.dp)
        )

        // ── Rank delta arrow ──────────────────────────────────────────────────
        val (deltaText, deltaColor) = when {
            entry.rankDelta > 0 -> "↑" to Color(0xFF00FF88)
            entry.rankDelta < 0 -> "↓" to Color(0xFFFF1744)
            else                -> "–" to TextSecondary
        }
        Text(
            text = deltaText,
            fontFamily = DataFontFamily,
            fontSize = 11.sp,
            color = deltaColor
        )
    }
}
