package com.example.justfan.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.justfan.data.model.UserPreferences
import com.example.justfan.data.model.UserProfile
import com.example.justfan.ui.theme.AVAILABLE_THEMES
import com.example.justfan.ui.theme.GoldAccent
import com.example.justfan.ui.theme.SuccessGreen
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen(
    preferences: UserPreferences,
    userProfile: UserProfile,
    requestQuota: com.example.justfan.data.model.RequestQuota = com.example.justfan.data.model.RequestQuota(),
    onUpdateTheme: (String, Boolean) -> Unit,
    onUpdateContentFilter: (String) -> Unit,
    onAddPreferredTag: (String) -> Unit,
    onRemovePreferredTag: (String) -> Unit,
    onUpdatePlan: (String) -> Unit,
    onUpdateWallpaper: (String?, Float) -> Unit = { _, _ -> },
    onOpenAuth: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onOpenAdmin: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var newTagInput by remember { mutableStateOf("") }
    var customUrlInput by remember { mutableStateOf("") }
    var wallpaperDimSlider by remember(preferences.wallpaperDim) { mutableFloatStateOf(preferences.wallpaperDim) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val targetFile = java.io.File(context.filesDir, "user_wallpaper.jpg")
                if (inputStream != null) {
                    targetFile.outputStream().use { out ->
                        inputStream.copyTo(out)
                    }
                    onUpdateWallpaper(targetFile.absolutePath, wallpaperDimSlider)
                } else {
                    onUpdateWallpaper(uri.toString(), wallpaperDimSlider)
                }
            } catch (e: Exception) {
                onUpdateWallpaper(uri.toString(), wallpaperDimSlider)
            }
        }
    }

    val presetWallpapers = listOf(
        "Neon Cyber" to "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=1000&q=80",
        "Midnight Noir" to "https://images.unsplash.com/photo-1529626455594-4ff0802cfb7e?w=1000&q=80",
        "Sunset Glow" to "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=1000&q=80",
        "Anime Sakura" to "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=1000&q=80"
    )

    Scaffold(
        containerColor = if (!preferences.customWallpaperUri.isNullOrBlank()) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (!preferences.customWallpaperUri.isNullOrBlank()) Color.Black.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Preferences & Personalization", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    TextButton(onClick = onOpenAuth) {
                        Icon(
                            imageVector = if (userProfile.isSignedIn) Icons.Default.AccountCircle else Icons.Default.Login,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (userProfile.isSignedIn) userProfile.tier else "Sign In",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Sign in prompt banner if not signed in
            if (!userProfile.isSignedIn) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Signed User Exclusive Perks",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Sign in with Google, Passkey, or Email to unlock custom Themes and Uploaded Background Wallpapers!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = onOpenAuth,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Sign In", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Theme & Color Section (Signed user option)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Appearance & Themes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            if (userProfile.isSignedIn) {
                                Surface(
                                    color = SuccessGreen.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "UNLOCKED",
                                        color = SuccessGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Dark mode toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Dark Mode", fontWeight = FontWeight.SemiBold)
                                Text("High contrast OLED dark theme", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = preferences.isDarkMode,
                                onCheckedChange = {
                                    onUpdateTheme(preferences.themeVariant, it)
                                },
                                modifier = Modifier.testTag("dark_mode_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Accent Color Palette", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(AVAILABLE_THEMES) { themeOpt ->
                                val isSelected = preferences.themeVariant == themeOpt.id
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .testTag("theme_color_${themeOpt.id}")
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            onUpdateTheme(themeOpt.id, preferences.isDarkMode)
                                        }
                                        .padding(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(themeOpt.color)
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) Color.White else Color.Transparent,
                                                shape = CircleShape
                                            )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = themeOpt.id.replaceFirstChar { it.uppercase() },
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Uploaded Background Wallpaper Section (Signed user option)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Wallpaper, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Uploaded Background Wallpaper", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            if (userProfile.isSignedIn) {
                                Surface(
                                    color = SuccessGreen.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "UNLOCKED",
                                        color = SuccessGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Set a custom photo or wallpaper from your device gallery to personalize your JUSTFAN background.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Current Wallpaper Preview
                        if (!preferences.customWallpaperUri.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                AsyncImage(
                                    model = preferences.customWallpaperUri,
                                    contentDescription = "Current Wallpaper",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = preferences.wallpaperDim))
                                )
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onUpdateWallpaper(null, 0.65f) },
                                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Remove Wallpaper", color = Color.White, fontSize = 12.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Wallpaper Dim Slider
                            Text(
                                text = "Wallpaper Dim / Overlay: ${(preferences.wallpaperDim * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Slider(
                                value = preferences.wallpaperDim,
                                onValueChange = { onUpdateWallpaper(preferences.customWallpaperUri, it) },
                                valueRange = 0.2f..0.85f,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Upload button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).testTag("btn_upload_wallpaper")
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Choose from Gallery")
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // URL input option
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customUrlInput,
                                onValueChange = { customUrlInput = it },
                                placeholder = { Text("Or paste image URL (https://…)", fontSize = 12.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (customUrlInput.isNotBlank()) {
                                        onUpdateWallpaper(customUrlInput.trim(), wallpaperDimSlider)
                                        customUrlInput = ""
                                    }
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Apply")
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text("Or Select Curated Preset Wallpaper:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(presetWallpapers) { (name, url) ->
                                val isSelected = preferences.customWallpaperUri == url
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            onUpdateWallpaper(url, wallpaperDimSlider)
                                        }
                                        .padding(2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 72.dp, height = 54.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                    ) {
                                        AsyncImage(
                                            model = url,
                                            contentDescription = name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(name, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
            }

            // User Membership Profile & 3 Tiers
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Membership & Tiers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            val isStrictAdmin = userProfile.isAdmin && userProfile.email.equals("reytherapper12@gmail.com", ignoreCase = true)
                            if (isStrictAdmin) {
                                Surface(
                                    color = Color(0xFFEF4444),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "ADMIN",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("User: ${userProfile.username} ${if (userProfile.email.isNotBlank()) "(${userProfile.email})" else ""}", fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Current Tier: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Surface(
                                color = when (userProfile.tier) {
                                    "Legendary" -> GoldAccent
                                    "Pro" -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.surface
                                },
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = userProfile.tier.uppercase(),
                                    color = if (userProfile.tier == "Legendary") Color.Black else MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val hasUnlimited = userProfile.tier == "Legendary" || userProfile.isProActive || requestQuota.unlimited
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
                                        } catch (_: Exception) {}
                                    }
                                    if (parsedDate != null) {
                                        SimpleDateFormat("MMM dd, yyyy", Locale.US).format(parsedDate)
                                    } else {
                                        raw.substringBefore("T")
                                    }
                                } catch (_: Exception) {
                                    raw.substringBefore("T")
                                }
                            }
                        }

                        // Requests Allowance display
                        val quotaDescription = when {
                            userProfile.tier == "Legendary" -> "🌟 Unlimited requests forever (Lifetime VIP)"
                            userProfile.isProActive -> {
                                val expiresFormatted = if (userProfile.proExpiresAt > 0L && userProfile.proExpiresAt != Long.MAX_VALUE) {
                                    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                                    "Expires: ${sdf.format(Date(userProfile.proExpiresAt))}"
                                } else {
                                    "Active 1 Month"
                                }
                                "⚡ Unlimited requests for 1 month ($expiresFormatted)"
                            }
                            hasUnlimited -> "✨ Unlimited requests"
                            else -> "📝 ${requestQuota.remaining} of ${requestQuota.limit} requests left this month • Resets on $resetsDateFormatted"
                        }

                        Text(
                            text = quotaDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        val isStrictAdmin = userProfile.isAdmin && userProfile.email.equals("reytherapper12@gmail.com", ignoreCase = true)
                        if (isStrictAdmin) {
                            Text("Admin User Tier Management:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Free", "Pro", "Legendary").forEach { plan ->
                                    val isSelected = userProfile.tier.equals(plan, ignoreCase = true)
                                    OutlinedButton(
                                        onClick = { onUpdatePlan(plan) },
                                        colors = if (isSelected) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.outlinedButtonColors(),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(plan, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = onOpenAdmin,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open Admin Dashboard to Manage Users")
                            }
                        } else {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Tier Assignment", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Membership tiers (Free, Pro, Legendary) are assigned and managed directly by the platform Administrator. Normal users cannot modify their own tier.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onOpenAuth,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (userProfile.isSignedIn) "Account Info" else "Sign In")
                            }

                            if (userProfile.isSignedIn) {
                                OutlinedButton(
                                    onClick = onSignOut,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Sign Out")
                                }
                            }
                        }
                    }
                }
            }

            // Fingerprint Login Toggle
            item {
                var isBioEnabled by remember {
                    mutableStateOf(com.example.justfan.util.BiometricAuthManager.isBiometricEnabled(context))
                }
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Fingerprint login", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = if (isBioEnabled) "Enabled for ${com.example.justfan.util.BiometricAuthManager.getSavedAccountEmail(context) ?: "this device"}" else "Disabled (Shortcut for returning users)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = isBioEnabled,
                                onCheckedChange = { checked ->
                                    if (!checked) {
                                        com.example.justfan.util.BiometricAuthManager.clearBiometricData(context)
                                        isBioEnabled = false
                                    } else {
                                        if (userProfile.isSignedIn && !userProfile.refreshToken.isNullOrBlank()) {
                                            val act = context as? androidx.fragment.app.FragmentActivity
                                            if (act != null) {
                                                com.example.justfan.util.BiometricAuthManager.promptEnableBiometric(
                                                    activity = act,
                                                    email = userProfile.email,
                                                    refreshToken = userProfile.refreshToken!!,
                                                    onSuccess = { isBioEnabled = true },
                                                    onError = { /* ignored */ }
                                                )
                                            }
                                        } else {
                                            onOpenAuth()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Content Safety Filter
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Content Safety & Filter", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Content Filter Mode", fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (preferences.contentFilter == "sfw") "SFW Only (Safe For Work)" else "All Content (Unrestricted)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = {
                                    val next = if (preferences.contentFilter == "sfw") "nsfw" else "sfw"
                                    onUpdateContentFilter(next)
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("filter_toggle_button")
                            ) {
                                Text(preferences.contentFilter.uppercase())
                            }
                        }
                    }
                }
            }

            // Preferred Tags
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocalOffer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Preferred Hashtags", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Content matching your preferred tags is prioritized on your feed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            preferences.preferredTags.forEach { tag ->
                                InputChip(
                                    selected = true,
                                    onClick = { onRemovePreferredTag(tag) },
                                    label = { Text("#$tag") },
                                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp)) },
                                    modifier = Modifier.testTag("pref_tag_$tag")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newTagInput,
                                onValueChange = { newTagInput = it },
                                placeholder = { Text("Add tag e.g. cosplay, noir…") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .testTag("add_tag_input")
                                    .weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (newTagInput.isNotBlank()) {
                                        onAddPreferredTag(newTagInput)
                                        newTagInput = ""
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("add_tag_button")
                            ) {
                                Text("Add")
                            }
                        }
                    }
                }
            }
        }
    }
}
