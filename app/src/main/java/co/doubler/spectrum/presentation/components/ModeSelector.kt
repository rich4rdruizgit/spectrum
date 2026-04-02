package co.doubler.spectrum.presentation.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import co.doubler.spectrum.domain.model.ScanMode
import co.doubler.spectrum.ui.theme.BluetoothAccent
import co.doubler.spectrum.ui.theme.CompeteAccent
import co.doubler.spectrum.ui.theme.DarkSurface
import co.doubler.spectrum.ui.theme.GhostAccent
import co.doubler.spectrum.ui.theme.MagneticAccent
import co.doubler.spectrum.ui.theme.NearBlack

private data class ModeTab(
    val mode: ScanMode,
    val icon: ImageVector,
    val label: String,
    val accentColor: Color
)

private val modeTabs = listOf(
    ModeTab(ScanMode.GHOST, Icons.Default.Wifi, "Ghost", GhostAccent),
    ModeTab(ScanMode.COMPETE, Icons.AutoMirrored.Filled.CompareArrows, "Compete", CompeteAccent),
    ModeTab(ScanMode.BLUETOOTH, Icons.Default.Bluetooth, "Bluetooth", BluetoothAccent),
    ModeTab(ScanMode.MAGNETIC, Icons.Default.Explore, "Magnetic", MagneticAccent)
)

@Composable
fun ModeSelector(
    currentMode: ScanMode,
    onModeSelected: (ScanMode) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = NearBlack,
        contentColor = DarkSurface
    ) {
        modeTabs.forEach { tab ->
            val selected = tab.mode == currentMode
            val iconScale by animateFloatAsState(
                targetValue = if (selected) 1.2f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "icon_scale_${tab.mode.name}"
            )
            NavigationBarItem(
                selected = selected,
                onClick = { onModeSelected(tab.mode) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer(scaleX = iconScale, scaleY = iconScale)
                    )
                },
                label = { Text(text = tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = tab.accentColor,
                    selectedTextColor = tab.accentColor,
                    unselectedIconColor = DarkSurface,
                    unselectedTextColor = DarkSurface,
                    indicatorColor = tab.accentColor.copy(alpha = 0.12f)
                )
            )
        }
    }
}
