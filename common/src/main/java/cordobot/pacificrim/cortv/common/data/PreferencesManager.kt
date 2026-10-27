package cordobot.pacificrim.cortv.common.data

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import androidx.core.content.edit
import androidx.core.os.ConfigurationCompat
import cordobot.pacificrim.cortv.common.model.MediaItem
import cordobot.pacificrim.cortv.common.model.PlaybackProgress
import cordobot.pacificrim.cortv.common.parser.M3UParser
import kotlinx.serialization.json.Json

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val KEY_M3U_URL = "m3u_url"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_HISTORY = "history"
        private const val KEY_SUBTITLE_LANG = "subtitle_lang"
        private const val KEY_AUDIO_LANG = "audio_lang"
        private const val KEY_ASPECT_MODE = "aspect_mode"
        private const val KEY_SUBTITLE_SIZE = "subtitle_size"
        private const val KEY_SUBTITLE_STYLE = "subtitle_style"
        private const val KEY_PROGRESS_PREFIX = "progress_"
        private const val KEY_LAST_EPISODE_PREFIX = "last_episode_"
        private const val KEY_LIVE_COUNTRY = "live_country"
        private const val KEY_HIDDEN_CHANNELS = "hidden_channels"
        private const val KEY_LAST_CHANNEL = "last_channel"
        private const val LIVE_CATEGORY = "Canales de TV"
        const val PREFS_NAME = "cortv_prefs"
        const val KEY_APP_LANGUAGE = "app_language"
        const val DEFAULT_APP_LANGUAGE = "es"
    }

    var appLanguage: String
        get() = prefs.getString(KEY_APP_LANGUAGE, DEFAULT_APP_LANGUAGE) ?: DEFAULT_APP_LANGUAGE // "system" or a language tag such as "es", "en"
        set(value) = prefs.edit { putString(KEY_APP_LANGUAGE, value) }

    fun getProgress(key: String): PlaybackProgress? {
        val raw = prefs.getString(KEY_PROGRESS_PREFIX + key, null) ?: return null
        val parts = raw.split("/")
        val position = parts.getOrNull(0)?.toLongOrNull() ?: return null
        val duration = parts.getOrNull(1)?.toLongOrNull() ?: return null
        return PlaybackProgress(position, duration)
    }

    fun saveProgress(key: String, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0) return
        prefs.edit { putString(KEY_PROGRESS_PREFIX + key, "$positionMs/$durationMs") }
    }

    fun getLastEpisodeId(seriesId: String): String? = prefs.getString(KEY_LAST_EPISODE_PREFIX + seriesId, null)

    fun setLastEpisodeId(seriesId: String, episodeId: String) {
        prefs.edit { putString(KEY_LAST_EPISODE_PREFIX + seriesId, episodeId) }
    }

    var m3uUrl: String
        get() = prefs.getString(KEY_M3U_URL, M3UParser.DEFAULT_M3U_URL) ?: M3UParser.DEFAULT_M3U_URL
        set(value) = prefs.edit { putString(KEY_M3U_URL, value) }

    // Country whose channels are shown (null = every country). Until the viewer picks one in the
    // settings it is the device's region.
    var liveCountry: String?
        get() {
            val saved = prefs.getString(KEY_LIVE_COUNTRY, null) ?: return deviceRegion()
            return saved.ifEmpty { null }
        }
        set(value) = prefs.edit { putString(KEY_LIVE_COUNTRY, value.orEmpty()) }

    // From the system configuration: the app replaces the default Locale with its own language, which has no region
    private fun deviceRegion(): String? =
        ConfigurationCompat.getLocales(Resources.getSystem().configuration)[0]?.country?.ifEmpty { null }

    /** Stream URLs that failed to play; those channels are hidden until restored in the settings. */
    var hiddenChannels: Set<String>
        get() = prefs.getStringSet(KEY_HIDDEN_CHANNELS, null)?.toSet().orEmpty()
        set(value) = prefs.edit { putStringSet(KEY_HIDDEN_CHANNELS, value) }

    var subtitleLanguage: String
        get() = prefs.getString(KEY_SUBTITLE_LANG, "es") ?: "es" // "es", "en" or "off"
        set(value) = prefs.edit { putString(KEY_SUBTITLE_LANG, value) }

    var audioLanguage: String
        get() = prefs.getString(KEY_AUDIO_LANG, "original") ?: "original" // "es", "en" or "original"
        set(value) = prefs.edit { putString(KEY_AUDIO_LANG, value) }

    var aspectMode: String
        get() = prefs.getString(KEY_ASPECT_MODE, "fit") ?: "fit" // "fill", "fit", "16:9" or "4:3"
        set(value) = prefs.edit { putString(KEY_ASPECT_MODE, value) }

    var subtitleSize: String
        get() = prefs.getString(KEY_SUBTITLE_SIZE, "normal") ?: "normal" // "small", "normal" or "large"
        set(value) = prefs.edit { putString(KEY_SUBTITLE_SIZE, value) }

    var subtitleStyle: String
        get() = prefs.getString(KEY_SUBTITLE_STYLE, "white") ?: "white" // "white", "yellow" or "black_bg"
        set(value) = prefs.edit { putString(KEY_SUBTITLE_STYLE, value) }

    fun getFavorites(): List<MediaItem> {
        val jsonStr = prefs.getString(KEY_FAVORITES, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<MediaItem>>(jsonStr) }.getOrDefault(emptyList())
    }

    fun addFavorite(item: MediaItem) {
        val favorites = getFavorites().toMutableList()
        if (favorites.none { it.id == item.id }) {
            favorites.add(item.copy(isFavorite = true))
            saveFavorites(favorites)
        }
    }

    fun removeFavorite(itemId: String) {
        val favorites = getFavorites().filter { it.id != itemId }
        saveFavorites(favorites)
    }

    fun isFavorite(itemId: String): Boolean {
        return getFavorites().any { it.id == itemId }
    }

    private fun saveFavorites(list: List<MediaItem>) {
        val jsonStr = json.encodeToString(list)
        prefs.edit { putString(KEY_FAVORITES, jsonStr) }
    }

    /** Movies and series watched, most recent first. Channels are not kept here (see [lastChannel]). */
    fun getHistory(): List<MediaItem> {
        val jsonStr = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        // Older versions also stored channels
        return runCatching { json.decodeFromString<List<MediaItem>>(jsonStr) }.getOrDefault(emptyList())
            .filter { it.category != LIVE_CATEGORY }
    }

    fun addToHistory(item: MediaItem) {
        if (item.category == LIVE_CATEGORY) return
        val history = getHistory().toMutableList()
        // Remove duplicate if it exists, to push it to the top/recent
        history.removeAll { it.id == item.id }
        history.add(0, item)
        // Keep history size reasonable, e.g., last 50 items
        if (history.size > 50) {
            history.removeAt(history.lastIndex)
        }
        val jsonStr = json.encodeToString(history)
        prefs.edit { putString(KEY_HISTORY, jsonStr) }
    }

    fun removeFromHistory(itemId: String) {
        val history = getHistory().filter { it.id != itemId }
        prefs.edit { putString(KEY_HISTORY, json.encodeToString(history)) }
    }

    fun clearHistory() {
        prefs.edit { remove(KEY_HISTORY) }
    }

    /** The last live channel watched; the live section opens on it. */
    var lastChannel: MediaItem?
        get() = prefs.getString(KEY_LAST_CHANNEL, null)?.let { runCatching { json.decodeFromString<MediaItem>(it) }.getOrNull() }
        set(value) {
            if (value == null) prefs.edit { remove(KEY_LAST_CHANNEL) }
            else prefs.edit { putString(KEY_LAST_CHANNEL, json.encodeToString(value)) }
        }
}
