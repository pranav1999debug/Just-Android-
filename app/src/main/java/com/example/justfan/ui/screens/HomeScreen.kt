package com.example.justfan.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.justfan.data.model.PostEntity
import com.example.justfan.data.model.RequestEntity
import com.example.justfan.ui.components.ContentCard
import com.example.justfan.ui.components.RequestTicker
import com.example.justfan.ui.components.TopHeader
import com.example.justfan.ui.components.TrendingCarousel

@Composable
fun HomeScreen(
    posts: List<PostEntity>,
    trendingPosts: List<PostEntity>,
    requests: List<RequestEntity>,
    favoritePostIds: Set<String>,
    contentFilter: String,
    onPostClick: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onIncrementClicks: (String) -> Unit,
    onNotificationsClick: () -> Unit,
    onAdminClick: () -> Unit,
    onPricingClick: () -> Unit,
    onViewGallery: () -> Unit,
    isSyncing: Boolean = false,
    onSyncClick: () -> Unit = {},
    userProfile: com.example.justfan.data.model.UserProfile? = null,
    onAuthClick: () -> Unit = {},
    hasCustomWallpaper: Boolean = false,
    isScreenActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf("All") }
    var shuffleSeed by remember { mutableIntStateOf(0) }

    val tagsList = remember(posts) {
        val dynamicTags = posts
            .flatMap { it.tags }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(20)
        listOf("All", "Free Only") + dynamicTags
    }

    // Filter posts
    val filteredPosts = remember(posts, searchQuery, selectedTag, contentFilter, shuffleSeed) {
        var result = posts
        if (contentFilter == "sfw") {
            result = result.filter { !it.isNsfw }
        }
        if (selectedTag == "Free Only") {
            result = result.filter { it.isFree }
        } else if (selectedTag != "All") {
            result = result.filter { post ->
                post.tags.any { it.equals(selectedTag, ignoreCase = true) }
            }
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            result = result.filter { post ->
                post.title.lowercase().contains(q) ||
                        post.description.lowercase().contains(q) ||
                        post.author.lowercase().contains(q) ||
                        post.tags.any { it.lowercase().contains(q) }
            }
        }
        if (shuffleSeed > 0) {
            result = result.shuffled()
        }
        result
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopHeader(
            searchQuery = searchQuery,
            onSearchChange = { searchQuery = it },
            onNotificationsClick = onNotificationsClick,
            onAdminClick = onAdminClick,
            onPricingClick = onPricingClick,
            unreadNotificationsCount = 0,
            isSyncing = isSyncing,
            onSyncClick = onSyncClick,
            userProfile = userProfile,
            onAuthClick = onAuthClick,
            hasCustomWallpaper = hasCustomWallpaper
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
        ) {
            // Live Delivered Requests Ticker (Top of Everything, only if real delivered requests exist)
            if (requests.any { it.status == "delivered" }) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    RequestTicker(
                        requests = requests,
                        onViewGallery = onViewGallery
                    )
                }
            }

            // Trending Carousel
            item(span = { GridItemSpan(maxLineSpan) }) {
                TrendingCarousel(
                    trendingPosts = trendingPosts,
                    onPostClick = onPostClick
                )
            }

            // Discover Bar & Shuffle
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Discover",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Discover Feed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    OutlinedButton(
                        onClick = { shuffleSeed++ },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("shuffle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Shuffle", fontSize = 12.sp)
                    }
                }
            }

            // Tag Filter Chips
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(tagsList) { tag ->
                        val isSelected = selectedTag == tag
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedTag = tag },
                            label = { Text(text = if (tag == "All") "All" else "#$tag") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("tag_chip_$tag")
                        )
                    }
                }
            }

            // Posts Grid
            if (filteredPosts.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No content found matching your filter.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(filteredPosts, key = { it.id }) { post ->
                    val isFav = favoritePostIds.contains(post.id)
                    ContentCard(
                        post = post,
                        isFavorite = isFav,
                        onPostClick = { onPostClick(post.id) },
                        onToggleFavorite = { onToggleFavorite(post.id) },
                        // Lazy grids compose visible cards on demand; every visible video
                        // starts muted autoplay whenever the home feed is active.
                        autoPlay = isScreenActive,
                        onDownloadClick = {
                            onIncrementClicks(post.id)
                            val targetUrl = if (post.linkUrl.isNotBlank()) post.linkUrl else post.imageUrl
                            com.example.justfan.util.DownloadHelper.enqueueDownload(
                                context = context,
                                url = targetUrl,
                                title = post.title,
                                isPremium = !post.isFree
                            )
                        }
                    )
                }
            }
        }
    }
}
