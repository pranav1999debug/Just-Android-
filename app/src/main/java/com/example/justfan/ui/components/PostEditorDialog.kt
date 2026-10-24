package com.example.justfan.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.justfan.data.model.PostEntity
import com.example.justfan.data.remote.ImgchestUploader
import com.example.justfan.data.remote.MediaUploadClient
import com.example.justfan.ui.theme.DangerRed
import com.example.justfan.ui.theme.SuccessGreen
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostEditorDialog(
    initialPost: PostEntity? = null,
    onDismiss: () -> Unit,
    onSave: (PostEntity) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEditing = initialPost != null

    var title by remember(initialPost) { mutableStateOf(initialPost?.title ?: "") }
    var imageUrl by remember(initialPost) { mutableStateOf(initialPost?.imageUrl ?: "") }
    var contentImages by remember(initialPost) {
        mutableStateOf(
            initialPost?.contentImages?.filter { it.isNotBlank() } ?: emptyList()
        )
    }
    var manualContentUrlInput by remember { mutableStateOf("") }
    var description by remember(initialPost) { mutableStateOf(initialPost?.description ?: "") }
    var freeDownloadLink by remember(initialPost) { mutableStateOf(initialPost?.linkUrl ?: "") }
    var premiumLink by remember(initialPost) { mutableStateOf(initialPost?.premiumLinkUrl ?: "") }
    var directLink by remember(initialPost) { mutableStateOf(initialPost?.directLinkUrl ?: "") }
    var tagsInput by remember(initialPost) {
        mutableStateOf(initialPost?.tags?.joinToString(", ") ?: "")
    }
    var section by remember(initialPost) { mutableStateOf(initialPost?.section ?: "home") }
    var isFree by remember(initialPost) { mutableStateOf(initialPost?.isFree ?: false) }
    var isNsfw by remember(initialPost) { mutableStateOf(initialPost?.isNsfw ?: true) }

    var isUploadingCover by remember { mutableStateOf(false) }
    var isUploadingContent by remember { mutableStateOf(false) }
    var isGeneratingAiCopy by remember { mutableStateOf(false) }
    var aiVariationCounter by remember { mutableIntStateOf(0) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isStatusError by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var sectionExpanded by remember { mutableStateOf(false) }

    fun triggerAiCopyGeneration(forceRegenerate: Boolean) {
        if (!forceRegenerate && description.isNotBlank() && tagsInput.isNotBlank()) {
            return
        }
        scope.launch {
            isGeneratingAiCopy = true
            isStatusError = false
            aiVariationCounter += 1
            val effectiveTitle = title.trim().ifBlank { "Midnight Velvet Exclusive Drop" }
            statusMessage = "✨ AI Writer crafting a short & spicy description + hashtags for \"$effectiveTitle\"..."
            val aiRes = MediaUploadClient.generateCaption(effectiveTitle, aiVariationCounter)
            isGeneratingAiCopy = false
            aiRes.onSuccess { generated ->
                if (generated.description.isNotBlank()) {
                    description = generated.description
                }
                if (generated.hashtags.isNotEmpty()) {
                    tagsInput = generated.hashtags.joinToString(", ")
                }
                statusMessage = "✨ AI generated spicy description & ${generated.hashtags.size} hashtags! ✓"
                isStatusError = false
            }.onFailure { err ->
                statusMessage = "AI generation failed: ${err.message}"
                isStatusError = true
            }
        }
    }

    // Cover Image / Video Picker -> Uploads via Imgchest API (or MediaUploadClient for video)
    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            scope.launch {
                isUploadingCover = true
                isStatusError = false
                statusMessage = "Uploading cover media to Imgchest API..."
                val mime = MediaUploadClient.getMimeType(context, selectedUri)
                val uploadRes = if (mime.startsWith("video/")) {
                    MediaUploadClient.uploadSingleMedia(
                        context = context,
                        uri = selectedUri,
                        title = title.ifBlank { "JUSTFAN Cover Video" }
                    )
                } else {
                    val imgRes = ImgchestUploader.uploadImageDirect(
                        context = context,
                        imageUri = selectedUri,
                        title = title.ifBlank { "JUSTFAN Cover Image" }
                    )
                    if (imgRes.isSuccess) imgRes else MediaUploadClient.uploadSingleMedia(context, selectedUri, title)
                }
                isUploadingCover = false
                if (uploadRes.isSuccess) {
                    val url = uploadRes.getOrNull().orEmpty()
                    imageUrl = url
                    statusMessage = "Cover media uploaded to Imgchest! ✓"
                    isStatusError = false
                    triggerAiCopyGeneration(forceRegenerate = description.isBlank())
                } else {
                    statusMessage = "Cover upload failed: ${uploadRes.exceptionOrNull()?.message}"
                    isStatusError = true
                }
            }
        }
    }

    // Multiple Content Images / Videos Picker -> Batch uploads via Imgchest API
    val contentMultiplePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                isUploadingContent = true
                isStatusError = false
                statusMessage = "Uploading ${uris.size} content file(s) to Imgchest API..."

                val imageUris = uris.filter { !MediaUploadClient.getMimeType(context, it).startsWith("video/") }
                val videoUris = uris.filter { MediaUploadClient.getMimeType(context, it).startsWith("video/") }

                val uploadedUrls = mutableListOf<String>()
                var lastErr: String? = null

                if (imageUris.isNotEmpty()) {
                    val imgRes = ImgchestUploader.uploadImagesDirect(
                        context = context,
                        uris = imageUris,
                        title = title.ifBlank { "JUSTFAN Gallery Content" }
                    )
                    if (imgRes.isSuccess) {
                        uploadedUrls.addAll(imgRes.getOrNull().orEmpty())
                    } else {
                        val fallbackRes = MediaUploadClient.uploadMedia(context, imageUris, title)
                        if (fallbackRes.isSuccess) {
                            uploadedUrls.addAll(fallbackRes.getOrNull()?.groups?.flatMap { it.urls }.orEmpty())
                        } else {
                            lastErr = imgRes.exceptionOrNull()?.message
                        }
                    }
                }

                if (videoUris.isNotEmpty()) {
                    val vidRes = MediaUploadClient.uploadMedia(
                        context = context,
                        uris = videoUris,
                        title = title.ifBlank { "JUSTFAN Content Video" }
                    )
                    if (vidRes.isSuccess) {
                        uploadedUrls.addAll(vidRes.getOrNull()?.groups?.flatMap { it.urls }.orEmpty())
                    } else {
                        lastErr = vidRes.exceptionOrNull()?.message
                    }
                }

                isUploadingContent = false
                if (uploadedUrls.isNotEmpty()) {
                    contentImages = (contentImages + uploadedUrls).distinct()
                    if (imageUrl.isBlank()) {
                        imageUrl = uploadedUrls.first()
                    }
                    statusMessage = "Uploaded ${uploadedUrls.size} content media file(s) to Imgchest! ✓"
                    isStatusError = false
                    triggerAiCopyGeneration(forceRegenerate = description.isBlank())
                } else {
                    statusMessage = "Content upload failed: ${lastErr ?: "Unable to reach Imgchest"}"
                    isStatusError = true
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color(0xFF121820),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF263244)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 20.dp)
                .testTag("post_editor_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEditing) "Edit Post" else "Create New Post",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                    }
                }

                if (statusMessage != null) {
                    Surface(
                        color = if (isStatusError) DangerRed.copy(alpha = 0.18f) else Color(0xFF10B981).copy(alpha = 0.18f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = statusMessage!!,
                            color = if (isStatusError) DangerRed else Color(0xFF10B981),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // Title
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Title", fontSize = 12.sp, color = Color.LightGray, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("Enter post title") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_title_input")
                    )
                }

                // Cover Image / Video
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Cover Image / Video", fontSize = 12.sp, color = Color.LightGray, fontWeight = FontWeight.SemiBold)
                    if (imageUrl.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E293B))
                        ) {
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = "Cover Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (isVideoMediaUrl(imageUrl)) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(4.dp)
                                ) {
                                    Text(
                                        text = "VIDEO",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { coverPickerLauncher.launch("*/*") },
                            enabled = !isUploadingCover,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF10B981)),
                            modifier = Modifier.testTag("editor_choose_cover_btn")
                        ) {
                            if (isUploadingCover) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF10B981))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Uploading...", color = Color(0xFF10B981), fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Choose File (Imgchest)", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            text = if (imageUrl.isNotBlank()) "Cover URL ready ✓" else "No file chosen",
                            fontSize = 11.sp,
                            color = if (imageUrl.isNotBlank()) Color(0xFF10B981) else Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedTextField(
                        value = imageUrl,
                        onValueChange = { imageUrl = it },
                        placeholder = { Text("Or paste Cover Image/Video URL") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_cover_url_input")
                    )
                }

                // Content Images / Videos (Multiple)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Content Images / Videos (${contentImages.size} files)",
                        fontSize = 12.sp,
                        color = Color.LightGray,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (contentImages.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(contentImages) { idx, mediaUrl ->
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E293B))
                                ) {
                                    AsyncImage(
                                        model = mediaUrl,
                                        contentDescription = "Content $idx",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (isVideoMediaUrl(mediaUrl)) {
                                        Surface(
                                            color = Color.Red.copy(alpha = 0.85f),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(3.dp)
                                        ) {
                                            Text(
                                                text = "VID",
                                                color = Color.White,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Surface(
                                        color = DangerRed,
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(3.dp)
                                            .size(20.dp)
                                            .clickable {
                                                contentImages = contentImages.filterIndexed { i, _ -> i != idx }
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove",
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { contentMultiplePickerLauncher.launch("*/*") },
                            enabled = !isUploadingContent,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF10B981)),
                            modifier = Modifier.testTag("editor_choose_content_files_btn")
                        ) {
                            if (isUploadingContent) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF10B981))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Uploading to Imgchest...", color = Color(0xFF10B981), fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.Collections, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Choose Files (Multiple)", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (contentImages.isNotEmpty()) {
                            TextButton(onClick = { contentImages = emptyList() }) {
                                Text("Clear All", color = DangerRed, fontSize = 11.sp)
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = manualContentUrlInput,
                            onValueChange = { manualContentUrlInput = it },
                            placeholder = { Text("Add content URL(s) comma-separated") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val urls = manualContentUrlInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                if (urls.isNotEmpty()) {
                                    contentImages = (contentImages + urls).distinct()
                                    manualContentUrlInput = ""
                                }
                            },
                            enabled = manualContentUrlInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Add", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Description + AI Generator Button
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Description & Hashtags",
                            fontSize = 12.sp,
                            color = Color.LightGray,
                            fontWeight = FontWeight.SemiBold
                        )
                        Button(
                            onClick = { triggerAiCopyGeneration(forceRegenerate = true) },
                            enabled = !isGeneratingAiCopy,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF59E0B),
                                contentColor = Color.Black
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                            modifier = Modifier.testTag("editor_ai_generate_caption_btn")
                        ) {
                            if (isGeneratingAiCopy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.Black
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Writing...", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                            } else {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = "AI Generate Spicy Description & Hashtags",
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("✨ AI Spicy Copy & #Tags", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        placeholder = { Text("Short & spicy gallery description (auto-generated on upload or tap ✨ AI)...") },
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(98.dp)
                            .testTag("editor_description_input")
                    )
                }

                // Free Download Link
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Free Download Link", fontSize = 12.sp, color = Color.LightGray, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = freeDownloadLink,
                        onValueChange = { freeDownloadLink = it },
                        placeholder = { Text("https://pasteit.top/...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Premium Link
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Premium Link", fontSize = 12.sp, color = Color.LightGray, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = premiumLink,
                        onValueChange = { premiumLink = it },
                        placeholder = { Text("https://...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Direct Download / No Ads Link
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Direct Download / No Ads Link", fontSize = 12.sp, color = Color.LightGray, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = directLink,
                        onValueChange = { directLink = it },
                        placeholder = { Text("https://mega.nz/... or https://1024terabox.com/...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Tags (comma separated)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Tags (comma separated)", fontSize = 12.sp, color = Color.LightGray, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = tagsInput,
                        onValueChange = { tagsInput = it },
                        placeholder = { Text("Cosplay, 4K, Exclusive") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Section selector
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Section", fontSize = 12.sp, color = Color.LightGray, fontWeight = FontWeight.SemiBold)
                    ExposedDropdownMenuBox(
                        expanded = sectionExpanded,
                        onExpandedChange = { sectionExpanded = !sectionExpanded }
                    ) {
                        OutlinedTextField(
                            value = section,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sectionExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = sectionExpanded,
                            onDismissRequest = { sectionExpanded = false }
                        ) {
                            listOf("home", "free", "trending", "vip").forEach { sec ->
                                DropdownMenuItem(
                                    text = { Text(sec) },
                                    onClick = {
                                        section = sec
                                        sectionExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Checkboxes: Free post & NSFW
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { isFree = !isFree }
                    ) {
                        Checkbox(
                            checked = isFree,
                            onCheckedChange = { isFree = it },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF10B981))
                        )
                        Text("Free post", color = Color.White, fontSize = 13.sp)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { isNsfw = !isNsfw }
                    ) {
                        Checkbox(
                            checked = isNsfw,
                            onCheckedChange = { isNsfw = it },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF10B981))
                        )
                        Text("NSFW", color = Color.White, fontSize = 13.sp)
                    }
                }

                // Delete Post Option (when editing)
                if (isEditing && onDelete != null && initialPost != null) {
                    if (showDeleteConfirm) {
                        Surface(
                            color = DangerRed.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Confirm permanent delete?", color = DangerRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { showDeleteConfirm = false }) {
                                        Text("No")
                                    }
                                    Button(
                                        onClick = { onDelete(initialPost.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed, contentColor = Color.White)
                                    ) {
                                        Text("Delete Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            border = BorderStroke(1.dp, DangerRed),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("editor_delete_post_btn")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = DangerRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete This Post", color = DangerRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Action Buttons: Cancel & Save Changes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = Color.White)
                    }

                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                isStatusError = true
                                statusMessage = "Please enter a post title."
                                return@Button
                            }
                            val finalCover = imageUrl.trim().ifBlank { contentImages.firstOrNull().orEmpty() }
                            if (finalCover.isBlank()) {
                                isStatusError = true
                                statusMessage = "Please upload or enter a Cover Image/Video URL."
                                return@Button
                            }
                            val mergedContent = buildList {
                                add(finalCover)
                                contentImages.forEach { if (it.isNotBlank()) add(it.trim()) }
                                if (manualContentUrlInput.isNotBlank()) {
                                    manualContentUrlInput.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { add(it) }
                                }
                            }.distinct()

                            val parsedTags = tagsInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                            val resolvedFreeLink = freeDownloadLink.trim().ifBlank { finalCover }

                            val updatedPost = PostEntity(
                                id = initialPost?.id ?: UUID.randomUUID().toString(),
                                title = title.trim(),
                                description = description.trim(),
                                imageUrl = finalCover,
                                linkUrl = resolvedFreeLink,
                                premiumLinkUrl = premiumLink.trim().ifBlank { null },
                                directLinkUrl = directLink.trim().ifBlank { null },
                                contentImages = mergedContent,
                                tags = parsedTags,
                                section = section,
                                author = initialPost?.author ?: "JustFan Creator",
                                isNsfw = isNsfw,
                                isFree = isFree,
                                clicksCount = initialPost?.clicksCount ?: 0,
                                likesCount = initialPost?.likesCount ?: 0,
                                createdAt = initialPost?.createdAt ?: System.currentTimeMillis()
                            )
                            onSave(updatedPost)
                        },
                        enabled = !isUploadingCover && !isUploadingContent,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("editor_save_changes_btn")
                    ) {
                        Text(
                            text = if (isEditing) "Save Changes" else "Publish Post",
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}
