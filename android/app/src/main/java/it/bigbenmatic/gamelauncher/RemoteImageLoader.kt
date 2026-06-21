package it.bigbenmatic.gamelauncher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Tiny dependency-free image fetcher used to render branding (logo/background) that
 * the fleet config points at remotely. Avoids pulling in an image-loading library for
 * what is just an occasional, cacheable download. */
object RemoteImageLoader {
    suspend fun load(url: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.inputStream.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }
}
