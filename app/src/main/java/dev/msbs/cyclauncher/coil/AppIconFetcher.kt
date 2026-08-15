package dev.msbs.cyclauncher.coil

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Ключ для загрузки иконки приложения формата "packageName/activityName". */
data class AppIconKey(val componentKey: String)

/** Загрузчик иконок установленных приложений через PackageManager для Coil 3. */
internal class AppIconFetcher private constructor(
    private val context: Context,
    private val key: AppIconKey,
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val parts = key.componentKey.split("/", limit = 2)
        if (parts.size != 2) return@withContext null
        val (pkg, activity) = parts

        val drawable: Drawable = try {
            resolveIcon(pm, pkg, activity)
        } catch (_: Exception) {
            pm.defaultActivityIcon
        }

        ImageFetchResult(
            image = drawable.asImage(),
            isSampled = false,
            dataSource = DataSource.DISK,
        )
    }

    private fun resolveIcon(pm: PackageManager, pkg: String, activity: String): Drawable {
        val component = android.content.ComponentName(pkg, activity)
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getActivityInfo(component, 0)
        }
        return info.loadIcon(pm)
    }

    class Factory(private val context: Context) : Fetcher.Factory<Any> {
        override fun create(data: Any, options: Options, imageLoader: ImageLoader): Fetcher? {
            val key = when (data) {
                is AppIconKey -> data
                is String -> {
                    val uri = data.toUri()
                    if (uri.scheme != null) return null
                    if (!data.contains('/')) return null
                    AppIconKey(data)
                }
                else -> return null
            }
            return AppIconFetcher(context.applicationContext, key)
        }
    }
}
