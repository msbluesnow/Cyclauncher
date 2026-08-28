package dev.msbs.cyclauncher.ui.theme

import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Represents an accent color theme for Cyclauncher.
 * Features the official Echo Icon Theme color palette, dynamic wallpaper colors (Material You),
 * and user-defined custom colors.
 */
data class AccentColor(
    val name: String,
    val displayName: String,
    val color: Color,
    val glowColor: Color = color.copy(alpha = 0.2f),
    val isDynamicWallpaper: Boolean = false,
    val isCustom: Boolean = false,
    val customHex: String? = null
) {
    companion object {
        // --- Official Echo Icon Theme Palette ---

        // Echo Blue
        val ECHO_BLUE_LIGHT = AccentColor("ECHO_BLUE_LIGHT", "Sky", Color(0xFF19AEFF), Color(0x3319AEFF))
        val ECHO_BLUE = AccentColor("ECHO_BLUE", "Blue", Color(0xFF0084C8), Color(0x330084C8))
        val ECHO_BLUE_DARK = AccentColor("ECHO_BLUE_DARK", "Deep Blue", Color(0xFF005C94), Color(0x33005C94))

        // Echo Green
        val ECHO_GREEN_LIGHT = AccentColor("ECHO_GREEN_LIGHT", "Lime", Color(0xFFCCFF42), Color(0x33CCFF42))
        val ECHO_GREEN = AccentColor("ECHO_GREEN", "Green", Color(0xFF9ADE00), Color(0x339ADE00))
        val ECHO_GREEN_DARK = AccentColor("ECHO_GREEN_DARK", "Forest", Color(0xFF009100), Color(0x33009100))

        // Echo Orange & Yellow
        val ECHO_YELLOW = AccentColor("ECHO_YELLOW", "Yellow", Color(0xFFFFFF3E), Color(0x33FFFF3E))
        val ECHO_ORANGE = AccentColor("ECHO_ORANGE", "Orange", Color(0xFFFF9900), Color(0x33FF9900))
        val ECHO_ORANGE_DARK = AccentColor("ECHO_ORANGE_DARK", "Amber", Color(0xFFFF6600), Color(0x33FF6600))

        // Echo Red
        val ECHO_RED_LIGHT = AccentColor("ECHO_RED_LIGHT", "Coral", Color(0xFFFF4141), Color(0x33FF4141))
        val ECHO_RED = AccentColor("ECHO_RED", "Red", Color(0xFFDC0000), Color(0x33DC0000))
        val ECHO_RED_DARK = AccentColor("ECHO_RED_DARK", "Crimson", Color(0xFFB50000), Color(0x33B50000))

        // Echo Purple
        val ECHO_PURPLE_LIGHT = AccentColor("ECHO_PURPLE_LIGHT", "Lilac", Color(0xFFF1CAFF), Color(0x33F1CAFF))
        val ECHO_PURPLE = AccentColor("ECHO_PURPLE", "Purple", Color(0xFFD76CFF), Color(0x33D76CFF))
        val ECHO_PURPLE_DARK = AccentColor("ECHO_PURPLE_DARK", "Violet", Color(0xFFBA00FF), Color(0x33BA00FF))

        // Echo Brown
        val ECHO_BROWN_LIGHT = AccentColor("ECHO_BROWN_LIGHT", "Sand", Color(0xFFFFC022), Color(0x33FFC022))
        val ECHO_BROWN = AccentColor("ECHO_BROWN", "Ochre", Color(0xFFB88100), Color(0x33B88100))
        val ECHO_BROWN_DARK = AccentColor("ECHO_BROWN_DARK", "Brown", Color(0xFF804D00), Color(0x33804D00))

        // Echo Metallic
        val ECHO_METALLIC_LIGHT = AccentColor("ECHO_METALLIC_LIGHT", "Steel", Color(0xFFBDCDD4), Color(0x33BDCDD4))
        val ECHO_METALLIC = AccentColor("ECHO_METALLIC", "Slate", Color(0xFF9EABB0), Color(0x339EABB0))
        val ECHO_METALLIC_DARK = AccentColor("ECHO_METALLIC_DARK", "Navy Slate", Color(0xFF364E59), Color(0x33364E59))
        val ECHO_METALLIC_DEEP = AccentColor("ECHO_METALLIC_DEEP", "Abyss", Color(0xFF0E232E), Color(0x330E232E))

        // Echo Monochrome
        val ECHO_WHITE = AccentColor("ECHO_WHITE", "White", Color(0xFFFFFFFF), Color(0x33FFFFFF))
        val ECHO_GREY_LIGHT = AccentColor("ECHO_GREY_LIGHT", "Platinum", Color(0xFFCCCCCC), Color(0x33CCCCCC))
        val ECHO_GREY = AccentColor("ECHO_GREY", "Grey", Color(0xFF999999), Color(0x33999999))
        val ECHO_GREY_DARK = AccentColor("ECHO_GREY_DARK", "Graphite", Color(0xFF666666), Color(0x33666666))
        val ECHO_CHARCOAL = AccentColor("ECHO_CHARCOAL", "Charcoal", Color(0xFF2D2D2D), Color(0x332D2D2D))

        // --- Backward Compatibility Aliases ---
        val SKY = ECHO_BLUE_LIGHT
        val LAVENDER = ECHO_PURPLE_LIGHT
        val MINT = ECHO_GREEN
        val ROSE = ECHO_RED_LIGHT
        val PEACH = ECHO_ORANGE
        val SNOW = ECHO_WHITE

        val DARK_SKY = ECHO_BLUE
        val DARK_LAVENDER = ECHO_PURPLE_DARK
        val DARK_MINT = ECHO_GREEN_DARK
        val DARK_ROSE = ECHO_RED_DARK
        val DARK_PEACH = ECHO_ORANGE_DARK
        val DARK_SLATE = ECHO_METALLIC_DARK

        /**
         * Curated Echo Icon Theme light & dark preset pairs for UI grid.
         */
        val PRESET_PAIRS = listOf(
            ECHO_BLUE_LIGHT to ECHO_BLUE_DARK,
            ECHO_GREEN_LIGHT to ECHO_GREEN_DARK,
            ECHO_YELLOW to ECHO_ORANGE_DARK,
            ECHO_RED_LIGHT to ECHO_RED_DARK,
            ECHO_PURPLE_LIGHT to ECHO_PURPLE_DARK,
            ECHO_BROWN_LIGHT to ECHO_BROWN_DARK,
            ECHO_METALLIC_LIGHT to ECHO_METALLIC_DARK,
            ECHO_WHITE to ECHO_CHARCOAL
        )

        /**
         * Extracts the primary dynamic accent color from current wallpaper / Material You theme.
         */
        fun getWallpaperAccentColor(context: Context): Color {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                    val scheme = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                    return scheme.primary
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    val wpManager = context.getSystemService(Context.WALLPAPER_SERVICE) as? WallpaperManager
                    val colors = wpManager?.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                    val primary = colors?.primaryColor
                    if (primary != null) {
                        return Color(primary.toArgb())
                    }
                }
            } catch (_: Exception) {}
            return Color(0xFF19AEFF)
        }

        /**
         * Creates a dynamic Wallpaper AccentColor instance for the given context.
         */
        fun wallpaper(context: Context): AccentColor {
            val dynamicColor = getWallpaperAccentColor(context)
            return AccentColor(
                name = "WALLPAPER",
                displayName = "Wallpaper Color",
                color = dynamicColor,
                glowColor = dynamicColor.copy(alpha = 0.2f),
                isDynamicWallpaper = true
            )
        }

        /**
         * Creates a custom AccentColor instance with a specified Color.
         */
        fun custom(color: Color): AccentColor {
            val argb = color.toArgb()
            val hex = String.format("%06X", 0xFFFFFF and argb)
            return AccentColor(
                name = "CUSTOM_$hex",
                displayName = "Custom (#$hex)",
                color = color,
                glowColor = color.copy(alpha = 0.2f),
                isCustom = true,
                customHex = hex
            )
        }

        /**
         * Creates an AccentColor instance from a hex string (e.g. "FF4081", "#FF4081", "CUSTOM_FF4081").
         */
        fun fromHex(hexStr: String): AccentColor {
            val clean = hexStr.removePrefix("#").removePrefix("CUSTOM_").removePrefix("0x").trim()
            val colorInt = try {
                val parsed = clean.toLong(16)
                if (clean.length <= 6) (0xFF000000 or parsed).toInt() else parsed.toInt()
            } catch (_: Exception) {
                0xFF19AEFF.toInt()
            }
            return custom(Color(colorInt))
        }

        /**
         * Resolves an AccentColor from its serialized name key.
         */
        fun fromName(name: String, context: Context? = null): AccentColor {
            if (name == "WALLPAPER") {
                return if (context != null) wallpaper(context) else AccentColor(
                    name = "WALLPAPER",
                    displayName = "Wallpaper Color",
                    color = Color(0xFF19AEFF),
                    glowColor = Color(0x3319AEFF),
                    isDynamicWallpaper = true
                )
            }
            if (name.startsWith("CUSTOM_") || name.startsWith("#")) {
                return fromHex(name)
            }
            return when (name) {
                // Echo Blue
                "ECHO_BLUE_LIGHT", "SKY" -> ECHO_BLUE_LIGHT
                "ECHO_BLUE", "DARK_SKY" -> ECHO_BLUE
                "ECHO_BLUE_DARK" -> ECHO_BLUE_DARK

                // Echo Green
                "ECHO_GREEN_LIGHT" -> ECHO_GREEN_LIGHT
                "ECHO_GREEN", "MINT" -> ECHO_GREEN
                "ECHO_GREEN_DARK", "DARK_MINT" -> ECHO_GREEN_DARK

                // Echo Orange & Yellow
                "ECHO_YELLOW" -> ECHO_YELLOW
                "ECHO_ORANGE", "PEACH" -> ECHO_ORANGE
                "ECHO_ORANGE_DARK", "DARK_PEACH" -> ECHO_ORANGE_DARK

                // Echo Red
                "ECHO_RED_LIGHT", "ROSE" -> ECHO_RED_LIGHT
                "ECHO_RED" -> ECHO_RED
                "ECHO_RED_DARK", "DARK_ROSE" -> ECHO_RED_DARK

                // Echo Purple
                "ECHO_PURPLE_LIGHT", "LAVENDER" -> ECHO_PURPLE_LIGHT
                "ECHO_PURPLE" -> ECHO_PURPLE
                "ECHO_PURPLE_DARK", "DARK_LAVENDER" -> ECHO_PURPLE_DARK

                // Echo Brown
                "ECHO_BROWN_LIGHT" -> ECHO_BROWN_LIGHT
                "ECHO_BROWN" -> ECHO_BROWN
                "ECHO_BROWN_DARK" -> ECHO_BROWN_DARK

                // Echo Metallic
                "ECHO_METALLIC_LIGHT" -> ECHO_METALLIC_LIGHT
                "ECHO_METALLIC" -> ECHO_METALLIC
                "ECHO_METALLIC_DARK", "DARK_SLATE" -> ECHO_METALLIC_DARK
                "ECHO_METALLIC_DEEP" -> ECHO_METALLIC_DEEP

                // Echo Monochrome
                "ECHO_WHITE", "SNOW" -> ECHO_WHITE
                "ECHO_GREY_LIGHT" -> ECHO_GREY_LIGHT
                "ECHO_GREY" -> ECHO_GREY
                "ECHO_GREY_DARK" -> ECHO_GREY_DARK
                "ECHO_CHARCOAL" -> ECHO_CHARCOAL

                else -> ECHO_BLUE_LIGHT
            }
        }
    }
}
