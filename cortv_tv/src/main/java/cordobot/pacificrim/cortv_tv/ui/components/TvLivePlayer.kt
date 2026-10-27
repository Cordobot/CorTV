@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package cordobot.pacificrim.cortv_tv.ui.components

import android.graphics.Color as AndroidColor
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import cordobot.pacificrim.cortv.common.model.MediaItem
import cordobot.pacificrim.cortv.common.model.countryFlag
import cordobot.pacificrim.cortv.common.model.countryName
import cordobot.pacificrim.cortv_tv.R
import cordobot.pacificrim.cortv_tv.ui.theme.CorTvColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.media3.common.MediaItem as ExoMediaItem

private const val ZAP_DELAY_MS = 400L // lets the viewer press up/down several times before a channel loads
private const val BANNER_TIMEOUT_MS = 4_000L
private const val CONTROLS_TIMEOUT_MS = 6_000L
private const val STALL_TIMEOUT_MS = 10_000L // no new video for this long counts as frozen
private const val HEALTHY_RESET_MS = 30_000L // playing this long without problems resets the reconnection attempts
private const val MAX_AUTO_RELOADS = 3 // for a channel that was playing and dropped
private const val MAX_FIRST_LOAD_RELOADS = 1 // for a channel that never started: move on sooner
private const val SKIP_NOTICE_MS = 1_500L
private const val MAX_CONSECUTIVE_SKIPS = 10 // then stop: probably the connection is down, not the channels

// Errors that won't fix themselves by reconnecting
private val PERMANENT_ERRORS = setOf(
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
)

/**
 * Full-screen player for live channels. Up/down on the D-pad (or CH+/CH-) switch to the next or
 * previous channel of [channels]; a frozen or dropped signal reconnects on its own, and the
 * "Reload" button forces it. No seeking, ratings or skip-intro: those only make sense on demand.
 */
@Composable
fun TvLivePlayer(
    channels: List<MediaItem>,
    startIndex: Int,
    settings: PlayerSettings,
    isFavorite: (MediaItem) -> Boolean,
    onToggleFavorite: (MediaItem) -> Unit,
    onChannelStarted: (MediaItem) -> Unit,
    onChannelFailed: (MediaItem) -> Unit,
    onBack: (lastChannel: MediaItem) -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, (channels.size - 1).coerceAtLeast(0))) }
    val channel = channels[index]
    // The player listener lives for the whole session, so it must read the channel on air at call time
    fun current() = channels[index]
    val currentOnChannelFailed by rememberUpdatedState(onChannelFailed)

    var isBuffering by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var autoReloads by remember { mutableIntStateOf(0) }
    var showControls by remember { mutableStateOf(false) }
    var showBanner by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var favorite by remember(index) { mutableStateOf(isFavorite(channel)) }
    var everPlayed by remember(index) { mutableStateOf(false) }
    var consecutiveSkips by remember { mutableIntStateOf(0) }
    var lastDelta by remember { mutableIntStateOf(+1) }
    // Set when the channel couldn't be played: shown briefly, then the player moves to the next channel
    var skipNotice by remember { mutableStateOf<String?>(null) }

    val rootFocus = remember { FocusRequester() }
    val reloadFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }

    // One player for the whole session; zapping only swaps the stream. The HTTP factory is shared
    // so each channel can set the User-Agent/Referer it needs before it loads.
    val httpFactory = remember {
        DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(10_000)
    }
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpFactory)))
            .build()
            .apply { playWhenReady = true }
    }

    fun load(item: MediaItem) {
        httpFactory.setUserAgent(item.userAgent)
        httpFactory.setDefaultRequestProperties(item.referrer?.let { mapOf("Referer" to it) } ?: emptyMap())
        exoPlayer.setMediaItem(ExoMediaItem.fromUri(item.url))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    /** Opens a fresh connection to the current channel, at the live edge. */
    fun reload(manual: Boolean) {
        if (manual) autoReloads = 0
        errorMessage = null
        isBuffering = true
        exoPlayer.stop()
        load(current())
    }

    fun maxReloads() = if (everPlayed) MAX_AUTO_RELOADS else MAX_FIRST_LOAD_RELOADS

    /**
     * The channel can't be played: skip to the next one in the direction the viewer was zapping.
     * After too many channels in a row fail, stop and show [message]. [hide] reports the channel as
     * broken (only for errors that reconnecting can't fix, not a dropped connection).
     */
    fun fail(message: String, hide: Boolean) {
        isBuffering = false
        if (hide) currentOnChannelFailed(current())
        if (channels.size > 1 && consecutiveSkips < MAX_CONSECUTIVE_SKIPS) {
            consecutiveSkips++
            skipNotice = resources.getString(R.string.live_skipping, current().title)
        } else {
            errorMessage = if (channels.size > 1) resources.getString(R.string.live_none_available) else message
        }
    }

    fun zap(delta: Int, manual: Boolean = true) {
        if (channels.size < 2) return
        lastDelta = delta
        if (manual) consecutiveSkips = 0
        index = (index + delta).mod(channels.size)
        showControls = false
    }

    // Load the channel whenever it changes (with a short delay so fast zapping doesn't load every channel on the way)
    LaunchedEffect(index) {
        showBanner = true
        skipNotice = null
        errorMessage = null
        autoReloads = 0
        isBuffering = true
        exoPlayer.stop()
        delay(ZAP_DELAY_MS)
        load(channel)
        onChannelStarted(channel)
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) errorMessage = null
            }

            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    exoPlayer.seekToDefaultPosition()
                    exoPlayer.prepare()
                    return
                }
                if (error.errorCode !in PERMANENT_ERRORS && autoReloads < maxReloads()) {
                    autoReloads++
                    scope.launch { delay(1_500); reload(manual = false) }
                } else {
                    fail(friendlyError(context, error), hide = error.errorCode in PERMANENT_ERRORS)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Watchdog: if the picture stops advancing (frozen or endless buffering), reconnect automatically
    LaunchedEffect(exoPlayer, index) {
        var lastPosition = -1L
        var stalledMs = 0L
        var healthyMs = 0L
        while (true) {
            delay(1_000)
            if (errorMessage != null || skipNotice != null || !exoPlayer.playWhenReady) {
                stalledMs = 0
                continue
            }
            val position = exoPlayer.currentPosition
            val advancing = exoPlayer.isPlaying && position != lastPosition
            lastPosition = position
            if (advancing) {
                everPlayed = true
                consecutiveSkips = 0
                stalledMs = 0
                healthyMs += 1_000
                if (healthyMs >= HEALTHY_RESET_MS) autoReloads = 0
            } else {
                healthyMs = 0
                stalledMs += 1_000
            }
            if (stalledMs >= STALL_TIMEOUT_MS) {
                stalledMs = 0
                if (autoReloads < maxReloads()) {
                    autoReloads++
                    reload(manual = false)
                } else {
                    fail(resources.getString(R.string.live_error_frozen), hide = false)
                }
            }
        }
    }

    // Pause in the background; when coming back jump to the live edge instead of resuming old video
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> exoPlayer.pause()
                Lifecycle.Event.ON_START -> if (exoPlayer.mediaItemCount > 0) reload(manual = false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(skipNotice) {
        if (skipNotice != null) {
            delay(SKIP_NOTICE_MS)
            zap(lastDelta, manual = false)
        }
    }

    LaunchedEffect(showBanner, index) {
        if (showBanner) {
            delay(BANNER_TIMEOUT_MS)
            showBanner = false
        }
    }

    LaunchedEffect(showControls, interactionTick, errorMessage) {
        if (showControls && errorMessage == null) {
            delay(CONTROLS_TIMEOUT_MS)
            showControls = false
        }
    }

    LaunchedEffect(showControls, errorMessage) {
        delay(50) // let the buttons enter composition
        runCatching {
            when {
                errorMessage != null -> retryFocus.requestFocus()
                showControls -> reloadFocus.requestFocus()
                else -> rootFocus.requestFocus()
            }
        }
    }

    BackHandler {
        if (showControls && errorMessage == null) showControls = false else onBack(channel)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.ChannelUp -> { zap(+1); true }
                    Key.ChannelDown -> { zap(-1); true }
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        if (exoPlayer.playWhenReady) exoPlayer.pause() else reload(manual = true)
                        true
                    }
                    Key.Back, Key.Escape -> false
                    // Up/down change channel unless the controls are open (there they move the focus)
                    Key.DirectionUp -> if (!showControls) { zap(+1); true } else { interactionTick++; false }
                    Key.DirectionDown -> if (!showControls) { zap(-1); true } else { interactionTick++; false }
                    else -> if (!showControls && errorMessage == null) {
                        showControls = true
                        true
                    } else {
                        interactionTick++
                        false
                    }
                }
            }
            .focusRequester(rootFocus)
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        val frameModifier = when (settings.aspectMode) {
            "16:9" -> Modifier.aspectRatio(16f / 9f)
            "4:3" -> Modifier.aspectRatio(4f / 3f)
            else -> Modifier.fillMaxSize()
        }
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.tv_player_view, null) as PlayerView).apply {
                    useController = false
                    keepScreenOn = true
                    setShutterBackgroundColor(AndroidColor.BLACK)
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    resizeMode = when (settings.aspectMode) {
                        "fill" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        "16:9", "4:3" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                }
            },
            update = { view -> view.player = exoPlayer },
            modifier = frameModifier
        )

        skipNotice?.let { notice ->
            Text(
                notice,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(CorTvColors.Card.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            )
        }

        if (isBuffering && errorMessage == null && skipNotice == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(color = CorTvColors.Accent, modifier = Modifier.size(64.dp))
                if (autoReloads > 0) {
                    Text(
                        stringResource(R.string.live_reconnecting, autoReloads, maxReloads()),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        errorMessage?.let { message ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(CorTvColors.Card.copy(alpha = 0.95f), RoundedCornerShape(16.dp))
                    .padding(32.dp)
                    .widthIn(max = 620.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = CorTvColors.Accent, modifier = Modifier.size(48.dp))
                Text(channel.title, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = CorTvColors.SecondaryText)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = { reload(manual = true) }, modifier = Modifier.focusRequester(retryFocus), colors = accentButtonColors()) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.player_retry))
                    }
                    if (channels.size > 1) {
                        Button(onClick = { zap(+1) }, colors = neutralButtonColors()) {
                            Icon(Icons.Default.SkipNext, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.live_next_channel))
                        }
                    }
                    Button(onClick = { onBack(channel) }, colors = neutralButtonColors()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.player_back))
                    }
                }
                if (channels.size > 1) {
                    Text(stringResource(R.string.live_zap_hint), style = MaterialTheme.typography.bodySmall, color = CorTvColors.SecondaryText)
                }
            }
        }

        // Channel banner after opening or switching channel
        if (showBanner && !showControls && errorMessage == null) {
            ChannelInfo(
                channel = channel,
                position = index + 1,
                total = channels.size,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 48.dp, top = 32.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            )
        }

        if (showControls && errorMessage == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                    .padding(horizontal = 48.dp, vertical = 27.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopStart),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Button(onClick = { onBack(channel) }, colors = neutralButtonColors()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.player_back))
                    }
                    ChannelInfo(channel = channel, position = index + 1, total = channels.size, modifier = Modifier.weight(1f))
                }

                Column(
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomStart),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { reload(manual = true); interactionTick++ }, modifier = Modifier.focusRequester(reloadFocus), colors = accentButtonColors()) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.live_reload))
                        }
                        if (channels.size > 1) {
                            Button(onClick = { zap(-1) }, colors = neutralButtonColors()) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.live_previous))
                            }
                            Button(onClick = { zap(+1) }, colors = neutralButtonColors()) {
                                Icon(Icons.Default.SkipNext, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.live_next))
                            }
                        }
                        Button(
                            onClick = { onToggleFavorite(channel); favorite = !favorite; interactionTick++ },
                            colors = neutralButtonColors()
                        ) {
                            Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(if (favorite) R.string.action_remove_favorite else R.string.action_add_favorite))
                        }
                    }
                    if (channels.size > 1) {
                        Text(stringResource(R.string.live_zap_hint), style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                    }
                }
            }
        }
    }
}

/** Logo, number, name and details of the channel on air. */
@Composable
private fun ChannelInfo(channel: MediaItem, position: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (!channel.logoUrl.isNullOrEmpty()) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(width = 96.dp, height = 54.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(4.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (total > 1) {
                    Text(
                        stringResource(R.string.live_channel_position, position, total),
                        style = MaterialTheme.typography.titleMedium,
                        color = CorTvColors.SecondaryText
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    channel.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(16.dp))
                LiveBadge()
            }
            val details = listOfNotNull(
                channel.country?.let { "${countryFlag(it)} ${countryName(it)}".trim() },
                channel.genre,
                channel.quality,
                channel.network
            ).joinToString("  ·  ")
            if (details.isNotEmpty()) {
                Text(details, style = MaterialTheme.typography.bodyMedium, color = Color.LightGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
