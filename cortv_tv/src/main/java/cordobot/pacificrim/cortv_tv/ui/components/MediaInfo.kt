package cordobot.pacificrim.cortv_tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import cordobot.pacificrim.cortv.common.data.PreferencesManager
import cordobot.pacificrim.cortv.common.model.CastMember
import cordobot.pacificrim.cortv.common.model.MediaItem
import cordobot.pacificrim.cortv_tv.R
import cordobot.pacificrim.cortv_tv.ui.theme.CorTvColors
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale

/** Playback preferences chosen in Ajustes. Values match the strings stored by [PreferencesManager]. */
data class PlayerSettings(
    val aspectMode: String = "fit",          // "fill", "fit", "16:9", "4:3"
    val audioLanguage: String = "original",  // "es", "en", "original"
    val subtitleLanguage: String = "es",     // "es", "en", "off"
    val subtitleSize: String = "normal",     // "small", "normal", "large"
    val subtitleStyle: String = "white"      // "white", "yellow", "black_bg"
) {
    fun saveTo(prefs: PreferencesManager) {
        prefs.aspectMode = aspectMode
        prefs.audioLanguage = audioLanguage
        prefs.subtitleLanguage = subtitleLanguage
        prefs.subtitleSize = subtitleSize
        prefs.subtitleStyle = subtitleStyle
    }

    companion object {
        fun from(prefs: PreferencesManager) = PlayerSettings(
            aspectMode = prefs.aspectMode,
            audioLanguage = prefs.audioLanguage,
            subtitleLanguage = prefs.subtitleLanguage,
            subtitleSize = prefs.subtitleSize,
            subtitleStyle = prefs.subtitleStyle
        )
    }
}

/** "2012-09-26" -> "26 de septiembre de 2012" (in the app language); "2013" -> "2013". */
fun formatReleaseDate(date: String?): String? {
    if (date.isNullOrBlank()) return null
    if (date.length == 4) return date
    return runCatching {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date) ?: return date
        DateFormat.getDateInstance(DateFormat.LONG, Locale.getDefault()).format(parsed)
    }.getOrDefault(date)
}

fun releaseYear(date: String?): String? = date?.take(4)?.takeIf { it.length == 4 }

/** "Ciencia ficción · 2012 · Dir. Ian Hubert" */
@Composable
fun metaLine(item: MediaItem): String = listOfNotNull(
    item.genre,
    releaseYear(item.releaseDate),
    item.director?.let { stringResource(R.string.director_short, it) }
).joinToString("  ·  ")

/** Localized name of a catalog category key ("Peliculas", "Series", ...). */
@Composable
fun categoryLabel(category: String): String = when (category) {
    "Peliculas" -> stringResource(R.string.nav_movies)
    "Series" -> stringResource(R.string.nav_series)
    "Animes" -> stringResource(R.string.nav_animes)
    "Canales de TV" -> stringResource(R.string.category_live)
    else -> category
}

@Composable
fun RatingBadges(item: MediaItem, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // IMDb
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .background(Color(0xFFF5C518), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("IMDb", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = item.imdbRating?.let { String.format(Locale.US, "%.1f", it) + "/10" } ?: stringResource(R.string.not_available),
                color = if (item.imdbRating != null) Color.White else CorTvColors.SecondaryText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
        // Metacritic
        Row(verticalAlignment = Alignment.CenterVertically) {
            val score = item.metacriticScore
            val scoreColor = when {
                score == null -> CorTvColors.Neutral
                score >= 61 -> Color(0xFF66CC33)
                score >= 40 -> Color(0xFFFFCC33)
                else -> Color(0xFFFF0000)
            }
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(scoreColor, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = score?.toString() ?: "–",
                    color = if (score != null && score in 40..60) Color.Black else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(if (score != null) R.string.metacritic else R.string.metacritic_na),
                color = if (score != null) Color.White else CorTvColors.SecondaryText,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/** Round actor photo; shows the initials when there is no photo or it fails to load. */
@Composable
fun CastAvatar(member: CastMember, size: Dp = 44.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(CorTvColors.Accent.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = member.name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() },
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.36f).sp
        )
        if (!member.photoUrl.isNullOrEmpty()) {
            AsyncImage(
                model = member.photoUrl,
                contentDescription = member.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
