package com.example.justfan.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.justfan.data.model.RequestEntity
import com.example.justfan.data.model.UserProfile
import com.example.justfan.ui.theme.GoldAccent
import com.example.justfan.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestScreen(
    userProfile: UserProfile,
    requests: List<RequestEntity>,
    onSubmitRequest: (name: String, email: String, telegram: String?, message: String, imageUrl: String?) -> Unit,
    onViewGallery: () -> Unit,
    onUpgradeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    var modelName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(userProfile.email) }
    var telegram by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var referenceImageUrl by remember { mutableStateOf("") }
    var showSuccessSnackbar by remember { mutableStateOf(false) }

    val isPro = userProfile.plan == "Pro" || userProfile.plan == "Legendary" || userProfile.isAdmin
    val remainingRequests = if (isPro) 999 else (3 - userProfile.weeklyRequestsUsed).coerceAtLeast(0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Content Request Hub", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    TextButton(onClick = onViewGallery) {
                        Text("Gallery Archive →", color = MaterialTheme.colorScheme.primary)
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
            // Subscription status bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Plan: ${userProfile.plan}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isPro) GoldAccent else MaterialTheme.colorScheme.onSurface
                            )
                            if (isPro) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.Default.Verified, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(16.dp))
                            }
                        }
                        Text(
                            text = if (isPro) "Unlimited Weekly Requests" else "$remainingRequests of 3 requests remaining this week",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!isPro) {
                        Button(
                            onClick = onUpgradeClick,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("Upgrade", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Submit Request", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("tab_submit_request")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("My Requests (${requests.size})", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("tab_my_requests")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (selectedTab == 0) {
                // Submit Request Form
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        OutlinedTextField(
                            value = modelName,
                            onValueChange = { modelName = it },
                            label = { Text("Creator / Model Name or Social Handle *") },
                            placeholder = { Text("e.g. Aria Nova or @arianova") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .testTag("request_input_name")
                                .fillMaxWidth()
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Notification Email *") },
                            placeholder = { Text("your@email.com") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .testTag("request_input_email")
                                .fillMaxWidth()
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = telegram,
                            onValueChange = { telegram = it },
                            label = { Text("Telegram Username (Optional)") },
                            placeholder = { Text("@yourtelegram") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .testTag("request_input_telegram")
                                .fillMaxWidth()
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = referenceImageUrl,
                            onValueChange = { referenceImageUrl = it },
                            label = { Text("Reference Photo Image URL (Optional)") },
                            placeholder = { Text("https://example.com/photo.jpg") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .testTag("request_input_image")
                                .fillMaxWidth()
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Request Details / Outfit / Theme *") },
                            placeholder = { Text("Describe specific shoot, theme, outfit or set requested…") },
                            minLines = 3,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .testTag("request_input_notes")
                                .fillMaxWidth()
                        )
                    }

                    if (showSuccessSnackbar) {
                        item {
                            Surface(
                                color = SuccessGreen.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Your request was successfully submitted to the team!",
                                        color = SuccessGreen,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                if (modelName.isNotBlank() && email.isNotBlank() && notes.isNotBlank()) {
                                    onSubmitRequest(
                                        modelName.trim(),
                                        email.trim(),
                                        telegram.trim().ifBlank { null },
                                        notes.trim(),
                                        referenceImageUrl.trim().ifBlank { null }
                                    )
                                    modelName = ""
                                    notes = ""
                                    referenceImageUrl = ""
                                    showSuccessSnackbar = true
                                }
                            },
                            enabled = modelName.isNotBlank() && notes.isNotBlank() && (isPro || remainingRequests > 0),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .testTag("submit_request_button")
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Submit Request Now", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // My Requests List
                if (requests.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("You haven't submitted any requests yet.")
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(requests, key = { it.id }) { req ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier
                                    .testTag("request_item_${req.id}")
                                    .fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!req.imageUrl.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = req.imageUrl,
                                            contentDescription = req.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(60.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = req.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = req.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Surface(
                                            color = when (req.status) {
                                                "delivered" -> SuccessGreen.copy(alpha = 0.2f)
                                                "in_progress" -> GoldAccent.copy(alpha = 0.2f)
                                                else -> MaterialTheme.colorScheme.primaryContainer
                                            },
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = req.status.replace("_", " ").uppercase(),
                                                color = when (req.status) {
                                                    "delivered" -> SuccessGreen
                                                    "in_progress" -> GoldAccent
                                                    else -> MaterialTheme.colorScheme.primary
                                                },
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    if (req.status == "delivered" && !req.downloadLink.isNullOrEmpty()) {
                                        IconButton(onClick = {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(req.downloadLink)))
                                        }) {
                                            Icon(Icons.Default.Download, contentDescription = "Download", tint = SuccessGreen)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
