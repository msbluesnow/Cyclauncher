package dev.msbs.cyclauncher.ui.theme

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
         * Applies the Color Hue Angle Shift algorithm:
         * - On dark wallpapers ("Luminous highlight for dark wallpaper"):
         *   Shifts hue slightly toward warm luminous tones (~55° Gold/Amber) with high brightness (V >= 0.90)
         *   and vibrant saturation (S ~ 0.45..0.85) to create a glowing highlight effect.
         * - On light wallpapers ("Deep dark shade for light wallpaper"):
         *   Shifts hue slightly toward cool deep shadows (~255° Indigo/Violet) with darkened value (V ~ 0.38..0.52)
         *   and rich saturation (S ~ 0.65..0.95) to create a bold, high-contrast darkened accent.
         */
        fun applyAdaptiveHueShift(baseColor: Color, isDarkWallpaper: Boolean): Color {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(baseColor.toArgb(), hsv)
            val hue = hsv[0]
            val sat = hsv[1]
            val value = hsv[2]

            val effectiveHue = if (sat < 0.08f) {
                if (isDarkWallpaper) 200f else 220f
            } else hue

            if (isDarkWallpaper) {
                // Highlight shift toward warm luminous tones (~55° Gold/Amber)
                val warmAnchor = 55f
                val diff = (warmAnchor - effectiveHue + 540f) % 360f - 180f
                val shiftAmount = (diff * 0.22f).coerceIn(-25f, 25f)
                val shiftedHue = (effectiveHue + shiftAmount + 360f) % 360f

                val brightSat = if (sat < 0.08f) 0.35f else sat.coerceIn(0.45f, 0.85f)
                val brightVal = (value * 1.25f).coerceIn(0.90f, 1.0f)

                return Color(android.graphics.Color.HSVToColor(floatArrayOf(shiftedHue, brightSat, brightVal)))
            } else {
                // Shadow shift toward cool deep tones (~255° Indigo/Violet)
                val coolAnchor = 255f
                val diff = (coolAnchor - effectiveHue + 540f) % 360f - 180f
                val shiftAmount = (diff * 0.22f).coerceIn(-25f, 25f)
                val shiftedHue = (effectiveHue + shiftAmount + 360f) % 360f

                val richSat = if (sat < 0.08f) 0.40f else (sat * 1.15f).coerceIn(0.65f, 0.95f)
                val deepVal = (value * 0.70f).coerceIn(0.38f, 0.52f)

                return Color(android.graphics.Color.HSVToColor(floatArrayOf(shiftedHue, richSat, deepVal)))
            }
        }

        data class WallpaperTheme(
            val isDark: Boolean,
            val accentColor: Color
        )

        @Volatile
        private var lastResolvedTheme: WallpaperTheme? = null
        @Volatile
        private var lastWallpaperColorsPrimary: Int? = null
        @Volatile
        private var lastWallpaperColorsHints: Int? = null

        /**
         * Resolves both wallpaper darkness and adaptive accent color in a SINGLE pass,
         * with in-memory caching to eliminate redundant Binder IPC calls to WallpaperManager.
         */
        fun resolveWallpaperTheme(context: Context, cachedColors: WallpaperColors? = null): WallpaperTheme {
            return try {
                var colors = cachedColors
                if (colors == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    val wpManager = context.getSystemService(Context.WALLPAPER_SERVICE) as? WallpaperManager
                    colors = try {
                        wpManager?.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                            ?: wpManager?.getWallpaperColors(WallpaperManager.FLAG_LOCK)
                    } catch (_: Exception) { null }
                }

                val primaryArgb = colors?.primaryColor?.toArgb()
                val hints = colors?.colorHints ?: 0
                val cached = lastResolvedTheme
                if (cached != null && primaryArgb != null && primaryArgb == lastWallpaperColorsPrimary && hints == lastWallpaperColorsHints) {
                    return cached
                }

                val isWpDark = isWallpaperDark(context, colors)
                var rawColor: Color? = null
                if (primaryArgb != null) {
                    rawColor = Color(primaryArgb)
                }

                if (rawColor == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val scheme = if (isWpDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                    rawColor = scheme.primary
                }

                val finalColor = applyAdaptiveHueShift(rawColor ?: Color(0xFF19AEFF), isWpDark)
                val theme = WallpaperTheme(isDark = isWpDark, accentColor = finalColor)
                lastWallpaperColorsPrimary = primaryArgb
                lastWallpaperColorsHints = hints
                lastResolvedTheme = theme
                theme
            } catch (_: Exception) {
                val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                WallpaperTheme(isDark = isNight, accentColor = applyAdaptiveHueShift(Color(0xFF19AEFF), isNight))
            }
        }

        /**
         * Extracts the dynamic accent color from current wallpaper / theme
         * using adaptive Hue Angle Shift (bright tone on dark wallpapers, dark tone on light wallpapers).
         */
        fun getWallpaperAccentColor(context: Context, cachedColors: WallpaperColors? = null): Color {
            return resolveWallpaperTheme(context, cachedColors).accentColor
        }

        /**
         * Determines if the current wallpaper is dark.
         * Correctly analyzes WallpaperColors hints and weighted luminance so light wallpapers are never falsely marked as dark.
         */
        fun isWallpaperDark(context: Context, cachedColors: WallpaperColors? = null): Boolean {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    val colors = cachedColors ?: run {
                        val wpManager = context.getSystemService(Context.WALLPAPER_SERVICE) as? WallpaperManager
                        wpManager?.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                            ?: wpManager?.getWallpaperColors(WallpaperManager.FLAG_LOCK)
                    }
                    if (colors != null) {
                        val hints = colors.colorHints
                        // 1. Explicit OS flag for light wallpaper (requires dark text)
                        val supportsDarkText = (hints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
                        if (supportsDarkText) {
                            return false
                        }

                        // 2. Explicit OS flag for dark wallpaper (supports dark theme)
                        // HINT_SUPPORTS_DARK_THEME is 1 shl 1 = 2
                        val supportsDarkTheme = (hints and 2) != 0
                        if (supportsDarkTheme) {
                            return true
                        }

                        // 3. Measure true luminance from primary, secondary, and tertiary colors
                        val primaryColor = Color(colors.primaryColor.toArgb())
                        val primaryLum = primaryColor.luminance()
                        val secondaryLum = colors.secondaryColor?.let { Color(it.toArgb()).luminance() }
                        val tertiaryLum = colors.tertiaryColor?.let { Color(it.toArgb()).luminance() }

                        val weightedLum = when {
                            secondaryLum != null && tertiaryLum != null -> {
                                primaryLum * 0.50f + secondaryLum * 0.30f + tertiaryLum * 0.20f
                            }
                            secondaryLum != null -> {
                                primaryLum * 0.65f + secondaryLum * 0.35f
                            }
                            else -> {
                                primaryLum
                            }
                        }

                        // If weighted luminance >= 0.44, the wallpaper is light; otherwise dark
                        return weightedLum < 0.44f
                    }
                }
                val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                return isNight
            } catch (_: Exception) {
                val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                return isNight
            }
        }

        /**
         * Creates a dynamic Wallpaper AccentColor instance for the given context.
         */
        fun wallpaper(context: Context, cachedColors: WallpaperColors? = null): AccentColor {
            val dynamicColor = getWallpaperAccentColor(context, cachedColors)
            return AccentColor(
                name = "WALLPAPER",
                displayName = "Hue Angle Shift",
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
                    displayName = "Hue Angle Shift",
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
