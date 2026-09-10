package dev.msbs.cyclauncher.model

import androidx.compose.ui.graphics.Color

/**
 * Primary color categories used for application icon color filtering and search.
 */
enum class AppColorBucket(
    val displayColor: Color,
    val hexCode: String
) {
    MULTICOLOR(
        displayColor = Color.Transparent,
        hexCode = "#MULTI"
    ),
    RED(
        displayColor = Color(0xFFE53935),
        hexCode = "#E53935"
    ),
    ORANGE(
        displayColor = Color(0xFFFB8C00),
        hexCode = "#FB8C00"
    ),
    YELLOW(
        displayColor = Color(0xFFFDD835),
        hexCode = "#FDD835"
    ),
    GREEN(
        displayColor = Color(0xFF43A047),
        hexCode = "#43A047"
    ),
    BLUE(
        displayColor = Color(0xFF1E88E5),
        hexCode = "#1E88E5"
    ),
    PURPLE(
        displayColor = Color(0xFF6A40EC),
        hexCode = "#6A40EC"
    ),
    MONOCHROME(
        displayColor = Color(0xFF757575),
        hexCode = "#757575"
    );

    companion object {
        private val byNameMap: Map<String, AppColorBucket> = entries.associateBy { it.name }

        fun fromNameOrNull(name: String?): AppColorBucket? {
            if (name == null) return null
            return byNameMap[name]
        }
    }
}
