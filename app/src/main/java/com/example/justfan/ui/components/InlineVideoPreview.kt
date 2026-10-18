package com.example.justfan.ui.components

import android.net.Uri
import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.justfan.R

@OptIn(UnstableApi::class)
@Composable
fun InlineVideoPreview(
    videoUrl: String,
    thumbnailUrl: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isVideoReady by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }

    val exoPlayer = remember(videoUrl) {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)

        // Enable fallback decoders to prevent hardware interface query errors on virtual emulators
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            .setEnableDecoderFallback(true)

        val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                val clean = videoUrl.trim().lowercase().split("?").first()
                val mime = when {
                    clean.endsWith(".mp4") -> MimeTypes.VIDEO_MP4
                    clean.endsWith(".webm") -> MimeTypes.VIDEO_WEBM
                    clean.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
                    clean.endsWith(".mkv") -> MimeTypes.VIDEO_MATROSKA
                    else -> null
                }
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(videoUrl.trim()))
                    .apply { if (mime != null) setMimeType(mime) }
                    .build()

                setMediaItem(mediaItem)
                volume = 0f // Muted feed preview
                repeatMode = Player.REPEAT_MODE_ALL
                playWhenReady = true
                prepare()
            }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    isVideoReady = true
                    hasError = false
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // Fall back gracefully to static image without crashing
                hasError = true
                isVideoReady = false
                try {
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                } catch (_: Exception) {}
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            try {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.release()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (!isVideoReady || hasError) {
            AsyncImage(
                model = thumbnailUrl ?: videoUrl,
                contentDescription = "Video Poster",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (!hasError) {
            AndroidView(
                factory = { ctx ->
                    val view = LayoutInflater.from(ctx).inflate(R.layout.layout_player_view, null) as PlayerView
                    view.player = exoPlayer
                    view.useController = false
                    view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    view
                },
                update = { playerView ->
                    playerView.player = exoPlayer
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
