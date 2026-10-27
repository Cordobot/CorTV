@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package cordobot.pacificrim.cortv.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

@Composable
fun VideoPlayer(
    videoUrl: String,
    title: String,
    description: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    userAgent: String? = null,
    referrer: String? = null
) {
    val context = LocalContext.current
    var showSubtitlesDialog by remember { mutableStateOf(false) }
    var currentSubtitle by remember { mutableStateOf("Desactivado") }

    // Initialize ExoPlayer
    val exoPlayer = remember {
        // Some IPTV streams need a specific User-Agent/Referer and redirect between http and https
        val httpFactory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        userAgent?.let { httpFactory.setUserAgent(it) }
        referrer?.let { httpFactory.setDefaultRequestProperties(mapOf("Referer" to it)) }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpFactory)))
            .build().apply {
                playWhenReady = true
                val mediaItem = MediaItem.Builder()
                    .setUri(videoUrl)
                    // Add English and Spanish subtitles if they exist (conceptually or via side-loading/track parameters)
                    .build()
                setMediaItem(mediaItem)
                prepare()
            }
    }

    DisposableEffect(videoUrl) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // Player View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Custom Overlay controls on top of player
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = Color.White
                    )
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )

                Button(
                    onClick = { showSubtitlesDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f))
                ) {
                    Text(text = "Sub: $currentSubtitle", color = Color.White)
                }
            }

            // Bottom Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Video Description / Details overlay
                Card(
                    modifier = Modifier.weight(1f).padding(end = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White
                        )
                        if (!description.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray,
                                maxLines = 2
                            )
                        }
                    }
                }

                // Skip Intro Button
                Button(
                    onClick = {
                        val currentPos = exoPlayer.currentPosition
                        exoPlayer.seekTo(currentPos + 85000) // Skip +85 seconds
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = "Saltar Intro (+85s)")
                }
            }
        }
    }

    // Subtitles Dialog (Spanish, English, None)
    if (showSubtitlesDialog) {
        AlertDialog(
            onDismissRequest = { showSubtitlesDialog = false },
            title = { Text(text = "Idiomas de Subtítulos") },
            text = {
                Column {
                    val options = listOf("Desactivado", "Español", "Inglés")
                    options.forEach { option ->
                        TextButton(
                            onClick = {
                                currentSubtitle = option
                                showSubtitlesDialog = false
                                // Update ExoPlayer track selection parameters for subtitle languages
                                val preferredLanguage = when (option) {
                                    "Español" -> "spa"
                                    "Inglés" -> "eng"
                                    else -> null
                                }
                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                                    .setPreferredTextLanguage(preferredLanguage)
                                    .build()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSubtitlesDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
}
