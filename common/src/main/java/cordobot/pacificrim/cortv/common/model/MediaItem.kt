package cordobot.pacificrim.cortv.common.model

import kotlinx.serialization.Serializable

@Serializable
data class CastMember(
    val name: String,
    val character: String? = null,
    val photoUrl: String? = null
)

@Serializable
data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val url: String,
    val description: String? = null,
    val director: String? = null,
    val releaseDate: String? = null,
    val thumbnailUrl: String? = null
)

@Serializable
data class MediaItem(
    val id: String,
    val title: String,
    val url: String,
    val category: String, // "Series", "Animes", "Peliculas", "Canales de TV"
    val logoUrl: String? = null,
    val description: String? = null,
    val isFavorite: Boolean = false,
    val genre: String? = null,
    val director: String? = null,
    val releaseDate: String? = null, // ISO "yyyy-MM-dd", or just "yyyy" when only the year is known
    val imdbRating: Double? = null, // 0.0 - 10.0
    val metacriticScore: Int? = null, // 0 - 100
    val cast: List<CastMember> = emptyList(),
    val episodes: List<Episode> = emptyList(), // only for series
    val userAgent: String? = null, // HTTP headers some IPTV streams require
    val referrer: String? = null,
    // Live channel data from the iptv-org database
    val country: String? = null, // ISO 3166-1 alpha-2 code
    val network: String? = null,
    val website: String? = null,
    val quality: String? = null, // "1080p", "720p"...
    val labels: List<String> = emptyList(), // iptv-org warnings: "Geo-blocked", "Not 24/7"
    val isKids: Boolean = false, // shown in the Kids section
    val originalTitle: String? = null, // title to look the poster up with, when [title] is translated
    val posterUrl: String? = null // portrait cover of a movie or series (see PosterApi)
)

/** Saved playback position of a movie or episode. */
data class PlaybackProgress(val positionMs: Long, val durationMs: Long) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val isFinished: Boolean get() = durationMs > 0 && positionMs >= durationMs - FINISHED_MARGIN_MS

    /** Position to resume from, or null when there is nothing worth resuming. */
    val resumePositionMs: Long? get() = positionMs.takeIf { it > MIN_RESUME_MS && !isFinished }

    companion object {
        private const val MIN_RESUME_MS = 5_000L
        private const val FINISHED_MARGIN_MS = 15_000L
    }
}
