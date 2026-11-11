package com.example.justfan.ui.components

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.justfan.R
import com.example.justfan.data.remote.AppConfig
import com.example.justfan.data.remote.AppVersionConfig
import com.example.justfan.data.remote.VersionGateManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private fun openExternalUpdateUrl(context: Context, updateUrl: String) {
    val safeUrl = updateUrl.trim().ifBlank { AppVersionConfig.DEFAULT_UPDATE_URL }
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

private fun enqueueInAppApkDownload(
    context: Context,
    updateUrl: String,
    versionLabel: String
): Long {
    val safeUrl = updateUrl.trim().ifBlank { AppVersionConfig.DEFAULT_UPDATE_URL }
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
    if (dm == null) {
        openExternalUpdateUrl(context, safeUrl)
        return -1L
    }
    return try {
        val fileName = "JustFan_v${versionLabel}_update.apk"
        val request = DownloadManager.Request(Uri.parse(safeUrl)).apply {
            setTitle("JustFan v$versionLabel Update")
            setDescription("Downloading latest JustFan APK update...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setMimeType("application/vnd.android.package-archive")
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        val id = dm.enqueue(request)
        Toast.makeText(
            context,
            "Downloading JustFan v$versionLabel APK...",
            Toast.LENGTH_SHORT
        ).show()
        id
    } catch (e: Exception) {
        openExternalUpdateUrl(context, safeUrl)
        -1L
    }
}

private fun launchDownloadedApkInstaller(
    context: Context,
    downloadId: Long,
    fallbackUrl: String
) {
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
    val apkUri = if (dm != null && downloadId > 0L) {
        runCatching { dm.getUriForDownloadedFile(downloadId) }.getOrNull()
    } else null

    if (apkUri != null) {
        val openedApk = runCatching {
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            true
        }.getOrDefault(false)
        if (openedApk) return
    }

    val openedDownloads = runCatching {
        val downloadsIntent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(downloadsIntent)
        Toast.makeText(
            context,
            "Tap the downloaded JustFan APK in Downloads to install.",
            Toast.LENGTH_LONG
        ).show()
        true
    }.getOrDefault(false)

    if (!openedDownloads) {
        openExternalUpdateUrl(context, fallbackUrl)
    }
}

private fun formatMegabytes(bytes: Long): String {
    if (bytes <= 0L) return "0.0 MB"
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f MB", mb)
}

/**
 * Shown on initial launch before the login screen or any other screen loads
 * while `app_config (id = 1)` is checked.
 */
@Composable
fun VersionGateCheckingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0C12),
                        Color(0xFF11141E),
                        Color(0xFF080A0F)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("version_gate_checking_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_icon_new),
                contentDescription = "JustFan Logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(22.dp))
            )
            Spacer(modifier = Modifier.height(20.dp))
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Checking database version...",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Full-screen non-dismissible Update Required page shown when `APP_VERSION < min_version`
 * or when the database requires a newer version.
 * - Blocks all access to database contents on previous versions.
 * - Cannot be dismissed; Android Back button exits the app instead.
 * - Provides in-app APK download with live progress tracking and automatic installer launch,
 *   plus an external browser fallback button.
 */
@Composable
fun UpdateRequiredScreen(config: AppConfig) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()

    val latestVersionNumber = maxOf(config.latestVersion, config.minVersion)
    val latestVersionDisplay = config.latestVersionName.ifBlank { "$latestVersionNumber.0" }

    var activeDownloadId by remember { mutableLongStateOf(-1L) }
    var isDownloading by remember { mutableStateOf(false) }
    var isDownloadComplete by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadedBytes by remember { mutableLongStateOf(0L) }
    var totalBytes by remember { mutableLongStateOf(0L) }
    var statusText by remember { mutableStateOf<String?>(null) }

    // Poll DownloadManager while an in-app APK download is active
    LaunchedEffect(activeDownloadId) {
        if (activeDownloadId <= 0L) return@LaunchedEffect
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            ?: return@LaunchedEffect

        while (isDownloading) {
            var cursor: Cursor? = null
            try {
                cursor = dm.query(DownloadManager.Query().setFilterById(activeDownloadId))
                if (cursor != null && cursor.moveToFirst()) {
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val downIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                    val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else DownloadManager.STATUS_RUNNING
                    val down = if (downIdx >= 0) cursor.getLong(downIdx) else 0L
                    val total = if (totalIdx >= 0) cursor.getLong(totalIdx) else -1L

                    downloadedBytes = down
                    totalBytes = total
                    if (total > 0L) {
                        downloadProgress = (down.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    }

                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            downloadProgress = 1f
                            isDownloading = false
                            isDownloadComplete = true
                            statusText = "Download complete! Launching installer..."
                            launchDownloadedApkInstaller(context, activeDownloadId, config.updateUrl)
                        }
                        DownloadManager.STATUS_FAILED -> {
                            isDownloading = false
                            statusText = "Direct download interrupted. Opening in browser..."
                            openExternalUpdateUrl(context, config.updateUrl)
                        }
                    }
                } else {
                    isDownloading = false
                }
            } catch (_: Exception) {
                isDownloading = false
            } finally {
                cursor?.close()
            }
            delay(250L)
        }
    }

    // Android back button must not close the Update Required screen; exit the app instead.
    BackHandler(enabled = true) {
        activity?.finishAffinity() ?: activity?.finish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF090B10),
                        Color(0xFF121521),
                        Color(0xFF07080D)
                    )
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {} // Consume all background taps
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .testTag("update_required_screen"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
            ),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .testTag("update_required_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier.size(88.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_icon_new),
                        contentDescription = "JustFan App Logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp))
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Update Required",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("update_required_title")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = config.updateMessage.ifBlank { AppVersionConfig.DEFAULT_UPDATE_MESSAGE },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.testTag("update_required_message")
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Installed vs. Latest Database version numbers
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("update_required_versions")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Installed Version",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "v${AppVersionConfig.APP_VERSION_NAME} (${AppVersionConfig.APP_VERSION})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(34.dp)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Database Version",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "v$latestVersionDisplay ($latestVersionNumber)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // In-app APK Download Progress Section
                AnimatedVisibility(
                    visible = isDownloading || isDownloadComplete || statusText != null,
                    enter = fadeIn()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isDownloading) {
                            if (totalBytes > 0L) {
                                LinearProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .testTag("update_download_progress_bar")
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Downloading APK: ${(downloadProgress * 100).toInt()}% (${formatMegabytes(downloadedBytes)} / ${formatMegabytes(totalBytes)})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .testTag("update_download_progress_bar")
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Downloading APK (${formatMegabytes(downloadedBytes)})...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else if (!statusText.isNullOrBlank()) {
                            Text(
                                text = statusText!!,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Primary In-App Download & Install Button
                Button(
                    onClick = {
                        if (isDownloadComplete && activeDownloadId > 0L) {
                            launchDownloadedApkInstaller(context, activeDownloadId, config.updateUrl)
                        } else if (!isDownloading) {
                            val id = enqueueInAppApkDownload(
                                context = context,
                                updateUrl = config.updateUrl,
                                versionLabel = latestVersionDisplay
                            )
                            if (id > 0L) {
                                activeDownloadId = id
                                isDownloadComplete = false
                                isDownloading = true
                                statusText = null
                            }
                        }
                    },
                    enabled = !isDownloading,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("download_update_button")
                ) {
                    Icon(
                        imageVector = if (isDownloadComplete) Icons.Default.CheckCircle else Icons.Default.Download,
                        contentDescription = "Download Update",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = when {
                            isDownloading -> "Downloading Update..."
                            isDownloadComplete -> "Install Downloaded Update (v$latestVersionDisplay)"
                            else -> "Download & Install Update (v$latestVersionDisplay)"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Secondary Browser & Re-Check Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { openExternalUpdateUrl(context, config.updateUrl) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("browser_download_update_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Open in Browser",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Browser Download", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                VersionGateManager.checkVersionGate()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("recheck_version_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Re-check Version",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Download, open the file, and tap Install. If asked, allow installs from this source.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp,
                    modifier = Modifier.testTag("update_required_help_text")
                )
            }
        }
    }
}

/**
 * Dismissible "New version available" banner shown when `min_version <= APP_VERSION < latest_version`.
 */
@Composable
fun UpdateAvailableBanner(
    config: AppConfig,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val versionDisplay = config.latestVersionName.ifBlank { "${config.latestVersion}.0" }

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 8.dp,
        shadowElevation = 6.dp,
        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .testTag("update_available_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = "New version available",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "New version available (v$versionDisplay)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = config.updateMessage.ifBlank { "Tap Update to download & install the latest APK." },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    enqueueInAppApkDownload(
                        context = context,
                        updateUrl = config.updateUrl,
                        versionLabel = versionDisplay
                    )
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.testTag("banner_update_button")
            ) {
                Text(
                    text = "Update",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("banner_dismiss_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss update banner"
                )
            }
        }
    }
}
