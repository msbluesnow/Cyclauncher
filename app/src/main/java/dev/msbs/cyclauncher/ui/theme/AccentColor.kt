package dev.msbs.cyclauncher.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Defines the available theme accent colors for the application.
 * Each accent color has a display name, a primary color, and a glow/shadow overlay color.
 */
enum class AccentColor(
    val displayName: String,
    val color: Color,
    val glowColor: Color
) {
    SKY("Sky Blue", Color(0xFF38BDF8), Color(0x3338BDF8)),
    LAVENDER("Lavender", Color(0xFFA78BFA), Color(0x33A78BFA)),
    MINT("Mint", Color(0xFF2DD4BF), Color(0x332DD4BF)),
    ROSE("Rose", Color(0xFFFB7185), Color(0x33FB7185)),
    PEACH("Peach", Color(0xFFFB923C), Color(0x33FB923C)),
    AMBER("Amber", Color(0xFFFBBF24), Color(0x33FBBF24)),
    LIME("Lime", Color(0xFFA3E635), Color(0x33A3E635)),
    INDIGO("Indigo", Color(0xFF818CF8), Color(0x33818CF8)),
    FUCHSIA("Fuchsia", Color(0xFFE879F9), Color(0x33E879F9)),
    SNOW("Snow", Color(0xFFF1F5F9), Color(0x33F1F5F9));

    companion object {
        /**
         * Resolves the [AccentColor] enum element corresponding to the given string name.
         * Falls back to [SKY] if no match is found.
         *
         * @param name The name of the accent color to lookup.
         * @return The resolved AccentColor instance.
         */
        fun fromName(name: String): AccentColor {
            return try {
                valueOf(name)
            } catch (e: Exception) {
                SKY
            }
        }
    }
}
