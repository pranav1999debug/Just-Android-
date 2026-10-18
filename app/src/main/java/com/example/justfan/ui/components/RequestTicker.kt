package com.example.justfan.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.filled.Image
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
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
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

    // Real requests from database: delivered/replied first, followed by pending/in_progress
    val displayRequests = remember(requests) {
        val delivered = requests.filter { it.status.equals("delivered", ignoreCase = true) || it.status.equals("replied", ignoreCase = true) }
        val pending = requests.filter { !it.status.equals("delivered", ignoreCase = true) && !it.status.equals("replied", ignoreCase = true) }
        (delivered + pending).take(30)
    }

    if (displayRequests.isEmpty()) {
        return
    }

    Surface(
        modifier = modifier
            .testTag("request_ticker")
            .fillMaxWidth()
            .animateContentSize()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: ✦ COMMUNITY REQUESTS    Archive
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
                        text = "COMMUNITY REQUESTS",
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
                        onClick = { selectedRequest = req }
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
                Text(text = req.name ?: "Fan Request", fontWeight = FontWeight.Bold, maxLines = 1)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!req.imageUrl.isNullOrBlank()) {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(req.imageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = req.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            loading = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            },
                            error = {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(10.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Default.Image,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(26.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Reference Archive Media",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = req.imageUrl,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.clickable {
                                                try {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(req.imageUrl)))
                                                } catch (_: Exception) {}
                                            }
                                        )
                                    }
                                }
                            }
                        )
                    }

                    if (!req.downloadLink.isNullOrBlank()) {
                        Text(
                            text = "Reference link",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = req.downloadLink,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            modifier = Modifier.clickable {
                                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(req.downloadLink))) } catch (_: Exception) {}
                            }
                        )
                    }

                    Text(
                        text = if (req.message.isNotBlank()) req.message else "Requested by community member for archive release.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        color = when (req.status.lowercase()) {
                            "delivered", "replied" -> SuccessGreen.copy(alpha = 0.2f)
                            "in_progress" -> GoldAccent.copy(alpha = 0.2f)
                            else -> MaterialTheme.colorScheme.primaryContainer
                        },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "STATUS: ${req.status.replace("_", " ").uppercase()}",
                            color = when (req.status.lowercase()) {
                                "delivered", "replied" -> SuccessGreen
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
            val initials = remember(req.name) {
                req.name?.trim()?.take(1)?.uppercase()?.ifBlank { "R" } ?: "R"
            }
            val isDirectImage = remember(req.imageUrl) {
                val url = req.imageUrl.orEmpty()
                url.isNotBlank() && !url.contains("instagram.com") && !url.contains("onlyfans.com") && !url.contains("fansly.com")
            }
            if (isDirectImage) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(req.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = req.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                    loading = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initials,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 16.sp
                            )
                        }
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = req.name ?: "Fan Request",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = req.status.replace("_", " ").uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (req.status.lowercase()) {
                        "delivered", "replied" -> SuccessGreen
                        "in_progress" -> GoldAccent
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }
        }
    }
}
