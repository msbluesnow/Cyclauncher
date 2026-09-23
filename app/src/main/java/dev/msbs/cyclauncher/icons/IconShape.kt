package dev.msbs.cyclauncher.icons

import android.content.Context
import android.graphics.Path
import android.graphics.RectF
import android.provider.Settings
import androidx.core.graphics.PathParser

/**
 * Utility for resolving the active system icon shape Path mask.
 */
object IconShapeHelper {

    @Volatile
    private var cachedSystemPath: Path? = null

    /**
     * Resolves the Path for a normalized 100x100 box corresponding to the system icon shape overlay.
     * Caches the resolved path to prevent repeated Binder IPC queries and SVG parsing.
     */
    fun getSystemPath(context: Context, forceRefresh: Boolean = false): Path {
        if (!forceRefresh) {
            cachedSystemPath?.let { return Path(it) }
        }

        val cr = context.contentResolver
        var shapeName = ""

        try {
            val jsonStr = Settings.Secure.getString(cr, "theme_customization_overlay_packages")
            if (!jsonStr.isNullOrBlank()) {
                shapeName = jsonStr.lowercase()
            }
        } catch (_: Exception) {}

        if (shapeName.isBlank()) {
            val keys = listOf("icon_shape", "theme_icon_shape", "current_icon_shape")
            for (key in keys) {
                try {
                    val valStr = Settings.Secure.getString(cr, key) ?: Settings.System.getString(cr, key)
                    if (!valStr.isNullOrBlank()) {
                        shapeName = valStr.lowercase()
                        break
                    }
                } catch (_: Exception) {}
            }
        }

        val resolved = when {
            shapeName.contains("circle") || shapeName.contains("circular") ->
                Path().apply { addCircle(50f, 50f, 50f, Path.Direction.CW) }
            shapeName.contains("rounded") || shapeName.contains("roundedrect") ->
                Path().apply { addRoundRect(RectF(0f, 0f, 100f, 100f), 22f, 22f, Path.Direction.CW) }
            shapeName.contains("teardrop") ->
                Path().apply { addRoundRect(RectF(0f, 0f, 100f, 100f), floatArrayOf(50f, 50f, 50f, 50f, 50f, 50f, 12f, 12f), Path.Direction.CW) }
            shapeName.contains("cylinder") || shapeName.contains("vessel") ->
                Path().apply { addRoundRect(RectF(0f, 0f, 100f, 100f), 38f, 38f, Path.Direction.CW) }
            shapeName.contains("hexagon") ->
                parseOrFallback("M 50 0 L 93.3 25 L 93.3 75 L 50 100 L 6.7 75 L 6.7 25 Z", 25f)
            else ->
                // Default: Squircle (modern Android standard)
                parseOrFallback("M 50 0 C 80 0 100 20 100 50 C 100 80 80 100 50 100 C 20 100 0 80 0 50 C 0 20 20 0 50 0 Z", 30f)
        }

        cachedSystemPath = resolved
        return Path(resolved)
    }

    /**
     * Clears the cached system path so it can be re-queried upon system theme changes.
     */
    fun invalidateCache() {
        cachedSystemPath = null
    }

    private fun parseOrFallback(pathData: String, cornerRadius: Float): Path {
        return try {
            PathParser.createPathFromPathData(pathData)
                ?: Path().apply { addRoundRect(RectF(0f, 0f, 100f, 100f), cornerRadius, cornerRadius, Path.Direction.CW) }
        } catch (_: Exception) {
            Path().apply { addRoundRect(RectF(0f, 0f, 100f, 100f), cornerRadius, cornerRadius, Path.Direction.CW) }
        }
    }
}
