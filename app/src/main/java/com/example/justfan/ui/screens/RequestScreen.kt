package com.example.justfan.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import com.example.justfan.data.model.RequestEntity
import com.example.justfan.data.model.RequestQuota
import com.example.justfan.data.model.UserProfile
import com.example.justfan.data.remote.ImgchestUploader
import com.example.justfan.data.remote.MediaUploadClient
import com.example.justfan.ui.theme.DangerRed
import com.example.justfan.ui.theme.GoldAccent
import com.example.justfan.ui.theme.SuccessGreen
import com.example.justfan.util.BiometricAuthManager
import com.example.justfan.util.DeviceBiometricInfo
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RequestScreen(
    userProfile: UserProfile,
    quota: RequestQuota,
    requests: List<RequestEntity>,
    deviceBiometricInfo: DeviceBiometricInfo? = null,
    initialIdea: String = "",
    onSubmitRequest: (
        name: String,
        email: String,
        telegram: String?,
        message: String,
        imageUrl: String?,
        onResult: (Result<Boolean>) -> Unit
    ) -> Unit,
    onBiometricVerified: (DeviceBiometricInfo) -> Unit = {},
    onRequestSignIn: () -> Unit = {},
    onNavigateToPricing: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()

    var name by remember(userProfile) {
        mutableStateOf(if (userProfile.isSignedIn) userProfile.username else "")
    }
    var email by remember(userProfile) {
        mutableStateOf(if (userProfile.isSignedIn) userProfile.email else "")
    }
    var referenceLink by remember { mutableStateOf("") }
    var message by remember(initialIdea) { mutableStateOf(initialIdea) }

    // Automatically retrieved Imgchest URL from gallery upload (no manual URL input allowed)
    var uploadedGalleryImgchestUrl by remember { mutableStateOf("") }
    var isUploadingImage by remember { mutableStateOf(false) }

    var isSubmitting by remember { mutableStateOf(false) }
    var submitSuccess by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isProActive = userProfile.isProActive
    val isLegendary = userProfile.tier.equals("legendary", ignoreCase = true) || userProfile.isAdmin
    val hasUnlimited = isLegendary || isProActive || quota.unlimited
    val monthlyUsed = quota.used
    val monthlyLimit = 3
    val canSubmit = hasUnlimited || monthlyUsed < monthlyLimit

    // Personal requests made by this particular user
    val personalRequests = remember(requests, userProfile) {
        if (!userProfile.isSignedIn && email.isBlank()) {
            emptyList()
        } else {
            requests.filter { req ->
                (userProfile.isSignedIn && req.userId == userProfile.id) ||
                        (userProfile.email.isNotBlank() && req.email.equals(userProfile.email, ignoreCase = true)) ||
                        (email.isNotBlank() && req.email.equals(email.trim(), ignoreCase = true))
            }.sortedByDescending { it.createdAt }
        }
    }

    // Community requests (all requests across the community, prioritizing fulfilled/with images)
    val communityRequests = remember(requests) {
        requests.sortedWith(
            compareByDescending<RequestEntity> {
                it.status.equals("fulfilled", ignoreCase = true) ||
                        it.status.equals("delivered", ignoreCase = true) ||
                        it.status.equals("replied", ignoreCase = true)
            }.thenByDescending { !it.imageUrl.isNullOrBlank() }
                .thenByDescending { it.createdAt }
        )
    }

    // Gallery Image Picker -> Automatically uploads to Imgchest API and retrieves URL
    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            scope.launch {
                isUploadingImage = true
                errorMessage = null
                val res = ImgchestUploader.uploadImageDirect(
                    context = context,
                    imageUri = selectedUri,
                    title = "JUSTFAN Request Image - ${name.ifBlank { "User" }}"
                )
                val finalRes = if (res.isSuccess) {
                    res
                } else {
                    MediaUploadClient.uploadSingleMedia(
                        context = context,
                        uri = selectedUri,
                        title = "JUSTFAN Request Image"
                    )
                }
                isUploadingImage = false
                if (finalRes.isSuccess) {
                    uploadedGalleryImgchestUrl = finalRes.getOrNull().orEmpty()
                } else {
                    errorMessage = "Imgchest upload failed: ${finalRes.exceptionOrNull()?.message}"
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
    ) {
        // Page Header
        item {
            Column {
                Text(
                    text = "Content Requests",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Explore community requests, track your personal requests, or upload a photo from your gallery to request new content.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // =========================================================================
        // 1. TOP SECTION: COMMUNITY REQUESTS (Styled like Homepage Successful Uploads)
        // =========================================================================
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("section_community_requests")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF10B981).copy(alpha = 0.16f),
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Community Requests",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Recent requests & fulfilled uploads from the community",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "${communityRequests.size} Total",
                            color = Color(0xFF10B981),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (communityRequests.isEmpty()) {
                    EmptyRequestShowcasePlaceholder(
                        title = "No community requests yet",
                        subtitle = "Be the first to submit a request below!"
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(communityRequests, key = { "comm_${it.id}" }) { req ->
                            RequestShowcaseCard(
                                request = req,
                                isPersonal = false
                            )
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 2. MIDDLE SECTION: USER'S PERSONAL REQUESTS (Styled like Successful Uploads)
        // =========================================================================
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("section_personal_requests")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Your Personal Requests",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = if (userProfile.isSignedIn) {
                                    "Requests submitted by ${userProfile.email.ifBlank { userProfile.username }}"
                                } else {
                                    "Sign in to track your personal request history"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "${personalRequests.size} Yours",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (personalRequests.isEmpty()) {
                    EmptyRequestShowcasePlaceholder(
                        title = "You haven't made any requests yet",
                        subtitle = "Use the form below to upload an image from your gallery and submit your first request."
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(personalRequests, key = { "pers_${it.id}" }) { req ->
                            RequestShowcaseCard(
                                request = req,
                                isPersonal = true
                            )
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 3. BOTTOM SECTION: SUBMIT A REQUEST FORM (Gallery-Only Imgchest Upload)
        // =========================================================================
        item {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        // Tier & Monthly Quota Banner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.dp,
                    when {
                        isLegendary -> GoldAccent.copy(alpha = 0.5f)
                        isProActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else -> Color(0xFF10B981).copy(alpha = 0.35f)
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when {
                                    isLegendary -> Icons.Default.WorkspacePremium
                                    isProActive -> Icons.Default.Diamond
                                    else -> Icons.Default.VerifiedUser
                                },
                                contentDescription = null,
                                tint = when {
                                    isLegendary -> GoldAccent
                                    isProActive -> MaterialTheme.colorScheme.primary
                                    else -> Color(0xFF10B981)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = when {
                                        isLegendary -> "Legendary Tier • Lifetime Unlimited"
                                        isProActive -> "Pro Tier • 1 Month Unlimited"
                                        else -> "Free Tier • 3 Requests / Month"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isProActive && userProfile.proExpiresAt > 0L && userProfile.proExpiresAt != Long.MAX_VALUE) {
                                    val expDate = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                                        .format(Date(userProfile.proExpiresAt))
                                    Text(
                                        text = "Active until $expDate (auto-reverts to Free after 1 month)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Surface(
                            color = if (hasUnlimited) GoldAccent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (hasUnlimited) "UNLIMITED" else "${(monthlyLimit - monthlyUsed).coerceAtLeast(0)} / $monthlyLimit LEFT",
                                color = if (hasUnlimited) GoldAccent else MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (!hasUnlimited) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { (monthlyUsed.toFloat() / monthlyLimit.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (canSubmit) MaterialTheme.colorScheme.primary else DangerRed,
                            trackColor = MaterialTheme.colorScheme.surface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (canSubmit) {
                                    "You have ${(monthlyLimit - monthlyUsed).coerceAtLeast(0)} free request(s) remaining this month."
                                } else {
                                    "Monthly Free limit reached (3/3 used)."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (canSubmit) MaterialTheme.colorScheme.onSurfaceVariant else DangerRed
                            )
                            TextButton(
                                onClick = onNavigateToPricing,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("Upgrade Tier", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldAccent)
                            }
                        }
                    }

                    if (deviceBiometricInfo != null && userProfile.isSignedIn) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Device: ${deviceBiometricInfo.manufacturer} ${deviceBiometricInfo.model} (${deviceBiometricInfo.deviceId.take(8)}…)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!deviceBiometricInfo.biometricEnabled) {
                                TextButton(
                                    onClick = {
                                        BiometricAuthManager.promptEnableBiometricAndDeviceTracking(
                                            activity = activity,
                                            context = context,
                                            email = userProfile.email,
                                            refreshToken = userProfile.refreshToken,
                                            onSuccess = { info -> onBiometricVerified(info) },
                                            onError = { err -> errorMessage = err }
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text("Verify Biometric", fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Request Submission Form Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("request_submission_form_card")
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Submit a New Request",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Your Name / Handle") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("request_name_input")
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("request_email_input")
                    )

                    OutlinedTextField(
                        value = referenceLink,
                        onValueChange = { referenceLink = it },
                        label = { Text("Reference / Social Link (Optional)") },
                        placeholder = { Text("https://instagram.com/... or reference URL") },
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("request_reference_link_input")
                    )

                    // GALLERY-ONLY UPLOAD TO IMGCHEST API (No manual URL input box)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            1.dp,
                            if (uploadedGalleryImgchestUrl.isNotBlank()) Color(0xFF10B981) else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Request Sample Image (Gallery Upload)",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Select a photo from your device gallery — it uploads to Imgchest automatically.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (uploadedGalleryImgchestUrl.isNotBlank()) {
                                    Surface(
                                        color = Color(0xFF10B981).copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "UPLOADED ✓",
                                            color = Color(0xFF10B981),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            if (uploadedGalleryImgchestUrl.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(170.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black)
                                ) {
                                    AsyncImage(
                                        model = uploadedGalleryImgchestUrl,
                                        contentDescription = "Uploaded Request Image",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    IconButton(
                                        onClick = { uploadedGalleryImgchestUrl = "" },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .size(32.dp)
                                            .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove Uploaded Image",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = { galleryPickerLauncher.launch("image/*") },
                                enabled = !isUploadingImage,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF10B981),
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("btn_upload_request_image_imgchest")
                            ) {
                                if (isUploadingImage) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.Black
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Uploading to Imgchest API...", fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (uploadedGalleryImgchestUrl.isBlank()) {
                                            "Upload Image from Gallery"
                                        } else {
                                            "Change Gallery Image"
                                        },
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text("Request Details / Description") },
                        placeholder = { Text("Describe the creator, set, or exclusive pack you are requesting...") },
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("request_message_input")
                    )

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = DangerRed,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (submitSuccess) {
                        Surface(
                            color = SuccessGreen.copy(alpha = 0.15f),
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
                                    text = "Request submitted! Check Your Personal Requests above for status updates.",
                                    color = SuccessGreen,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (name.isBlank() || email.isBlank() || message.isBlank()) {
                                errorMessage = "Please fill in your name, email, and request description."
                                return@Button
                            }
                            errorMessage = null
                            isSubmitting = true
                            submitSuccess = false

                            val combinedMessage = if (referenceLink.trim().isNotBlank() && !message.contains(referenceLink.trim())) {
                                "${message.trim()}\nRef: ${referenceLink.trim()}"
                            } else {
                                message.trim()
                            }

                            onSubmitRequest(
                                name.trim(),
                                email.trim(),
                                referenceLink.trim().ifBlank { null },
                                combinedMessage,
                                uploadedGalleryImgchestUrl.trim().ifBlank { null }
                            ) { res ->
                                isSubmitting = false
                                if (res.isSuccess) {
                                    submitSuccess = true
                                    message = ""
                                    referenceLink = ""
                                    uploadedGalleryImgchestUrl = ""
                                } else {
                                    errorMessage = res.exceptionOrNull()?.message ?: "Failed to submit request."
                                }
                            }
                        },
                        enabled = canSubmit && !isSubmitting && !isUploadingImage,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("submit_request_button")
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (canSubmit) "Submit Content Request" else "Monthly Free Limit Reached (3/3)",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun extractRequestRefUrl(req: RequestEntity): String? {
    if (!req.downloadLink.isNullOrBlank() && req.downloadLink.startsWith("http")) {
        return req.downloadLink.trim()
    }
    if (!req.telegramUsername.isNullOrBlank() && req.telegramUsername.startsWith("http")) {
        return req.telegramUsername.trim()
    }
    val urlRegex = Regex("""https?://\S+""")
    return urlRegex.find(req.message.orEmpty())?.value?.trim()
}

private fun extractRequestCleanMessage(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return raw
        .replace(Regex("""Ref:\s*https?://\S+""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""https?://\S+"""), "")
        .trim()
}

/**
 * Showcase card matching the Homepage's "Successful Uploads" card design,
 * used for both Community Requests and Personal Requests.
 */
@Composable
private fun RequestShowcaseCard(
    request: RequestEntity,
    isPersonal: Boolean
) {
    val context = LocalContext.current
    val refLink: String? = extractRequestRefUrl(request)
    val cleanMessage: String = extractRequestCleanMessage(request.message)
    val statusLower = request.status.lowercase()

    val (badgeLabel, badgeColor) = when (statusLower) {
        "fulfilled", "delivered", "completed", "approved" -> "FULFILLED" to Color(0xFF10B981)
        "replied" -> "REPLIED" to Color(0xFF3B82F6)
        "rejected" -> "REJECTED" to DangerRed
        else -> "PENDING" to Color(0xFFF59E0B)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF141822)
        ),
        border = BorderStroke(
            1.dp,
            if (isPersonal) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            else Color.White.copy(alpha = 0.08f)
        ),
        modifier = Modifier
            .width(235.dp)
            .testTag(if (isPersonal) "personal_request_card_${request.id}" else "community_request_card_${request.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top Thumbnail Image (with fallback gradient placeholder if no image was attached)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(126.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF1E2434), Color(0xFF0F131C))
                        )
                    )
            ) {
                if (!request.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = request.imageUrl,
                        contentDescription = cleanMessage,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.28f),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Request Preview",
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.15f),
                                    Color.Black.copy(alpha = 0.72f)
                                )
                            )
                        )
                )

                // Top-left status badge
                Surface(
                    color = badgeColor.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = when (badgeLabel) {
                                "FULFILLED", "REPLIED" -> Icons.Default.CheckCircle
                                "REJECTED" -> Icons.Default.Cancel
                                else -> Icons.Default.Schedule
                            },
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = badgeLabel,
                            color = Color.Black,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                // Bottom-left requester name
                Text(
                    text = "By ${request.name.orEmpty().ifBlank { "Fan" }}",
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                )
            }

            // Card Body: Description + Clickable Reference Link / Delivered Link
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(11.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = cleanMessage.ifBlank { "Custom Creator Request" },
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (statusLower == "rejected" && !request.rejectionReason.isNullOrBlank()) {
                    Text(
                        text = "Reason: ${request.rejectionReason}",
                        color = DangerRed,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!refLink.isNullOrBlank()) {
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    val normalized = if (refLink.startsWith("http")) refLink else "https://$refLink"
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized)))
                                } catch (_: Exception) {}
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (!request.downloadLink.isNullOrBlank()) "Open Delivered Link" else "Reference Link",
                                    color = Color(0xFF10B981),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open Link",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                } else {
                    Text(
                        text = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(request.createdAt)),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyRequestShowcasePlaceholder(
    title: String,
    subtitle: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Inbox,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
