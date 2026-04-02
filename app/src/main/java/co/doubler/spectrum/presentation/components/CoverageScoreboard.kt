package co.doubler.spectrum.presentation.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import co.doubler.spectrum.ui.theme.TextPrimary
import co.doubler.spectrum.ui.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * Top panel Composable showing AP coverage rankings in Compete mode.
 *
 * Displayed at the top of the screen (unlike Ghost/BT modes that use bottom panels).
 * Shows: rank, SSID snippet, territory color swatch, RSSI, coverage %, rank delta arrow.
 *
 * @param entries Ranked list of score entries, already sorted by rank ascending.
 * @param modifier Composable modifier.
 */
@Composable
fun CoverageScoreboard(
    entries: List<CompeteScoreEntry>,
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
            .background(CompeteLabelBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "COVERAGE RANKING",
                fontFamily = DataFontFamily,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = CompeteAccent,
                letterSpacing = 1.5.sp
            )
            Text(
                text = "${entries.size} AP${if (entries.size > 1) "s" else ""}",
                fontFamily = DataFontFamily,
                fontSize = 9.sp,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // ── Score rows ───────────────────────────────────────────────────────
        entries.forEach { entry ->
            ScoreRow(entry = entry)
        }
    }
}

@Composable
private fun ScoreRow(entry: CompeteScoreEntry) {
    val apColor = Color(entry.color)
    val coveragePct = (entry.coveragePercent * 100).roundToInt()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Rank number ──────────────────────────────────────────────────────
        Text(
            text = "#${entry.rank}",
            fontFamily = DataFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (entry.rank == 1) CompeteAccent else TextSecondary,
            modifier = Modifier.width(24.dp)
        )

        // ── Territory color swatch ───────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(apColor)
        )

        // ── SSID name (truncated) ────────────────────────────────────────────
        Text(
            text = entry.ssid.ifEmpty { "Hidden" }.take(16),
            fontFamily = DataFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )

        // ── Coverage progress + percent ──────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LinearProgressIndicator(
                progress = { entry.coveragePercent },
                modifier = Modifier
                    .width(48.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = apColor,
                trackColor = apColor.copy(alpha = 0.2f)
            )
            Text(
                text = "$coveragePct%",
                fontFamily = DataFontFamily,
                fontSize = 10.sp,
                color = apColor,
                modifier = Modifier.width(28.dp)
            )
        }

        // ── RSSI ─────────────────────────────────────────────────────────────
        Text(
            text = "${entry.rssi}dBm",
            fontFamily = DataFontFamily,
            fontSize = 9.sp,
            color = TextSecondary,
            modifier = Modifier.width(44.dp)
        )

        // ── Rank delta arrow ─────────────────────────────────────────────────
        val (deltaText, deltaColor) = when {
            entry.rankDelta > 0 -> "↑" to Color(0xFF00FF88)   // moved up — green
            entry.rankDelta < 0 -> "↓" to Color(0xFFFF1744)   // moved down — red
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
