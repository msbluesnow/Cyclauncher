package dev.msbs.cyclauncher.ui.theme

import androidx.compose.ui.graphics.Color

/** Available theme accent color presets. */
enum class AccentColor(
    val displayName: String,
    val color: Color,
    val glowColor: Color
) {
    SKY("Sapphire", Color(0xFF74C7EC), Color(0x3374C7EC)),
    LAVENDER("Lavender", Color(0xFFB4BEFE), Color(0x33B4BEFE)),
    MINT("Emerald Mint", Color(0xFF94E2D5), Color(0x3394E2D5)),
    ROSE("Flamingo", Color(0xFFF2CDCD), Color(0x33F2CDCD)),
    PEACH("Peach", Color(0xFFFAB387), Color(0x33FAB387)),
    SNOW("White", Color(0xFFFFFFFF), Color(0x33FFFFFF)),

    DARK_SKY("Royal Blue", Color(0xFF1E66F5), Color(0x331E66F5)),
    DARK_LAVENDER("Deep Mauve", Color(0xFF8839EF), Color(0x338839EF)),
    DARK_MINT("Nordic Teal", Color(0xFF179299), Color(0x33179299)),
    DARK_ROSE("Maroon", Color(0xFFE64553), Color(0x33E64553)),
    DARK_PEACH("Burnt Amber", Color(0xFFFE640B), Color(0x33FE640B)),
    DARK_SLATE("Dark Slate", Color(0xFF18181B), Color(0x3318181B));

    companion object {
        fun fromName(name: String): AccentColor {
            return try { valueOf(name) } catch (_: Exception) { SKY }
        }
    }
}
