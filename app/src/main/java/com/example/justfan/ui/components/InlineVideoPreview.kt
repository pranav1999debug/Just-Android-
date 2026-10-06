package com.example.justfan.ui.components

import android.net.Uri
import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.justfan.BuildConfig
import com.example.justfan.R

@OptIn(UnstableApi::class)
@Composable
fun InlineVideoPreview(
    videoUrl: String,
    thumbnailUrl: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isVideoReady by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }

    val exoPlayer = remember(videoUrl) {
        val reqHeaders = mutableMapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
            "Referer" to if (videoUrl.contains("catbox.moe")) "https://catbox.moe/" else "https://justfan.app/",
            "Accept" to "*/*"
        )
        if (videoUrl.contains("supabase.co")) {
            reqHeaders["apikey"] = BuildConfig.SUPABASE_KEY
            reqHeaders["Authorization"] = "Bearer ${BuildConfig.SUPABASE_KEY}"
        }

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
            .setDefaultRequestProperties(reqHeaders)

        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context)
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            .setEnableDecoderFallback(true)

        val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(videoUrl.trim()))
                    .apply {
                        val clean = videoUrl.trim().lowercase().split("?").first()
                        if (clean.endsWith(".mp4")) {
                            setMimeType(MimeTypes.VIDEO_MP4)
                        } else if (clean.endsWith(".webm")) {
                            setMimeType(MimeTypes.VIDEO_WEBM)
                        } else if (clean.endsWith(".m3u8")) {
                            setMimeType(MimeTypes.APPLICATION_M3U8)
                        } else if (clean.endsWith(".mkv")) {
                            setMimeType(MimeTypes.VIDEO_MATROSKA)
                        }
                    }
                    .build()

                setMediaItem(mediaItem)
                volume = 0f // Muted for inline feed preview
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
                // Safely handle codec or network failure by falling back to static poster thumbnail
                android.util.Log.w("InlineVideoPreview", "Feed video player error (safe fallback): ${error.message}")
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
        // Thumbnail poster while video is preparing or if error
        if (!isVideoReady || hasError) {
            val fallbackModel = if (!thumbnailUrl.isNullOrBlank() && !isVideoMediaUrl(thumbnailUrl)) {
                thumbnailUrl
            } else {
                videoUrl
            }
            AsyncImage(
                model = fallbackModel,
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
                    view.useController = false // No controls on card preview
                    view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    view
                },
                update = { playerView ->
                    playerView.player = exoPlayer
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Live Muted Audio indicator
        Surface(
            color = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeMute,
                    contentDescription = "Muted",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "AUTO-PLAY",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
