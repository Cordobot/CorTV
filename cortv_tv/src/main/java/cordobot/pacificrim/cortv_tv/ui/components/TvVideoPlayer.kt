@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package cordobot.pacificrim.cortv_tv.ui.components

import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.LayoutInflater
import android.text.Layout
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import cordobot.pacificrim.cortv.common.model.CastMember
import cordobot.pacificrim.cortv.common.model.MediaItem
import cordobot.pacificrim.cortv_tv.R
import cordobot.pacificrim.cortv_tv.ui.theme.CorTvColors
import kotlinx.coroutines.delay
import java.util.Locale
import androidx.media3.common.MediaItem as ExoMediaItem

private const val SEEK_STEP_MS = 10_000L
private const val SKIP_INTRO_MS = 85_000L
private const val CONTROLS_TIMEOUT_MS = 5_000L
private const val PROGRESS_SAVE_INTERVAL_MS = 5_000L

// Some manifests use technical track names such as "stream_5"; those are not useful to show
private val TECHNICAL_TRACK_LABEL = Regex("(?i)stream_?\\d+")

/** A selectable audio or subtitle track of the current media. */
private data class TrackOption(
    val label: String,
    val group: Tracks.Group,
    val trackIndex: Int
)

private enum class TrackDialog { NONE, AUDIO, SUBTITLES }

@Composable
fun TvVideoPlayer(
    item: MediaItem,
    settings: PlayerSettings,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    startPositionMs: Long = 0L,
    onProgress: (positionMs: Long, durationMs: Long) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val offLabel = stringResource(R.string.track_off)
    val originalLabel = stringResource(R.string.track_original)
    val lifecycleOwner = LocalLifecycleOwner.current

    var isPlaying by remember { mutableStateOf(false) }
    // Play intent: stays true while buffering, so the play/pause button doesn't flicker
    var wantsToPlay by remember { mutableStateOf(true) }
    var userPaused by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var isLive by remember { mutableStateOf(false) }
    var isEnded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var subtitleOptions by remember { mutableStateOf<List<TrackOption>>(emptyList()) }
    var currentSubtitle by remember { mutableStateOf(offLabel) }
    var audioOptions by remember { mutableStateOf<List<TrackOption>>(emptyList()) }
    var currentAudio by remember { mutableStateOf(originalLabel) }
    var cues by remember { mutableStateOf<List<Cue>>(emptyList()) }

    var showControls by remember { mutableStateOf(true) }
    var showCast by remember { mutableStateOf(false) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var openDialog by remember { mutableStateOf(TrackDialog.NONE) }
    var seekFeedback by remember { mutableStateOf<String?>(null) }

    val rootFocusRequester = remember { FocusRequester() }
    val playFocusRequester = remember { FocusRequester() }
    val retryFocusRequester = remember { FocusRequester() }
    val castFocusRequester = remember { FocusRequester() }

    val exoPlayer = remember(item.url) {
        // Some IPTV streams need a specific User-Agent/Referer and redirect between http and https
        val httpFactory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        item.userAgent?.let { httpFactory.setUserAgent(it) }
        item.referrer?.let { httpFactory.setDefaultRequestProperties(mapOf("Referer" to it)) }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpFactory)))
            .build().apply {
                trackSelectionParameters = trackSelectionParameters.buildUpon().apply {
                    if (settings.subtitleLanguage == "off") {
                        setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    } else {
                        setPreferredTextLanguage(settings.subtitleLanguage)
                    }
                    // "original" keeps the stream's default audio track
                    if (settings.audioLanguage != "original") setPreferredAudioLanguage(settings.audioLanguage)
                }.build()
                // Resume where the viewer left off
                setMediaItem(ExoMediaItem.fromUri(item.url), startPositionMs.coerceAtLeast(0L))
                playWhenReady = true
                prepare()
            }
    }

    fun refreshTracks(tracks: Tracks) {
        val (subs, selectedSub) = collectTracks(context, tracks, C.TRACK_TYPE_TEXT)
        subtitleOptions = subs
        currentSubtitle = selectedSub ?: offLabel
        val (audios, selectedAudio) = collectTracks(context, tracks, C.TRACK_TYPE_AUDIO)
        audioOptions = audios
        currentAudio = selectedAudio ?: originalLabel
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                wantsToPlay = playWhenReady
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                isEnded = playbackState == Player.STATE_ENDED
                if (playbackState == Player.STATE_READY) {
                    errorMessage = null
                    isLive = exoPlayer.isCurrentMediaItemLive
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                }
                if (playbackState == Player.STATE_ENDED) {
                    showControls = true
                    if (!isLive) onProgress(exoPlayer.duration, exoPlayer.duration)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                errorMessage = friendlyError(context, error)
            }

            override fun onTracksChanged(tracks: Tracks) {
                refreshTracks(tracks)
            }

            override fun onCues(cueGroup: CueGroup) {
                cues = cueGroup.cues.map { it.withDefaultPosition() }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            saveProgress(exoPlayer, onProgress)
            exoPlayer.release()
        }
    }

    // Pause when the app goes to background, resume when it returns (unless the user paused).
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    exoPlayer.pause()
                    saveProgress(exoPlayer, onProgress)
                }
                Lifecycle.Event.ON_START -> if (!userPaused) exoPlayer.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Save the position periodically so progress survives even if the app is killed.
    LaunchedEffect(exoPlayer, isPlaying) {
        while (isPlaying) {
            delay(PROGRESS_SAVE_INTERVAL_MS)
            saveProgress(exoPlayer, onProgress)
        }
    }

    // Track playback position while there is something on screen that shows it.
    LaunchedEffect(exoPlayer, showControls, seekFeedback) {
        while (showControls || seekFeedback != null) {
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
            delay(500)
        }
    }

    // Auto-hide the controls while playing (but not while the user is browsing the cast or a menu).
    LaunchedEffect(showControls, interactionTick, isPlaying, openDialog, errorMessage, showCast) {
        if (showControls && isPlaying && openDialog == TrackDialog.NONE && errorMessage == null && !showCast) {
            delay(CONTROLS_TIMEOUT_MS)
            showControls = false
        }
    }

    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(1200)
            seekFeedback = null
        }
    }

    // Keep D-pad focus somewhere sensible: on the main button when controls are visible,
    // otherwise on the player surface so it keeps receiving key events.
    LaunchedEffect(showControls, errorMessage) {
        if (!showControls) showCast = false
        runCatching {
            when {
                errorMessage != null -> retryFocusRequester.requestFocus()
                showControls -> playFocusRequester.requestFocus()
                else -> rootFocusRequester.requestFocus()
            }
        }
    }

    LaunchedEffect(showCast) {
        if (showCast) {
            delay(150) // let the panel enter composition
            runCatching { castFocusRequester.requestFocus() }
        }
    }

    fun togglePlayPause() {
        if (exoPlayer.playWhenReady && exoPlayer.playbackState != Player.STATE_ENDED) {
            userPaused = true
            exoPlayer.pause()
        } else {
            userPaused = false
            if (exoPlayer.playbackState == Player.STATE_ENDED) exoPlayer.seekTo(0)
            exoPlayer.play()
        }
    }

    fun seekBy(deltaMs: Long) {
        if (isLive || !exoPlayer.isCurrentMediaItemSeekable) return
        val duration = exoPlayer.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        val target = (exoPlayer.currentPosition + deltaMs).coerceIn(0L, duration)
        exoPlayer.seekTo(target)
        positionMs = target
        seekFeedback = if (deltaMs >= 0) "+${deltaMs / 1000} s" else "-${-deltaMs / 1000} s"
    }

    fun retry() {
        errorMessage = null
        isBuffering = true
        if (isLive) exoPlayer.seekToDefaultPosition()
        exoPlayer.prepare()
        exoPlayer.play()
    }

    BackHandler {
        when {
            showCast -> {
                showCast = false
                runCatching { playFocusRequester.requestFocus() }
            }
            showControls && errorMessage == null && isPlaying -> showControls = false
            else -> onBack()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Back, Key.Escape -> false
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        togglePlayPause(); showControls = true; interactionTick++; true
                    }
                    Key.MediaFastForward -> { seekBy(SEEK_STEP_MS); true }
                    Key.MediaRewind -> { seekBy(-SEEK_STEP_MS); true }
                    else -> if (!showControls && errorMessage == null) {
                        when (event.key) {
                            Key.DirectionLeft -> seekBy(-SEEK_STEP_MS)
                            Key.DirectionRight -> seekBy(SEEK_STEP_MS)
                            else -> { showControls = true; interactionTick++ }
                        }
                        true
                    } else {
                        interactionTick++
                        false
                    }
                }
            }
            .focusRequester(rootFocusRequester)
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        // Screen format: 16:9 / 4:3 force a frame of that ratio; "fill" crops to fill the screen.
        val frameModifier = when (settings.aspectMode) {
            "16:9" -> Modifier.aspectRatio(16f / 9f)
            "4:3" -> Modifier.aspectRatio(4f / 3f)
            else -> Modifier.fillMaxSize()
        }
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.tv_player_view, null) as PlayerView).apply {
                    useController = false // TV-optimized controls are drawn in Compose
                    keepScreenOn = true
                    setShutterBackgroundColor(AndroidColor.BLACK)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    resizeMode = when (settings.aspectMode) {
                        "fill" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        "16:9", "4:3" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                    // Subtitles are drawn by our own overlay below, so they can avoid the controls
                    subtitleView?.visibility = View.GONE
                }
            },
            update = { view -> view.player = exoPlayer },
            modifier = frameModifier
        )

        AndroidView(
            factory = { ctx -> SubtitleView(ctx).apply { applyStyle(settings) } },
            update = { view ->
                view.setCues(cues)
                // Lift subtitles above the controls overlay while it is visible
                view.setBottomPaddingFraction(if (showControls) 0.36f else 0.08f)
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isBuffering && errorMessage == null) {
            CircularProgressIndicator(
                color = CorTvColors.Accent,
                modifier = Modifier.align(Alignment.Center).size(64.dp)
            )
        }

        errorMessage?.let { message ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(CorTvColors.Card.copy(alpha = 0.95f), RoundedCornerShape(16.dp))
                    .padding(32.dp)
                    .widthIn(max = 520.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = CorTvColors.Accent, modifier = Modifier.size(48.dp))
                Text(item.title, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = CorTvColors.SecondaryText)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = ::retry,
                        modifier = Modifier.focusRequester(retryFocusRequester),
                        colors = accentButtonColors()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.player_retry))
                    }
                    Button(onClick = onBack, colors = neutralButtonColors()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.player_back))
                    }
                }
            }
        }

        // Quick seek feedback when seeking with the controls hidden
        if (!showControls && errorMessage == null) {
            seekFeedback?.let { feedback ->
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(feedback, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    ProgressRow(positionMs, durationMs)
                }
            }
        }

        if (showControls && errorMessage == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .padding(horizontal = 48.dp, vertical = 27.dp)
            ) {
                // Top: back, title + technical sheet, cast dropdown
                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopStart),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Button(onClick = onBack, colors = neutralButtonColors()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.player_back))
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isLive) {
                                Spacer(Modifier.width(16.dp))
                                LiveBadge()
                            }
                        }
                        val meta = metaLine(item)
                        if (meta.isNotEmpty()) {
                            Text(meta, style = MaterialTheme.typography.bodyMedium, color = Color.LightGray, maxLines = 1)
                        }
                        if (!isLive) RatingBadges(item)
                    }
                    if (item.cast.isNotEmpty()) {
                        Column(horizontalAlignment = Alignment.End) {
                            Button(onClick = { showCast = !showCast; interactionTick++ }, colors = neutralButtonColors()) {
                                Icon(Icons.Default.People, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.player_cast))
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    if (showCast) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null
                                )
                            }
                            AnimatedVisibility(
                                visible = showCast,
                                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                            ) {
                                CastPanel(
                                    cast = item.cast,
                                    firstFocus = castFocusRequester,
                                    onInteraction = { interactionTick++ }
                                )
                            }
                        }
                    }
                }

                // Bottom: synopsis, progress and actions
                Column(
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomStart),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (!item.description.isNullOrEmpty() && !showCast) {
                        Text(
                            text = item.description.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!isLive && durationMs > 0) ProgressRow(positionMs, durationMs)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isLive) {
                            Button(onClick = { seekBy(-SEEK_STEP_MS) }, colors = neutralButtonColors()) {
                                Icon(Icons.Default.Replay10, contentDescription = stringResource(R.string.player_rewind))
                            }
                        }
                        Button(
                            onClick = { togglePlayPause(); interactionTick++ },
                            modifier = Modifier.focusRequester(playFocusRequester),
                            colors = accentButtonColors()
                        ) {
                            Icon(
                                imageVector = when {
                                    isEnded -> Icons.Default.Replay
                                    wantsToPlay -> Icons.Default.Pause
                                    else -> Icons.Default.PlayArrow
                                },
                                contentDescription = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when {
                                    isEnded -> stringResource(R.string.player_replay)
                                    wantsToPlay -> stringResource(R.string.player_pause)
                                    else -> stringResource(R.string.player_play)
                                }
                            )
                        }
                        if (!isLive) {
                            Button(onClick = { seekBy(SEEK_STEP_MS) }, colors = neutralButtonColors()) {
                                Icon(Icons.Default.Forward10, contentDescription = stringResource(R.string.player_forward))
                            }
                            Button(onClick = { seekBy(SKIP_INTRO_MS) }, colors = neutralButtonColors()) {
                                Icon(Icons.Default.FastForward, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.player_skip_intro))
                            }
                        }
                        Button(onClick = { openDialog = TrackDialog.AUDIO }, colors = neutralButtonColors()) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.player_audio, currentAudio), maxLines = 1)
                        }
                        Button(onClick = { openDialog = TrackDialog.SUBTITLES }, colors = neutralButtonColors()) {
                            Icon(Icons.Default.Subtitles, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.player_subtitles, currentSubtitle), maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    when (openDialog) {
        TrackDialog.NONE -> Unit
        TrackDialog.SUBTITLES -> TrackSelectionDialog(
            title = stringResource(R.string.dialog_subtitles),
            emptyMessage = stringResource(R.string.no_subtitles),
            options = subtitleOptions,
            current = currentSubtitle,
            offLabel = offLabel,
            onOff = {
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
                openDialog = TrackDialog.NONE
            },
            onSelect = { option ->
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.trackIndex))
                    .build()
                openDialog = TrackDialog.NONE
            },
            onDismiss = { openDialog = TrackDialog.NONE }
        )
        TrackDialog.AUDIO -> TrackSelectionDialog(
            title = stringResource(R.string.dialog_audio),
            emptyMessage = stringResource(R.string.single_audio),
            options = audioOptions,
            current = currentAudio,
            offLabel = null,
            onOff = {},
            onSelect = { option ->
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                    .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.trackIndex))
                    .build()
                openDialog = TrackDialog.NONE
            },
            onDismiss = { openDialog = TrackDialog.NONE }
        )
    }
}

/** Builds human-readable options ("Español", "Inglés · 5.1") for the tracks of [type], plus the selected label. */
private fun collectTracks(context: Context, tracks: Tracks, type: Int): Pair<List<TrackOption>, String?> {
    val options = mutableListOf<TrackOption>()
    var selected: String? = null
    tracks.groups.filter { it.type == type }.forEach { group ->
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            val format = group.getTrackFormat(i)
            val language = format.language
                ?.takeIf { it != C.LANGUAGE_UNDETERMINED }
                ?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.getDefault()) }
                ?.replaceFirstChar { it.titlecase(Locale.getDefault()) }
            val label = format.label?.takeUnless { it.matches(TECHNICAL_TRACK_LABEL) }
            val channels = when {
                type != C.TRACK_TYPE_AUDIO || format.channelCount == Format.NO_VALUE -> null
                format.channelCount >= 6 -> "5.1"
                format.channelCount == 1 -> context.getString(R.string.track_mono)
                else -> null
            }
            var text = listOfNotNull(language, label, channels).distinct().joinToString(" · ")
                .ifEmpty { context.getString(R.string.track_n, options.size + 1) }
            if (options.any { it.label == text }) text += " (${options.count { it.label.startsWith(text) } + 1})"
            options += TrackOption(text, group, i)
            if (group.isTrackSelected(i)) selected = text
        }
    }
    return options to selected
}

/** Drops positions embedded in the subtitle file (e.g. WebVTT "line:") so cues sit at the bottom and respect our padding. */
private fun Cue.withDefaultPosition(): Cue {
    if (bitmap != null) return this
    return buildUpon()
        .setLine(Cue.DIMEN_UNSET, Cue.TYPE_UNSET)
        .setLineAnchor(Cue.TYPE_UNSET)
        .setPosition(Cue.DIMEN_UNSET)
        .setPositionAnchor(Cue.TYPE_UNSET)
        .setSize(Cue.DIMEN_UNSET)
        .setTextAlignment(Layout.Alignment.ALIGN_CENTER)
        .build()
}

private fun SubtitleView.applyStyle(settings: PlayerSettings) {
    // Our settings win over styles embedded in the subtitle file
    setApplyEmbeddedStyles(false)
    setApplyEmbeddedFontSizes(false)
    val scale = when (settings.subtitleSize) {
        "small" -> 0.8f
        "large" -> 1.35f
        else -> 1f
    }
    setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * scale)
    setStyle(
        when (settings.subtitleStyle) {
            "yellow" -> CaptionStyleCompat(
                AndroidColor.YELLOW, AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE, AndroidColor.BLACK, null
            )
            "black_bg" -> CaptionStyleCompat(
                AndroidColor.WHITE, AndroidColor.argb(220, 0, 0, 0), AndroidColor.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_NONE, AndroidColor.TRANSPARENT, null
            )
            else -> CaptionStyleCompat(
                AndroidColor.WHITE, AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE, AndroidColor.BLACK, null
            )
        }
    )
}

/** Translucent dropdown with the cast: small photo, real name and character. */
@Composable
private fun CastPanel(cast: List<CastMember>, firstFocus: FocusRequester, onInteraction: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(top = 12.dp)
            .width(340.dp)
            .heightIn(max = 300.dp)
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .padding(vertical = 8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        cast.forEachIndexed { index, member ->
            Surface(
                onClick = onInteraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    focusedContainerColor = Color.White.copy(alpha = 0.18f),
                    focusedContentColor = Color.White
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CastAvatar(member, size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(member.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                        member.character?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = Color.LightGray, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackSelectionDialog(
    title: String,
    emptyMessage: String,
    options: List<TrackOption>,
    current: String,
    offLabel: String?,
    onOff: () -> Unit,
    onSelect: (TrackOption) -> Unit,
    onDismiss: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    // Focus the active option; fall back to the first one if nothing matches
    val focusLabel = when {
        offLabel != null && current == offLabel -> offLabel
        options.any { it.label == current } -> current
        else -> offLabel ?: options.firstOrNull()?.label
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .width(380.dp)
                .background(CorTvColors.Card, RoundedCornerShape(16.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
            if (options.size <= if (offLabel == null) 1 else 0) {
                Text(emptyMessage, color = CorTvColors.SecondaryText)
            }
            Column(
                modifier = Modifier
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (offLabel != null) {
                    OptionButton(
                        label = offLabel,
                        selected = current == offLabel,
                        onClick = onOff,
                        modifier = if (focusLabel == offLabel) Modifier.focusRequester(firstFocus) else Modifier
                    )
                }
                options.forEach { option ->
                    OptionButton(
                        label = option.label,
                        selected = current == option.label,
                        onClick = { onSelect(option) },
                        modifier = if (focusLabel == option.label) Modifier.focusRequester(firstFocus) else Modifier
                    )
                }
            }
            Button(onClick = onDismiss, colors = neutralButtonColors(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
}

@Composable
private fun OptionButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = if (selected) accentButtonColors() else neutralButtonColors()
    ) {
        Icon(
            imageVector = if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
            contentDescription = null
        )
        Spacer(Modifier.width(12.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ProgressRow(positionMs: Long, durationMs: Long) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(formatTime(positionMs), color = Color.White, style = MaterialTheme.typography.bodySmall)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(3.dp))
        ) {
            val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(CorTvColors.Accent, RoundedCornerShape(3.dp))
            )
        }
        Text(formatTime(durationMs), color = Color.White, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun LiveBadge() {
    Row(
        modifier = Modifier
            .background(CorTvColors.Accent, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.player_live), color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun accentButtonColors() = ButtonDefaults.colors(
    containerColor = CorTvColors.Accent,
    contentColor = Color.White,
    focusedContainerColor = Color.White,
    focusedContentColor = CorTvColors.Accent
)

@Composable
internal fun neutralButtonColors() = ButtonDefaults.colors(
    containerColor = CorTvColors.Neutral.copy(alpha = 0.85f),
    contentColor = Color.White,
    focusedContainerColor = Color.White,
    focusedContentColor = Color.Black
)

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

internal fun friendlyError(context: Context, error: PlaybackException): String = when (error.errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> context.getString(R.string.error_network)
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> context.getString(R.string.error_unavailable)
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> context.getString(R.string.error_format)
    PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> context.getString(R.string.error_live_window)
    else -> context.getString(R.string.error_generic, error.errorCodeName)
}

/** Reports the current position of on-demand content; live streams have no meaningful progress. */
private fun saveProgress(player: ExoPlayer, onProgress: (Long, Long) -> Unit) {
    if (player.isCurrentMediaItemLive) return
    val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
    onProgress(player.currentPosition.coerceIn(0L, duration), duration)
}
