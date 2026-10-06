package com.example.justfan.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.example.justfan.data.model.CommentEntity
import com.example.justfan.data.model.PostEntity
import com.example.justfan.ui.components.VideoPlayerView
import com.example.justfan.ui.components.isVideoMediaUrl
import com.example.justfan.ui.theme.DangerRed
import com.example.justfan.ui.theme.GoldAccent
import com.example.justfan.ui.theme.ThemeEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    post: PostEntity?,
    allPosts: List<PostEntity>,
    comments: List<CommentEntity>,
    isFavorite: Boolean,
    canAccessDirectLink: Boolean = false,
    onNavigateToPricing: () -> Unit = {},
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddComment: (String) -> Unit,
    onSelectPost: (String) -> Unit,
    onShare: () -> Unit = {},
    onDownloadClick: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    if (post == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var selectedImageIndex by remember { mutableIntStateOf(0) }
    var isLightboxOpen by remember { mutableStateOf(false) }
    var newCommentText by remember { mutableStateOf("") }
    var isDescExpanded by remember { mutableStateOf(false) }

    val images = remember(post) {
        if (post.contentImages.isNotEmpty()) post.contentImages else listOf(post.imageUrl)
    }

    val similarPosts = remember(post, allPosts) {
        allPosts.filter { other ->
            other.id != post.id && other.tags.any { tag -> post.tags.contains(tag) }
        }.take(6)
    }

    val discoverPosts = remember(post, allPosts) {
        allPosts.filter { it.id != post.id }
            .shuffled(kotlin.random.Random(post.id.hashCode()))
            .take(10)
    }

    val trendingPosts = remember(post, allPosts) {
        allPosts.filter { it.id != post.id }
            .sortedByDescending { it.clicksCount }
            .take(10)
    }

    val prevPost = remember(post, allPosts) {
        val idx = allPosts.indexOfFirst { it.id == post.id }
        if (idx > 0) allPosts[idx - 1] else null
    }
    val nextPost = remember(post, allPosts) {
        val idx = allPosts.indexOfFirst { it.id == post.id }
        if (idx >= 0 && idx < allPosts.size - 1) allPosts[idx + 1] else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = post.title, maxLines = 1, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.testTag("detail_fav_button")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) DangerRed else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = {
                            onShare()
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, post.title)
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Check out ${post.title} on JUSTFAN: ${post.linkUrl.ifBlank { post.imageUrl }}"
                                )
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Gallery"))
                        },
                        modifier = Modifier.testTag("detail_share_button")
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Share")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Main Featured Media (Video or Image)
            item {
                val currentMedia = images.getOrNull(selectedImageIndex) ?: post.imageUrl
                val resolvedVideoUrl: String? = when {
                    isVideoMediaUrl(currentMedia) -> currentMedia
                    isVideoMediaUrl(post.imageUrl) -> post.imageUrl
                    post.contentImages.any { isVideoMediaUrl(it) } -> post.contentImages.first { isVideoMediaUrl(it) }
                    isVideoMediaUrl(post.directLinkUrl) -> post.directLinkUrl
                    isVideoMediaUrl(post.linkUrl) -> post.linkUrl
                    else -> null
                }

                if (resolvedVideoUrl != null) {
                    VideoPlayerView(
                        videoUrl = resolvedVideoUrl,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                    )
                } else {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { isLightboxOpen = true }
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = currentMedia,
                                contentDescription = post.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Tag indicating video download available
                            val hasVideoReference = post.title.contains("video", ignoreCase = true) ||
                                    post.tags.any { it.contains("video", ignoreCase = true) }
                            if (hasVideoReference) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.75f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Videocam,
                                            contentDescription = null,
                                            tint = Color.Red,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Video Download Available Below",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Surface(
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ZoomIn,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${selectedImageIndex + 1}/${images.size}",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Thumbnail strip if multiple images
            if (images.size > 1) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(images.indices.toList()) { i ->
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedImageIndex = i }
                            ) {
                                AsyncImage(
                                    model = images[i],
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (selectedImageIndex == i) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Title, Author, Tags
            item {
                Column {
                    Text(
                        text = post.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Published by ${post.author}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        post.tags.forEach { tag ->
                            val cleanTag = tag.trim().removePrefix("#")
                            if (cleanTag.isNotBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "#$cleanTag",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Stats row
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("${post.clicksCount} Views", fontWeight = FontWeight.SemiBold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Favorite, contentDescription = null, tint = DangerRed)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("${post.likesCount} Likes", fontWeight = FontWeight.SemiBold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("${images.size} Photos", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Description
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "About This Gallery",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = post.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (isDescExpanded) Int.MAX_VALUE else 3
                        )
                        if (post.description.length > 120) {
                            Text(
                                text = if (isDescExpanded) "Show Less" else "Read More...",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { isDescExpanded = !isDescExpanded }
                                    .padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            // 3 Download Action Buttons requested by user
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Download Links",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // 1st Button: Download (Ads) => link_url from supabase
                        val adsUrl = post.linkUrl.ifBlank { post.imageUrl }
                        DownloadActionButton(
                            title = "Download (Ads)",
                            subtitle = "Free Access • Ad Supported Link",
                            icon = Icons.Default.Download,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            badge = "FREE",
                            testTag = "detail_download_ads_button",
                            onClick = {
                                onDownloadClick(post.id, "ads")
                                openDownloadUrl(context, adsUrl, "Download (Ads)")
                            },
                            onCopy = {
                                onDownloadClick(post.id, "ads_copy")
                                copyToClipboard(context, adsUrl, "Ad Download link copied to clipboard")
                            }
                        )

                        // 2nd Button: Premium => premium_link_url from supabase
                        val premiumUrl = if (!post.premiumLinkUrl.isNullOrBlank()) post.premiumLinkUrl else post.linkUrl
                        DownloadActionButton(
                            title = "Premium",
                            subtitle = if (!post.premiumLinkUrl.isNullOrBlank()) "High-Speed Direct • 4K Uncompressed" else "Premium Access Link",
                            icon = Icons.Default.Diamond,
                            containerColor = GoldAccent,
                            contentColor = Color.Black,
                            badge = "VIP",
                            testTag = "detail_download_premium_button",
                            onClick = {
                                onDownloadClick(post.id, "premium")
                                openDownloadUrl(context, premiumUrl, "Premium")
                            },
                            onCopy = {
                                onDownloadClick(post.id, "premium_copy")
                                copyToClipboard(context, premiumUrl, "Premium link copied to clipboard")
                            }
                        )

                        // 3rd Button: Direct Download => Only Pro and Admin can access
                        val directUrl = if (!post.directLinkUrl.isNullOrBlank()) post.directLinkUrl else post.linkUrl
                        if (canAccessDirectLink) {
                            DownloadActionButton(
                                title = "Direct Download",
                                subtitle = if (!post.directLinkUrl.isNullOrBlank()) "Instant Mirror • Zero Waiting" else "Direct File Link",
                                icon = Icons.Default.Bolt,
                                containerColor = ThemeEmerald,
                                contentColor = Color.White,
                                badge = "PRO / ADMIN",
                                testTag = "detail_download_direct_button",
                                onClick = {
                                    onDownloadClick(post.id, "direct")
                                    openDownloadUrl(context, directUrl, "Direct Download")
                                },
                                onCopy = {
                                    onDownloadClick(post.id, "direct_copy")
                                    copyToClipboard(context, directUrl, "Direct link copied to clipboard")
                                }
                            )
                        } else {
                            DownloadActionButton(
                                title = "Direct Download",
                                subtitle = "🔒 High-Speed Mirror • Reserved for Pro & Admin • Tap to Unlock",
                                icon = Icons.Default.Lock,
                                containerColor = Color(0xFF7C3AED),
                                contentColor = Color.White,
                                badge = "PRO ONLY",
                                testTag = "detail_download_direct_button",
                                onClick = {
                                    Toast.makeText(
                                        context,
                                        "Direct link is exclusive to Pro members and Admins. Opening payment page...",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    onNavigateToPricing()
                                },
                                onCopy = {
                                    Toast.makeText(
                                        context,
                                        "Upgrade to Pro or contact Admin to unlock direct links.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    onNavigateToPricing()
                                }
                            )
                        }
                    }
                }
            }

            // Prev & Next navigation
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (prevPost != null) {
                        OutlinedButton(
                            onClick = { onSelectPost(prevPost.id) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Previous", maxLines = 1)
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    if (nextPost != null) {
                        OutlinedButton(
                            onClick = { onSelectPost(nextPost.id) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Next", maxLines = 1)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // Comments Section
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Community Comments (${comments.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        comments.forEach { comment ->
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = comment.authorName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = comment.content,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newCommentText,
                                onValueChange = { newCommentText = it },
                                placeholder = { Text("Write a comment…") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .testTag("comment_input_field")
                                    .weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (newCommentText.isNotBlank()) {
                                        onAddComment(newCommentText.trim())
                                        newCommentText = ""
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("comment_send_button")
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Send")
                            }
                        }
                    }
                }
            }

            // Similar Posts
            if (similarPosts.isNotEmpty()) {
                item {
                    Text(
                        text = "Similar Recommendations",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(similarPosts) { sim ->
                            Card(
                                modifier = Modifier
                                    .width(140.dp)
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSelectPost(sim.id) }
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = sim.imageUrl,
                                        contentDescription = sim.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .background(Color.Black.copy(alpha = 0.7f))
                                            .padding(6.dp)
                                    ) {
                                        Text(
                                            text = sim.title,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Discover Section
            if (discoverPosts.isNotEmpty()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Explore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Discover More",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(discoverPosts, key = { it.id }) { disc ->
                            Card(
                                modifier = Modifier
                                    .width(140.dp)
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSelectPost(disc.id) }
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    val isDiscVideo = isVideoMediaUrl(disc.imageUrl)
                                    AsyncImage(
                                        model = disc.imageUrl,
                                        contentDescription = disc.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (isDiscVideo) {
                                        Surface(
                                            color = Color.Red.copy(alpha = 0.85f),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier
                                                .padding(6.dp)
                                                .align(Alignment.TopStart)
                                        ) {
                                            Text(
                                                text = "▶ VIDEO",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .background(Color.Black.copy(alpha = 0.75f))
                                            .padding(6.dp)
                                    ) {
                                        Text(
                                            text = disc.title,
                                            color = Color.White,
                                            fontSize = 11.sp,
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

            // Trending Section
            if (trendingPosts.isNotEmpty()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Whatshot,
                            contentDescription = null,
                            tint = DangerRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Trending Content",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(trendingPosts, key = { it.id }) { tr ->
                            Card(
                                modifier = Modifier
                                    .width(140.dp)
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSelectPost(tr.id) }
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    val isTrVideo = isVideoMediaUrl(tr.imageUrl)
                                    AsyncImage(
                                        model = tr.imageUrl,
                                        contentDescription = tr.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (isTrVideo) {
                                        Surface(
                                            color = Color.Red.copy(alpha = 0.85f),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier
                                                .padding(6.dp)
                                                .align(Alignment.TopStart)
                                        ) {
                                            Text(
                                                text = "▶ VIDEO",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .background(Color.Black.copy(alpha = 0.75f))
                                            .padding(6.dp)
                                    ) {
                                        Text(
                                            text = tr.title,
                                            color = Color.White,
                                            fontSize = 11.sp,
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

    // Lightbox Dialog
    if (isLightboxOpen) {
        Dialog(onDismissRequest = { isLightboxOpen = false }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = images.getOrNull(selectedImageIndex) ?: post.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = { isLightboxOpen = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun DownloadActionButton(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color,
    badge: String,
    testTag: String,
    onClick: () -> Unit,
    onCopy: () -> Unit
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = contentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            color = contentColor.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = badge,
                                color = contentColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy Link",
                    tint = contentColor.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun openDownloadUrl(context: Context, url: String, label: String) {
    if (url.isBlank()) {
        Toast.makeText(context, "$label link is unavailable", Toast.LENGTH_SHORT).show()
        return
    }
    val cleanUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
        "https://$url"
    } else {
        url
    }
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "No browser app found to open link", Toast.LENGTH_SHORT).show()
    }
}

private fun copyToClipboard(context: Context, url: String, message: String) {
    if (url.isBlank()) {
        Toast.makeText(context, "Link unavailable", Toast.LENGTH_SHORT).show()
        return
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText("JUSTFAN Link", url)
    clipboard?.setPrimaryClip(clip)
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
