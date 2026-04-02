package co.doubler.spectrum.domain.model

/**
 * Represents the four visualization modes of Invisible Spectrum.
 *
 * Colors are stored as ARGB Long values to keep the domain layer free of
 * Compose/Android framework dependencies. Map to `androidx.compose.ui.graphics.Color`
 * in the presentation layer via `Color(primaryColor)`.
 */
enum class ScanMode(
    val displayName: String,
    val description: String,
    val primaryColor: Long // ARGB hex — use Color(primaryColor) in Compose
) {
    GHOST(
        displayName = "Ghost",
        description = "Visualize hidden WiFi networks as spectral entities",
        primaryColor = 0xFFFF1744
    ),
    COMPETE(
        displayName = "Compete",
        description = "Compare signal strengths in real-time AR overlay",
        primaryColor = 0xFF76FF03
    ),
    BLUETOOTH(
        displayName = "Bluetooth",
        description = "Map nearby Bluetooth devices in 3D space",
        primaryColor = 0xFF2979FF
    ),
    MAGNETIC(
        displayName = "Magnetic Field",
        description = "Sense and render magnetic field distortions",
        primaryColor = 0xFFE040FB
    )
}
