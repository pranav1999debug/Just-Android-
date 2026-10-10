package com.example.justfan.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.justfan.data.model.UserProfile
import com.example.justfan.ui.theme.GoldAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopHeader(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onNotificationsClick: () -> Unit,
    onAdminClick: () -> Unit,
    onPricingClick: () -> Unit,
    unreadNotificationsCount: Int = 0,
    isSyncing: Boolean = false,
    onSyncClick: () -> Unit = {},
    userProfile: UserProfile? = null,
    onAuthClick: () -> Unit = {},
    hasCustomWallpaper: Boolean = false,
    modifier: Modifier = Modifier
) {
    var searchVisible by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .statusBarsPadding()
            .background(if (hasCustomWallpaper) Color.Black.copy(alpha = 0.55f) else MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Brand Logo & Subtitle
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "JUSTFAN",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.1.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = if (userProfile?.tier == "Legendary") GoldAccent.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        val isRealAdmin = userProfile?.isAdmin == true && userProfile.email.equals("reytherapper12@gmail.com", ignoreCase = true)
                        Text(
                            text = if (isRealAdmin) "ADMIN" else (userProfile?.tier?.uppercase() ?: "VIP"),
                            color = if (userProfile?.tier == "Legendary") GoldAccent else MaterialTheme.colorScheme.primary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "Trending Creator Gallery",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            // Right Action Icons (Notifications & Account Pill)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Notifications Bell with Badge
                Box {
                    IconButton(
                        onClick = onNotificationsClick,
                        modifier = Modifier.size(36.dp).testTag("header_notifications_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (unreadNotificationsCount > 0) {
                        Badge(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 2.dp, end = 2.dp),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text(text = "$unreadNotificationsCount", fontSize = 9.sp)
                        }
                    }
                }

                // 2. Account / Tier Avatar Pill
                Surface(
                    color = when (userProfile?.tier) {
                        "Legendary" -> GoldAccent
                        "Pro" -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .clickable { onAuthClick() }
                        .testTag("header_auth_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Account",
                            tint = if (userProfile?.tier == "Legendary") Color.Black else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (userProfile?.isSignedIn == true) userProfile.tier.uppercase() else "SIGN IN",
                            color = if (userProfile?.tier == "Legendary") Color.Black else MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 2nd Line: Full-Width Search Bar to free up header space
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search creator, tags, keywords…", fontSize = 13.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ),
            modifier = Modifier
                .testTag("search_text_input")
                .fillMaxWidth()
                .height(48.dp)
        )
    }
}
