package com.example.justfan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.justfan.data.remote.BufferChannel
import com.example.justfan.ui.theme.GoldAccent
import com.example.justfan.ui.theme.ThemeEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialPostingBottomSheet(
    channels: List<BufferChannel>,
    isLoadingChannels: Boolean,
    channelLoadError: String?,
    onReloadChannels: () -> Unit,
    onDismissRequest: () -> Unit,
    onGenerateCaption: (title: String, onResult: (String, String) -> Unit) -> Unit,
    isGeneratingCaption: Boolean,
    onSubmit: (
        title: String,
        description: String,
        hashtags: String,
        selectedChannelId: String,
        mode: String
    ) -> Unit,
    isSubmitting: Boolean
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var hashtags by remember { mutableStateOf("") }
    var selectedChannelId by remember {
        mutableStateOf(channels.firstOrNull()?.id ?: "")
    }
    var selectedMode by remember { mutableStateOf("addToQueue") } // addToQueue, shareNow, schedule
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var channelDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(channels) {
        if (selectedChannelId.isEmpty() && channels.isNotEmpty()) {
            selectedChannelId = channels.first().id
        }
    }

    val selectedChannel = channels.find { it.id == selectedChannelId }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.testTag("social_posting_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = GoldAccent.copy(alpha = 0.2f),
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Social Media Post",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Publish to Buffer connected channels",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Error banner if any
            if (!errorMessage.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Title Field (Required)
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    errorMessage = null
                },
                label = { Text("Post Title *") },
                placeholder = { Text("e.g., Exclusive New Photo Set") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("social_title_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Groq Generate Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                FilledTonalButton(
                    onClick = {
                        if (title.isBlank()) {
                            errorMessage = "Please enter a title first to generate description with Groq"
                        } else {
                            errorMessage = null
                            onGenerateCaption(title) { genDesc, genTags ->
                                description = genDesc
                                hashtags = genTags
                            }
                        }
                    },
                    enabled = !isGeneratingCaption && title.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("groq_generate_button")
                ) {
                    if (isGeneratingCaption) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generating with Groq...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = GoldAccent
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Generate with Groq", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Description Field (Multiline)
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                placeholder = { Text("Engaging caption describing your content...") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("social_description_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Hashtags Field
            OutlinedTextField(
                value = hashtags,
                onValueChange = { hashtags = it },
                label = { Text("Hashtags") },
                placeholder = { Text("#JustFan #Exclusive #Photography") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("social_hashtags_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Buffer Channel Picker
            Text(
                text = "Target Social Channel",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            if (isLoadingChannels) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Loading Buffer channels...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (!channelLoadError.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = channelLoadError,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onReloadChannels) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            } else if (channels.isEmpty()) {
                Text(
                    text = "No connected Buffer channels found. Check your buffer_api_key in Supabase app_secrets.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ExposedDropdownMenuBox(
                    expanded = channelDropdownExpanded,
                    onExpandedChange = { channelDropdownExpanded = !channelDropdownExpanded }
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = selectedChannel?.let { "${it.displayName} (${it.service})" } ?: "Select Channel",
                        onValueChange = {},
                        label = { Text("Buffer Channel") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = channelDropdownExpanded) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .testTag("social_channel_picker")
                    )
                    ExposedDropdownMenu(
                        expanded = channelDropdownExpanded,
                        onDismissRequest = { channelDropdownExpanded = false }
                    ) {
                        channels.forEach { ch ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.padding(end = 8.dp)
                                        ) {
                                            Text(
                                                text = ch.service.uppercase(),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(ch.displayName, fontWeight = FontWeight.SemiBold)
                                    }
                                },
                                onClick = {
                                    selectedChannelId = ch.id
                                    channelDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mode Selector: Add to queue / Share now / Schedule
            Text(
                text = "Posting Mode",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "addToQueue" to "Add to Queue",
                    "shareNow" to "Share Now",
                    "schedule" to "Schedule"
                ).forEach { (modeKey, modeLabel) ->
                    val isSelected = selectedMode == modeKey
                    Surface(
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedMode = modeKey }
                            .testTag("social_mode_$modeKey")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                        ) {
                            Text(
                                text = modeLabel,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        if (title.isBlank()) {
                            errorMessage = "Please enter a Title for your post"
                        } else if (channels.isNotEmpty() && selectedChannelId.isBlank()) {
                            errorMessage = "Please select a Buffer channel"
                        } else {
                            errorMessage = null
                            onSubmit(title, description, hashtags, selectedChannelId, selectedMode)
                        }
                    },
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .weight(1.5f)
                        .testTag("submit_social_upload_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ThemeEmerald)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Posting...", color = Color.White)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Upload & Post", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
