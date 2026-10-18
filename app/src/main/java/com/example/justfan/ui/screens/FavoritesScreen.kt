package com.example.justfan.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.justfan.data.model.PostEntity
import com.example.justfan.ui.components.CompactPostCard

@Composable
fun FavoritesScreen(
    allPosts: List<PostEntity>,
    favoriteIds: Set<String>,
    isPaidOrLegendary: Boolean,
    onPostClick: (PostEntity) -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val favoritePosts = remember(allPosts, favoriteIds) {
        allPosts.filter { it.id in favoriteIds }
    }

    if (favoritePosts.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No saved favorites yet. Tap the heart icon on any post to bookmark it!",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 300.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = modifier.fillMaxSize()
        ) {
            items(favoritePosts, key = { it.id }) { post ->
                CompactPostCard(
                    post = post,
                    isFavorite = true,
                    isPaidOrLegendary = isPaidOrLegendary,
                    onCardClick = { onPostClick(post) },
                    onToggleFavorite = { onToggleFavorite(post.id) },
                    onDirectDownloadClick = {
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
