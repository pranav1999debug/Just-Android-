package com.example.justfan.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast

object DownloadHelper {

    fun enqueueDownload(
        context: Context,
        url: String,
        title: String,
        isPremium: Boolean = false
    ): Long {
        if (url.isBlank()) {
            Toast.makeText(context, "Download link unavailable", Toast.LENGTH_SHORT).show()
            return -1L
        }

        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (downloadManager == null) {
                // Fallback to browser intent if service is unavailable
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return -1L
            }

            val uri = Uri.parse(url)
            val extension = getExtensionFromUrl(url)
            val sanitizedTitle = title
                .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                .take(30)
                .trimEnd('_')
            val fileName = "JUSTFAN_${sanitizedTitle}_${System.currentTimeMillis()}.$extension"

            val request = DownloadManager.Request(uri).apply {
                setTitle(if (isPremium) "👑 [4K HD] $title" else "JUSTFAN - $title")
                setDescription("Downloading creator gallery to Downloads folder")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)

                val mime = when (extension.lowercase()) {
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    "mp4" -> "video/mp4"
                    "zip" -> "application/zip"
                    else -> "image/jpeg"
                }
                setMimeType(mime)
            }

            val downloadId = downloadManager.enqueue(request)
            Toast.makeText(
                context,
                "Download started via DownloadManager! Check notifications.",
                Toast.LENGTH_LONG
            ).show()

            return downloadId
        } catch (e: Exception) {
            e.printStackTrace()
            // Graceful fallback to opening direct link
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
            return -1L
        }
    }

    fun openDownloadsFolder(context: Context) {
        try {
            val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Downloads folder is available in your Files app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getExtensionFromUrl(url: String): String {
        val clean = url.substringBefore("?").substringBefore("#")
        val ext = clean.substringAfterLast(".", "jpg")
        return if (ext.length in 2..5 && ext.all { it.isLetterOrDigit() }) ext else "jpg"
    }
}

class DownloadCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id != -1L) {
                Toast.makeText(context, "JUSTFAN download completed! Saved to Downloads.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
