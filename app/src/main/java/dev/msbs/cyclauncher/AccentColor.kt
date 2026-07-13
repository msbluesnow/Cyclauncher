package dev.msbs.cyclauncher

import androidx.compose.ui.graphics.Color

enum class AccentColor(
    val displayName: String,
    val color: Color,
    val glowColor: Color
) {
    CYAN("Cyan", Color(0xFF00F2FE), Color(0x3300F2FE)),
    VIOLET("Violet", Color(0xFFD946EF), Color(0x33D946EF)),
    GREEN("Green", Color(0xFF10B981), Color(0x3310B981)),
    PINK("Pink", Color(0xFFF43F5E), Color(0x33F43F5E)),
    ORANGE("Orange", Color(0xFFF97316), Color(0x33F97316)),
    BLUE("Blue", Color(0xFF3B82F6), Color(0x333B82F6)),
    RED("Red", Color(0xFFEF4444), Color(0x33EF4444)),
    YELLOW("Yellow", Color(0xFFEAB308), Color(0x33EAB308)),
    WHITE("White", Color(0xFFFFFFFF), Color(0x33FFFFFF));

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
