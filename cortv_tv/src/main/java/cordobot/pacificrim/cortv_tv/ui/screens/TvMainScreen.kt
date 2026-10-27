package cordobot.pacificrim.cortv_tv.ui.screens

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.*
import coil.annotation.ExperimentalCoilApi
import coil.compose.AsyncImage
import coil.imageLoader
import cordobot.pacificrim.cortv.common.data.PreferencesManager
import cordobot.pacificrim.cortv.common.model.Episode
import cordobot.pacificrim.cortv.common.model.MediaItem
import cordobot.pacificrim.cortv.common.model.PlaybackProgress
import cordobot.pacificrim.cortv.common.model.countryFlag
import cordobot.pacificrim.cortv.common.model.countryName
import cordobot.pacificrim.cortv.common.model.isAvailableIn
import cordobot.pacificrim.cortv.common.model.isFrom
import cordobot.pacificrim.cortv.common.parser.IptvOrgApi
import cordobot.pacificrim.cortv.common.parser.M3UParser
import cordobot.pacificrim.cortv.common.parser.PosterApi
import cordobot.pacificrim.cortv_tv.LocaleHelper
import cordobot.pacificrim.cortv_tv.R
import cordobot.pacificrim.cortv_tv.ui.components.CastAvatar
import cordobot.pacificrim.cortv_tv.ui.components.PlayerSettings
import cordobot.pacificrim.cortv_tv.ui.components.RatingBadges
import cordobot.pacificrim.cortv_tv.ui.components.TvDropdown
import cordobot.pacificrim.cortv_tv.ui.components.TvLivePlayer
import cordobot.pacificrim.cortv_tv.ui.components.TvVideoPlayer
import cordobot.pacificrim.cortv_tv.ui.components.accentButtonColors
import cordobot.pacificrim.cortv_tv.ui.components.categoryLabel
import cordobot.pacificrim.cortv_tv.ui.components.formatReleaseDate
import cordobot.pacificrim.cortv_tv.ui.components.releaseYear
import cordobot.pacificrim.cortv_tv.ui.theme.CorTvColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private data class NavOption(val category: String, val labelRes: Int, val icon: ImageVector)

private const val CATEGORY_HOME = "Inicio"
private const val CATEGORY_LIVE = "Canales de TV"
private const val CATEGORY_KIDS = "Kids"
private const val CATEGORY_FAVORITES = "Favoritos"
private const val CATEGORY_HISTORY = "Historial"
private const val CATEGORY_SEARCH = "Buscador"

// Sections whose cards show the portrait cover of each title
private val POSTER_CATEGORIES = setOf("Peliculas", "Series", "Animes", CATEGORY_KIDS)

// The first option is the section shown when the app opens
private val railOptions = listOf(
    NavOption(CATEGORY_HOME, R.string.nav_home, Icons.Default.Home),
    NavOption(CATEGORY_LIVE, R.string.nav_live, Icons.Default.LiveTv),
    NavOption("Peliculas", R.string.nav_movies, Icons.Default.Movie),
    NavOption("Series", R.string.nav_series, Icons.Default.Tv),
    NavOption("Animes", R.string.nav_animes, Icons.Default.Animation),
    NavOption(CATEGORY_KIDS, R.string.nav_kids, Icons.Default.ChildCare),
    NavOption(CATEGORY_FAVORITES, R.string.nav_favorites, Icons.Default.Favorite),
    NavOption(CATEGORY_HISTORY, R.string.nav_history, Icons.Default.History)
)

private enum class SortOrder(val labelRes: Int) {
    RELEVANCE(R.string.sort_relevance),
    TITLE_ASC(R.string.sort_az),
    TITLE_DESC(R.string.sort_za),
    NEWEST(R.string.sort_newest),
    OLDEST(R.string.sort_oldest)
}

/** What the player should show: [item] is the movie, channel or episode (as a playable item). */
private data class PlaybackRequest(val item: MediaItem, val progressKey: String, val startPositionMs: Long)

/**
 * A live channel to open full screen; up/down in the player move through [channels].
 * [fromRail] is set when it was opened from the "Live" menu option, so Back returns the focus there.
 */
private data class LiveRequest(val channels: List<MediaItem>, val startIndex: Int, val fromRail: Boolean = false)

/** A "Continue watching" card: the title, the episode to play (series) and what the card says under the title. */
private data class ContinueEntry(val item: MediaItem, val episode: Episode?, val subtitle: String?, val progress: PlaybackProgress?)

/**
 * Builds "Continue watching": the last channel watched, then from the history (most recent first)
 * movies not finished and the episode where each series was left (or the next one if it was finished).
 */
private fun buildContinueWatching(
    context: Context,
    history: List<MediaItem>,
    lastChannel: MediaItem?,
    prefs: PreferencesManager
): List<ContinueEntry> {
    val channel = lastChannel?.let { ContinueEntry(it, null, context.getString(R.string.continue_last_channel), null) }
    return listOfNotNull(channel) + history.mapNotNull { item ->
        when {
            item.episodes.isNotEmpty() -> {
                val episodes = item.episodes.sortedWith(compareBy({ it.season }, { it.number }))
                val last = episodes.firstOrNull { it.id == prefs.getLastEpisodeId(item.id) } ?: episodes.first()
                val progress = prefs.getProgress(last.id)
                if (progress?.isFinished == true) {
                    val next = episodes.getOrNull(episodes.indexOf(last) + 1) ?: return@mapNotNull null
                    ContinueEntry(item, next, context.getString(R.string.continue_next_episode, next.season, next.number, next.title), null)
                } else {
                    ContinueEntry(item, last, context.getString(R.string.continue_episode, last.season, last.number, last.title), progress)
                }
            }
            else -> {
                val progress = prefs.getProgress(item.id)
                if (progress?.isFinished == true) null
                else ContinueEntry(
                    item, null,
                    progress?.let { context.getString(R.string.continue_time_left, ((it.durationMs - it.positionMs) / 60_000).coerceAtLeast(1)) },
                    progress
                )
            }
        }
    }
}

private fun categoryIcon(category: String) =
    railOptions.firstOrNull { it.category == category }?.icon ?: Icons.Default.LiveTv

private fun MediaItem.genres(): List<String> =
    genre?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

/** Key under which the playback progress of an item is saved (the last episode, for series). */
private fun progressKeyFor(item: MediaItem, prefs: PreferencesManager): String =
    if (item.episodes.isEmpty()) item.id else prefs.getLastEpisodeId(item.id) ?: item.episodes.first().id

@Composable
fun TvMainScreen(onLanguageChanged: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val prefsManager = remember { PreferencesManager(context) }
    var onDemandItems by remember { mutableStateOf(M3UParser.DEMO_STREAMS) }
    var channelItems by remember { mutableStateOf(emptyList<MediaItem>()) }
    val mediaItems = remember(onDemandItems, channelItems) { onDemandItems + channelItems }
    var channelsLoading by remember { mutableStateOf(true) }

    // Covers of movies and series (TVMaze / Wikipedia, cached on disk)
    LaunchedEffect(Unit) {
        onDemandItems = withContext(Dispatchers.IO) { PosterApi.withPosters(context, M3UParser.DEMO_STREAMS) }
    }

    // The iptv-org live channels (API + database, cached on disk) are added after the demo catalog
    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) { IptvOrgApi.loadChannels(context) }
        channelItems = result.items
        channelsLoading = false
        result.error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    var selectedCategory by remember { mutableStateOf(railOptions.first().category) }
    var searchQuery by remember { mutableStateOf("") }
    var genreFilter by remember { mutableStateOf<String?>(null) }
    var yearFilter by remember { mutableStateOf<String?>(null) }
    var sortOrder by remember { mutableStateOf(SortOrder.RELEVANCE) }
    var countryFilter by remember { mutableStateOf(prefsManager.liveCountry) }
    var hiddenChannels by remember { mutableStateOf(prefsManager.hiddenChannels) }
    var live by remember { mutableStateOf<LiveRequest?>(null) }
    var focusRailOption by remember { mutableStateOf<String?>(null) }
    var playerSettings by remember { mutableStateOf(PlayerSettings.from(prefsManager)) }
    var selectedMediaItem by remember { mutableStateOf<MediaItem?>(null) }
    var playback by remember { mutableStateOf<PlaybackRequest?>(null) }
    var restoreFocusId by remember { mutableStateOf<String?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    var lastChannel by remember { mutableStateOf(prefsManager.lastChannel) }
    // "Continue watching" card the viewer asked to remove (long press on OK)
    var entryToRemove by remember { mutableStateOf<ContinueEntry?>(null) }
    var favoritesList by remember { mutableStateOf(prefsManager.getFavorites()) }
    var historyList by remember { mutableStateOf(prefsManager.getHistory()) }
    val favoriteIds = remember(favoritesList) { favoritesList.map { it.id }.toSet() }

    val gridState = rememberLazyGridState()
    val railFocusRequesters = remember { railOptions.associate { it.category to FocusRequester() } }

    val refreshData: () -> Unit = {
        favoritesList = prefsManager.getFavorites()
        historyList = prefsManager.getHistory()
        lastChannel = prefsManager.lastChannel
    }

    // Channels that failed to play are hidden; the rest of the catalog is always offered
    val catalog = remember(mediaItems, hiddenChannels) {
        mediaItems.filter { it.category != CATEGORY_LIVE || it.url !in hiddenChannels }
    }
    // Channels of the country chosen in the settings first, then the US ones for Latin America
    val countryChannels = remember(catalog, countryFilter) {
        val channels = catalog.filter { it.category == CATEGORY_LIVE && it.isAvailableIn(countryFilter) }
        countryFilter?.let { country -> channels.sortedBy { !it.isFrom(country) } } ?: channels
    }
    // What the viewer can browse and search: all on-demand titles and the channels of their country
    val browsable = remember(catalog, countryChannels) { catalog.filter { it.category != CATEGORY_LIVE } + countryChannels }

    // Favorites/history are stored snapshots; show the current catalog entry and drop titles no longer offered
    val resolve: (List<MediaItem>) -> List<MediaItem> = { saved ->
        saved.mapNotNull { s -> catalog.firstOrNull { it.id == s.id } }
    }

    // Channels open straight in the full-screen live player; zapping goes through [list] (or the country's channels)
    val playLive: (MediaItem, List<MediaItem>) -> Unit = { item, list ->
        val channels = list.filter { it.category == CATEGORY_LIVE }
        val zapList = if (channels.any { it.id == item.id }) channels else listOf(item) + channels
        restoreFocusId = item.id
        live = LiveRequest(zapList, zapList.indexOfFirst { it.id == item.id }.coerceAtLeast(0))
    }

    val play: (MediaItem, Episode?, Boolean) -> Unit = { item, episode, fromStart ->
        prefsManager.addToHistory(item)
        if (episode != null) prefsManager.setLastEpisodeId(item.id, episode.id)
        val key = episode?.id ?: item.id
        val start = if (fromStart) 0L else prefsManager.getProgress(key)?.resumePositionMs ?: 0L
        val playable = if (episode == null) item else item.copy(
            id = episode.id,
            title = resources.getString(R.string.player_episode_title, item.title, episode.season, episode.number, episode.title),
            url = episode.url,
            description = episode.description ?: item.description,
            director = episode.director ?: item.director,
            releaseDate = episode.releaseDate ?: item.releaseDate,
            episodes = emptyList()
        )
        restoreFocusId = item.id
        playback = PlaybackRequest(playable, key, start)
        refreshData()
    }

    // ──────────── Live player (full screen, replaces the browse UI) ────────────
    live?.let { request ->
        TvLivePlayer(
            channels = request.channels,
            startIndex = request.startIndex,
            settings = playerSettings,
            isFavorite = { prefsManager.isFavorite(it.id) },
            onToggleFavorite = { ch -> if (prefsManager.isFavorite(ch.id)) prefsManager.removeFavorite(ch.id) else prefsManager.addFavorite(ch) },
            onChannelStarted = { ch -> prefsManager.lastChannel = ch },
            onChannelFailed = { ch -> prefsManager.hiddenChannels = prefsManager.hiddenChannels + ch.url },
            onBack = { last ->
                if (request.fromRail) focusRailOption = CATEGORY_LIVE else restoreFocusId = last.id
                live = null
                hiddenChannels = prefsManager.hiddenChannels
                refreshData()
            }
        )
        return
    }

    // ──────────── Video Player (replaces the browse UI so it gets all the D-pad input) ────────────
    playback?.let { request ->
        key(request.progressKey) {
            TvVideoPlayer(
                item = request.item,
                settings = playerSettings,
                startPositionMs = request.startPositionMs,
                onProgress = { position, duration -> prefsManager.saveProgress(request.progressKey, position, duration) },
                onBack = { playback = null; refreshData() }
            )
        }
        return
    }

    val allGenres = remember(mediaItems) { mediaItems.flatMap { it.genres() }.distinct().sorted() }
    val allYears = remember(mediaItems) { mediaItems.mapNotNull { releaseYear(it.releaseDate) }.distinct().sortedDescending() }
    // Countries with how many channels each has (for the settings), sorted by their name in the app language
    val liveCountries = remember(catalog) {
        catalog.filter { it.category == CATEGORY_LIVE }.mapNotNull { it.country }.groupingBy { it }.eachCount().entries
            .map { (code, count) -> code to "${countryFlag(code)} ${countryName(code)} ($count)".trim() }
            .sortedBy { countryName(it.first).lowercase() }
    }
    // Kids: only children's series and movies, no channels
    val kidsItems = remember(catalog) { catalog.filter { it.isKids && it.category != CATEGORY_LIVE } }
    val homeEntries = remember(historyList, catalog, lastChannel) {
        // Show the current catalog entry of the last channel, unless it was hidden for not playing
        val channel = lastChannel?.let { saved -> catalog.firstOrNull { it.id == saved.id } ?: saved.takeIf { it.url !in hiddenChannels } }
        buildContinueWatching(context, resolve(historyList), channel, prefsManager)
    }

    val filteredItems: List<MediaItem> = when (selectedCategory) {
        CATEGORY_FAVORITES -> resolve(favoritesList)
        CATEGORY_HISTORY -> resolve(historyList)
        CATEGORY_SEARCH -> {
            val q = searchQuery.trim()
            val matches = browsable.filter { m ->
                (q.isEmpty() ||
                    m.title.contains(q, ignoreCase = true) ||
                    m.genre?.contains(q, ignoreCase = true) == true ||
                    m.director?.contains(q, ignoreCase = true) == true ||
                    m.network?.contains(q, ignoreCase = true) == true ||
                    m.country?.let { countryName(it).contains(q, ignoreCase = true) } == true ||
                    m.cast.any { it.name.contains(q, ignoreCase = true) } ||
                    m.episodes.any { it.title.contains(q, ignoreCase = true) }) &&
                    (genreFilter == null || genreFilter in m.genres()) &&
                    (yearFilter == null || releaseYear(m.releaseDate) == yearFilter)
            }
            when (sortOrder) {
                SortOrder.RELEVANCE -> matches
                SortOrder.TITLE_ASC -> matches.sortedBy { it.title.lowercase() }
                SortOrder.TITLE_DESC -> matches.sortedByDescending { it.title.lowercase() }
                // Items without a date go last in both directions
                SortOrder.NEWEST -> matches.sortedWith(compareBy<MediaItem> { it.releaseDate == null }.thenByDescending { it.releaseDate })
                SortOrder.OLDEST -> matches.sortedWith(compareBy<MediaItem> { it.releaseDate == null }.thenBy { it.releaseDate })
            }
        }
        CATEGORY_HOME -> homeEntries.map { it.item }
        CATEGORY_KIDS -> kidsItems
        else -> catalog.filter { m -> m.category == selectedCategory }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(CorTvColors.Accent.copy(alpha = 0.22f), CorTvColors.Background, CorTvColors.Background)))
    ) {

        // ──────────── Left Navigation Rail ────────────
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(120.dp)
                .background(CorTvColors.Card.copy(alpha = 0.45f))
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = CorTvColors.Accent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.app_name), color = CorTvColors.Text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(6.dp))
            railOptions.forEach { option ->
                RailItem(
                    option = option,
                    selected = selectedCategory == option.category,
                    onClick = {
                        if (option.category == CATEGORY_LIVE) {
                            when {
                                countryChannels.isNotEmpty() -> live = LiveRequest(
                                    countryChannels,
                                    countryChannels.indexOfFirst { it.id == lastChannel?.id }.coerceAtLeast(0),
                                    fromRail = true
                                )
                                channelsLoading -> Toast.makeText(context, R.string.loading_channels, Toast.LENGTH_SHORT).show()
                                else -> Toast.makeText(context, R.string.empty_category, Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            selectedCategory = option.category
                            if (option.category == CATEGORY_FAVORITES || option.category == CATEGORY_HISTORY) refreshData()
                        }
                    },
                    modifier = Modifier.focusRequester(railFocusRequesters.getValue(option.category))
                )
            }
        }

        // ──────────── Main Content ────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 32.dp, end = 48.dp, top = 27.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = when (selectedCategory) {
                            CATEGORY_HOME -> stringResource(R.string.nav_home)
                            CATEGORY_KIDS -> stringResource(R.string.nav_kids)
                            CATEGORY_SEARCH -> stringResource(R.string.category_search)
                            CATEGORY_FAVORITES -> stringResource(R.string.nav_favorites)
                            CATEGORY_HISTORY -> stringResource(R.string.nav_history)
                            else -> categoryLabel(selectedCategory)
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        color = CorTvColors.Text,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            selectedCategory == CATEGORY_HOME && homeEntries.isNotEmpty() -> stringResource(R.string.continue_watching_hint)
                            selectedCategory == CATEGORY_HOME -> stringResource(R.string.continue_watching)
                            else -> pluralStringResource(R.plurals.titles_count, filteredItems.size, filteredItems.size)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = CorTvColors.SecondaryText
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (selectedCategory == CATEGORY_HISTORY && historyList.isNotEmpty()) {
                        Button(onClick = { confirmClearHistory = true }, colors = cardButtonColors()) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_clear_history))
                        }
                    }
                    Button(
                        onClick = { selectedCategory = CATEGORY_SEARCH },
                        colors = if (selectedCategory == CATEGORY_SEARCH) accentButtonColors() else cardButtonColors()
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_search))
                    }
                    Button(onClick = { showSettingsDialog = true }, colors = cardButtonColors()) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_settings))
                    }
                }
            }

            if (selectedCategory == CATEGORY_SEARCH) {
                SearchField(query = searchQuery, onQueryChange = { searchQuery = it })
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    TvDropdown(
                        label = stringResource(R.string.filter_genre),
                        options = listOf<Pair<String?, String>>(null to stringResource(R.string.filter_genre_all)) + allGenres.map { it to it },
                        selected = genreFilter,
                        onSelect = { genreFilter = it }
                    )
                    TvDropdown(
                        label = stringResource(R.string.filter_date),
                        options = listOf<Pair<String?, String>>(null to stringResource(R.string.filter_date_all)) + allYears.map { it to it },
                        selected = yearFilter,
                        onSelect = { yearFilter = it }
                    )
                    TvDropdown(
                        label = stringResource(R.string.filter_sort),
                        options = SortOrder.entries.map { it to stringResource(it.labelRes) },
                        selected = sortOrder,
                        onSelect = { sortOrder = it }
                    )
                }
            }

            if (filteredItems.isEmpty()) {
                EmptyState(
                    message = stringResource(
                        when (selectedCategory) {
                            CATEGORY_FAVORITES -> R.string.empty_favorites
                            CATEGORY_HISTORY -> R.string.empty_history
                            CATEGORY_SEARCH -> R.string.empty_search
                            CATEGORY_HOME -> R.string.empty_home
                            CATEGORY_KIDS -> R.string.empty_kids
                            else -> R.string.empty_category
                        }
                    )
                )
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = if (selectedCategory in POSTER_CATEGORIES) 150.dp else 200.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp, start = 4.dp, end = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    itemsIndexed(filteredItems) { index, item ->
                        val entry = if (selectedCategory == CATEGORY_HOME) homeEntries.getOrNull(index) else null
                        MediaCard(
                            item = item,
                            isFavorite = item.id in favoriteIds,
                            progress = if (entry != null) entry.progress else prefsManager.getProgress(progressKeyFor(item, prefsManager)),
                            subtitle = entry?.subtitle,
                            poster = selectedCategory in POSTER_CATEGORIES,
                            onLongClick = entry?.let { { entryToRemove = it } },
                            requestFocus = item.id == restoreFocusId,
                            onFocusRestored = { restoreFocusId = null },
                            onClick = {
                                when {
                                    // Up/down in the player move through the channels of the viewer's country
                                    item.category == CATEGORY_LIVE -> playLive(item, countryChannels)
                                    entry != null -> play(item, entry.episode, false)
                                    else -> selectedMediaItem = item
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Back from the live player opened from the menu: focus that menu option again
    focusRailOption?.let { option ->
        LaunchedEffect(option) {
            runCatching { railFocusRequesters[option]?.requestFocus() }
            focusRailOption = null
        }
    }

    // Initial focus, and focus fallback when returning from the player to an item that is no longer visible
    LaunchedEffect(Unit) {
        val target = railFocusRequesters[selectedCategory] ?: railFocusRequesters.getValue(railOptions.first().category)
        runCatching { target.requestFocus() }
    }

    // ──────────── Details ────────────
    selectedMediaItem?.let { item ->
        DetailsDialog(
            item = item,
            isFavorite = item.id in favoriteIds,
            initialEpisodeId = prefsManager.getLastEpisodeId(item.id),
            progressOf = { key -> prefsManager.getProgress(key) },
            onPlay = { episode, fromStart -> selectedMediaItem = null; play(item, episode, fromStart) },
            onToggleFavorite = {
                if (item.id in favoriteIds) prefsManager.removeFavorite(item.id) else prefsManager.addFavorite(item)
                refreshData()
            },
            onDismiss = { selectedMediaItem = null }
        )
    }

    // ──────────── Settings ────────────
    if (showSettingsDialog) {
        SettingsDialog(
            currentSettings = playerSettings,
            currentLanguage = prefsManager.appLanguage,
            countries = liveCountries,
            currentCountry = countryFilter,
            hiddenCount = hiddenChannels.size,
            onRestoreHidden = {
                prefsManager.hiddenChannels = emptySet()
                hiddenChannels = emptySet()
            },
            onSave = { settings, language, country ->
                settings.saveTo(prefsManager)
                playerSettings = settings
                if (country != countryFilter) {
                    prefsManager.liveCountry = country
                    countryFilter = country
                }
                showSettingsDialog = false
                if (language != prefsManager.appLanguage) {
                    prefsManager.appLanguage = language
                    onLanguageChanged()
                }
            },
            onDismiss = { showSettingsDialog = false }
        )
    }

    entryToRemove?.let { entry ->
        ConfirmDialog(
            title = stringResource(R.string.remove_continue_title),
            message = stringResource(R.string.remove_continue_message, entry.item.title),
            confirmLabel = stringResource(R.string.action_remove),
            onConfirm = {
                if (entry.item.category == CATEGORY_LIVE) prefsManager.lastChannel = null
                else prefsManager.removeFromHistory(entry.item.id)
                entryToRemove = null
                refreshData()
            },
            onDismiss = { entryToRemove = null }
        )
    }

    if (confirmClearHistory) {
        ConfirmDialog(
            title = stringResource(R.string.confirm_clear_history_title),
            message = stringResource(R.string.confirm_clear_history_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                prefsManager.clearHistory()
                refreshData()
                confirmClearHistory = false
            },
            onDismiss = { confirmClearHistory = false }
        )
    }
}

@Composable
private fun RailItem(option: NavOption, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) CorTvColors.Accent else Color.Transparent,
            contentColor = if (selected) Color.White else CorTvColors.SecondaryText,
            focusedContainerColor = Color.White,
            focusedContentColor = CorTvColors.Accent
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(option.icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(2.dp))
            Text(stringResource(option.labelRes), style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun MediaCard(
    item: MediaItem,
    isFavorite: Boolean,
    progress: PlaybackProgress?,
    subtitle: String? = null,
    poster: Boolean = false, // portrait cover instead of the 16:9 artwork
    onLongClick: (() -> Unit)? = null,
    requestFocus: Boolean,
    onFocusRestored: () -> Unit,
    onClick: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (poster) 2f / 3f else 16f / 9f)
            .focusRequester(focusRequester),
        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
        colors = CardDefaults.colors(containerColor = CorTvColors.Card, focusedContainerColor = CorTvColors.CardFocused),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = RoundedCornerShape(12.dp))
        ),
        scale = CardDefaults.scale(focusedScale = 1.08f)
    ) {
        Box(Modifier.fillMaxSize()) {
            Artwork(if (poster) item.posterUrl ?: item.logoUrl else item.logoUrl, item.category, item.title, isLogo = item.country != null)
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)))
                )
            )
            if (isFavorite) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = stringResource(R.string.favorite),
                    tint = CorTvColors.Accent,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp)
                )
            }
            item.imdbRating?.let { rating ->
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF5C518), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(String.format(Locale.US, "%.1f", rating), color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
            // Live channels: country flag and stream quality
            val channelBadge = listOfNotNull(item.country?.let { countryFlag(it) }?.ifEmpty { null }, item.quality).joinToString(" ")
            if (channelBadge.isNotEmpty()) {
                Text(
                    channelBadge,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 12.dp, bottom = 14.dp)) {
                Text(
                    text = item.title,
                    color = CorTvColors.Text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (subtitle == null) 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )
                // "Continue watching": the episode, time left or "last channel"
                subtitle?.let {
                    Text(it, color = CorTvColors.Text.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            progress?.let { ProgressBar(it, Modifier.align(Alignment.BottomStart)) }
        }
    }
    if (requestFocus) {
        LaunchedEffect(Unit) {
            runCatching { focusRequester.requestFocus() }
            onFocusRestored()
        }
    }
}

/** Thin bar at the bottom of a card showing how much was watched. */
@Composable
private fun ProgressBar(progress: PlaybackProgress, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(Color.White.copy(alpha = 0.25f))
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(if (progress.isFinished) 1f else progress.fraction)
                .background(CorTvColors.Accent)
        )
    }
}

/** Artwork with a branded placeholder behind it, so items without a logo (or with a broken one) still look good. */
@Composable
private fun Artwork(url: String?, category: String, description: String, modifier: Modifier = Modifier, isLogo: Boolean = false) {
    Box(
        modifier = modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(CorTvColors.Accent.copy(alpha = 0.55f), CorTvColors.Card))
        ),
        contentAlignment = Alignment.Center
    ) {
        var loaded by remember(url) { mutableStateOf(false) }
        // Logos are often transparent, so the placeholder icon is hidden once one is shown
        if (!(isLogo && loaded)) {
            Icon(
                imageVector = categoryIcon(category),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(48.dp)
            )
        }
        if (!url.isNullOrEmpty()) {
            AsyncImage(
                model = url,
                contentDescription = description,
                // Channel logos are shown whole; photos and posters fill the card
                modifier = if (isLogo) Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 22.dp) else Modifier.fillMaxSize(),
                contentScale = if (isLogo) ContentScale.Fit else ContentScale.Crop,
                onSuccess = { loaded = true },
                onError = { Log.w("CorTV", "No se pudo cargar $url", it.result.throwable) }
            )
        }
    }
}

/**
 * Search box that only opens the on-screen keyboard when the user presses OK on it, so moving
 * through the screen with the D-pad never gets trapped in the keyboard.
 */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var editing by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val boxFocus = remember { FocusRequester() }
    var fieldHadFocus by remember { mutableStateOf(false) }

    val finishEditing = {
        keyboardController?.hide()
        editing = false
        fieldHadFocus = false
    }

    if (editing) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            label = { androidx.compose.material3.Text(stringResource(R.string.search_hint)) },
            leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { finishEditing() }),
            colors = textFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .focusRequester(fieldFocus)
                .onFocusChanged { state ->
                    if (state.isFocused) fieldHadFocus = true
                    else if (fieldHadFocus) finishEditing()
                }
        )
        LaunchedEffect(Unit) {
            runCatching { fieldFocus.requestFocus() }
            keyboardController?.show()
        }
    } else {
        Surface(
            onClick = { editing = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .focusRequester(boxFocus),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = CorTvColors.Text,
                focusedContainerColor = CorTvColors.CardFocused,
                focusedContentColor = CorTvColors.Text
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, CorTvColors.SecondaryText), shape = RoundedCornerShape(8.dp)),
                focusedBorder = Border(BorderStroke(2.dp, CorTvColors.Accent), shape = RoundedCornerShape(8.dp))
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = CorTvColors.SecondaryText)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = query.ifEmpty { stringResource(R.string.search_hint) },
                    color = if (query.isEmpty()) CorTvColors.SecondaryText else CorTvColors.Text,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = CorTvColors.SecondaryText, modifier = Modifier.size(48.dp))
            Text(message, color = CorTvColors.SecondaryText, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun TvDialog(onDismiss: () -> Unit, width: Dp, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .width(width)
                .background(CorTvColors.Card, RoundedCornerShape(16.dp))
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

/** Round "X" shown in the top-right corner of popups. */
@Composable
private fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(44.dp),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = CorTvColors.Neutral,
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black
        )
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close), modifier = Modifier.size(24.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailsDialog(
    item: MediaItem,
    isFavorite: Boolean,
    initialEpisodeId: String?,
    progressOf: (String) -> PlaybackProgress?,
    onPlay: (episode: Episode?, fromStart: Boolean) -> Unit,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit
) {
    val playFocus = remember { FocusRequester() }
    val isSeries = item.episodes.isNotEmpty()
    var selectedEpisode by remember {
        mutableStateOf(item.episodes.firstOrNull { it.id == initialEpisodeId } ?: item.episodes.firstOrNull())
    }
    val seasons = remember(item) { item.episodes.map { it.season }.distinct().sorted() }
    val seasonEpisodes = item.episodes.filter { it.season == selectedEpisode?.season }
    val progress = progressOf(selectedEpisode?.id ?: item.id)
    val resumeAt = progress?.resumePositionMs

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .width(820.dp)
                .heightIn(max = 500.dp)
                .background(CorTvColors.Card, RoundedCornerShape(16.dp))
        ) {
            CompositionLocalProvider(LocalBringIntoViewSpec provides ScrollOnlyWhenNeeded) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Box(
                            Modifier
                                .width(260.dp)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            Artwork(selectedEpisode?.thumbnailUrl ?: item.logoUrl, item.category, item.title, isLogo = item.country != null)
                            progress?.let { ProgressBar(it, Modifier.align(Alignment.BottomStart)) }
                        }
                        Column(
                            modifier = Modifier.weight(1f).padding(end = 40.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.headlineSmall,
                                color = CorTvColors.Text,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            // For series the heading says which season and episode is selected
                            selectedEpisode?.let { ep ->
                                Text(
                                    stringResource(R.string.season_episode_title, ep.season, ep.number, ep.title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = CorTvColors.Text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                listOfNotNull(categoryLabel(item.category), item.genre).joinToString("  ·  "),
                                style = MaterialTheme.typography.labelLarge,
                                color = CorTvColors.Accent
                            )
                            (selectedEpisode?.director ?: item.director)?.let { DetailLine(stringResource(R.string.detail_director), it) }
                            item.country?.let { DetailLine(stringResource(R.string.detail_country), "${countryFlag(it)} ${countryName(it)}".trim()) }
                            item.network?.let { DetailLine(stringResource(R.string.detail_network), it) }
                            item.quality?.let { DetailLine(stringResource(R.string.detail_quality), it) }
                            formatReleaseDate(selectedEpisode?.releaseDate ?: item.releaseDate)?.let {
                                DetailLine(stringResource(if (item.category == CATEGORY_LIVE) R.string.detail_launched else R.string.detail_release), it)
                            }
                            item.website?.let { DetailLine(stringResource(R.string.detail_website), it.removePrefix("https://").removePrefix("http://").removeSuffix("/")) }
                            // iptv-org warns about streams that may not play everywhere or all day
                            val warnings = item.labels.mapNotNull { label ->
                                when (label) {
                                    "Geo-blocked" -> stringResource(R.string.label_geo_blocked)
                                    "Not 24/7" -> stringResource(R.string.label_not_24_7)
                                    else -> null
                                }
                            }
                            if (warnings.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF5C518), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(warnings.joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFF5C518))
                                }
                            }
                            if (item.category != CATEGORY_LIVE) RatingBadges(item, Modifier.padding(top = 4.dp))
                        }
                    }
                    // iptv-org channels have no synopsis; their details above already describe them
                    if (item.description != null || item.category != CATEGORY_LIVE) {
                        Text(
                            selectedEpisode?.description ?: item.description ?: stringResource(R.string.no_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = CorTvColors.SecondaryText,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { onPlay(selectedEpisode, false) },
                            colors = accentButtonColors(),
                            modifier = Modifier.focusRequester(playFocus)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (resumeAt != null) stringResource(R.string.action_resume, formatTime(resumeAt))
                                else stringResource(R.string.action_play)
                            )
                        }
                        if (resumeAt != null) {
                            Button(onClick = { onPlay(selectedEpisode, true) }, colors = cardButtonColors(CorTvColors.Neutral)) {
                                Icon(Icons.Default.Replay, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.action_from_start))
                            }
                        }
                        Button(onClick = onToggleFavorite, colors = cardButtonColors(CorTvColors.Neutral)) {
                            Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(if (isFavorite) R.string.action_remove_favorite else R.string.action_add_favorite))
                        }
                    }

                    if (isSeries) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            TvDropdown(
                                label = null,
                                options = seasons.map { it to stringResource(R.string.season_n, it) },
                                selected = selectedEpisode?.season ?: seasons.first(),
                                onSelect = { season -> selectedEpisode = item.episodes.first { it.season == season } }
                            )
                            TvDropdown(
                                label = null,
                                options = seasonEpisodes.map { it.id to stringResource(R.string.episode_n_title, it.number, it.title) },
                                selected = selectedEpisode?.id,
                                onSelect = { id -> selectedEpisode = item.episodes.first { it.id == id } }
                            )
                        }
                        Text(stringResource(R.string.detail_episodes), style = MaterialTheme.typography.titleSmall, color = CorTvColors.Text)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)
                        ) {
                            items(seasonEpisodes, key = { it.id }) { ep ->
                                EpisodeCard(
                                    episode = ep,
                                    fallbackArtwork = item.logoUrl,
                                    category = item.category,
                                    selected = ep.id == selectedEpisode?.id,
                                    progress = progressOf(ep.id),
                                    onClick = { selectedEpisode = ep }
                                )
                            }
                        }
                    }

                    if (item.cast.isNotEmpty()) {
                        Text(stringResource(R.string.detail_cast), style = MaterialTheme.typography.titleSmall, color = CorTvColors.Text)
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            item.cast.take(7).forEach { member ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(76.dp)
                                ) {
                                    CastAvatar(member, size = 48.dp)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        member.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CorTvColors.Text,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
            CloseButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp))
        }
    }
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }
}

@Composable
private fun EpisodeCard(
    episode: Episode,
    fallbackArtwork: String?,
    category: String,
    selected: Boolean,
    progress: PlaybackProgress?,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(190.dp),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        colors = CardDefaults.colors(containerColor = CorTvColors.Background, focusedContainerColor = CorTvColors.CardFocused),
        border = CardDefaults.border(
            border = if (selected) Border(BorderStroke(2.dp, CorTvColors.Accent), shape = RoundedCornerShape(10.dp)) else Border.None,
            focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = RoundedCornerShape(10.dp))
        ),
        scale = CardDefaults.scale(focusedScale = 1.05f)
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                Artwork(episode.thumbnailUrl ?: fallbackArtwork, category, episode.title)
                if (progress?.isFinished == true) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF66CC33), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.watched), color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
                progress?.let { ProgressBar(it, Modifier.align(Alignment.BottomStart)) }
            }
            Text(
                stringResource(R.string.episode_short, episode.number, episode.title),
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) CorTvColors.Accent else CorTvColors.Text,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row {
        Text("$label: ", style = MaterialTheme.typography.bodyMedium, color = CorTvColors.SecondaryText)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = CorTvColors.Text, maxLines = 1)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsDialog(
    currentSettings: PlayerSettings,
    currentLanguage: String,
    countries: List<Pair<String, String>>,
    currentCountry: String?,
    hiddenCount: Int,
    onRestoreHidden: () -> Unit,
    onSave: (settings: PlayerSettings, language: String, country: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var temp by remember { mutableStateOf(currentSettings) }
    var language by remember { mutableStateOf(currentLanguage) }
    var country by remember { mutableStateOf(currentCountry) }
    var hiddenLeft by remember { mutableIntStateOf(hiddenCount) }
    var cacheBytes by remember { mutableLongStateOf(-1L) }
    var cacheMessage by remember { mutableStateOf<String?>(null) }
    var confirmClearCache by remember { mutableStateOf(false) }
    val saveFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { cacheBytes = withContext(Dispatchers.IO) { context.cacheDir.sizeInBytes() } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .width(720.dp)
                .background(CorTvColors.Card, RoundedCornerShape(16.dp))
                .padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall, color = CorTvColors.Text, fontWeight = FontWeight.Bold)

            CompositionLocalProvider(LocalBringIntoViewSpec provides ScrollOnlyWhenNeeded) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    SettingsCard(Icons.Default.Language, stringResource(R.string.settings_language)) {
                        TvDropdown(
                            label = null,
                            options = listOf(LocaleHelper.SYSTEM to stringResource(R.string.language_system)) +
                                LocaleHelper.SUPPORTED_LANGUAGES.map { it to LocaleHelper.nativeName(it) },
                            selected = language,
                            onSelect = { language = it }
                        )
                    }
                    SettingsCard(Icons.Default.Public, stringResource(R.string.settings_country)) {
                        Text(stringResource(R.string.settings_country_hint), style = MaterialTheme.typography.bodySmall, color = CorTvColors.SecondaryText)
                        TvDropdown(
                            label = null,
                            options = listOf<Pair<String?, String>>(null to stringResource(R.string.filter_country_all)) + countries,
                            selected = country,
                            onSelect = { country = it }
                        )
                        if (hiddenLeft > 0) {
                            Text(pluralStringResource(R.plurals.hidden_channels_hint, hiddenLeft, hiddenLeft), style = MaterialTheme.typography.bodySmall, color = CorTvColors.SecondaryText)
                            Button(onClick = { onRestoreHidden(); hiddenLeft = 0 }, colors = cardButtonColors(CorTvColors.Neutral)) {
                                Icon(Icons.Default.Restore, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.action_restore_hidden))
                            }
                        }
                    }
                    SettingsCard(Icons.Default.AspectRatio, stringResource(R.string.settings_aspect)) {
                        ChoiceRow(
                            options = listOf(
                                "fill" to stringResource(R.string.aspect_fill),
                                "fit" to stringResource(R.string.aspect_fit),
                                "16:9" to "16:9",
                                "4:3" to "4:3"
                            ),
                            selected = temp.aspectMode,
                            onSelect = { temp = temp.copy(aspectMode = it) }
                        )
                    }
                    SettingsCard(Icons.Default.RecordVoiceOver, stringResource(R.string.settings_audio)) {
                        ChoiceRow(
                            options = listOf(
                                "original" to stringResource(R.string.audio_original),
                                "es" to stringResource(R.string.lang_spanish),
                                "en" to stringResource(R.string.lang_english)
                            ),
                            selected = temp.audioLanguage,
                            onSelect = { temp = temp.copy(audioLanguage = it) }
                        )
                    }
                    SettingsCard(Icons.Default.Subtitles, stringResource(R.string.settings_subtitles)) {
                        ChoiceRow(
                            options = listOf(
                                "off" to stringResource(R.string.subtitles_off),
                                "es" to stringResource(R.string.lang_spanish),
                                "en" to stringResource(R.string.lang_english)
                            ),
                            selected = temp.subtitleLanguage,
                            onSelect = { temp = temp.copy(subtitleLanguage = it) }
                        )
                        SubsectionTitle(stringResource(R.string.settings_subtitle_size))
                        ChoiceRow(
                            options = listOf(
                                "small" to stringResource(R.string.size_small),
                                "normal" to stringResource(R.string.size_normal),
                                "large" to stringResource(R.string.size_large)
                            ),
                            selected = temp.subtitleSize,
                            onSelect = { temp = temp.copy(subtitleSize = it) }
                        )
                        SubsectionTitle(stringResource(R.string.settings_subtitle_style))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(
                                "white" to stringResource(R.string.style_white),
                                "yellow" to stringResource(R.string.style_yellow),
                                "black_bg" to stringResource(R.string.style_black_bg)
                            ).forEach { (code, name) ->
                                StyleChip(
                                    style = code,
                                    name = name,
                                    selected = temp.subtitleStyle == code,
                                    onClick = { temp = temp.copy(subtitleStyle = code) }
                                )
                            }
                        }
                        SubtitlePreview(temp)
                    }
                }
            }

            cacheMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF66CC33))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onSave(temp, language, country) },
                    colors = accentButtonColors(),
                    modifier = Modifier.focusRequester(saveFocus)
                ) { Text(stringResource(R.string.action_save)) }
                Button(onClick = onDismiss, colors = cardButtonColors(CorTvColors.Neutral)) { Text(stringResource(R.string.action_cancel)) }
                Spacer(Modifier.weight(1f))
                Button(onClick = { confirmClearCache = true }, colors = cardButtonColors(CorTvColors.Neutral)) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (cacheBytes < 0) stringResource(R.string.action_clear_cache)
                        else stringResource(R.string.action_clear_cache_size, formatSize(cacheBytes))
                    )
                }
            }
        }
    }

    if (confirmClearCache) {
        ConfirmDialog(
            title = stringResource(R.string.confirm_clear_cache_title),
            message = stringResource(R.string.confirm_clear_cache_message, formatSize(cacheBytes.coerceAtLeast(0))),
            confirmLabel = stringResource(R.string.action_clear),
            onConfirm = {
                confirmClearCache = false
                scope.launch {
                    val freed = withContext(Dispatchers.IO) { clearAppCache(context) }
                    cacheBytes = withContext(Dispatchers.IO) { context.cacheDir.sizeInBytes() }
                    cacheMessage = resources.getString(R.string.cache_cleared, formatSize(freed))
                }
            },
            onDismiss = { confirmClearCache = false }
        )
    }

    LaunchedEffect(Unit) { runCatching { saveFocus.requestFocus() } }
}

/**
 * A visually separated block of the settings, with an icon and a title.
 * When any control inside gains focus the whole card is scrolled into view,
 * so its title is never left hidden above the scroll viewport.
 */
@Composable
private fun SettingsCard(icon: ImageVector, title: String, content: @Composable ColumnScope.() -> Unit) {
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .bringIntoViewRequester(bringIntoView)
            .onFocusChanged { if (it.hasFocus) scope.launch { bringIntoView.bringIntoView() } }
            .fillMaxWidth()
            .background(CorTvColors.Background.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .border(1.dp, CorTvColors.Neutral, RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = CorTvColors.Accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(title, color = CorTvColors.Text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        content()
    }
}

@Composable
private fun SubsectionTitle(title: String) {
    Text(
        title,
        color = CorTvColors.SecondaryText,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun ChoiceRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { (code, name) ->
            val isSelected = selected == code
            Button(
                onClick = { onSelect(code) },
                colors = if (isSelected) accentButtonColors() else cardButtonColors(CorTvColors.Neutral),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(
                    if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(name, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Subtitle style option showing a sample "Aa" drawn in that style. */
@Composable
private fun StyleChip(style: String, name: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = if (selected) accentButtonColors() else cardButtonColors(CorTvColors.Neutral),
        contentPadding = PaddingValues(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 6.dp)
    ) {
        SubtitleSample(
            text = "Aa",
            style = style,
            fontSize = 18.sp,
            modifier = Modifier
                .background(Color(0xFF4A5A6A), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(name, style = MaterialTheme.typography.labelLarge)
    }
}

/** Text drawn like a subtitle in the given style ("white", "yellow" or "black_bg"). */
@Composable
private fun SubtitleSample(text: String, style: String, fontSize: TextUnit, modifier: Modifier = Modifier) {
    val color = if (style == "yellow") Color.Yellow else Color.White
    Box(modifier) {
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            style = TextStyle(shadow = if (style == "black_bg") null else Shadow(Color.Black, blurRadius = 6f)),
            modifier = if (style == "black_bg") {
                Modifier.background(Color.Black.copy(alpha = 0.86f)).padding(horizontal = 6.dp, vertical = 1.dp)
            } else Modifier
        )
    }
}

/** Live preview of the chosen subtitle size and style. */
@Composable
private fun SubtitlePreview(settings: PlayerSettings) {
    val size = when (settings.subtitleSize) {
        "small" -> 14.sp
        "large" -> 24.sp
        else -> 18.sp
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3A5A78), Color(0xFF8A6A4A)))),
        contentAlignment = Alignment.Center
    ) {
        SubtitleSample(stringResource(R.string.subtitle_preview), settings.subtitleStyle, size)
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    // Focus starts on "Cancelar" so a stray click never deletes anything
    val cancelFocus = remember { FocusRequester() }
    TvDialog(onDismiss = onDismiss, width = 480.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = CorTvColors.Accent, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = CorTvColors.Text, fontWeight = FontWeight.Bold)
        }
        Text(message, style = MaterialTheme.typography.bodyMedium, color = CorTvColors.SecondaryText)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onConfirm, colors = accentButtonColors()) { Text(confirmLabel) }
            Button(
                onClick = onDismiss,
                colors = cardButtonColors(CorTvColors.Neutral),
                modifier = Modifier.focusRequester(cancelFocus)
            ) { Text(stringResource(R.string.action_cancel)) }
        }
    }
    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
}

/**
 * On TV, Compose keeps the focused item at ~30% of a scrolling container, which pushes the top of
 * popups out of view. Inside popups we only scroll when the focused item would be cut off.
 */
private val ScrollOnlyWhenNeeded = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val trailingEdge = offset + size
        return when {
            offset < 0f -> offset
            trailingEdge > containerSize -> trailingEdge - containerSize
            else -> 0f
        }
    }
}

private fun File.sizeInBytes(): Long = walkTopDown().filter { it.isFile }.sumOf { it.length() }

/** Clears Coil's memory/disk caches and the app cache dir. Returns the bytes freed. */
@OptIn(ExperimentalCoilApi::class)
private fun clearAppCache(context: Context): Long {
    val before = context.cacheDir.sizeInBytes()
    context.imageLoader.memoryCache?.clear()
    context.imageLoader.diskCache?.clear()
    context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    return (before - context.cacheDir.sizeInBytes()).coerceAtLeast(0)
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
    bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.US, "%d:%02d", minutes, seconds)
}

@Composable
private fun cardButtonColors(container: Color = CorTvColors.Card) = ButtonDefaults.colors(
    containerColor = container,
    contentColor = Color.White,
    focusedContainerColor = Color.White,
    focusedContentColor = Color.Black
)

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = CorTvColors.Text,
    unfocusedTextColor = CorTvColors.Text,
    cursorColor = CorTvColors.Accent,
    focusedBorderColor = CorTvColors.Accent,
    unfocusedBorderColor = CorTvColors.SecondaryText,
    focusedLabelColor = CorTvColors.Accent,
    unfocusedLabelColor = CorTvColors.SecondaryText,
    focusedLeadingIconColor = CorTvColors.Accent,
    unfocusedLeadingIconColor = CorTvColors.SecondaryText
)
