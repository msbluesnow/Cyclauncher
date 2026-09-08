package dev.msbs.cyclauncher.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import dev.msbs.cyclauncher.model.AppColorBucket
import android.graphics.Color as AndroidColor

/**
 * Utility for extracting the dominant color bucket from application icons
 * using direct pixel-histogram analysis and HSV classification.
 */
object AppColorExtractor {

    // 32x32 = 1,024 pixels, provides ultra-fast (sub-millisecond) pixel-level precision
    private const val TARGET_BITMAP_SIZE = 32

    /**
     * Extracts the primary color category from a given application icon [Drawable].
     */
    fun extractColorBucket(drawable: Drawable): AppColorBucket {
        // For AdaptiveIconDrawable, prioritize the foreground layer to isolate
        // the app's brand graphic from any white/neutral background container.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && drawable is AdaptiveIconDrawable) {
            val foreground = drawable.foreground
            if (foreground != null) {
                val fgBitmap = drawableToBitmap(foreground, TARGET_BITMAP_SIZE)
                val fgBucket = analyzeBitmapPixels(fgBitmap)
                // If the foreground has distinct chromatic color(s), use it!
                // If the foreground is monochrome (e.g. white icon on colored background like Telegram/WhatsApp),
                // fall back to the full composite drawable.
                if (fgBucket != AppColorBucket.MONOCHROME) {
                    return fgBucket
                }
            }
        }

        // Full composite icon analysis
        val bitmap = drawableToBitmap(drawable, TARGET_BITMAP_SIZE)
        return analyzeBitmapPixels(bitmap)
    }

    private fun analyzeBitmapPixels(bitmap: Bitmap): AppColorBucket {
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height
        val pixels = IntArray(totalPixels)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val hsv = FloatArray(3)
        val bucketCounts = mutableMapOf<AppColorBucket, Int>()
        var chromaticCount = 0

        for (color in pixels) {
            val alpha = (color ushr 24) and 0xFF
            if (alpha < 40) continue // Skip transparent or semi-transparent edge pixels

            val r = (color ushr 16) and 0xFF
            val g = (color ushr 8) and 0xFF
            val b = color and 0xFF

            AndroidColor.RGBToHSV(r, g, b, hsv)
            val sat = hsv[1]
            val value = hsv[2]

            // Neutral / monochrome check (white, black, gray)
            if (sat < 0.18f || value < 0.14f || (sat < 0.22f && value > 0.88f)) {
                continue
            }

            val bucket = mapHueToBucket(hsv[0])
            bucketCounts[bucket] = (bucketCounts[bucket] ?: 0) + 1
            chromaticCount++
        }

        // If less than ~2% of pixels are chromatic, it's truly monochrome
        if (chromaticCount < 20) {
            return AppColorBucket.MONOCHROME
        }

        if (bucketCounts.size == 1) {
            return bucketCounts.keys.first()
        }

        val sorted = bucketCounts.entries.sortedByDescending { it.value }
        val top = sorted[0]
        val second = sorted[1]

        val topRatio = top.value.toFloat() / chromaticCount
        val secondRatio = second.value.toFloat() / chromaticCount

        // Если доминирующий цвет занимает более 69%, иконка не может быть в радуге.
        // Если доминирующий цвет занимает <= 69% и среди других цветов есть хотя бы один с долей >= 19%:
        val isMulticolor = topRatio <= 0.69f && secondRatio >= 0.19f

        if (isMulticolor) {
            return AppColorBucket.MULTICOLOR
        }

        return top.key
    }

    private fun mapHueToBucket(hue: Float): AppColorBucket {
        return when (hue) {
            in 0f..18f, in 340f..360f -> AppColorBucket.RED
            in 18f..45f -> AppColorBucket.ORANGE
            in 45f..72f -> AppColorBucket.YELLOW
            in 72f..165f -> AppColorBucket.GREEN
            in 165f..225f -> AppColorBucket.BLUE
            in 225f..340f -> AppColorBucket.PURPLE
            else -> AppColorBucket.RED
        }
    }

    private fun drawableToBitmap(drawable: Drawable, size: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            val src = drawable.bitmap
            if (src.width == size && src.height == size) {
                return src
            }
            if (src.width > 0 && src.height > 0) {
                return Bitmap.createScaledBitmap(src, size, size, true)
            }
        }
        val safeSize = size.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, safeSize, safeSize)
        drawable.draw(canvas)
        return bitmap
    }
}
