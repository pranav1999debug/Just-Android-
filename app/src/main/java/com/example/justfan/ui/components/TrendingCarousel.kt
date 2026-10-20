package com.example.justfan.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.justfan.data.model.PostEntity
import com.example.justfan.data.model.RequestEntity
import com.example.justfan.ui.theme.GoldAccent
import kotlinx.coroutines.delay

@Composable
fun TrendingCarousel(
    trendingPosts: List<PostEntity>,
    onPostClick: (String) -> Unit,
    onViewAllClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (trendingPosts.isEmpty()) return

    val listState = rememberLazyListState()

    // Automatically sliding loop carousel format
    LaunchedEffect(trendingPosts.size) {
        if (trendingPosts.size > 1) {
            while (true) {
                delay(4000)
                val current = listState.firstVisibleItemIndex
                val next = (current + 1) % trendingPosts.size
                listState.animateScrollToItem(next, scrollOffset = 0)
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = "Trending Now",
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Trending Now",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onViewAllClick() }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .testTag("trending_view_all_btn")
            ) {
                Text(
                    text = "View All",
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "View All",
                    tint = Color.LightGray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(trendingPosts.take(10), key = { _, post -> post.id }) { index, post ->
                TrendingCardItem(
                    rank = index + 1,
                    post = post,
                    onClick = { onPostClick(post.id) }
                )
            }
        }
    }
}

@Composable
private fun TrendingCardItem(
    rank: Int,
    post: PostEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .testTag("trending_item_$rank")
            .width(190.dp)
            .height(215.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, Color(0xFF10B981)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121820)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val isVideo = isVideoMediaUrl(post.imageUrl) ||
                    post.contentImages.any { isVideoMediaUrl(it) } ||
                    isVideoMediaUrl(post.directLinkUrl) ||
                    isVideoMediaUrl(post.linkUrl)

            AsyncImage(
                model = post.imageUrl,
                contentDescription = post.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            if (isVideo) {
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(36.dp)
                        .align(Alignment.Center)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Video",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Top-Left Emerald Rank Badge (#1, #2, #3...)
            Surface(
                color = Color(0xFF10B981),
                shape = CircleShape,
                modifier = Modifier
                    .padding(10.dp)
                    .size(28.dp)
                    .align(Alignment.TopStart)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "#$rank",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Top-Right Click Count Pill Badge (🔥 901)
            Surface(
                color = Color.Black.copy(alpha = 0.72f),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .padding(10.dp)
                    .align(Alignment.TopEnd)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Whatshot,
                        contentDescription = null,
                        tint = Color(0xFFF97316),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${post.clicksCount}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Bottom gradient with Title
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))
                        )
                    )
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            ) {
                Text(
                    text = post.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    lineHeight = 16.sp,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Small Homepage Section for Free Users displaying Successful Request Uploads
 * with the user-submitted small request image and clickable reference link.
 */
@Composable
fun SuccessfulRequestUploadsSection(
    requests: List<RequestEntity>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val fulfilledRequests = remember(requests) {
        requests.filter { req ->
            val st = req.status.lowercase().trim()
            val isCompletedStatus = st in setOf("replied", "fulfilled", "completed", "approved", "delivered")
            val hasMediaOrRef = !req.imageUrl.isNullOrBlank() || !req.downloadLink.isNullOrBlank() || req.message.contains("http", ignoreCase = true)
            isCompletedStatus && hasMediaOrRef
        }
    }

    if (fulfilledRequests.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag("successful_uploads_section")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Successful Uploads",
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Successful Request Uploads",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Surface(
                color = Color(0xFF10B981).copy(alpha = 0.16f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "COMMUNITY FULFILLED",
                    color = Color(0xFF10B981),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(fulfilledRequests, key = { it.id }) { req ->
                val refLink = remember(req) {
                    if (!req.downloadLink.isNullOrBlank()) {
                        req.downloadLink.trim()
                    } else {
                        val urlRegex = Regex("(https?://[^\\s]+)")
                        urlRegex.find(req.message)?.value ?: ""
                    }
                }
                val cleanDetails = remember(req.message) {
                    req.message
                        .lines()
                        .map { it.trim() }
                        .firstOrNull { !it.startsWith("Social:", ignoreCase = true) && it.isNotBlank() }
                        ?.removePrefix("Details:")
                        ?.trim()
                        ?: req.name
                        ?: "Fulfilled Request"
                }

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF151C24)),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.45f)),
                    modifier = Modifier
                        .width(230.dp)
                        .height(78.dp)
                        .clickable {
                            if (refLink.isNotBlank()) {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(refLink)))
                                } catch (_: Exception) {}
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Small request image thumbnail provided by user
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E293B))
                        ) {
                            if (!req.imageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = req.imageUrl,
                                    contentDescription = cleanDetails,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Link,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    color = Color(0xFF10B981),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "UPLOADED",
                                        color = Color.Black,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                                Text(
                                    text = req.name ?: "Fan Request",
                                    color = Color.LightGray,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Text(
                                text = cleanDetails,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (refLink.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            try {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(refLink)))
                                            } catch (_: Exception) {}
                                        }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = "Reference Link",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = refLink.removePrefix("https://").removePrefix("http://").removePrefix("www."),
                                        color = Color(0xFF10B981),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiscoverSection(
    historicalPosts: List<PostEntity>,
    onPostClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (historicalPosts.isEmpty()) return

    val listState = rememberLazyListState()

    // Auto-scrolling random old post discovery container context window
    LaunchedEffect(historicalPosts.size) {
        if (historicalPosts.size > 1) {
            while (true) {
                delay(4500)
                val current = listState.firstVisibleItemIndex
                val next = (current + 1) % historicalPosts.size
                listState.animateScrollToItem(next, scrollOffset = 0)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Explore,
                contentDescription = "Discover",
                tint = GoldAccent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Deep Vault Discover",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = GoldAccent.copy(alpha = 0.2f),
                shape = CircleShape
            ) {
                Text(
                    text = "RANDOM ARCHIVES",
                    color = GoldAccent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(historicalPosts) { index, post ->
                DiscoverCardItem(
                    post = post,
                    onClick = { onPostClick(post.id) },
                    index = index
                )
            }
        }
    }
}

@Composable
private fun DiscoverCardItem(
    post: PostEntity,
    onClick: () -> Unit,
    index: Int
) {
    Card(
        modifier = Modifier
            .testTag("discover_item_$index")
            .width(160.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = post.imageUrl,
                contentDescription = post.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            ) {
                Text(
                    text = if (post.isFree) "FREE" else "VIP",
                    color = if (post.isFree) Color(0xFF10B981) else GoldAccent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                        )
                    )
                    .padding(8.dp)
            ) {
                Text(
                    text = post.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
