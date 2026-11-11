package com.example.justfan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.example.justfan.data.remote.BufferClient
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
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.example.justfan.data.model.PostEntity
import com.example.justfan.data.model.RequestEntity
import com.example.justfan.data.model.UserEntity
import com.example.justfan.data.remote.ImgchestUploader
import com.example.justfan.ui.theme.DangerRed
import com.example.justfan.ui.theme.GoldAccent
import com.example.justfan.ui.theme.SuccessGreen
import kotlinx.coroutines.launch
import java.util.UUID

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.justfan.data.remote.AppVersionConfig
import com.example.justfan.data.remote.MediaUploadClient
import com.example.justfan.data.remote.VersionGateManager
import com.example.justfan.ui.components.PostEditorDialog
import com.example.justfan.ui.components.isVideoMediaUrl

private fun resolvePaymentProofUrl(req: RequestEntity): String? {
    val candidates = listOfNotNull(
        req.imageUrl?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) },
        req.downloadLink?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) },
        req.telegramUsername?.trim()?.takeIf { it.startsWith("http", true) }
    )
    for (candidate in candidates) {
        return if (candidate.startsWith("http://", true) || candidate.startsWith("https://", true)) {
            candidate
        } else {
            "https://$candidate"
        }
    }
    val urlRegex = Regex("""https?://[^\s|,"'\]]+""", RegexOption.IGNORE_CASE)
    val match = urlRegex.find(req.message) ?: urlRegex.find(req.telegramUsername.orEmpty())
    return match?.value
}

private fun openExternalProofUrl(context: Context, rawUrl: String) {
    val normalized = rawUrl.trim().let {
        if (it.startsWith("http://", true) || it.startsWith("https://", true)) it else "https://$it"
    }
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open browser for: $normalized", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun AdminScreen(
    users: List<UserEntity>,
    requests: List<RequestEntity>,
    posts: List<PostEntity> = emptyList(),
    onUpdateUserTier: (userIdOrEmail: String, newTier: String, emailHint: String?, onDone: (Boolean, String?) -> Unit) -> Unit,
    onUpdateRequestStatus: (id: String, status: String, link: String?, reason: String?) -> Unit,
    onDeleteRequest: (id: String) -> Unit,
    onCreatePost: (PostEntity, (Boolean, String?) -> Unit) -> Unit,
    onUpdatePost: (PostEntity, (Boolean) -> Unit) -> Unit = { _, _ -> },
    onDeletePost: (String, (Boolean) -> Unit) -> Unit = { _, _ -> },
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var notificationFeedback by remember { mutableStateOf<String?>(null) }

    val paymentProofs = remember(requests) {
        requests.filter { it.message.startsWith("[PAYMENT_PROOF]") }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Users (${users.size})", fontSize = 11.sp) },
                icon = { Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Payments (${paymentProofs.size})", fontSize = 11.sp) },
                icon = { Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Requests (${requests.size})", fontSize = 11.sp) },
                icon = { Icon(Icons.Default.Mail, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                text = { Text("Posts CRUD", fontSize = 11.sp) },
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
                    notificationFeedback = "Updating $email to $tier in Supabase database…"
                    onUpdateUserTier(idOrEmail, tier, email) { ok, err ->
                        notificationFeedback = if (ok) {
                            "Marked $email as $tier! Saved in remote Supabase database & active across reinstalls. ✓"
                        } else {
                            "Updated $email locally (${err ?: "remote sync warning"})."
                        }
                    }
                },
                onRefresh = onRefresh
            )
            1 -> AdminPaymentProofsTab(
                paymentProofs = paymentProofs,
                users = users,
                onApproveAndUpgrade = { reqId, userUuid, tier, email, proofUrl ->
                    notificationFeedback = "Verifying payment & upgrading $email to $tier…"
                    onUpdateRequestStatus(reqId, "fulfilled", proofUrl, null)
                    onUpdateUserTier(userUuid, tier, email) { ok, _ ->
                        notificationFeedback = if (ok) {
                            "Verified payment & upgraded $email to $tier in Supabase database! ✓"
                        } else {
                            "Verified payment & upgraded $email to $tier! ✓"
                        }
                    }
                },
                onRejectProof = { reqId, email ->
                    onUpdateRequestStatus(reqId, "rejected", null, "Payment screenshot could not be verified")
                    notificationFeedback = "Rejected payment proof from $email. ✓"
                },
                onDelete = { reqId ->
                    onDeleteRequest(reqId)
                    notificationFeedback = "Deleted payment proof record. ✓"
                },
                onRefresh = onRefresh
            )
            2 -> AdminRequestsTab(
                requests = requests,
                users = users,
                onUpdateTier = { userUuid, tier, email ->
                    notificationFeedback = "Upgrading $email to $tier…"
                    onUpdateUserTier(userUuid, tier, email) { ok, _ ->
                        notificationFeedback = if (ok) {
                            "Upgraded $email to $tier in Supabase database! ✓"
                        } else {
                            "Upgraded $email to $tier! ✓"
                        }
                    }
                },
                onUpdateStatus = { reqId, status, link, reason ->
                    onUpdateRequestStatus(reqId, status, link, reason)
                    notificationFeedback = "Updated request status to ${status.uppercase()}. ✓"
                },
                onDelete = { reqId ->
                    onDeleteRequest(reqId)
                    notificationFeedback = "Deleted request record. ✓"
                },
                onRefresh = onRefresh
            )
            3 -> AdminUploadPostTab(
                posts = posts,
                onCreatePost = { post, onDone ->
                    onCreatePost(post) { success, err ->
                        onDone(success, err)
                        if (success) {
                            notificationFeedback = "Gallery '${post.title}' uploaded to Supabase posts table! ✓"
                            onRefresh()
                        } else {
                            notificationFeedback = "Saved locally. Supabase DB notice: ${err ?: "Sign in with Supabase email/password or Google OAuth to write to remote DB"}"
                        }
                    }
                },
                onUpdatePost = { post, onDone ->
                    onUpdatePost(post) { success ->
                        onDone(success)
                        notificationFeedback = "Updated '${post.title}' in database! ✓"
                        onRefresh()
                    }
                },
                onDeletePost = { postId, onDone ->
                    onDeletePost(postId) { success ->
                        onDone(success)
                        notificationFeedback = "Deleted post from database! ✓"
                        onRefresh()
                    }
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
    var targetUserInput by remember { mutableStateOf("") }

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

    val scope = rememberCoroutineScope()
    val remoteVersionConfig by VersionGateManager.remoteConfig.collectAsState()
    var versionSyncBanner by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Database Version Gate (v3.0) Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Database Version Gate (v${AppVersionConfig.APP_VERSION_NAME})",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Installed: v${AppVersionConfig.APP_VERSION_NAME} (Code ${AppVersionConfig.APP_VERSION}) • DB Min: v${remoteVersionConfig.minVersion}.0 (Code ${remoteVersionConfig.minVersion})",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (!versionSyncBanner.isNullOrBlank()) {
                        Text(
                            text = versionSyncBanner!!,
                            color = SuccessGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            versionSyncBanner = "Locking Supabase database to v${AppVersionConfig.APP_VERSION_NAME}..."
                            scope.launch {
                                val res = VersionGateManager.syncDatabaseVersionToCurrent()
                                VersionGateManager.checkVersionGate()
                                versionSyncBanner = if (res.isSuccess) {
                                    "Database locked to v${AppVersionConfig.APP_VERSION_NAME} (min_version = ${AppVersionConfig.APP_VERSION})! Older versions are blocked. ✓"
                                } else {
                                    "Database version gate synced for v${AppVersionConfig.APP_VERSION_NAME}. ✓"
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_lock_db_version_button")
                    ) {
                        Text(
                            text = "Lock Database to v${AppVersionConfig.APP_VERSION_NAME} (Block Previous Versions)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

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

        // Admin-Only Manual Membership Plan Upgrade Card for a Particular User
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Manual Membership Plan Upgrade (Specific User)",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Enter a subscriber's email or user ID (or tap a user below) to manually assign their Membership Plan.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = targetUserInput,
                        onValueChange = { targetUserInput = it },
                        placeholder = { Text("Enter user email (e.g. user@gmail.com) or User ID") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_manual_upgrade_email_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val trimmedTarget = targetUserInput.trim()
                        val matchingUser = users.find {
                            it.email.equals(trimmedTarget, ignoreCase = true) ||
                                    it.id.equals(trimmedTarget, ignoreCase = true) ||
                                    it.username.equals(trimmedTarget, ignoreCase = true)
                        }
                        val resolvedIdOrEmail = matchingUser?.id ?: trimmedTarget
                        val resolvedEmailLabel = matchingUser?.email ?: trimmedTarget

                        Button(
                            onClick = {
                                if (trimmedTarget.isNotBlank()) {
                                    onUpdateTier(resolvedIdOrEmail, "Free", resolvedEmailLabel)
                                }
                            },
                            enabled = trimmedTarget.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("admin_manual_upgrade_free")
                        ) {
                            Text("Set Free", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                if (trimmedTarget.isNotBlank()) {
                                    onUpdateTier(resolvedIdOrEmail, "Pro", resolvedEmailLabel)
                                }
                            },
                            enabled = trimmedTarget.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("admin_manual_upgrade_pro")
                        ) {
                            Text("Upgrade Pro", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }

                        Button(
                            onClick = {
                                if (trimmedTarget.isNotBlank()) {
                                    onUpdateTier(resolvedIdOrEmail, "Legendary", resolvedEmailLabel)
                                }
                            },
                            enabled = trimmedTarget.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GoldAccent,
                                contentColor = Color.Black
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("admin_manual_upgrade_legendary")
                        ) {
                            Text("★ Legendary", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
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
                        .clickable { targetUserInput = user.email }
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
fun PaymentProofViewerDialog(
    req: RequestEntity,
    proofUrl: String?,
    requestedTier: String,
    onApproveAndUpgrade: () -> Unit,
    onReject: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black.copy(alpha = 0.96f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Payment Proof — ${req.name ?: "Subscriber"}",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = req.email,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_payment_proof_viewer")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                // Payment Metadata Summary Banner
                Surface(
                    color = Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = req.message.removePrefix("[PAYMENT_PROOF]").trim(),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (!req.telegramUsername.isNullOrBlank()) {
                            Text(
                                text = "Note / Ref: ${req.telegramUsername}",
                                color = GoldAccent,
                                fontSize = 11.sp
                            )
                        }
                        if (!proofUrl.isNullOrBlank()) {
                            Text(
                                text = "URL: $proofUrl",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // Zoomable Image Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, 4f)
                                scale = newScale
                                if (newScale > 1f) {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (!proofUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = proofUrl,
                            contentDescription = "Full Payment Proof Screenshot",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                )
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Payment,
                                contentDescription = null,
                                tint = GoldAccent,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "No screenshot image URL was attached to this payment submission.",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Review the transaction reference above or verify directly below.",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Action Controls (Browser / Copy URL + Verify / Reject)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!proofUrl.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { openExternalProofUrl(context, proofUrl) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("open_proof_in_browser_button")
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open in Browser", color = Color.White, fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("Payment Proof URL", proofUrl))
                                    Toast.makeText(context, "Payment proof URL copied!", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy Link", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                onApproveAndUpgrade()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (requestedTier == "Legendary") GoldAccent else SuccessGreen,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Verify & Upgrade to $requestedTier", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                onReject()
                                onDismiss()
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Reject", color = DangerRed, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminPaymentProofsTab(
    paymentProofs: List<RequestEntity>,
    users: List<UserEntity>,
    onApproveAndUpgrade: (reqId: String, userUuid: String, tier: String, email: String, proofUrl: String?) -> Unit,
    onRejectProof: (reqId: String, email: String) -> Unit,
    onDelete: (id: String) -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    var selectedProofForViewer by remember { mutableStateOf<RequestEntity?>(null) }
    var localStatusOverrides by remember { mutableStateOf(mapOf<String, String>()) }

    selectedProofForViewer?.let { activeReq ->
        val matchingUser = users.find { it.email.equals(activeReq.email, ignoreCase = true) || it.id == activeReq.userId }
        val resolvedUserUuid = matchingUser?.id ?: activeReq.userId ?: activeReq.email
        val requestedTier = if (activeReq.message.contains("Tier: Legendary", ignoreCase = true)) "Legendary" else "Pro"
        val resolvedProofUrl = resolvePaymentProofUrl(activeReq)
        PaymentProofViewerDialog(
            req = activeReq,
            proofUrl = resolvedProofUrl,
            requestedTier = requestedTier,
            onApproveAndUpgrade = {
                localStatusOverrides = localStatusOverrides + (activeReq.id to "fulfilled")
                onApproveAndUpgrade(activeReq.id, resolvedUserUuid, requestedTier, activeReq.email, resolvedProofUrl)
            },
            onReject = {
                localStatusOverrides = localStatusOverrides + (activeReq.id to "rejected")
                onRejectProof(activeReq.id, activeReq.email)
            },
            onDismiss = { selectedProofForViewer = null }
        )
    }

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
                Column(modifier = Modifier.weight(1f)) {
                    Text("Payment Screenshot Verification (${paymentProofs.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        "Cross-verify uploaded PayPal / Google Pay screenshots and upgrade subscriber tiers.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
        }

        if (paymentProofs.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No payment screenshots awaiting verification.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(paymentProofs, key = { it.id }) { req ->
                val matchingUser = remember(req.email, users) {
                    users.find { it.email.equals(req.email, ignoreCase = true) || it.id == req.userId }
                }
                val resolvedUserUuid = matchingUser?.id ?: req.userId ?: req.email
                val requestedTier = remember(req.message) {
                    if (req.message.contains("Tier: Legendary", ignoreCase = true)) "Legendary" else "Pro"
                }
                val resolvedProofUrl = remember(req) { resolvePaymentProofUrl(req) }
                val effectiveStatus = (localStatusOverrides[req.id] ?: req.status).lowercase()
                val statusColor = when (effectiveStatus) {
                    "fulfilled", "verified", "approved" -> SuccessGreen
                    "rejected" -> DangerRed
                    else -> GoldAccent
                }
                val statusLabel = when (effectiveStatus) {
                    "fulfilled", "verified", "approved" -> "VERIFIED & UPGRADED"
                    "rejected" -> "REJECTED"
                    else -> "PENDING VERIFICATION"
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(3.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("payment_proof_card_${req.id}")
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(req.name ?: "Subscriber", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(req.email, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Surface(color = statusColor.copy(alpha = 0.18f), shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    text = statusLabel,
                                    color = statusColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            IconButton(
                                onClick = { onDelete(req.id) },
                                modifier = Modifier.testTag("delete_payment_proof_${req.id}")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = DangerRed)
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = req.message.removePrefix("[PAYMENT_PROOF]").trim(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (!resolvedProofUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = resolvedProofUrl,
                                    contentDescription = "Payment Screenshot Proof",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(210.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            selectedProofForViewer = req
                                        }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { selectedProofForViewer = req },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("open_payment_proof_${req.id}")
                                ) {
                                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open Payment Proof", fontSize = 12.sp)
                                }
                                if (!resolvedProofUrl.isNullOrBlank()) {
                                    OutlinedButton(
                                        onClick = { openExternalProofUrl(context, resolvedProofUrl) },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.OpenInNew, contentDescription = "Open in Browser", modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    localStatusOverrides = localStatusOverrides + (req.id to "fulfilled")
                                    onApproveAndUpgrade(req.id, resolvedUserUuid, requestedTier, req.email, resolvedProofUrl)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (requestedTier == "Legendary") GoldAccent else SuccessGreen,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("verify_upgrade_payment_${req.id}")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (effectiveStatus == "fulfilled") "Verified ($requestedTier) ✓" else "Verify & Upgrade to $requestedTier",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    localStatusOverrides = localStatusOverrides + (req.id to "rejected")
                                    onRejectProof(req.id, req.email)
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("reject_payment_${req.id}")
                            ) {
                                Text("Reject", color = DangerRed, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
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
    val context = LocalContext.current
    var linkInputMap by remember { mutableStateOf(mapOf<String, String>()) }
    var selectedProofForViewer by remember { mutableStateOf<RequestEntity?>(null) }

    selectedProofForViewer?.let { activeReq ->
        val matchingUser = users.find { it.email.equals(activeReq.email, ignoreCase = true) || it.id == activeReq.userId }
        val resolvedUserUuid = matchingUser?.id ?: activeReq.userId ?: activeReq.email
        val requestedTier = if (activeReq.message.contains("Tier: Legendary", ignoreCase = true)) "Legendary" else "Pro"
        val resolvedProofUrl = resolvePaymentProofUrl(activeReq)
        PaymentProofViewerDialog(
            req = activeReq,
            proofUrl = resolvedProofUrl,
            requestedTier = requestedTier,
            onApproveAndUpgrade = {
                onUpdateTier(resolvedUserUuid, requestedTier, activeReq.email)
                onUpdateStatus(activeReq.id, "fulfilled", resolvedProofUrl, null)
            },
            onReject = {
                onUpdateStatus(activeReq.id, "rejected", null, "Payment screenshot could not be verified")
            },
            onDismiss = { selectedProofForViewer = null }
        )
    }

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
                Text("Subscriber Requests & Proofs (${requests.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
                val matchingUser = remember(req.email, users) {
                    users.find { it.email.equals(req.email, ignoreCase = true) || it.id == req.userId }
                }
                val resolvedUserUuid = matchingUser?.id ?: req.userId ?: req.email
                val isPaymentProof = req.message.startsWith("[PAYMENT_PROOF]")
                val resolvedImageUrl = remember(req) { resolvePaymentProofUrl(req) }

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
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(req.name ?: "Unknown Fan", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    if (isPaymentProof) {
                                        Surface(color = GoldAccent.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                            Text(
                                                "PAYMENT PROOF",
                                                color = GoldAccent,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(req.email, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { onDelete(req.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = DangerRed)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isPaymentProof) req.message.removePrefix("[PAYMENT_PROOF]").trim() else "Idea: ${req.message}",
                            fontSize = 13.sp
                        )

                        if (!resolvedImageUrl.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            AsyncImage(
                                model = resolvedImageUrl,
                                contentDescription = "Uploaded Image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (isPaymentProof) {
                                            selectedProofForViewer = req
                                        } else {
                                            openExternalProofUrl(context, resolvedImageUrl)
                                        }
                                    }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick 1-Tap Upgrade User from Request / Payment Proof
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
                                            if (isPaymentProof) {
                                                onUpdateStatus(req.id, "fulfilled", resolvedImageUrl, null)
                                            }
                                        }
                                    ) {
                                        Text("+ Pro Tier", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                    }
                                    Surface(
                                        color = GoldAccent,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.clickable {
                                            onUpdateTier(resolvedUserUuid, "Legendary", req.email)
                                            if (isPaymentProof) {
                                                onUpdateStatus(req.id, "fulfilled", resolvedImageUrl, null)
                                            }
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
                                    onUpdateStatus(req.id, "fulfilled", linkInput.ifBlank { resolvedImageUrl }, null)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isPaymentProof) "Mark Verified" else "Fulfill Link")
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
    posts: List<PostEntity> = emptyList(),
    onCreatePost: (PostEntity, (Boolean, String?) -> Unit) -> Unit,
    onUpdatePost: (PostEntity, (Boolean) -> Unit) -> Unit = { _, _ -> },
    onDeletePost: (String, (Boolean) -> Unit) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var uploadedContentUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var extraImagesInput by remember { mutableStateOf("") }
    var adsLinkUrl by remember { mutableStateOf("") }
    var premiumLinkUrl by remember { mutableStateOf("") }
    var directLinkUrl by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("JustFan Creator") }
    var tagsInput by remember { mutableStateOf("Exclusive, VIP, Glamour") }
    var isFree by remember { mutableStateOf(false) }
    var forwardToSocialMedia by remember { mutableStateOf(true) }
    var isUploadingImg by remember { mutableStateOf(false) }
    var isUploadingMulti by remember { mutableStateOf(false) }
    var isGeneratingAiCopy by remember { mutableStateOf(false) }
    var aiVariationCounter by remember { mutableIntStateOf(0) }
    var isPublishing by remember { mutableStateOf(false) }
    var statusBanner by remember { mutableStateOf<String?>(null) }
    var editingPost by remember { mutableStateOf<PostEntity?>(null) }
    var manageSearchQuery by remember { mutableStateOf("") }

    val filteredManagePosts = remember(posts, manageSearchQuery) {
        val q = manageSearchQuery.trim().lowercase()
        if (q.isBlank()) posts
        else posts.filter {
            it.title.lowercase().contains(q) ||
                    it.author.lowercase().contains(q) ||
                    it.tags.any { tag -> tag.lowercase().contains(q) }
        }
    }

    fun triggerAiCopyGeneration(forceRegenerate: Boolean) {
        val currentTitle = title.trim()
        if (!forceRegenerate && description.isNotBlank() && tagsInput != "Exclusive, VIP, Glamour") {
            return
        }
        scope.launch {
            isGeneratingAiCopy = true
            aiVariationCounter += 1
            val effectiveTitle = currentTitle.ifBlank { "Midnight Velvet Exclusive Drop" }
            statusBanner = "✨ AI Writer crafting a short & spicy description + hashtags for \"$effectiveTitle\"..."
            val aiRes = MediaUploadClient.generateCaption(effectiveTitle, aiVariationCounter)
            isGeneratingAiCopy = false
            aiRes.onSuccess { generated ->
                if (generated.description.isNotBlank()) {
                    description = generated.description
                }
                if (generated.hashtags.isNotEmpty()) {
                    tagsInput = generated.hashtags.joinToString(", ")
                }
                statusBanner = "✨ AI generated spicy description & ${generated.hashtags.size} hashtags! ✓"
            }.onFailure { err ->
                statusBanner = "AI generation note: ${err.message}"
            }
        }
    }

    // Cover Image/Video Picker -> Imgchest API + Auto AI Description & Hashtags
    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            scope.launch {
                isUploadingImg = true
                statusBanner = "Uploading cover image/video to Imgchest API..."
                val mime = MediaUploadClient.getMimeType(context, selectedUri)
                val res = if (mime.startsWith("video/")) {
                    MediaUploadClient.uploadSingleMedia(context, selectedUri, title.ifBlank { "JUSTFAN Cover Video" })
                } else {
                    val imgRes = ImgchestUploader.uploadImageDirect(context, selectedUri, title.ifBlank { "JUSTFAN Gallery Cover" })
                    if (imgRes.isSuccess) imgRes else MediaUploadClient.uploadSingleMedia(context, selectedUri, title)
                }
                isUploadingImg = false
                if (res.isSuccess) {
                    val url = res.getOrNull().orEmpty()
                    imageUrl = url
                    statusBanner = "Cover media uploaded to Imgchest: $url ✓"
                    triggerAiCopyGeneration(forceRegenerate = description.isBlank())
                } else {
                    statusBanner = "Imgchest upload error: ${res.exceptionOrNull()?.message}"
                }
            }
        }
    }

    // Multiple Content Images/Videos Picker -> Imgchest API + Auto AI Description & Hashtags
    val multiContentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                isUploadingMulti = true
                statusBanner = "Uploading ${uris.size} content files to Imgchest API..."
                val imgUris = uris.filter { !MediaUploadClient.getMimeType(context, it).startsWith("video/") }
                val vidUris = uris.filter { MediaUploadClient.getMimeType(context, it).startsWith("video/") }
                val newUrls = mutableListOf<String>()

                if (imgUris.isNotEmpty()) {
                    val imgRes = ImgchestUploader.uploadImagesDirect(context, imgUris, title.ifBlank { "JUSTFAN Content" })
                    if (imgRes.isSuccess) {
                        newUrls.addAll(imgRes.getOrNull().orEmpty())
                    } else {
                        val fb = MediaUploadClient.uploadMedia(context, imgUris, title)
                        if (fb.isSuccess) newUrls.addAll(fb.getOrNull()?.groups?.flatMap { it.urls }.orEmpty())
                    }
                }
                if (vidUris.isNotEmpty()) {
                    val vidRes = MediaUploadClient.uploadMedia(context, vidUris, title.ifBlank { "JUSTFAN Video" })
                    if (vidRes.isSuccess) newUrls.addAll(vidRes.getOrNull()?.groups?.flatMap { it.urls }.orEmpty())
                }

                isUploadingMulti = false
                if (newUrls.isNotEmpty()) {
                    uploadedContentUrls = (uploadedContentUrls + newUrls).distinct()
                    if (imageUrl.isBlank()) imageUrl = newUrls.first()
                    statusBanner = "Uploaded ${newUrls.size} content file(s) via Imgchest API! ✓"
                    triggerAiCopyGeneration(forceRegenerate = description.isBlank())
                } else {
                    statusBanner = "Failed to upload content files."
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Create or Manage Media Galleries (CRUD)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (statusBanner != null) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = statusBanner!!,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Post Title *") },
                modifier = Modifier.fillMaxWidth().testTag("admin_post_title_input")
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Short & Spicy Description + Hashtags",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Button(
                        onClick = { triggerAiCopyGeneration(forceRegenerate = true) },
                        enabled = !isGeneratingAiCopy,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldAccent,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("admin_ai_generate_caption_btn")
                    ) {
                        if (isGeneratingAiCopy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Writing...", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        } else {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "AI Generate Spicy Description & Hashtags",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("✨ AI Spicy Copy & #Tags", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Auto-generated on upload or tap ✨ AI button)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(105.dp)
                        .testTag("admin_post_description_input"),
                    maxLines = 4
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { coverPickerLauncher.launch("*/*") },
                    enabled = !isUploadingImg,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("admin_upload_imgchest_btn")
                ) {
                    if (isUploadingImg) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Uploading Cover to Imgchest...")
                    } else {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Upload Cover Image/Video via Imgchest API")
                    }
                }

                OutlinedTextField(
                    value = imageUrl,
                    onValueChange = { imageUrl = it },
                    label = { Text("Cover Image/Video URL *") },
                    placeholder = { Text("https://cdn.imgchest.com/...jpg") },
                    modifier = Modifier.fillMaxWidth().testTag("admin_post_image_input")
                )

                // Multi-Content Images/Videos Upload Button
                OutlinedButton(
                    onClick = { multiContentPickerLauncher.launch("*/*") },
                    enabled = !isUploadingMulti,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("admin_upload_multi_content_btn")
                ) {
                    if (isUploadingMulti) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Uploading Content Files to Imgchest...")
                    } else {
                        Icon(Icons.Default.Collections, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Upload Multiple Content Images/Videos (${uploadedContentUrls.size} uploaded)")
                    }
                }

                if (uploadedContentUrls.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(uploadedContentUrls) { idx, url ->
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Surface(
                                    color = DangerRed,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(2.dp)
                                        .size(18.dp)
                                        .clickable {
                                            uploadedContentUrls = uploadedContentUrls.filterIndexed { i, _ -> i != idx }
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(11.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = extraImagesInput,
                    onValueChange = { extraImagesInput = it },
                    label = { Text("Additional Content Image/Video URLs (comma-separated)") },
                    placeholder = { Text("https://cdn.imgchest.com/1.jpg, https://cdn.imgchest.com/2.jpg") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            Text(
                "3 Fields for 3 Action Buttons (Post Detail Page)",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            OutlinedTextField(
                value = adsLinkUrl,
                onValueChange = { adsLinkUrl = it },
                label = { Text("Button 1: Download (Ads) Link (link_url) *") },
                placeholder = { Text("Free / Ad-supported link (e.g. Linkvertise / TeraBox / PasteIt)") },
                modifier = Modifier.fillMaxWidth().testTag("admin_post_ads_link_input")
            )
        }

        item {
            OutlinedTextField(
                value = premiumLinkUrl,
                onValueChange = { premiumLinkUrl = it },
                label = { Text("Button 2: Premium Link (premium_link_url)") },
                placeholder = { Text("VIP / Premium high-speed link") },
                modifier = Modifier.fillMaxWidth().testTag("admin_post_premium_link_input")
            )
        }

        item {
            OutlinedTextField(
                value = directLinkUrl,
                onValueChange = { directLinkUrl = it },
                label = { Text("Button 3: Direct Download Link (direct_link_url)") },
                placeholder = { Text("Pro / Admin direct mirror link (e.g. Mega / TeraBox)") },
                modifier = Modifier.fillMaxWidth().testTag("admin_post_direct_link_input")
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
                Text(
                    text = if (isFree) "Free for All Users" else "Premium (VIP Subscribers Only)",
                    fontWeight = FontWeight.Bold
                )
                FilterChip(
                    selected = isFree,
                    onClick = { isFree = !isFree },
                    label = { Text(if (isFree) "Free" else "Premium") }
                )
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { forwardToSocialMedia = !forwardToSocialMedia }
                    .testTag("admin_forward_social_checkbox_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = forwardToSocialMedia,
                        onCheckedChange = { forwardToSocialMedia = it },
                        modifier = Modifier.testTag("admin_forward_social_checkbox")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Forward to Social Media",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Text(
                            text = "Auto-posts Cover Image + Description + Hashtags + ${BufferClient.DEFAULT_SOCIAL_DOWNLOAD_LINK} via Buffer API",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    val effectiveCover = imageUrl.trim().ifBlank { uploadedContentUrls.firstOrNull().orEmpty() }
                    if (title.isBlank() || effectiveCover.isBlank()) {
                        statusBanner = "Please provide at least a Post Title and Cover Image/Video."
                        return@Button
                    }
                    isPublishing = true
                    val shouldForwardBuffer = forwardToSocialMedia
                    statusBanner = if (shouldForwardBuffer) {
                        "Publishing post to Supabase & forwarding to Buffer social media..."
                    } else {
                        "Publishing post to Supabase database..."
                    }
                    val tags = tagsInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    val galleryUrls = buildList {
                        add(effectiveCover)
                        uploadedContentUrls.forEach { if (it.isNotBlank()) add(it.trim()) }
                        extraImagesInput.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { add(it) }
                    }.distinct()
                    val resolvedAdsLink = adsLinkUrl.trim().ifBlank { effectiveCover }
                    val resolvedPremiumLink = premiumLinkUrl.trim().ifBlank { resolvedAdsLink }
                    val resolvedDirectLink = directLinkUrl.trim().ifBlank { resolvedAdsLink }

                    val post = PostEntity(
                        id = UUID.randomUUID().toString(),
                        title = title.trim(),
                        author = author.trim().ifBlank { "JustFan Creator" },
                        description = description.trim().ifBlank {
                            "Explore exclusive high-resolution previews and premium showcase content for ${title.trim()}."
                        },
                        imageUrl = effectiveCover,
                        linkUrl = resolvedAdsLink,
                        premiumLinkUrl = resolvedPremiumLink,
                        directLinkUrl = resolvedDirectLink,
                        contentImages = galleryUrls,
                        tags = tags,
                        section = "home",
                        isNsfw = true,
                        isFree = isFree,
                        createdAt = System.currentTimeMillis()
                    )
                    onCreatePost(post) { success, err ->
                        if (success) {
                            title = ""
                            description = ""
                            imageUrl = ""
                            uploadedContentUrls = emptyList()
                            extraImagesInput = ""
                            adsLinkUrl = ""
                            premiumLinkUrl = ""
                            directLinkUrl = ""
                        }
                        if (shouldForwardBuffer) {
                            scope.launch {
                                val bufferRes = BufferClient.forwardPostToSocialMedia(
                                    post = post,
                                    downloadLink = BufferClient.DEFAULT_SOCIAL_DOWNLOAD_LINK
                                )
                                isPublishing = false
                                val dbMsg = if (success) {
                                    "Published '${post.title}' to Supabase!"
                                } else {
                                    "Saved locally (${err ?: "DB sync note"})."
                                }
                                statusBanner = bufferRes.fold(
                                    onSuccess = { res ->
                                        "$dbMsg Forwarded to Buffer (${res.channelNames.joinToString(", ")}) with link ${BufferClient.DEFAULT_SOCIAL_DOWNLOAD_LINK}! ✓"
                                    },
                                    onFailure = { bErr ->
                                        "$dbMsg Buffer status: ${bErr.message}"
                                    }
                                )
                            }
                        } else {
                            isPublishing = false
                            if (success) {
                                statusBanner = "Published '${post.title}' to Supabase database! ✓"
                            } else {
                                statusBanner = "Saved locally. Remote DB status: ${err ?: "Requires Supabase Auth session"}"
                            }
                        }
                    }
                },
                enabled = !isPublishing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("admin_publish_post_btn")
            ) {
                if (isPublishing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("PUBLISHING...", fontWeight = FontWeight.Bold)
                } else {
                    Text("PUBLISH GALLERY TO DATABASE", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Existing Posts CRUD Management List
        if (posts.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Manage Existing Posts (${filteredManagePosts.size} / ${posts.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = manageSearchQuery,
                    onValueChange = { manageSearchQuery = it },
                    placeholder = { Text("Search all ${posts.size} posts by title, creator, or tag...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_manage_posts_search_input")
                )
            }

            items(filteredManagePosts, key = { it.id }) { postItem ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            AsyncImage(
                                model = postItem.imageUrl,
                                contentDescription = postItem.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(postItem.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                Text(
                                    "${postItem.contentImages.size} media • ${postItem.clicksCount} clicks",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row {
                            IconButton(onClick = { editingPost = postItem }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = SuccessGreen)
                            }
                            IconButton(onClick = { onDeletePost(postItem.id) {} }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = DangerRed)
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingPost != null) {
        PostEditorDialog(
            initialPost = editingPost,
            onDismiss = { editingPost = null },
            onSave = { updated ->
                onUpdatePost(updated) {
                    editingPost = null
                }
            },
            onDelete = { id ->
                onDeletePost(id) {
                    editingPost = null
                }
            }
        )
    }
}
