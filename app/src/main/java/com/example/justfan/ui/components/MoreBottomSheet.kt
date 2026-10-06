package com.example.justfan.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreBottomSheet(
    onDismissRequest: () -> Unit,
    onRequestClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onMembershipClick: () -> Unit,
    onActivityClick: () -> Unit,
    onAdminClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Color(0xFF0F172A), // Dark slate matching user mockup
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .navigationBarsPadding()
        ) {
            // Header: "More" on left, Close "X" button on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "More",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 20.sp
                )
                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("btn_close_more_sheet")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 1. Request
            MoreMenuItem(
                icon = Icons.AutoMirrored.Filled.Send,
                title = "Request",
                testTag = "more_item_request",
                onClick = onRequestClick
            )

            // 2. Favorites
            MoreMenuItem(
                icon = Icons.Default.FavoriteBorder,
                title = "Favorites",
                testTag = "more_item_favorites",
                onClick = onFavoritesClick
            )

            // 3. Gallery
            MoreMenuItem(
                icon = Icons.Default.Collections,
                title = "Gallery",
                testTag = "more_item_gallery",
                onClick = onGalleryClick
            )

            // 4. Membership
            MoreMenuItem(
                icon = Icons.Default.WorkspacePremium,
                title = "Membership",
                testTag = "more_item_membership",
                onClick = onMembershipClick
            )

            // 5. Activity
            MoreMenuItem(
                icon = Icons.Default.NotificationsNone,
                title = "Activity",
                testTag = "more_item_activity",
                onClick = onActivityClick
            )

            // 6. Admin
            MoreMenuItem(
                icon = Icons.Default.Shield,
                title = "Admin",
                testTag = "more_item_admin",
                onClick = onAdminClick
            )

            // 7. Settings
            MoreMenuItem(
                icon = Icons.Default.Settings,
                title = "Settings",
                testTag = "more_item_settings",
                onClick = onSettingsClick
            )

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun MoreMenuItem(
    icon: ImageVector,
    title: String,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 24.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = Color(0xFF38BDF8), // Cyan matching screenshot
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(18.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
