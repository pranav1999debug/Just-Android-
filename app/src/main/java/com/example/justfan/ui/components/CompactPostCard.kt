package com.example.justfan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.justfan.data.model.PostEntity

@Composable
fun CompactPostCard(
    post: PostEntity,
    onPostClick: () -> Unit,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true
) {
    Card(
        modifier = modifier
            .testTag("compact_post_card_${post.id}")
            .fillMaxWidth()
            .height(230.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onPostClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val videoUrl: String? = when {
                isVideoMediaUrl(post.imageUrl) -> post.imageUrl
                post.contentImages.any { isVideoMediaUrl(it) } -> post.contentImages.first { isVideoMediaUrl(it) }
                isVideoMediaUrl(post.directLinkUrl) -> post.directLinkUrl
                isVideoMediaUrl(post.linkUrl) -> post.linkUrl
                post.title.contains("video", ignoreCase = true) && post.contentImages.isNotEmpty() -> post.contentImages.first()
                else -> null
            }
            val isVideo = videoUrl != null

            if (isVideo && videoUrl != null && autoPlay) {
                InlineVideoPreview(
                    videoUrl = videoUrl,
                    thumbnailUrl = post.imageUrl,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AsyncImage(
                    model = post.imageUrl,
                    contentDescription = post.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Top Badges Row: Left = FREE / PREMIUM badge, Right = Views chip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Top-Left Badge: FREE (green #22C55E) or PREMIUM (gold #FFD700)
                Surface(
                    color = if (post.isFree) Color(0xFF22C55E) else Color(0xFFFFD700),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (post.isFree) "FREE" else "PREMIUM",
                        color = if (post.isFree) Color.White else Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }

                // Top-Right semi-transparent dark chip with eye icon and views count
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Views",
                            tint = Color.LightGray,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (post.clicksCount > 0) "${post.clicksCount} views" else "0 views",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Bottom Overlay: full-width strip with Color.Black at 70% alpha, padding 6dp, post title white 12sp ellipsis
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.70f))
                    .padding(6.dp)
            ) {
                Text(
                    text = post.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
