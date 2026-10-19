package com.example.justfan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import com.example.justfan.data.model.UserPreferencesState
import com.example.justfan.data.model.UserProfile
import com.example.justfan.data.remote.ImgchestUploader
import com.example.justfan.ui.theme.AVAILABLE_THEMES
import com.example.justfan.ui.theme.GoldAccent
import com.example.justfan.ui.theme.SuccessGreen
import kotlinx.coroutines.launch

@Composable
fun PreferencesScreen(
    userProfile: UserProfile,
    preferences: UserPreferencesState,
    onUpdateTheme: (themeVariant: String, isDark: Boolean) -> Unit,
    onUpdateWallpaper: (wallpaperUri: String?, dim: Float) -> Unit,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var wallpaperUrlInput by remember(preferences.customWallpaperUri) {
        mutableStateOf(preferences.customWallpaperUri ?: "")
    }
    var dimSlider by remember(preferences.wallpaperDim) {
        mutableFloatStateOf(preferences.wallpaperDim)
    }

    val isPaidOrLegendary = userProfile.tier.equals("legendary", ignoreCase = true) ||
            userProfile.tier.equals("pro", ignoreCase = true) || userProfile.isAdmin

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // User Account & Tier Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isPaidOrLegendary) Color(0xFF26200A) else MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("user_account_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = if (isPaidOrLegendary) GoldAccent else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPaidOrLegendary) Icons.Default.Star else Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color.Black
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (userProfile.isSignedIn) userProfile.username else "Guest Visitor",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = if (userProfile.isSignedIn) userProfile.email else "Not logged in",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        color = if (isPaidOrLegendary) GoldAccent else MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = userProfile.tier.uppercase(),
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (userProfile.isSignedIn) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Sync, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Preferences synced with database across all devices", fontSize = 11.sp, color = SuccessGreen)
                        }
                        OutlinedButton(
                            onClick = onSignOutClick,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Text("Sign Out", fontSize = 12.sp)
                        }
                    }
                } else {
                    Button(
                        onClick = onSignInClick,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sign_in_button")
                    ) {
                        Text("Log In or Connect Account to Sync Preferences", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Theme Customization Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ColorLens, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "App Theme Accent",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Select your accent color. Automatically updates across all your devices via the database.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(AVAILABLE_THEMES) { themeOpt ->
                        val isSelected = preferences.themeVariant.equals(themeOpt.id, ignoreCase = true)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .testTag("theme_btn_${themeOpt.id}")
                                .clickable {
                                    onUpdateTheme(themeOpt.id, preferences.isDarkMode)
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(themeOpt.color, CircleShape)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = themeOpt.name.split(" ").first(),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Dark Mode", fontWeight = FontWeight.SemiBold)
                    Switch(
                        checked = preferences.isDarkMode,
                        onCheckedChange = { onUpdateTheme(preferences.themeVariant, it) }
                    )
                }
            }
        }

        // Custom Wallpaper Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Custom Background Wallpaper",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Personalize your app background with a custom wallpaper photo. Synced to your user profile in Supabase.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Device Storage Gallery Picker Handle
                val context = androidx.compose.ui.platform.LocalContext.current
                val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
                var isUploadingWallpaper by remember { mutableStateOf(false) }

                val galleryPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
                ) { uri ->
                    if (uri != null) {
                        coroutineScope.launch {
                            isUploadingWallpaper = true
                            val uploadRes = ImgchestUploader.uploadImageDirect(
                                context = context,
                                imageUri = uri,
                                title = "Wallpaper ${userProfile.username}"
                            )
                            isUploadingWallpaper = false
                            val finalUrl = if (uploadRes.isSuccess && !uploadRes.getOrNull().isNullOrBlank()) {
                                uploadRes.getOrThrow()
                            } else {
                                try {
                                    val safeKey = userProfile.email.ifBlank { "guest" }.replace(Regex("[^a-zA-Z0-9]"), "_")
                                    val localFile = java.io.File(context.filesDir, "wallpaper_$safeKey.jpg")
                                    context.contentResolver.openInputStream(uri)?.use { input ->
                                        localFile.outputStream().use { output -> input.copyTo(output) }
                                    }
                                    android.net.Uri.fromFile(localFile).toString()
                                } catch (_: Exception) {
                                    uri.toString()
                                }
                            }
                            wallpaperUrlInput = finalUrl
                            onUpdateWallpaper(finalUrl, dimSlider)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            galleryPickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        enabled = !isUploadingWallpaper,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pick_wallpaper_gallery_btn")
                    ) {
                        if (isUploadingWallpaper) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Uploading Wallpaper...", fontSize = 13.sp)
                        } else {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pick Wallpaper from Device Gallery", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = wallpaperUrlInput,
                    onValueChange = { wallpaperUrlInput = it },
                    label = { Text("Or Paste Wallpaper Image URL") },
                    placeholder = { Text("https://example.com/wallpaper.jpg") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wallpaper_url_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Background Dim Intensity: ${(dimSlider * 100).toInt()}%",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = dimSlider,
                    onValueChange = { dimSlider = it },
                    valueRange = 0.2f..0.9f
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            val uri = wallpaperUrlInput.trim().ifBlank { null }
                            onUpdateWallpaper(uri, dimSlider)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("apply_wallpaper_button")
                    ) {
                        Text("Apply & Sync to Database", fontWeight = FontWeight.Bold)
                    }

                    if (!preferences.customWallpaperUri.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = {
                                wallpaperUrlInput = ""
                                onUpdateWallpaper(null, dimSlider)
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Clear")
                        }
                    }
                }
            }
        }
    }
}
