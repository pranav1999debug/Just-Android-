package com.example.justfan.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.justfan.data.model.RequestEntity
import com.example.justfan.data.model.RequestQuota
import com.example.justfan.data.model.UserProfile
import com.example.justfan.data.remote.ImgchestUploader
import com.example.justfan.ui.theme.DangerRed
import com.example.justfan.ui.theme.GoldAccent
import com.example.justfan.ui.theme.SuccessGreen
import kotlinx.coroutines.launch

@Composable
fun RequestScreen(
    userProfile: UserProfile,
    quota: RequestQuota,
    requests: List<RequestEntity>,
    initialIdea: String = "",
    onSubmitRequest: (name: String, email: String, telegram: String?, message: String, image: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var nameInput by remember(userProfile) { mutableStateOf(userProfile.username.ifBlank { "" }) }
    var emailInput by remember(userProfile) { mutableStateOf(userProfile.email.ifBlank { "" }) }
    var telegramInput by remember { mutableStateOf("") }
    var messageInput by remember(initialIdea) { mutableStateOf(initialIdea) }
    var imageUrlInput by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                isUploading = true
                statusMessage = "Uploading image to Imgchest..."
                val result = ImgchestUploader.uploadImageDirect(context, it, "JUSTFAN Request Reference")
                if (result.isSuccess) {
                    imageUrlInput = result.getOrNull() ?: ""
                    statusMessage = "Image uploaded successfully! ✓"
                } else {
                    statusMessage = "Upload failed: ${result.exceptionOrNull()?.message}"
                }
                isUploading = false
            }
        }
    }

    val isPaidOrLegendary = userProfile.tier.equals("legendary", ignoreCase = true) ||
            userProfile.tier.equals("pro", ignoreCase = true) || userProfile.isAdmin

    val myRequests = remember(requests, userProfile.email) {
        if (userProfile.email.isNotBlank()) {
            requests.filter { it.email.equals(userProfile.email, ignoreCase = true) }
        } else {
            requests.take(5)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Quota / Unlimited Status Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isPaidOrLegendary) Color(0xFF2A2208) else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("request_quota_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isPaidOrLegendary) Icons.Default.AllInclusive else Icons.Default.Star,
                                contentDescription = null,
                                tint = if (isPaidOrLegendary) GoldAccent else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isPaidOrLegendary) "UNLIMITED REQUESTS" else "REQUEST QUOTA",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = if (isPaidOrLegendary) GoldAccent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            color = if (isPaidOrLegendary) GoldAccent else MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isPaidOrLegendary) "LEGENDARY TIER" else "${quota.remaining} LEFT",
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isPaidOrLegendary) {
                            "As a Legendary subscriber, your requests are processed with top priority with no monthly limits."
                        } else {
                            "Free tier includes 3 requests per month. Upgrade to Legendary or Pro for unlimited requests and direct media downloads."
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // New Request Form Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Submit a Custom Content Request",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Your Name or Alias") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Email (for receiving fulfillment)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = telegramInput,
                        onValueChange = { telegramInput = it },
                        label = { Text("Telegram Username (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = messageInput,
                        onValueChange = { messageInput = it },
                        label = { Text("Request Details (Shoot, theme, model, outfit…)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .testTag("request_message_input"),
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = imageUrlInput,
                        onValueChange = { imageUrlInput = it },
                        label = { Text("Reference Photo URL (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            if (isUploading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                IconButton(onClick = { photoPickerLauncher.launch("image/*") }) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = "Upload Image")
                                }
                            }
                        }
                    )

                    if (imageUrlInput.isBlank() && !isUploading) {
                        Text(
                            text = "Or upload a photo directly to get a URL",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (emailInput.isNotBlank() && messageInput.isNotBlank()) {
                                onSubmitRequest(
                                    nameInput.ifBlank { "Fan Member" },
                                    emailInput.trim(),
                                    telegramInput.ifBlank { null },
                                    messageInput.trim(),
                                    imageUrlInput.ifBlank { null }
                                )
                                statusMessage = "Request submitted successfully! Admin will review it shortly. ✓"
                                messageInput = ""
                                imageUrlInput = ""
                            } else {
                                statusMessage = "Please enter an email and request details."
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPaidOrLegendary) GoldAccent else MaterialTheme.colorScheme.primary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("submit_request_button")
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SUBMIT REQUEST", fontWeight = FontWeight.Bold)
                    }

                    if (statusMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = statusMessage!!,
                            color = if (statusMessage!!.contains("✓")) SuccessGreen else DangerRed,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Past Requests Section
        item {
            Text(
                text = "Recent Community & Personal Requests (${myRequests.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (myRequests.isEmpty()) {
            item {
                Text(
                    text = "No previous requests logged yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        } else {
            items(myRequests, key = { it.id }) { req ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = req.name ?: req.email.substringBefore("@"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )

                            val (statusColor, statusIcon) = when (req.status.lowercase()) {
                                "approved", "fulfilled" -> Pair(SuccessGreen, Icons.Default.CheckCircle)
                                "rejected" -> Pair(DangerRed, Icons.Default.Warning)
                                else -> Pair(GoldAccent, Icons.Default.HourglassEmpty)
                            }

                            Surface(color = statusColor.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(req.status.uppercase(), color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = req.message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)

                        if (!req.downloadLink.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(req.downloadLink.trim()))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("DOWNLOAD FULFILLED MEDIA", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (!req.rejectionReason.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Admin Note: ${req.rejectionReason}",
                                fontSize = 11.sp,
                                color = DangerRed
                            )
                        }
                    }
                }
            }
        }
    }
}
