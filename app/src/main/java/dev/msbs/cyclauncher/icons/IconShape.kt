package dev.msbs.cyclauncher.icons

import android.content.Context
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import android.provider.Settings
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.core.graphics.PathParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Utility for resolving the active system icon shape Path mask.
 */
object IconShapeHelper {

    private data class ShapeSnapshot(
        val generation: Long,
        val basePath: Path,
        val composeShape: Shape
    )

    private data class ScaledPathEntry(
        val generation: Long,
        val path: Path
    )

    private val generationCounter = AtomicLong(1L)

    @Volatile
    private var snapshot: ShapeSnapshot? = null

    private val scaledPathCache = ConcurrentHashMap<Int, ScaledPathEntry>()

    val shapeGeneration: Long
        get() = generationCounter.get()

    val defaultComposeShape: Shape by lazy {
        buildComposeShape(
            parseOrFallback("M 50 0 C 80 0 100 20 100 50 C 100 80 80 100 50 100 C 20 100 0 80 0 50 C 0 20 20 0 50 0 Z", 30f)
        )
    }

    /**
     * Returns the already-resolved Compose [Shape] if cached, without performing Binder IPC.
     */
    fun getCachedComposeShape(): Shape? = snapshot?.composeShape

    /**
     * Pre-resolves the system icon shape off the Main Thread so Compose consumers read a cached snapshot.
     */
    fun preload(context: Context) {
        getOrResolveSnapshot(context.applicationContext)
    }

    /**
     * Resolves the Compose [Shape] on [Dispatchers.IO] without blocking the Main Thread.
     */
    suspend fun resolveComposeShapeAsync(context: Context): Shape {
        snapshot?.let { current ->
            if (current.generation == generationCounter.get()) {
                return current.composeShape
            }
        }
        return withContext(Dispatchers.IO) {
            getOrResolveSnapshot(context.applicationContext).composeShape
        }
    }

    /**
     * Resolves the Path for a normalized 100x100 box corresponding to the system icon shape overlay.
     * Caches the resolved path to prevent repeated Binder IPC queries and SVG parsing.
     */
    fun getSystemPath(context: Context, forceRefresh: Boolean = false): Path {
        return Path(getOrResolveSnapshot(context, forceRefresh).basePath)
    }

    private fun getOrResolveSnapshot(context: Context, forceRefresh: Boolean = false): ShapeSnapshot {
        val currentGen = generationCounter.get()
        if (!forceRefresh) {
            snapshot?.let { cached ->
                if (cached.generation == currentGen) {
                    return cached
                }
            }
        }

        val resolved = querySystemPath(context.applicationContext)
        val composeShape = buildComposeShape(resolved)
        val newSnapshot = ShapeSnapshot(
            generation = currentGen,
            basePath = resolved,
            composeShape = composeShape
        )
        // Only publish if no invalidation happened while querying Settings IPC
        if (generationCounter.get() == currentGen) {
            snapshot = newSnapshot
        }
        return newSnapshot
    }

    private fun querySystemPath(context: Context): Path {
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

        return when {
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
    }

    /**
     * Returns a cached scaled Path for the given pixel [size], avoiding redundant allocations
     * and matrix transformations during icon rasterization.
     * Guarded by an atomic generation counter so workers never write or read stale paths after [invalidateCache].
     * Always returns a defensive copy of the cached Path for thread safety.
     */
    fun getScaledPath(context: Context, size: Int): Path {
        val safeSize = size.coerceAtLeast(1)
        val snap = getOrResolveSnapshot(context)
        val gen = snap.generation
        val existing = scaledPathCache[safeSize]
        if (existing != null && existing.generation == gen) {
            return Path(existing.path)
        }
        val scale = safeSize / 100f
        val matrix = Matrix().apply { setScale(scale, scale) }
        val scaled = Path(snap.basePath).apply { transform(matrix) }
        if (generationCounter.get() == gen) {
            scaledPathCache[safeSize] = ScaledPathEntry(gen, scaled)
        }
        return Path(scaled)
    }

    /**
     * Returns a Compose [Shape] matching the active system icon shape mask.
     */
    fun getComposeShape(context: Context): Shape {
        return getOrResolveSnapshot(context).composeShape
    }

    private fun buildComposeShape(basePath: Path): Shape {
        val frozenBase = Path(basePath)
        return GenericShape { size, _ ->
            val matrix = Matrix().apply {
                setScale(size.width / 100f, size.height / 100f)
            }
            val scaled = Path(frozenBase).apply { transform(matrix) }
            addPath(scaled.asComposePath())
        }
    }

    /**
     * Clears the cached system path and advances the atomic generation counter so it is re-queried
     * upon system theme changes without allowing in-flight workers to write stale paths.
     */
    fun invalidateCache() {
        generationCounter.incrementAndGet()
        snapshot = null
        scaledPathCache.clear()
    }

    private fun parseOrFallback(pathData: String, cornerRadius: Float): Path {
        return try {
            PathParser.createPathFromPathData(pathData)
        } catch (_: Exception) {
            Path().apply { addRoundRect(RectF(0f, 0f, 100f, 100f), cornerRadius, cornerRadius, Path.Direction.CW) }
        }
    }
}
