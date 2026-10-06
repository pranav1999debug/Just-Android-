package com.example.justfan.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.justfan.data.model.RequestEntity
import com.example.justfan.ui.theme.GoldAccent
import com.example.justfan.ui.theme.SuccessGreen

@Composable
fun RequestTicker(
    requests: List<RequestEntity>,
    onViewGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedRequest by remember { mutableStateOf<RequestEntity?>(null) }

    // Real delivered requests from database only (no fake sample data)
    val displayRequests = remember(requests) {
        requests.filter { it.status == "delivered" }.ifEmpty { requests }
    }

    if (displayRequests.isEmpty()) {
        return
    }

    Surface(
        modifier = modifier
            .testTag("request_ticker")
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: ✦ LIVE DELIVERED    Archive
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LIVE DELIVERED",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "Archive",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable(onClick = onViewGallery)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Capsule Chips Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(displayRequests, key = { it.id }) { req ->
                    RequestChipItem(
                        req = req,
                        onClick = {
                            if (!req.downloadLink.isNullOrBlank()) {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(req.downloadLink)))
                                } catch (_: Exception) {
                                    selectedRequest = req
                                }
                            } else {
                                selectedRequest = req
                            }
                        }
                    )
                }
            }
        }
    }

    // Detail dialog when tapping any live request chip
    if (selectedRequest != null) {
        val req = selectedRequest!!
        AlertDialog(
            onDismissRequest = { selectedRequest = null },
            title = {
                Text(text = req.name, fontWeight = FontWeight.Bold, maxLines = 1)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!req.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = req.imageUrl,
                            contentDescription = req.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    }

                    Text(
                        text = if (req.message.isNotBlank()) req.message else "Requested by community member for archive release.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        color = when (req.status.lowercase()) {
                            "delivered" -> SuccessGreen.copy(alpha = 0.2f)
                            "in_progress" -> GoldAccent.copy(alpha = 0.2f)
                            else -> MaterialTheme.colorScheme.primaryContainer
                        },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "STATUS: ${req.status.replace("_", " ").uppercase()}",
                            color = when (req.status.lowercase()) {
                                "delivered" -> SuccessGreen
                                "in_progress" -> GoldAccent
                                else -> MaterialTheme.colorScheme.primary
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                if (!req.downloadLink.isNullOrBlank()) {
                    Button(
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(req.downloadLink)))
                            } catch (_: Exception) {}
                            selectedRequest = null
                        }
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Delivered Gallery")
                    }
                } else {
                    Button(onClick = { selectedRequest = null }) {
                        Text("Close")
                    }
                }
            },
            dismissButton = {
                if (!req.downloadLink.isNullOrBlank()) {
                    TextButton(onClick = { selectedRequest = null }) {
                        Text("Dismiss")
                    }
                }
            }
        )
    }
}

@Composable
private fun RequestChipItem(
    req: RequestEntity,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .height(48.dp)
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp)
        ) {
            // Circular Avatar
            if (!req.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = req.imageUrl,
                    contentDescription = req.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = req.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
