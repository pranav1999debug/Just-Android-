package com.example.justfan.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import com.example.justfan.data.remote.BufferChannel
import com.example.justfan.data.remote.MediaUploadClient
import com.example.justfan.ui.components.SocialPostingBottomSheet
import com.example.justfan.ui.theme.GoldAccent
import com.example.justfan.ui.theme.SuccessGreen
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestScreen(
    userProfile: UserProfile,
    requestQuota: com.example.justfan.data.model.RequestQuota,
    requests: List<RequestEntity>,
    onSubmitRequest: (name: String, email: String, telegram: String?, message: String, imageUrl: String?, onResult: (Boolean, String?) -> Unit) -> Unit,
    onViewGallery: () -> Unit,
    onUpgradeClick: () -> Unit,
    isSyncing: Boolean = false,
    onSyncRequests: () -> Unit = {},
    onSignInRequired: () -> Unit = {},
    onUserMismatch: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        onSyncRequests()
    }

    var modelName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(userProfile.email) }
    var telegram by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var referenceImageUrl by remember { mutableStateOf("") }
    var selectedLocalUri by remember { mutableStateOf<Uri?>(null) }
    var isUploadingMedia by remember { mutableStateOf(false) }
    var uploadStatusText by remember { mutableStateOf("Uploading media...") }
    var uploadError by remember { mutableStateOf<String?>(null) }
    var submitErrorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var showSuccessSnackbar by remember { mutableStateOf(false) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }

    // Smart Media Routing & Social Posting State
    var pendingMediaUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showSocialPromptDialog by remember { mutableStateOf(false) }
    var showSocialPostingSheet by remember { mutableStateOf(false) }
    var bufferChannels by remember { mutableStateOf<List<BufferChannel>>(emptyList()) }
    var isLoadingChannels by remember { mutableStateOf(false) }
    var channelLoadError by remember { mutableStateOf<String?>(null) }
    var isGeneratingCaption by remember { mutableStateOf(false) }
    var isSubmittingSocial by remember { mutableStateOf(false) }

    fun loadChannels() {
        isLoadingChannels = true
        channelLoadError = null
        coroutineScope.launch {
            val res = MediaUploadClient.listBufferChannels()
            isLoadingChannels = false
            if (res.isSuccess) {
                bufferChannels = res.getOrNull().orEmpty()
            } else {
                channelLoadError = res.exceptionOrNull()?.message ?: "Failed to load channels"
            }
        }
    }

    fun executeUpload(
        uris: List<Uri>,
        title: String? = null,
        description: String? = null,
        hashtags: String? = null,
        channelId: String? = null,
        mode: String = "addToQueue",
        shareToSocial: Boolean = false
    ) {
        if (uris.isEmpty()) return
        isUploadingMedia = true
        uploadStatusText = if (shareToSocial) "Uploading & cross-posting to Buffer..." else "Uploading image..."
        uploadError = null

        coroutineScope.launch {
            val result = MediaUploadClient.uploadMedia(
                context = context,
                uris = uris,
                title = title ?: modelName.ifBlank { "JUSTFAN Request Reference" },
                description = description,
                hashtags = hashtags,
                channelId = channelId,
                mode = mode,
                shareToSocial = shareToSocial,
                userId = userProfile.id
            )
            isUploadingMedia = false
            isSubmittingSocial = false

            if (result.isSuccess) {
                val resp = result.getOrNull()
                val allUrls = resp?.groups?.flatMap { it.urls } ?: emptyList()
                if (allUrls.isNotEmpty()) {
                    referenceImageUrl = allUrls.first()
                }
                showSuccessSnackbar = true
            } else {
                uploadError = result.exceptionOrNull()?.message ?: "Upload failed"
            }
        }
    }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            pendingMediaUris = uris
            selectedLocalUri = uris.firstOrNull()
            // Prompt user: "Also post this to social media?"
            showSocialPromptDialog = true
        }
    }

    val isLegendary = userProfile.tier == "Legendary" || userProfile.isAdmin
    val isPro = userProfile.isProActive
    val hasUnlimited = isLegendary || isPro || requestQuota.unlimited
    val remainingRequests = if (hasUnlimited) 999 else requestQuota.remaining
    val resetsDateFormatted = remember(requestQuota.resetsAt) {
        val raw = requestQuota.resetsAt
        if (raw.isNullOrBlank()) {
            "end of month"
        } else {
            try {
                val inputFormats = listOf(
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX", Locale.US),
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US),
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
                    SimpleDateFormat("yyyy-MM-dd", Locale.US)
                )
                var parsedDate: Date? = null
                for (fmt in inputFormats) {
                    try {
                        parsedDate = fmt.parse(raw)
                        if (parsedDate != null) break
                    } catch (_: Exception) {
                    }
                }
                if (parsedDate != null) {
                    val outFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
                    outFormat.format(parsedDate)
                } else {
                    raw.substringBefore("T")
                }
            } catch (_: Exception) {
                raw.substringBefore("T")
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
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
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Tier: ${userProfile.tier}${if (userProfile.isAdmin) " (Admin)" else ""}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isLegendary) GoldAccent else if (isPro) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            if (hasUnlimited) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.Default.Verified, contentDescription = null, tint = if (isLegendary) GoldAccent else MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        }
                        Text(
                            text = when {
                                hasUnlimited -> "Unlimited"
                                else -> "${requestQuota.remaining} of ${requestQuota.limit} requests left this month"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!hasUnlimited) {
                            Text(
                                text = "Resets on $resetsDateFormatted",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!isLegendary) {
                        Button(
                            onClick = onUpgradeClick,
                            colors = ButtonDefaults.buttonColors(containerColor = if (isPro) GoldAccent else MaterialTheme.colorScheme.primary, contentColor = if (isPro) Color.Black else Color.White),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(if (isPro) "Upgrade Legendary" else "Upgrade", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Supabase Live Database Connection Banner
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(SuccessGreen)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Supabase Live Database",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = SuccessGreen.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "RequestfromApp TABLE",
                                        color = SuccessGreen,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "zlboyxbqppoimhhbvrax.supabase.co • ${requests.size} Requests Connected",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = onSyncRequests,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("btn_sync_requests_supabase")
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Syncing...", fontSize = 11.sp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sync",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sync", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                    text = { Text("Live Requests (${requests.size})", fontWeight = FontWeight.SemiBold) },
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

                    // Reference Photo: Gallery Picker with Imgchest API Upload
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.AddPhotoAlternate,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Media Upload & Routing (Imgchest / Catbox)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }

                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "Auto-Routing",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (selectedLocalUri != null || referenceImageUrl.isNotBlank()) {
                                    // Preview of chosen image
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(80.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.Black.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AsyncImage(
                                                model = if (referenceImageUrl.isNotBlank()) referenceImageUrl else selectedLocalUri,
                                                contentDescription = "Selected Reference",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            if (isUploadingMedia) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Color.Black.copy(alpha = 0.6f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(28.dp),
                                                        strokeWidth = 3.dp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            if (isUploadingMedia) {
                                                Text(
                                                    text = uploadStatusText,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "Uploading securely to cloud hosting...",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            } else if (referenceImageUrl.isNotBlank()) {
                                                val hostLabel = when {
                                                    referenceImageUrl.contains("imgchest") -> "Uploaded to Imgchest ✓"
                                                    referenceImageUrl.contains("uguu") -> "Uploaded securely ✓"
                                                    referenceImageUrl.contains("catbox") || referenceImageUrl.contains("litter") -> "Uploaded to Catbox ✓"
                                                    referenceImageUrl.contains("tmpfiles") -> "Uploaded securely ✓"
                                                    else -> "Uploaded ✓"
                                                }
                                                Surface(
                                                    color = SuccessGreen.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.CheckCircle,
                                                            contentDescription = null,
                                                            tint = SuccessGreen,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = hostLabel,
                                                            color = SuccessGreen,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = referenceImageUrl,
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(
                                                    onClick = {
                                                        mediaPickerLauncher.launch(
                                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                                        )
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text("Change", fontSize = 11.sp)
                                                }

                                                TextButton(
                                                    onClick = {
                                                        selectedLocalUri = null
                                                        referenceImageUrl = ""
                                                        pendingMediaUris = emptyList()
                                                        uploadError = null
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text("Remove", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Empty state: Upload button
                                    Button(
                                        onClick = {
                                            mediaPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                            )
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("btn_pick_imgchest_image")
                                    ) {
                                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Select Images or Videos to Upload", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }

                                if (uploadError != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = uploadError!!,
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 11.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = referenceImageUrl,
                                    onValueChange = { referenceImageUrl = it },
                                    label = { Text("Direct Link (Imgchest or Image URL)") },
                                    placeholder = { Text("https://cdn.imgchest.com/files/...") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .testTag("request_input_image")
                                        .fillMaxWidth()
                                )
                            }
                        }
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

                    if (submitErrorMessage != null) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = submitErrorMessage!!,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    if (!hasUnlimited && remainingRequests <= 0) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Monthly limit reached on this device or account. Resets on $resetsDateFormatted, or upgrade for unlimited.",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = onUpgradeClick,
                                        colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = Color.Black),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Upgrade for Unlimited", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        val isSubmitEnabled = modelName.isNotBlank() && notes.isNotBlank() && !isSubmitting && (hasUnlimited || remainingRequests > 0)
                        Button(
                            onClick = {
                                if (modelName.isNotBlank() && email.isNotBlank() && notes.isNotBlank()) {
                                    isSubmitting = true
                                    submitErrorMessage = null
                                    onSubmitRequest(
                                        modelName.trim(),
                                        email.trim(),
                                        telegram.trim().ifBlank { null },
                                        notes.trim(),
                                        referenceImageUrl.trim().ifBlank { null }
                                    ) { success, errorText ->
                                        isSubmitting = false
                                        if (success) {
                                            modelName = ""
                                            notes = ""
                                            referenceImageUrl = ""
                                            showSuccessSnackbar = true
                                        } else {
                                            when (errorText) {
                                                "SIGN_IN_REQUIRED" -> {
                                                    onSignInRequired()
                                                }
                                                "DEVICE_REQUIRED" -> {
                                                    submitErrorMessage = "Device check failed, update the app"
                                                }
                                                "LIMIT_REACHED" -> {
                                                    onSyncRequests()
                                                    submitErrorMessage = "Monthly limit reached on this device or account. Resets on $resetsDateFormatted, or upgrade for unlimited."
                                                }
                                                "USER_MISMATCH" -> {
                                                    onUserMismatch()
                                                }
                                                else -> {
                                                    submitErrorMessage = errorText ?: "Failed to submit request"
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = isSubmitEnabled,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .testTag("submit_request_button")
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Submitting...")
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (hasUnlimited || remainingRequests > 0) "Submit to Supabase Database" else "Monthly limit reached. Resets on $resetsDateFormatted or Upgrade for unlimited",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            } else {
                // Live Supabase Requests List
                if (requests.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inbox,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No requests found yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Pull live records directly from the Supabase requests table or submit a new request.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = onSyncRequests) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Sync from Supabase")
                                }
                                OutlinedButton(onClick = { selectedTab = 0 }) {
                                    Text("Submit New Request")
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Connected Supabase Records (${requests.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                TextButton(onClick = onSyncRequests) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Refresh", fontSize = 12.sp)
                                }
                            }
                        }

                        items(requests, key = { it.id }) { req ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier
                                    .testTag("request_item_${req.id}")
                                    .fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = req.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (req.email.isNotBlank()) {
                                                Text(
                                                    text = "By: ${req.email}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }

                                        Surface(
                                            color = when (req.status.lowercase()) {
                                                "delivered" -> SuccessGreen.copy(alpha = 0.2f)
                                                "in_progress" -> GoldAccent.copy(alpha = 0.2f)
                                                "rejected" -> MaterialTheme.colorScheme.errorContainer
                                                else -> MaterialTheme.colorScheme.primaryContainer
                                            },
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = req.status.replace("_", " ").uppercase(),
                                                color = when (req.status.lowercase()) {
                                                    "delivered" -> SuccessGreen
                                                    "in_progress" -> GoldAccent
                                                    "rejected" -> MaterialTheme.colorScheme.error
                                                    else -> MaterialTheme.colorScheme.primary
                                                },
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    if (!req.message.isBlank()) {
                                        Text(
                                            text = req.message,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }

                                    // Imgchest Reference Image Preview
                                    if (!req.imageUrl.isNullOrBlank()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.Black.copy(alpha = 0.15f))
                                                .clickable { previewImageUrl = req.imageUrl }
                                                .padding(6.dp)
                                        ) {
                                            AsyncImage(
                                                model = req.imageUrl,
                                                contentDescription = "Imgchest Reference",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(54.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Imgchest Photo Reference", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                }
                                                Text(
                                                    text = req.imageUrl,
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    maxLines = 1
                                                )
                                            }
                                            Icon(Icons.Default.ZoomIn, contentDescription = "View Photo", modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }

                                    // Footer with date and actions
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val dateStr = remember(req.createdAt) {
                                            try {
                                                SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(req.createdAt))
                                            } catch (_: Exception) {
                                                ""
                                            }
                                        }
                                        Text(
                                            text = if (dateStr.isNotBlank()) dateStr else "ID: ${req.id.take(8)}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )

                                        if (req.status == "delivered" && !req.downloadLink.isNullOrEmpty()) {
                                            Button(
                                                onClick = {
                                                    try {
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(req.downloadLink)))
                                                    } catch (_: Exception) {}
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("View Delivery", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

    // Image lightbox preview dialog
    if (previewImageUrl != null) {
        AlertDialog(
            onDismissRequest = { previewImageUrl = null },
            title = { Text("Reference Image", fontWeight = FontWeight.Bold) },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    AsyncImage(
                        model = previewImageUrl,
                        contentDescription = "Full Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { previewImageUrl = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Social Posting Confirmation Prompt Dialog
    if (showSocialPromptDialog) {
        AlertDialog(
            onDismissRequest = {
                showSocialPromptDialog = false
                pendingMediaUris = emptyList()
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Also post this to social media?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Would you like to cross-post this media to your connected Buffer social accounts with an optional AI-generated caption?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSocialPromptDialog = false
                        showSocialPostingSheet = true
                        loadChannels()
                    },
                    modifier = Modifier.testTag("prompt_social_yes")
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showSocialPromptDialog = false
                        executeUpload(pendingMediaUris, shareToSocial = false)
                    },
                    modifier = Modifier.testTag("prompt_social_no")
                ) {
                    Text("No, just upload")
                }
            }
        )
    }

    // Social Posting Bottom Sheet
    if (showSocialPostingSheet) {
        SocialPostingBottomSheet(
            channels = bufferChannels,
            isLoadingChannels = isLoadingChannels,
            channelLoadError = channelLoadError,
            onReloadChannels = { loadChannels() },
            onDismissRequest = {
                showSocialPostingSheet = false
                // If user dismisses sheet, still complete upload without social posting
                if (!isUploadingMedia && referenceImageUrl.isBlank()) {
                    executeUpload(pendingMediaUris, shareToSocial = false)
                }
            },
            onGenerateCaption = { captionTitle, onResult ->
                isGeneratingCaption = true
                coroutineScope.launch {
                    val capRes = MediaUploadClient.generateCaption(captionTitle)
                    isGeneratingCaption = false
                    if (capRes.isSuccess) {
                        val cap = capRes.getOrNull()
                        if (cap != null) {
                            onResult(cap.description, cap.hashtags.joinToString(" "))
                        }
                    } else {
                        uploadError = capRes.exceptionOrNull()?.message ?: "Failed to generate caption"
                    }
                }
            },
            isGeneratingCaption = isGeneratingCaption,
            onSubmit = { postTitle, postDesc, postTags, postChannelId, postMode ->
                isSubmittingSocial = true
                showSocialPostingSheet = false
                executeUpload(
                    uris = pendingMediaUris,
                    title = postTitle,
                    description = postDesc,
                    hashtags = postTags,
                    channelId = postChannelId,
                    mode = postMode,
                    shareToSocial = true
                )
            },
            isSubmitting = isSubmittingSocial
        )
    }
}
