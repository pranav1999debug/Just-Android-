package com.example.justfan.ui.components

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.example.justfan.BuildConfig
import com.example.justfan.R

fun isVideoMediaUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val clean = url.trim().lowercase().split("?").first()
    // Explicitly reject static image formats
    if (clean.endsWith(".jpg") || clean.endsWith(".jpeg") || clean.endsWith(".png") ||
        clean.endsWith(".webp") || clean.endsWith(".gif") || clean.endsWith(".svg") ||
        clean.endsWith(".avif") || clean.endsWith(".bmp")
    ) {
        return false
    }
    // Reject HTML/web pages
    if (clean.endsWith(".html") || clean.endsWith(".htm") || clean.endsWith(".php")) {
        return false
    }
    return clean.endsWith(".mp4") ||
            clean.endsWith(".webm") ||
            clean.endsWith(".mkv") ||
            clean.endsWith(".mov") ||
            clean.endsWith(".m4v") ||
            clean.endsWith(".m3u8") ||
            clean.endsWith(".mpd") ||
            clean.endsWith(".avi") ||
            clean.endsWith(".ts")
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerView(
    videoUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isBuffering by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    val exoPlayer = remember(videoUrl, retryTrigger) {
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
            .setConnectTimeoutMs(25000)
            .setReadTimeoutMs(35000)
            .setDefaultRequestProperties(reqHeaders)

        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context)
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
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
                repeatMode = Player.REPEAT_MODE_ALL
                playWhenReady = true
                prepare()
            }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    playbackError = null
                    isPlaying = exoPlayer.isPlaying
                }
                if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                isPlaying = false
                val cause = error.cause
                playbackError = if (cause is androidx.media3.exoplayer.source.UnrecognizedInputFormatException) {
                    "Video stream format requires external player or direct download."
                } else {
                    error.message ?: "Failed to stream video"
                }
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .clip(RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                val view = LayoutInflater.from(ctx).inflate(R.layout.layout_player_view, null) as PlayerView
                view.player = exoPlayer
                view.useController = true
                view.controllerShowTimeoutMs = 3000
                view.controllerAutoShow = true
                view
            },
            update = { playerView ->
                playerView.player = exoPlayer
            },
            modifier = Modifier.fillMaxSize()
        )

        // Buffering indicator
        if (isBuffering && playbackError == null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Top bar actions: External open & Video badge
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color.Red.copy(alpha = 0.85f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "HD VIDEO",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }

            IconButton(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color.White
                ),
                modifier = Modifier.size(32.dp).testTag("btn_open_external_video")
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = "Open video in browser or system player",
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Error overlay with retry and external launch
        if (playbackError != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f))
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(42.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Playback Error",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = playbackError ?: "The media host responded with an error.",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { retryTrigger++ },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Retry", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Direct", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
