package co.doubler.spectrum.ui.theme

import androidx.compose.ui.graphics.Color
import co.doubler.spectrum.domain.model.WifiSecurityLevel

// ── Core Palette ──────────────────────────────────────────────
val NeonCyan = Color(0xFF00E5FF)
val ElectricPurple = Color(0xFFAA00FF)
val SignalGreen = Color(0xFF76FF03)
val NearBlack = Color(0xFF0A0A0F)
val DarkSurface = Color(0xFF1A1A2E)
val HotRed = Color(0xFFFF1744)

// ── On-Colors (contrast for readability) ─────────────────────
val OnDark = Color(0xFFE0E0E0)
val OnPrimary = Color(0xFF003544)
val OnSecondary = Color(0xFF3A0066)

// ── Mode Accent Colors ───────────────────────────────────────
val GhostAccent = Color(0xFFFF3366)         // Red/pink — WiFi ghost visualization
val CompeteAccent = Color(0xFF00FF88)       // Green — competition/signal strength
val BluetoothAccent = Color(0xFF5B7FFF)     // Blue — Bluetooth scanning
val MagneticAccent = Color(0xFFAA44FF)      // Purple — magnetic field

// ── Secondary Accents ────────────────────────────────────────
val CyanAccent = Color(0xFF00E5FF)          // Informational highlights
val AmberWarning = Color(0xFFFFAB00)        // Warnings (high magnetic fields, weak signals)

// ── Text Colors ──────────────────────────────────────────────
val TextPrimary = Color(0xFFF5F5F5)         // Bright white for primary text
val TextSecondary = Color(0xFF9E9E9E)       // Muted gray for secondary text
val TextDisabled = Color(0xFF616161)        // Disabled/tertiary text

// ── Danger / Warning ─────────────────────────────────────────
val DangerRed = Color(0xFFFF1744)           // Critical alerts, high magnetic anomaly
val WarningAmber = Color(0xFFFFAB00)        // Caution states

// ── HUD-Specific Colors ─────────────────────────────────────
val HudBracketColor = Color(0xFF00E5FF)     // Targeting bracket outlines
val HudScanningPulse = Color(0x8000E5FF)    // Semi-transparent pulse animation
val HudGridLines = Color(0x331A1A2E)        // Subtle grid overlay
val HudTextGlow = Color(0xFF76FF03)         // Glowing data readout text

// ── Ghost Wave Colors ───────────────────────────────────────
val GhostWaveGreen = Color(0xFF76FF03)         // User's network wave
val GhostWaveRed = Color(0xFFFF1744)           // Strong neighbor wave
val GhostWaveOrange = Color(0xFFFFAB00)        // Weak neighbor wave
val GhostInterferenceTint = Color(0x40FF6D00)  // Interference overlay tint
val GhostLabelBackground = Color(0xCC0A0A0F)   // Semi-transparent label bg

// ── Bluetooth Node Colors ────────────────────────────────────
val BluetoothNodeConnected = Color(0xFF5B7FFF)     // Connected device — accent blue
val BluetoothNodeDetected  = Color(0xFF9E9E9E)     // Detected but not connected — gray
val BluetoothNodeUnknown   = Color(0xFFFF1744)     // Unknown type device — red
val BluetoothLabelBackground = Color(0xCC0A0A1F)   // Semi-transparent label bg (blue-tinted)

// ── Compete Territory Colors (8-slot AP palette) ─────────────
val CompeteAp1 = Color(0xFF00FF88)   // Green  — slot 0
val CompeteAp2 = Color(0xFF00E5FF)   // Cyan   — slot 1
val CompeteAp3 = Color(0xFFFFD600)   // Amber  — slot 2
val CompeteAp4 = Color(0xFFFF6D00)   // Orange — slot 3
val CompeteAp5 = Color(0xFFE040FB)   // Purple — slot 4
val CompeteAp6 = Color(0xFFFF1744)   // Red    — slot 5
val CompeteAp7 = Color(0xFF64DD17)   // Lime   — slot 6
val CompeteAp8 = Color(0xFF00B0FF)   // Sky    — slot 7
val CompeteBorderColor = Color(0xFFFFFFFF)       // White pulsing border
val CompeteLabelBackground = Color(0xCC0A0A0F)   // Semi-transparent label bg

// ── WiFi Security Level Colors ────────────────────────────────
val SecurityOpen   = Color(0xFFFF4545)  // Red    — OPEN / WEP
val SecurityWeak   = Color(0xFFFF8C00)  // Orange — WPA + TKIP
val SecurityGood   = Color(0xFF45FF87)  // Green  — WPA2-CCMP only
val SecurityStrong = Color(0xFF00CEEF)  // Cyan   — WPA3

fun WifiSecurityLevel.toColor(): Color = when (this) {
    WifiSecurityLevel.OPEN,
    WifiSecurityLevel.WEP  -> SecurityOpen
    WifiSecurityLevel.WEAK   -> SecurityWeak
    WifiSecurityLevel.GOOD   -> SecurityGood
    WifiSecurityLevel.STRONG -> SecurityStrong
}
