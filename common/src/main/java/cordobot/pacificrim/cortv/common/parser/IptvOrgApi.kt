package cordobot.pacificrim.cortv.common.parser

import android.content.Context
import cordobot.pacificrim.cortv.common.model.MediaItem
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Live channels from the iptv-org API (https://github.com/iptv-org/api), whose data comes from
 * https://github.com/iptv-org/database. Streams are joined with their channel to get the country,
 * categories, network and logo; NSFW, closed and blocklisted channels are left out.
 */
object IptvOrgApi {

    private const val BASE_URL = "https://iptv-org.github.io/api/"
    private const val CACHE_FILE = "iptv_org_channels_v2.json" // bump when MediaItem gains fields filled from the API
    private const val CACHE_MAX_AGE_MS = 12 * 60 * 60 * 1000L
    // Streams that passed tools/verify_channels.py (they load and have sound); one URL per line
    private const val VERIFIED_ASSET = "verified_streams.txt"

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Serializable
    private data class ApiStream(
        val channel: String? = null,
        val feed: String? = null,
        val title: String,
        val url: String,
        val referrer: String? = null,
        @SerialName("user_agent") val userAgent: String? = null,
        val quality: String? = null,
        val labels: List<String> = emptyList()
    )

    @Serializable
    private data class ApiChannel(
        val id: String,
        val name: String,
        val network: String? = null,
        val country: String? = null,
        val categories: List<String> = emptyList(),
        @SerialName("is_nsfw") val isNsfw: Boolean = false,
        val launched: String? = null,
        val closed: String? = null,
        val website: String? = null
    )

    @Serializable
    private data class ApiLogo(
        val channel: String,
        val feed: String? = null,
        @SerialName("in_use") val inUse: Boolean = true,
        val format: String? = null,
        val url: String
    )

    @Serializable
    private data class ApiCategory(val id: String, val name: String)

    @Serializable
    private data class ApiBlocked(val channel: String)

    // Spanish names for the iptv-org categories (the API only has English ones)
    private val CATEGORY_NAMES_ES = mapOf(
        "animation" to "Animación", "auto" to "Motor", "business" to "Negocios", "classic" to "Clásicos",
        "comedy" to "Comedia", "cooking" to "Cocina", "culture" to "Cultura", "documentary" to "Documentales",
        "education" to "Educación", "entertainment" to "Entretenimiento", "family" to "Familia",
        "general" to "General", "interactive" to "Interactivo", "kids" to "Infantil", "legislative" to "Parlamento",
        "lifestyle" to "Estilo de vida", "movies" to "Cine", "music" to "Música", "news" to "Noticias",
        "outdoor" to "Aire libre", "public" to "Pública", "relax" to "Relax", "religious" to "Religión",
        "series" to "Series", "science" to "Ciencia", "shop" to "Compras", "sports" to "Deportes",
        "travel" to "Viajes", "weather" to "El tiempo"
    )

    /**
     * Returns the channels that passed the verification (see [VERIFIED_ASSET]), from a cache when it
     * is less than 12 hours old. If the API can't be reached it uses the stale cache, and as a last
     * resort the M3U playlist. Must be called off the main thread.
     */
    fun loadChannels(context: Context, forceRefresh: Boolean = false): PlaylistResult {
        val result = loadAllChannels(context.cacheDir, forceRefresh)
        val verified = readVerified(context) ?: return result
        return result.copy(items = result.items.filter { it.url in verified })
    }

    /** URLs of the verified streams, or null when the app ships without a verified list (show everything). */
    private fun readVerified(context: Context): Set<String>? = runCatching {
        context.assets.open(VERIFIED_ASSET).bufferedReader().useLines { lines ->
            lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toHashSet()
        }
    }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun loadAllChannels(cacheDir: File, forceRefresh: Boolean): PlaylistResult {
        val cache = File(cacheDir, CACHE_FILE)
        val cached = readCache(cache)
        if (!forceRefresh && cached != null && System.currentTimeMillis() - cache.lastModified() < CACHE_MAX_AGE_MS) {
            return PlaylistResult(cached)
        }
        return try {
            val channels = fetchChannels()
            runCatching { cache.writeText(json.encodeToString(channels)) }
            PlaylistResult(channels)
        } catch (e: Exception) {
            e.printStackTrace()
            if (cached != null) return PlaylistResult(cached)
            val fallback = M3UParser.loadLiveChannels()
            if (fallback.items.isNotEmpty()) fallback
            else PlaylistResult(emptyList(), "No se pudieron cargar los canales: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun readCache(file: File): List<MediaItem>? =
        if (!file.exists()) null
        else runCatching { json.decodeFromString<List<MediaItem>>(file.readText()) }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun fetchChannels(): List<MediaItem> {
        val streams = fetch<List<ApiStream>>("streams.json")
        val channels = fetch<List<ApiChannel>>("channels.json").associateBy { it.id }
        val logos = fetch<List<ApiLogo>>("logos.json").groupBy { it.channel }
        val categoryNames = runCatching { fetch<List<ApiCategory>>("categories.json") }.getOrDefault(emptyList())
            .associate { it.id to (CATEGORY_NAMES_ES[it.id] ?: it.name) }
        val blocked = runCatching { fetch<List<ApiBlocked>>("blocklist.json") }.getOrDefault(emptyList())
            .map { it.channel }.toSet()

        return streams.mapNotNull { stream ->
            val channel = stream.channel?.let { channels[it] }
            if (stream.channel in blocked || channel?.isNsfw == true || channel?.closed != null) return@mapNotNull null
            val tvgId = stream.channel?.let { id -> stream.feed?.let { "$id@$it" } ?: id }
            val genres = channel?.categories.orEmpty().map { categoryNames[it] ?: CATEGORY_NAMES_ES[it] ?: it }
            MediaItem(
                // Same id scheme as the M3U playlist, so favorites keep working
                id = if (tvgId == null) stream.url.hashCode().toString() else "$tvgId-${stream.url.hashCode()}",
                title = stream.title,
                url = stream.url,
                category = "Canales de TV",
                logoUrl = stream.channel?.let { pickLogo(logos[it], stream.feed) },
                genre = genres.joinToString(", ").ifEmpty { null },
                releaseDate = channel?.launched,
                userAgent = stream.userAgent,
                referrer = stream.referrer,
                country = channel?.country,
                network = channel?.network,
                website = channel?.website,
                quality = stream.quality,
                labels = stream.labels,
                isKids = channel?.categories.orEmpty().contains("kids")
            )
        }.sortedBy { it.title.lowercase(Locale.ROOT) }
    }

    /** Prefers the logo of the stream's feed, then the channel's, skipping SVG (Coil can't draw it without an extra decoder). */
    private fun pickLogo(logos: List<ApiLogo>?, feed: String?): String? {
        val usable = logos.orEmpty().filter { it.format != "SVG" && !it.url.endsWith(".svg", ignoreCase = true) }
        return (usable.firstOrNull { it.feed == feed && feed != null && it.inUse }
            ?: usable.firstOrNull { it.feed == null && it.inUse }
            ?: usable.firstOrNull { it.inUse }
            ?: usable.firstOrNull())?.url
    }

    @OptIn(ExperimentalSerializationApi::class)
    private inline fun <reified T> fetch(path: String): T {
        val connection = URL(BASE_URL + path).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10000
            connection.readTimeout = 20000
            connection.setRequestProperty("User-Agent", "CorTV/1.0 (Android TV)")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode} en $path")
            return connection.inputStream.buffered().use { json.decodeFromStream<T>(it) }
        } finally {
            connection.disconnect()
        }
    }
}
