package com.example.justfan.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.justfan.data.model.PostEntity
import com.example.justfan.data.model.RequestEntity
import com.example.justfan.ui.theme.DangerRed
import com.example.justfan.ui.theme.GoldAccent
import com.example.justfan.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    posts: List<PostEntity>,
    requests: List<RequestEntity>,
    onCreatePost: (title: String, desc: String, img: String, link: String, premLink: String?, tags: List<String>, isFree: Boolean, isNsfw: Boolean) -> Unit,
    onDeletePost: (String) -> Unit,
    onUpdateRequestStatus: (id: String, status: String, link: String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var adminTab by remember { mutableIntStateOf(0) }
    var isNewPostDialogOpen by remember { mutableStateOf(false) }

    // New post form state
    var postTitle by remember { mutableStateOf("") }
    var postDesc by remember { mutableStateOf("") }
    var postImg by remember { mutableStateOf("") }
    var postLink by remember { mutableStateOf("") }
    var postPremLink by remember { mutableStateOf("") }
    var postTags by remember { mutableStateOf("") }
    var postIsFree by remember { mutableStateOf(true) }
    var postIsNsfw by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Control Center", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (adminTab == 0) {
                        IconButton(
                            onClick = { isNewPostDialogOpen = true },
                            modifier = Modifier.testTag("admin_add_post_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Post")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            TabRow(
                selectedTabIndex = adminTab,
                containerColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = adminTab == 0,
                    onClick = { adminTab = 0 },
                    text = { Text("Posts (${posts.size})") },
                    modifier = Modifier.testTag("admin_tab_posts")
                )
                Tab(
                    selected = adminTab == 1,
                    onClick = { adminTab = 1 },
                    text = { Text("Requests (${requests.size})") },
                    modifier = Modifier.testTag("admin_tab_requests")
                )
                Tab(
                    selected = adminTab == 2,
                    onClick = { adminTab = 2 },
                    text = { Text("Analytics") },
                    modifier = Modifier.testTag("admin_tab_analytics")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            when (adminTab) {
                0 -> {
                    // Manage Posts
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(posts, key = { it.id }) { post ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = post.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "Clicks: ${post.clicksCount} · Likes: ${post.likesCount} · ${if (post.isFree) "Free" else "Premium"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    IconButton(
                                        onClick = { onDeletePost(post.id) },
                                        modifier = Modifier.testTag("admin_delete_post_${post.id}")
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = DangerRed)
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Manage Requests
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(requests, key = { it.id }) { req ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(text = req.name, fontWeight = FontWeight.Bold)
                                    Text(text = req.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilterChip(
                                            selected = req.status == "pending",
                                            onClick = { onUpdateRequestStatus(req.id, "pending", req.downloadLink) },
                                            label = { Text("Pending") }
                                        )
                                        FilterChip(
                                            selected = req.status == "in_progress",
                                            onClick = { onUpdateRequestStatus(req.id, "in_progress", req.downloadLink) },
                                            label = { Text("In Progress") }
                                        )
                                        FilterChip(
                                            selected = req.status == "delivered",
                                            onClick = {
                                                val link = req.downloadLink ?: "https://images.unsplash.com"
                                                onUpdateRequestStatus(req.id, "delivered", link)
                                            },
                                            label = { Text("Deliver") }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // Analytics
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val totalClicks = posts.sumOf { it.clicksCount }
                        val totalLikes = posts.sumOf { it.likesCount }
                        val deliveredCount = requests.count { it.status == "delivered" }

                        AnalyticsMetricCard("Total Published Galleries", "${posts.size}", Icons.Default.Collections)
                        AnalyticsMetricCard("Total Downloads & Clicks", "$totalClicks", Icons.Default.Download)
                        AnalyticsMetricCard("Total Likes & Bookmarks", "$totalLikes", Icons.Default.Favorite)
                        AnalyticsMetricCard("Delivered Community Requests", "$deliveredCount", Icons.Default.CheckCircle)
                    }
                }
            }
        }
    }

    if (isNewPostDialogOpen) {
        AlertDialog(
            onDismissRequest = { isNewPostDialogOpen = false },
            title = { Text("Add Creator Gallery") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = postTitle,
                        onValueChange = { postTitle = it },
                        label = { Text("Gallery Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = postDesc,
                        onValueChange = { postDesc = it },
                        label = { Text("Description") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = postImg,
                        onValueChange = { postImg = it },
                        label = { Text("Cover Image URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = postLink,
                        onValueChange = { postLink = it },
                        label = { Text("Download Link URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = postTags,
                        onValueChange = { postTags = it },
                        label = { Text("Tags (comma separated)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = postIsFree, onCheckedChange = { postIsFree = it })
                        Text("100% Free Download")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = postIsNsfw, onCheckedChange = { postIsNsfw = it })
                        Text("18+ Content")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (postTitle.isNotBlank() && postImg.isNotBlank()) {
                            val tagsList = postTags.split(",").map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }
                            onCreatePost(
                                postTitle.trim(),
                                postDesc.trim(),
                                postImg.trim(),
                                postLink.trim().ifBlank { postImg.trim() },
                                postPremLink.trim().ifBlank { null },
                                tagsList,
                                postIsFree,
                                postIsNsfw
                            )
                            postTitle = ""
                            postDesc = ""
                            postImg = ""
                            postLink = ""
                            postTags = ""
                            isNewPostDialogOpen = false
                        }
                    }
                ) {
                    Text("Save Gallery")
                }
            },
            dismissButton = {
                TextButton(onClick = { isNewPostDialogOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AnalyticsMetricCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}
