package cordobot.pacificrim.cortv.common.parser

import android.content.Context
import cordobot.pacificrim.cortv.common.model.MediaItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Finds the portrait cover of movies and series, without API keys:
 * series on TVMaze (https://www.tvmaze.com/api), movies (and series TVMaze doesn't know) on the
 * Wikipedia page summary API. Only portrait images are accepted, so logos and stills are skipped.
 * Results (including "no poster") are cached for a week.
 */
object PosterApi {

    private const val CACHE_FILE = "posters.json"
    private const val CACHE_MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000L

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class TvMazeShow(val name: String, val image: TvMazeImage? = null)

    @Serializable
    private data class TvMazeImage(val medium: String? = null, val original: String? = null)

    @Serializable
    private data class WikiSummary(val thumbnail: WikiImage? = null, val originalimage: WikiImage? = null)

    @Serializable
    private data class WikiImage(val source: String, val width: Int = 0, val height: Int = 0)

    @Serializable
    private data class Cache(val savedAt: Long, val posters: Map<String, String>) // id -> url ("" = none)

    /**
     * Returns [items] with [MediaItem.posterUrl] filled in where a poster was found. Live channels
     * and items that already have a poster are left alone. Must be called off the main thread.
     */
    fun withPosters(context: Context, items: List<MediaItem>): List<MediaItem> {
        val cacheFile = File(context.cacheDir, CACHE_FILE)
        val cache = runCatching { json.decodeFromString<Cache>(cacheFile.readText()) }.getOrNull()
            ?.takeIf { System.currentTimeMillis() - it.savedAt < CACHE_MAX_AGE_MS }
        val posters = cache?.posters.orEmpty().toMutableMap()

        val missing = items.filter { it.needsPoster() && it.id !in posters }
        if (missing.isNotEmpty()) {
            missing.forEach { item -> runCatching { posters[item.id] = find(item).orEmpty() } }
            runCatching { cacheFile.writeText(json.encodeToString(Cache(cache?.savedAt ?: System.currentTimeMillis(), posters))) }
        }
        return items.map { item ->
            val url = posters[item.id]?.ifEmpty { null }
            if (item.needsPoster() && url != null) item.copy(posterUrl = url) else item
        }
    }

    private fun MediaItem.needsPoster() = category != "Canales de TV" && posterUrl == null

    private fun find(item: MediaItem): String? {
        // "Sintel (Tráiler)" -> "Sintel"; translated titles carry the original one
        val query = item.originalTitle ?: item.title.substringBefore(" (").trim()
        return if (item.episodes.isNotEmpty()) tvMaze(query) ?: wikipedia(query) else wikipedia(query)
    }

    private fun tvMaze(query: String): String? {
        val show = get("https://api.tvmaze.com/singlesearch/shows?q=${encode(query)}")
            ?.let { json.decodeFromString<TvMazeShow>(it) } ?: return null
        // singlesearch returns the closest match, which may be another show
        if (!show.name.equals(query, ignoreCase = true)) return null
        return show.image?.original ?: show.image?.medium
    }

    private fun wikipedia(query: String): String? {
        val page = encode(query.replace(' ', '_'))
        val summary = get("https://en.wikipedia.org/api/rest_v1/page/summary/$page")
            ?.let { json.decodeFromString<WikiSummary>(it) } ?: return null
        val original = summary.originalimage ?: return null
        if (original.height <= original.width) return null // a logo or a still, not a poster
        return summary.thumbnail?.source ?: original.source
    }

    private fun encode(text: String) = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    private fun get(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("User-Agent", "CorTV/1.0 (Android TV)")
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> connection.inputStream.bufferedReader().use { it.readText() }
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw IOException("HTTP ${connection.responseCode}")
            }
        } finally {
            connection.disconnect()
        }
    }
}
