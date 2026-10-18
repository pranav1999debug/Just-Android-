package com.example.justfan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.justfan.data.model.PostEntity
import com.example.justfan.data.model.RequestEntity
import com.example.justfan.data.model.UserEntity
import com.example.justfan.ui.theme.DangerRed
import com.example.justfan.ui.theme.GoldAccent
import com.example.justfan.ui.theme.SuccessGreen
import java.util.UUID

@Composable
fun AdminScreen(
    users: List<UserEntity>,
    requests: List<RequestEntity>,
    onUpdateUserTier: (userIdOrEmail: String, newTier: String) -> Unit,
    onUpdateRequestStatus: (id: String, status: String, link: String?, reason: String?) -> Unit,
    onDeleteRequest: (id: String) -> Unit,
    onCreatePost: (PostEntity) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var notificationFeedback by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Users (${users.size})") },
                icon = { Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Requests (${requests.size})") },
                icon = { Icon(Icons.Default.Mail, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Upload Post") },
                icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }

        if (notificationFeedback != null) {
            Surface(
                color = SuccessGreen.copy(alpha = 0.2f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(notificationFeedback!!, color = SuccessGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { notificationFeedback = null }, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = SuccessGreen)
                    }
                }
            }
        }

        when (selectedTab) {
            0 -> AdminUsersTab(
                users = users,
                onUpdateTier = { idOrEmail, tier, email ->
                    onUpdateUserTier(idOrEmail, tier)
                    notificationFeedback = "Marked $email as $tier! Synced to Supabase subscriptions table. ✓"
                },
                onRefresh = onRefresh
            )
            1 -> AdminRequestsTab(
                requests = requests,
                users = users,
                onUpdateTier = { userUuid, tier, email ->
                    onUpdateUserTier(userUuid, tier)
                    notificationFeedback = "Upgraded $email to $tier! Synced to subscriptions table. ✓"
                },
                onUpdateStatus = onUpdateRequestStatus,
                onDelete = onDeleteRequest,
                onRefresh = onRefresh
            )
            2 -> AdminUploadPostTab(
                onCreatePost = { post ->
                    onCreatePost(post)
                    notificationFeedback = "Gallery '${post.title}' published to Supabase! ✓"
                    selectedTab = 0
                }
            )
        }
    }
}

@Composable
fun AdminUsersTab(
    users: List<UserEntity>,
    onUpdateTier: (idOrEmail: String, tier: String, email: String) -> Unit,
    onRefresh: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var tierFilter by remember { mutableStateOf("All") }

    val freeCount = remember(users) { users.count { it.tier.equals("free", ignoreCase = true) } }
    val proCount = remember(users) { users.count { it.tier.equals("pro", ignoreCase = true) } }
    val legendaryCount = remember(users) { users.count { it.tier.equals("legendary", ignoreCase = true) } }

    val filtered = remember(users, searchQuery, tierFilter) {
        users.filter { u ->
            val matchQuery = searchQuery.isBlank() ||
                    u.username.contains(searchQuery, ignoreCase = true) ||
                    u.email.contains(searchQuery, ignoreCase = true)

            val matchTier = when (tierFilter) {
                "Free" -> u.tier.equals("free", ignoreCase = true)
                "Pro" -> u.tier.equals("pro", ignoreCase = true)
                "Legendary" -> u.tier.equals("legendary", ignoreCase = true)
                else -> true
            }
            matchQuery && matchTier
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Stats Header Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Subscriber Tier Breakdown", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        IconButton(onClick = onRefresh, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        TierStatBadge("Free", freeCount, MaterialTheme.colorScheme.onSurfaceVariant)
                        TierStatBadge("Pro (30d)", proCount, MaterialTheme.colorScheme.primary)
                        TierStatBadge("Legendary", legendaryCount, GoldAccent)
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search subscriber by email or username…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_user_search"),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Free", "Pro", "Legendary").forEach { f ->
                    FilterChip(
                        selected = tierFilter == f,
                        onClick = { tierFilter = f },
                        label = { Text(f, fontSize = 11.sp) }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No users found matching query.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(filtered, key = { it.id }) { user ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_user_item_${user.id}")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(user.username, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(user.email, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            val badgeColor = when (user.tier.lowercase()) {
                                "legendary" -> GoldAccent
                                "pro" -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Surface(color = badgeColor, shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    user.tier.uppercase(),
                                    color = Color.Black,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick Tier Modifier Buttons
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Set Tier:", fontSize = 11.sp, fontWeight = FontWeight.Bold)

                            listOf("Free", "Pro", "Legendary").forEach { tierOpt ->
                                val isCurrent = user.tier.equals(tierOpt, ignoreCase = true)
                                Surface(
                                    color = if (isCurrent) {
                                        if (tierOpt == "Legendary") GoldAccent else MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .testTag("set_tier_${user.id}_$tierOpt")
                                        .clickable {
                                            onUpdateTier(user.id, tierOpt, user.email)
                                        }
                                ) {
                                    Text(
                                        text = if (tierOpt == "Legendary") "★ Legendary" else tierOpt,
                                        color = if (isCurrent) Color.Black else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 11.sp,
                                        fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
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
fun TierStatBadge(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$count", fontWeight = FontWeight.Black, fontSize = 16.sp, color = color)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AdminRequestsTab(
    requests: List<RequestEntity>,
    users: List<UserEntity>,
    onUpdateTier: (userUuid: String, tier: String, email: String) -> Unit,
    onUpdateStatus: (id: String, status: String, link: String?, reason: String?) -> Unit,
    onDelete: (id: String) -> Unit,
    onRefresh: () -> Unit
) {
    var linkInputMap by remember { mutableStateOf(mapOf<String, String>()) }
    var reasonInputMap by remember { mutableStateOf(mapOf<String, String>()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Subscriber Content Requests (${requests.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
        }

        if (requests.isEmpty()) {
            item {
                Text("No custom requests submitted yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(requests, key = { it.id }) { req ->
                // Resolve user UUID from user list or req.userId
                val matchingUser = remember(req.email, users) {
                    users.find { it.email.equals(req.email, ignoreCase = true) || it.id == req.userId }
                }
                val resolvedUserUuid = matchingUser?.id ?: req.userId ?: req.email

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(req.name ?: "Unknown Fan", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(req.email, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { onDelete(req.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = DangerRed)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "Idea: ${req.message}", fontSize = 13.sp)

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick 1-Tap Upgrade User from Request
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Upgrade Subscriber:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.clickable {
                                            onUpdateTier(resolvedUserUuid, "Pro", req.email)
                                        }
                                    ) {
                                        Text("+ Pro Tier", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                    }
                                    Surface(
                                        color = GoldAccent,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.clickable {
                                            onUpdateTier(resolvedUserUuid, "Legendary", req.email)
                                        }
                                    ) {
                                        Text("+ ★ Legendary", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Fulfillment & Status Actions
                        val linkInput = linkInputMap[req.id] ?: (req.downloadLink ?: "")
                        OutlinedTextField(
                            value = linkInput,
                            onValueChange = { linkInputMap = linkInputMap + (req.id to it) },
                            placeholder = { Text("Download / Fulfillment URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    onUpdateStatus(req.id, "fulfilled", linkInput.ifBlank { null }, null)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Fulfill Link")
                            }

                            OutlinedButton(
                                onClick = {
                                    onUpdateStatus(req.id, "rejected", null, "Cannot be accommodated at this time")
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Reject", color = DangerRed)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminUploadPostTab(
    onCreatePost: (PostEntity) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var adsLinkUrl by remember { mutableStateOf("") }
    var premiumLinkUrl by remember { mutableStateOf("") }
    var directLinkUrl by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("JustFan Creator") }
    var tagsInput by remember { mutableStateOf("Exclusive, VIP, Glamour") }
    var isFree by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Upload New Exclusive Media Gallery", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        item {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Post Title") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = author,
                onValueChange = { author = it },
                label = { Text("Author / Creator Name") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                maxLines = 3
            )
        }

        item {
            OutlinedTextField(
                value = imageUrl,
                onValueChange = { imageUrl = it },
                label = { Text("Preview Image/Video URL (Main Thumbnail)") },
                placeholder = { Text("https://cdn.imgchest.com/...jpg") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Text("Download Link Configuration (3 Buttons)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        item {
            OutlinedTextField(
                value = adsLinkUrl,
                onValueChange = { adsLinkUrl = it },
                label = { Text("1. Download (Ads) Link") },
                placeholder = { Text("Ad-supported link (e.g. Catbox/TeraBox)") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = premiumLinkUrl,
                onValueChange = { premiumLinkUrl = it },
                label = { Text("2. Premium Link (VIP Users)") },
                placeholder = { Text("High-speed link for VIP/Pro users") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = directLinkUrl,
                onValueChange = { directLinkUrl = it },
                label = { Text("3. Direct Download Link (Pro/Admin Only)") },
                placeholder = { Text("Instant direct file mirror") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = tagsInput,
                onValueChange = { tagsInput = it },
                label = { Text("Tags (comma separated)") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = if (isFree) "Free for All Users" else "Premium (VIP Subscribers Only)", fontWeight = FontWeight.Bold)
                FilterChip(
                    selected = isFree,
                    onClick = { isFree = !isFree },
                    label = { Text(if (isFree) "Free" else "Premium") }
                )
            }
        }

        item {
            Button(
                onClick = {
                    if (title.isNotBlank() && imageUrl.isNotBlank()) {
                        val tags = tagsInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        val post = PostEntity(
                            id = UUID.randomUUID().toString(),
                            title = title.trim(),
                            author = author.trim(),
                            description = description.trim(),
                            imageUrl = imageUrl.trim(),
                            linkUrl = adsLinkUrl.trim().ifBlank { imageUrl.trim() },
                            premiumLinkUrl = premiumLinkUrl.trim().ifBlank { null },
                            directLinkUrl = directLinkUrl.trim().ifBlank { null },
                            tags = tags,
                            isFree = isFree,
                            createdAt = System.currentTimeMillis()
                        )
                        onCreatePost(post)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.Black),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("PUBLISH GALLERY TO DATABASE", fontWeight = FontWeight.Bold)
            }
        }
    }
}
