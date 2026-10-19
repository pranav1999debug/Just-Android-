package com.example.justfan.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.justfan.data.model.PostEntity
import com.example.justfan.ui.components.CompactPostCard
import com.example.justfan.ui.components.DiscoverSection
import com.example.justfan.ui.components.TrendingCarousel

@Composable
fun HomeScreen(
    posts: List<PostEntity>,
    favoriteIds: Set<String>,
    isPaidOrLegendary: Boolean,
    onPostClick: (PostEntity) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDownloadClick: (PostEntity) -> Unit = {},
    externalSearchQuery: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember(externalSearchQuery) { mutableStateOf(externalSearchQuery) }
    var selectedFilter by remember { mutableStateOf("All") }

    val filteredPosts = remember(posts, searchQuery, selectedFilter) {
        posts.filter { post ->
            val matchesSearch = searchQuery.isBlank() ||
                    post.title.contains(searchQuery, ignoreCase = true) ||
                    post.description.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                "Free" -> post.isFree
                "Premium" -> !post.isFree
                "Video" -> post.linkUrl.contains(".mp4") || post.imageUrl.contains(".mp4")
                else -> true
            }

            matchesSearch && matchesFilter
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Filter chips (Search box is positioned only in TopHeader)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "Free", "Premium", "Video").forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter, fontSize = 12.sp) }
                )
            }
        }

        // Post Grid
        if (filteredPosts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No galleries found matching your filter.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (searchQuery.isBlank() && selectedFilter == "All") {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Column {
                            // 1. Trending Carousel: Top 10 calculated by historical click table values
                            TrendingCarousel(
                                trendingPosts = posts.sortedByDescending { it.clicksCount }.take(10),
                                onPostClick = { postId ->
                                    posts.find { it.id == postId }?.let(onPostClick)
                                }
                            )

                            // 2. Discover Section: Below trending, auto-scrolling random old post discovery container
                            val historicalPosts = remember(posts) {
                                posts.shuffled(kotlin.random.Random(1337)).take(12)
                            }
                            DiscoverSection(
                                historicalPosts = historicalPosts,
                                onPostClick = { postId ->
                                    posts.find { it.id == postId }?.let(onPostClick)
                                }
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                items(filteredPosts, key = { it.id }) { post ->
                    CompactPostCard(
                        post = post,
                        isFavorite = post.id in favoriteIds,
                        isPaidOrLegendary = isPaidOrLegendary,
                        onCardClick = { onPostClick(post) },
                        onToggleFavorite = { onToggleFavorite(post.id) },
                        onDirectDownloadClick = {
                            // Intercept the download action to asynchronously increment click count
                            onDownloadClick(post)
                            val url = post.directLinkUrl?.ifBlank { null } ?: post.linkUrl
                            if (url.isNotBlank()) {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        }
                    )
                }
            }
        }
    }
}
