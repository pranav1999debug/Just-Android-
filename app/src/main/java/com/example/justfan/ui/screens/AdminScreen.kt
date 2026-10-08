package com.example.justfan.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.justfan.data.model.PostEntity
import com.example.justfan.data.model.RequestEntity
import com.example.justfan.data.model.UserEntity
import com.example.justfan.data.remote.GeminiAiHelper
import com.example.justfan.data.remote.MediaUploadClient
import com.example.justfan.ui.theme.DangerRed
import com.example.justfan.ui.theme.GoldAccent
import com.example.justfan.ui.theme.SuccessGreen
import com.example.justfan.ui.theme.ThemeEmerald
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    posts: List<PostEntity>,
    requests: List<RequestEntity>,
    users: List<UserEntity>,
    onCreatePost: (title: String, desc: String, img: String, contentImages: List<String>, link: String, premLink: String?, tags: List<String>, isFree: Boolean, isNsfw: Boolean) -> Unit,
    onUpdatePost: (PostEntity) -> Unit,
    onDeletePost: (String) -> Unit,
    onUpdateUserTier: (userId: String, newTier: String) -> Unit,
    onUpdateUserRequestsCount: (userId: String, count: Int) -> Unit,
    onUpdateUserStatus: (userId: String, status: String) -> Unit,
    onDeleteUser: (userId: String) -> Unit,
    onFulfillRequest: (requestId: String, downloadLink: String) -> Unit,
    onRejectRequest: (requestId: String, reason: String) -> Unit,
    onDeleteRequest: (requestId: String) -> Unit,
    onBack: () -> Unit,
    userProfile: com.example.justfan.data.model.UserProfile? = null,
    modifier: Modifier = Modifier
) {
    val isAuthorized = userProfile != null && userProfile.isAdmin && userProfile.email.equals("reytherapper12@gmail.com", ignoreCase = true)
    if (!isAuthorized) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = DangerRed,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Admin Access Restricted",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "The Executive Dashboard is strictly reserved for the platform administrator (reytherapper12@gmail.com).\n\nYour account (${userProfile?.email?.ifBlank { "Guest" } ?: "Guest"}) does not have administrative rights.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Return to Feed")
                    }
                }
            }
        }
        return
    }

    var adminTab by remember { mutableIntStateOf(0) } // 0: Stats, 1: Users, 2: Posts, 3: Requests

    // Modals
    var isNewPostDialogOpen by remember { mutableStateOf(false) }
    var editingPost by remember { mutableStateOf<PostEntity?>(null) }
    var fulfillingRequest by remember { mutableStateOf<RequestEntity?>(null) }
    var rejectingRequest by remember { mutableStateOf<RequestEntity?>(null) }

    // Search queries
    var postSearchQuery by remember { mutableStateOf("") }
    var userSearchQuery by remember { mutableStateOf("") }
    var userTierFilter by remember { mutableStateOf("All") }
    var requestStatusFilter by remember { mutableStateOf("All") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = DangerRed.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "ADMIN",
                                color = DangerRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Executive Dashboard", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (adminTab == 2) {
                        IconButton(
                            onClick = { isNewPostDialogOpen = true },
                            modifier = Modifier.testTag("admin_add_post_btn")
                        ) {
                            Icon(Icons.Default.AddCircle, contentDescription = "Add Post", tint = MaterialTheme.colorScheme.primary)
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
            // Dashboard Tabs
            ScrollableTabRow(
                selectedTabIndex = adminTab,
                containerColor = MaterialTheme.colorScheme.background,
                edgePadding = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = adminTab == 0,
                    onClick = { adminTab = 0 },
                    text = { Text("📊 Overview") },
                    modifier = Modifier.testTag("admin_tab_overview")
                )
                Tab(
                    selected = adminTab == 1,
                    onClick = { adminTab = 1 },
                    text = { Text("👥 Users (${users.size})") },
                    modifier = Modifier.testTag("admin_tab_users")
                )
                Tab(
                    selected = adminTab == 2,
                    onClick = { adminTab = 2 },
                    text = { Text("📁 Content (${posts.size})") },
                    modifier = Modifier.testTag("admin_tab_posts")
                )
                Tab(
                    selected = adminTab == 3,
                    onClick = { adminTab = 3 },
                    text = { Text("📬 Requests (${requests.size})") },
                    modifier = Modifier.testTag("admin_tab_requests")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            when (adminTab) {
                0 -> {
                    // TAB 0: Analytics & Statistics
                    AnalyticsOverviewTab(
                        posts = posts,
                        requests = requests,
                        users = users
                    )
                }
                1 -> {
                    // TAB 1: User Management (Tiers, Requests, Status)
                    UsersManagementTab(
                        users = users,
                        searchQuery = userSearchQuery,
                        onSearchChange = { userSearchQuery = it },
                        tierFilter = userTierFilter,
                        onTierFilterChange = { userTierFilter = it },
                        onUpdateTier = onUpdateUserTier,
                        onResetRequests = { userId -> onUpdateUserRequestsCount(userId, 0) },
                        onToggleStatus = { user ->
                            val next = if (user.status == "Active") "Suspended" else "Active"
                            onUpdateUserStatus(user.id, next)
                        },
                        onDeleteUser = onDeleteUser
                    )
                }
                2 -> {
                    // TAB 2: Post CRUD Management
                    PostsCrudTab(
                        posts = posts,
                        searchQuery = postSearchQuery,
                        onSearchChange = { postSearchQuery = it },
                        onEditPost = { editingPost = it },
                        onDeletePost = onDeletePost,
                        onAddNewClick = { isNewPostDialogOpen = true }
                    )
                }
                3 -> {
                    // TAB 3: Requests Workflow Management (Uploaded, Rejected, Payment Proofs, Stats)
                    RequestsManagementTab(
                        requests = requests,
                        statusFilter = requestStatusFilter,
                        onStatusFilterChange = { requestStatusFilter = it },
                        onFulfill = { fulfillingRequest = it },
                        onReject = { rejectingRequest = it },
                        onDeleteRequest = onDeleteRequest,
                        onUpgradeUserTier = onUpdateUserTier
                    )
                }
            }
        }
    }

    // Modal: Create New Post
    if (isNewPostDialogOpen) {
        PostFormDialog(
            title = "Create New Gallery Post",
            initialPost = null,
            onDismiss = { isNewPostDialogOpen = false },
            onSave = { newPost ->
                onCreatePost(
                    newPost.title,
                    newPost.description,
                    newPost.imageUrl,
                    newPost.contentImages,
                    newPost.linkUrl,
                    newPost.premiumLinkUrl,
                    newPost.tags,
                    newPost.isFree,
                    newPost.isNsfw
                )
                isNewPostDialogOpen = false
            }
        )
    }

    // Modal: Edit Existing Post
    if (editingPost != null) {
        PostFormDialog(
            title = "Edit Gallery Content",
            initialPost = editingPost,
            onDismiss = { editingPost = null },
            onSave = { updated ->
                onUpdatePost(updated)
                editingPost = null
            }
        )
    }

    // Modal: Fulfill / Upload Request Link
    if (fulfillingRequest != null) {
        val req = fulfillingRequest!!
        var downloadUrlInput by remember { mutableStateOf(req.downloadLink ?: "") }

        AlertDialog(
            onDismissRequest = { fulfillingRequest = null },
            title = { Text("Upload / Fulfill Request") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Model: ${req.name}", fontWeight = FontWeight.Bold)
                    Text("Enter the direct download/mirror link for fulfilled content (MEGA, Google Drive, ZIP):", fontSize = 12.sp)
                    OutlinedTextField(
                        value = downloadUrlInput,
                        onValueChange = { downloadUrlInput = it },
                        placeholder = { Text("https://mega.nz/folder/...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanLink = downloadUrlInput.trim().ifBlank { "https://images.unsplash.com" }
                        onFulfillRequest(req.id, cleanLink)
                        fulfillingRequest = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) {
                    Text("Mark as Uploaded & Deliver")
                }
            },
            dismissButton = {
                TextButton(onClick = { fulfillingRequest = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Modal: Reject Request with Reason
    if (rejectingRequest != null) {
        val req = rejectingRequest!!
        var reasonInput by remember { mutableStateOf("Content not available from creator") }

        AlertDialog(
            onDismissRequest = { rejectingRequest = null },
            title = { Text("Reject Content Request") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Model: ${req.name}", fontWeight = FontWeight.Bold)
                    Text("Select or enter the reason for rejecting this request:", fontSize = 12.sp)

                    listOf(
                        "Content not available from creator",
                        "Duplicate request already uploaded",
                        "Violates terms / copyright restrictions",
                        "Insufficient reference details provided"
                    ).forEach { preset ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { reasonInput = preset }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = reasonInput == preset,
                                onClick = { reasonInput = preset }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(preset, fontSize = 12.sp)
                        }
                    }

                    OutlinedTextField(
                        value = reasonInput,
                        onValueChange = { reasonInput = it },
                        label = { Text("Custom Rejection Reason") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRejectRequest(req.id, reasonInput.trim())
                        rejectingRequest = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Confirm Rejection")
                }
            },
            dismissButton = {
                TextButton(onClick = { rejectingRequest = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ----------------------------------------------------------------------------
// TAB 0: Analytics Overview & Statistics
// ----------------------------------------------------------------------------
@Composable
private fun AnalyticsOverviewTab(
    posts: List<PostEntity>,
    requests: List<RequestEntity>,
    users: List<UserEntity>
) {
    val totalClicks = remember(posts) { posts.sumOf { it.clicksCount } }
    val totalLikes = remember(posts) { posts.sumOf { it.likesCount } }

    val deliveredRequests = remember(requests) { requests.count { it.status == "delivered" } }
    val rejectedRequests = remember(requests) { requests.count { it.status == "rejected" } }
    val pendingRequests = remember(requests) { requests.count { it.status == "pending" || it.status == "in_progress" } }

    val freeUsersCount = remember(users) { users.count { it.tier == "Free" } }
    val proUsersCount = remember(users) { users.count { it.tier == "Pro" } }
    val legendaryUsersCount = remember(users) { users.count { it.tier == "Legendary" } }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 80.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // High-level metrics grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "Total Posts",
                    value = "${posts.size}",
                    icon = Icons.Default.Collections,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Total Clicks",
                    value = "$totalClicks",
                    icon = Icons.Default.FileDownload,
                    color = ThemeEmerald,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "Total Users",
                    value = "${users.size}",
                    icon = Icons.Default.People,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Total Likes",
                    value = "$totalLikes",
                    icon = Icons.Default.Favorite,
                    color = DangerRed,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // User Tier Breakdown Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "User Tier Distribution",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        TierStatBadge(tier = "Free", count = freeUsersCount, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TierStatBadge(tier = "Pro (1 Mo)", count = proUsersCount, color = MaterialTheme.colorScheme.primary)
                        TierStatBadge(tier = "Legendary", count = legendaryUsersCount, color = GoldAccent)
                    }
                }
            }
        }

        // Requests Breakdown Card (Delivered vs Rejected vs Pending)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Community Request Statistics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Uploaded / Delivered", fontSize = 11.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
                            Text("$deliveredRequests", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = SuccessGreen)
                        }
                        Column {
                            Text("Pending Review", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text("$pendingRequests", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        }
                        Column {
                            Text("Rejected", fontSize = 11.sp, color = DangerRed, fontWeight = FontWeight.Bold)
                            Text("$rejectedRequests", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = DangerRed)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val totalReq = (requests.size).coerceAtLeast(1)
                    val deliveredRatio = (deliveredRequests.toFloat() / totalReq)
                    val rejectedRatio = (rejectedRequests.toFloat() / totalReq)

                    Text("Fulfillment Rate: ${(deliveredRatio * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { deliveredRatio },
                        color = SuccessGreen,
                        trackColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(text = title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun TierStatBadge(tier: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "$count", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = color)
        Surface(
            color = color.copy(alpha = 0.15f),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = tier,
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

// ----------------------------------------------------------------------------
// TAB 1: User Management (Tiers, Quotas, Status)
// ----------------------------------------------------------------------------
@Composable
private fun UsersManagementTab(
    users: List<UserEntity>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    tierFilter: String,
    onTierFilterChange: (String) -> Unit,
    onUpdateTier: (String, String) -> Unit,
    onResetRequests: (String) -> Unit,
    onToggleStatus: (UserEntity) -> Unit,
    onDeleteUser: (String) -> Unit
) {
    val filteredUsers = remember(users, searchQuery, tierFilter) {
        users.filter { user ->
            val matchesSearch = user.username.contains(searchQuery, ignoreCase = true) ||
                    user.email.contains(searchQuery, ignoreCase = true)
            val matchesTier = tierFilter == "All" || user.tier.equals(tierFilter, ignoreCase = true)
            matchesSearch && matchesTier
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search users by username or email…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("admin_user_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Tier Filter chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("All", "Free", "Pro", "Legendary")) { opt ->
                FilterChip(
                    selected = tierFilter == opt,
                    onClick = { onTierFilterChange(opt) },
                    label = { Text(opt) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredUsers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No users found matching query.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 80.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredUsers, key = { it.id }) { user ->
                    UserManagementCard(
                        user = user,
                        onUpdateTier = { newTier -> onUpdateTier(user.id, newTier) },
                        onResetRequests = { onResetRequests(user.id) },
                        onToggleStatus = { onToggleStatus(user) },
                        onDelete = { onDeleteUser(user.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun UserManagementCard(
    user: UserEntity,
    onUpdateTier: (String) -> Unit,
    onResetRequests: () -> Unit,
    onToggleStatus: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user.username.take(2).uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(user.username, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            if (user.isAdmin) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = DangerRed,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "ADMIN",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                }

                // Tier badge
                Surface(
                    color = when (user.tier) {
                        "Legendary" -> GoldAccent
                        "Pro" -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surface
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = user.tier.uppercase(),
                        color = if (user.tier == "Legendary") Color.Black else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // User info row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Requests count: ${user.requestsCount} (${if (user.tier == "Free") "${(3 - user.requestsCount).coerceAtLeast(0)} left" else "Unlimited"})",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Status: ${user.status}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (user.status == "Active") SuccessGreen else DangerRed
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tier Change Quick Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tier:", fontSize = 11.sp, fontWeight = FontWeight.Bold)

                listOf("Free", "Pro", "Legendary").forEach { tierOpt ->
                    val isCurrent = user.tier.equals(tierOpt, ignoreCase = true)
                    Surface(
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .clickable { onUpdateTier(tierOpt) }
                    ) {
                        Text(
                            text = tierOpt,
                            color = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = onResetRequests,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset Requests", modifier = Modifier.size(16.dp))
                }

                if (!user.isAdmin) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete User", tint = DangerRed, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// TAB 2: Post CRUD Management
// ----------------------------------------------------------------------------
@Composable
private fun PostsCrudTab(
    posts: List<PostEntity>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onEditPost: (PostEntity) -> Unit,
    onDeletePost: (String) -> Unit,
    onAddNewClick: () -> Unit
) {
    val filteredPosts = remember(posts, searchQuery) {
        if (searchQuery.isBlank()) posts
        else posts.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    it.author.contains(searchQuery, ignoreCase = true) ||
                    it.tags.any { tag -> tag.contains(searchQuery, ignoreCase = true) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search posts…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("admin_post_search_input")
            )
            Button(
                onClick = onAddNewClick,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("admin_btn_add_post")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 80.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredPosts, key = { it.id }) { post ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Thumbnail
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            AsyncImage(
                                model = post.imageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = post.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "By ${post.author} · Clicks: ${post.clicksCount} · Likes: ${post.likesCount}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Surface(
                                    color = if (post.isFree) SuccessGreen.copy(alpha = 0.2f) else GoldAccent.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = if (post.isFree) "FREE" else "PREMIUM",
                                        color = if (post.isFree) SuccessGreen else GoldAccent,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                                if (post.isNsfw) {
                                    Surface(
                                        color = DangerRed.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "18+",
                                            color = DangerRed,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Edit Button
                        IconButton(onClick = { onEditPost(post) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Post", tint = MaterialTheme.colorScheme.primary)
                        }

                        // Delete Button
                        IconButton(onClick = { onDeletePost(post.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Post", tint = DangerRed)
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// TAB 3: Requests Workflow Management (Uploaded, Rejected, Pending, Payment Proofs, Actions)
// ----------------------------------------------------------------------------
@Composable
private fun RequestsManagementTab(
    requests: List<RequestEntity>,
    statusFilter: String,
    onStatusFilterChange: (String) -> Unit,
    onFulfill: (RequestEntity) -> Unit,
    onReject: (RequestEntity) -> Unit,
    onDeleteRequest: (String) -> Unit,
    onUpgradeUserTier: (userId: String, newTier: String) -> Unit = { _, _ -> }
) {
    val paymentProofCount = remember(requests) {
        requests.count { it.name.contains("PAYMENT", ignoreCase = true) || it.message.contains("[PAYMENT_PROOF]", ignoreCase = true) }
    }

    val filteredRequests = remember(requests, statusFilter) {
        when (statusFilter) {
            "All" -> requests
            "payments" -> requests.filter { it.name.contains("PAYMENT", ignoreCase = true) || it.message.contains("[PAYMENT_PROOF]", ignoreCase = true) }
            else -> requests.filter { it.status.equals(statusFilter, ignoreCase = true) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Status filter bar
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("All", "payments", "pending", "delivered", "rejected")) { opt ->
                val label = when (opt) {
                    "payments" -> "💳 Payments ($paymentProofCount)"
                    "pending" -> "Pending"
                    "delivered" -> "Uploaded / Delivered"
                    "rejected" -> "Rejected"
                    else -> "All Requests"
                }
                FilterChip(
                    selected = statusFilter == opt,
                    onClick = { onStatusFilterChange(opt) },
                    label = { Text(label, fontWeight = if (opt == "payments") FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredRequests.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No requests in this category.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 80.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredRequests, key = { it.id }) { req ->
                    RequestAdminCard(
                        request = req,
                        onFulfill = { onFulfill(req) },
                        onReject = { onReject(req) },
                        onDelete = { onDeleteRequest(req.id) },
                        onUpgradeUserTier = onUpgradeUserTier
                    )
                }
            }
        }
    }
}

@Composable
private fun RequestAdminCard(
    request: RequestEntity,
    onFulfill: () -> Unit,
    onReject: () -> Unit,
    onDelete: () -> Unit,
    onUpgradeUserTier: (userId: String, newTier: String) -> Unit = { _, _ -> }
) {
    val isPaymentProof = request.name.contains("PAYMENT", ignoreCase = true) || request.message.contains("[PAYMENT_PROOF]", ignoreCase = true)
    var upgradedSuccessMessage by remember { mutableStateOf<String?>(null) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPaymentProof) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isPaymentProof) androidx.compose.foundation.BorderStroke(1.5.dp, SuccessGreen) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isPaymentProof) {
                        Surface(
                            color = SuccessGreen,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = "GOOGLE PAY PROOF",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(request.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                // Status chip
                val statusColor = when (request.status) {
                    "delivered" -> SuccessGreen
                    "rejected" -> DangerRed
                    "in_progress" -> MaterialTheme.colorScheme.primary
                    else -> GoldAccent
                }
                Surface(
                    color = statusColor.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = when (request.status) {
                            "delivered" -> "UPLOADED / APPROVED"
                            "rejected" -> "REJECTED"
                            else -> request.status.uppercase()
                        },
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "By ${request.email}${if (!request.telegramUsername.isNullOrBlank()) " · @${request.telegramUsername}" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))
            Text(text = request.message, style = MaterialTheme.typography.bodyMedium)

            // Payment proof screenshot image preview (Imgchest)
            if (!request.imageUrl.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Uploaded Screenshot (Imgchest):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Text(
                                text = "Tap to enlarge",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        AsyncImage(
                            model = request.imageUrl,
                            contentDescription = "Payment Proof Screenshot",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                }
            }

            // Quick Admin Action: Upgrade User Account Directly from Payment Proof
            if (isPaymentProof) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "⚡ Admin Tier Upgrade Action:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = SuccessGreen
                        )
                        Text(
                            text = "Approve payment and upgrade ${request.email} to:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    onUpgradeUserTier(request.email, "Pro")
                                    onFulfill()
                                    upgradedSuccessMessage = "Account ${request.email} upgraded to PRO!"
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Upgrade to Pro (Rs 150)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    onUpgradeUserTier(request.email, "Legendary")
                                    onFulfill()
                                    upgradedSuccessMessage = "Account ${request.email} upgraded to LEGENDARY!"
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = Color.Black),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Upgrade to Legendary (Rs 500)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        upgradedSuccessMessage?.let { msg ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = msg,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen
                            )
                        }
                    }
                }
            }

            // Rejection reason notice if rejected
            if (request.status == "rejected" && !request.rejectionReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = DangerRed.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = DangerRed, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Rejection Reason: ${request.rejectionReason}",
                            fontSize = 12.sp,
                            color = DangerRed,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Fulfillment link notice if delivered
            if (request.status == "delivered" && !request.downloadLink.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = SuccessGreen.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Fulfillment Link: ${request.downloadLink}",
                            fontSize = 11.sp,
                            color = SuccessGreen,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (request.status != "delivered") {
                    Button(
                        onClick = onFulfill,
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isPaymentProof) "Approve Payment" else "Upload / Deliver", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (request.status != "rejected") {
                    OutlinedButton(
                        onClick = onReject,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reject", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// Post Create / Edit Modal Dialog
// ----------------------------------------------------------------------------
@Composable
private fun PostFormDialog(
    title: String,
    initialPost: PostEntity?,
    onDismiss: () -> Unit,
    onSave: (PostEntity) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var postTitle by remember { mutableStateOf(initialPost?.title ?: "") }
    var postDesc by remember { mutableStateOf(initialPost?.description ?: "") }
    var postImg by remember { mutableStateOf(initialPost?.imageUrl ?: "") }
    var postContentImages by remember { mutableStateOf(initialPost?.contentImages ?: emptyList<String>()) }
    var postLink by remember { mutableStateOf(initialPost?.linkUrl ?: "") }
    var postPremLink by remember { mutableStateOf(initialPost?.premiumLinkUrl ?: "") }
    var postDirectLink by remember { mutableStateOf(initialPost?.directLinkUrl ?: "") }
    var postAuthor by remember { mutableStateOf(initialPost?.author ?: "JUSTFAN Creator") }
    var postTags by remember { mutableStateOf(initialPost?.tags?.joinToString(", ") ?: "exclusive, portrait, cosplay") }
    var postIsFree by remember { mutableStateOf(initialPost?.isFree ?: true) }
    var postIsNsfw by remember { mutableStateOf(initialPost?.isNsfw ?: false) }

    // Cover upload state
    var isUploadingCover by remember { mutableStateOf(false) }
    var coverUploadError by remember { mutableStateOf<String?>(null) }

    // Content media multi-upload state
    var isUploadingContent by remember { mutableStateOf(false) }
    var contentUploadStatus by remember { mutableStateOf<String?>(null) }
    var contentUploadError by remember { mutableStateOf<String?>(null) }
    var manualContentUrl by remember { mutableStateOf("") }
    var showManualUrlInput by remember { mutableStateOf(false) }

    // AI Generation state
    var isGeneratingAi by remember { mutableStateOf(false) }
    var aiMessage by remember { mutableStateOf<String?>(null) }
    var aiIsError by remember { mutableStateOf(false) }

    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                isUploadingCover = true
                coverUploadError = null
                val result = MediaUploadClient.uploadSingleMedia(
                    context = context,
                    uri = uri,
                    title = postTitle.ifBlank { "Cover Image" }
                )
                if (result.isSuccess) {
                    postImg = result.getOrNull() ?: ""
                } else {
                    coverUploadError = result.exceptionOrNull()?.message ?: "Cover upload failed"
                }
                isUploadingCover = false
            }
        }
    }

    val contentMediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30)
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            coroutineScope.launch {
                isUploadingContent = true
                contentUploadError = null
                val total = uris.size
                val uploadedUrls = mutableListOf<String>()
                for ((idx, u) in uris.withIndex()) {
                    contentUploadStatus = "Uploading ${idx + 1} of $total..."
                    val res = MediaUploadClient.uploadSingleMedia(
                        context = context,
                        uri = u,
                        title = postTitle.ifBlank { "Content Media ${idx + 1}" }
                    )
                    if (res.isSuccess) {
                        res.getOrNull()?.let { uploadedUrls.add(it) }
                    } else {
                        val errText = res.exceptionOrNull()?.message ?: "Upload failed"
                        contentUploadError = errText
                    }
                }
                if (uploadedUrls.isNotEmpty()) {
                    postContentImages = postContentImages + uploadedUrls
                }
                isUploadingContent = false
                contentUploadStatus = null
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }

                // Title field with AI Button
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = postTitle,
                            onValueChange = {
                                postTitle = it
                                aiMessage = null
                            },
                            label = { Text("Gallery Title *") },
                            placeholder = { Text("e.g. Cyberpunk 2077 Neon Lucy 4K Set") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("post_form_title")
                        )

                        // AI Generate Button
                        Button(
                            onClick = {
                                if (postTitle.isBlank()) {
                                    aiMessage = "Please enter a title first"
                                    aiIsError = true
                                } else {
                                    coroutineScope.launch {
                                        isGeneratingAi = true
                                        aiMessage = null
                                        aiIsError = false
                                        val genRes = GeminiAiHelper.generateDescriptionAndHashtags(postTitle)
                                        if (genRes.isSuccess) {
                                            val gen = genRes.getOrNull()!!
                                            postDesc = gen.description
                                            postTags = gen.hashtags.joinToString(", ")
                                            aiMessage = "✨ Description and hashtags generated with AI!"
                                            aiIsError = false
                                        } else {
                                            aiMessage = genRes.exceptionOrNull()?.message ?: "AI generation failed"
                                            aiIsError = true
                                        }
                                        isGeneratingAi = false
                                    }
                                }
                            },
                            enabled = !isGeneratingAi,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_ai_generate_btn")
                        ) {
                            if (isGeneratingAi) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Generating with Gemini AI...", fontSize = 13.sp)
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = "AI", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Generate Description & Hashtags with AI", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }

                        if (aiMessage != null) {
                            Text(
                                text = aiMessage!!,
                                color = if (aiIsError) DangerRed else SuccessGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Description field
                item {
                    OutlinedTextField(
                        value = postDesc,
                        onValueChange = { postDesc = it },
                        label = { Text("Description") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // --- COVER IMAGE SECTION ---
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Cover Image *", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                OutlinedButton(
                                    onClick = {
                                        coverPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    enabled = !isUploadingCover,
                                    modifier = Modifier.testTag("admin_upload_cover_btn")
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = "Upload", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (postImg.isBlank()) "Upload Cover" else "Change Cover", fontSize = 12.sp)
                                }
                            }

                            if (isUploadingCover) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text("Uploading cover to CDN...", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }

                            if (coverUploadError != null) {
                                Text(coverUploadError!!, color = DangerRed, fontSize = 11.sp)
                            }

                            if (postImg.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    AsyncImage(
                                        model = postImg,
                                        contentDescription = "Cover Preview",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Cover ready", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(postImg.take(45) + "...", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { postImg = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove Cover", tint = DangerRed)
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = postImg,
                                onValueChange = { postImg = it },
                                label = { Text("Cover Image Direct URL") },
                                placeholder = { Text("https://...") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("post_form_img")
                            )
                        }
                    }
                }

                // --- CONTENT IMAGES & VIDEOS SECTION ---
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Content Media (${postContentImages.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            contentMediaPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                            )
                                        },
                                        enabled = !isUploadingContent,
                                        modifier = Modifier.testTag("admin_upload_content_btn")
                                    ) {
                                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Add Media", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Add Media", fontSize = 12.sp)
                                    }

                                    IconButton(onClick = { showManualUrlInput = !showManualUrlInput }) {
                                        Icon(Icons.Default.Link, contentDescription = "Add Link")
                                    }
                                }
                            }

                            if (isUploadingContent) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text(contentUploadStatus ?: "Uploading media...", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }

                            if (contentUploadError != null) {
                                Text(contentUploadError!!, color = DangerRed, fontSize = 11.sp)
                            }

                            // Manual URL input toggle
                            if (showManualUrlInput) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedTextField(
                                        value = manualContentUrl,
                                        onValueChange = { manualContentUrl = it },
                                        placeholder = { Text("Paste image/video link") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = {
                                            if (manualContentUrl.isNotBlank()) {
                                                postContentImages = postContentImages + manualContentUrl.trim()
                                                manualContentUrl = ""
                                                showManualUrlInput = false
                                            }
                                        }
                                    ) {
                                        Text("Add")
                                    }
                                }
                            }

                            // Horizontal scroll of content images
                            if (postContentImages.isNotEmpty()) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    items(postContentImages) { mediaUrl ->
                                        Box(
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                        ) {
                                            AsyncImage(
                                                model = mediaUrl,
                                                contentDescription = "Content Media",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )

                                            // If video url, show small video icon
                                            val isVid = com.example.justfan.ui.components.isVideoMediaUrl(mediaUrl)
                                            if (isVid) {
                                                Surface(
                                                    color = Color.Black.copy(alpha = 0.6f),
                                                    shape = CircleShape,
                                                    modifier = Modifier.size(20.dp).align(Alignment.Center)
                                                ) {
                                                    Icon(
                                                        Icons.Default.PlayArrow,
                                                        contentDescription = "Video",
                                                        tint = Color.White,
                                                        modifier = Modifier.padding(2.dp)
                                                    )
                                                }
                                            }

                                            // Remove button
                                            Surface(
                                                color = Color.Black.copy(alpha = 0.7f),
                                                shape = CircleShape,
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .align(Alignment.TopEnd)
                                                    .padding(2.dp)
                                                    .clickable {
                                                        postContentImages = postContentImages.filter { it != mediaUrl }
                                                    }
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Remove",
                                                    tint = Color.White,
                                                    modifier = Modifier.padding(2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    "No content images or videos added yet. Upload from device or add links.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = postLink,
                        onValueChange = { postLink = it },
                        label = { Text("Free Ads Download Link URL *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("post_form_link")
                    )
                }

                item {
                    OutlinedTextField(
                        value = postPremLink,
                        onValueChange = { postPremLink = it },
                        label = { Text("Premium 4K Link URL (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = postDirectLink,
                        onValueChange = { postDirectLink = it },
                        label = { Text("Direct Fast Mirror Link URL (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = postAuthor,
                        onValueChange = { postAuthor = it },
                        label = { Text("Creator / Author Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = postTags,
                        onValueChange = { postTags = it },
                        label = { Text("Tags (comma separated)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Free Content")
                        Switch(checked = postIsFree, onCheckedChange = { postIsFree = it })
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("18+ NSFW Content")
                        Switch(checked = postIsNsfw, onCheckedChange = { postIsNsfw = it })
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (postTitle.isNotBlank() && postImg.isNotBlank()) {
                                    val tagsList = postTags.split(",")
                                        .map { it.trim().removePrefix("#") }
                                        .filter { it.isNotBlank() }

                                    val saved = (initialPost ?: PostEntity(
                                        id = UUID.randomUUID().toString(),
                                        title = postTitle.trim(),
                                        description = postDesc.trim(),
                                        imageUrl = postImg.trim(),
                                        contentImages = postContentImages,
                                        linkUrl = postLink.trim().ifBlank { postImg.trim() },
                                        tags = tagsList
                                    )).copy(
                                        title = postTitle.trim(),
                                        description = postDesc.trim(),
                                        imageUrl = postImg.trim(),
                                        contentImages = postContentImages,
                                        linkUrl = postLink.trim().ifBlank { postImg.trim() },
                                        premiumLinkUrl = postPremLink.trim().ifBlank { null },
                                        directLinkUrl = postDirectLink.trim().ifBlank { null },
                                        author = postAuthor.trim().ifBlank { "JUSTFAN Creator" },
                                        tags = tagsList,
                                        isFree = postIsFree,
                                        isNsfw = postIsNsfw
                                    )
                                    onSave(saved)
                                }
                            },
                            enabled = postTitle.isNotBlank() && postImg.isNotBlank() && !isUploadingCover && !isUploadingContent,
                            modifier = Modifier.weight(1f).testTag("post_form_submit")
                        ) {
                            Text("Save Post")
                        }
                    }
                }
            }
        }
    }
}
