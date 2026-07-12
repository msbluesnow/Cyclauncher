package dev.msbs.cyclauncher

import androidx.compose.ui.graphics.Color

enum class AccentColor(
    val displayName: String,
    val color: Color,
    val glowColor: Color
) {
    CYAN(
        displayName = "Cyan",
        color = Color(0xFF00F2FE),
        glowColor = Color(0x3300F2FE)
    ),
    VIOLET(
        displayName = "Violet",
        color = Color(0xFFD946EF),
        glowColor = Color(0x33D946EF)
    ),
    GREEN(
        displayName = "Green",
        color = Color(0xFF10B981),
        glowColor = Color(0x3310B981)
    ),
    PINK(
        displayName = "Pink",
        color = Color(0xFFF43F5E),
        glowColor = Color(0x33F43F5E)
    ),
    ORANGE(
        displayName = "Orange",
        color = Color(0xFFF97316),
        glowColor = Color(0x33F97316)
    );

    companion object {
        fun fromName(name: String): AccentColor {
            return try {
                valueOf(name)
            } catch (e: Exception) {
                CYAN
            }
        }
    }
}
