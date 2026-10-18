package com.example.justfan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.justfan.data.model.PostEntity
import com.example.justfan.ui.components.LoginDialog
import com.example.justfan.ui.components.PostDetailDialog
import com.example.justfan.ui.screens.AdminScreen
import com.example.justfan.ui.screens.FavoritesScreen
import com.example.justfan.ui.screens.HomeScreen
import com.example.justfan.ui.screens.PreferencesScreen
import com.example.justfan.ui.screens.RequestScreen
import com.example.justfan.ui.theme.GoldAccent
import com.example.justfan.ui.theme.JustFanTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JustFanAppRoot(viewModel: MainViewModel) {
    val preferences by viewModel.preferences.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val posts by viewModel.posts.collectAsState()
    val requests by viewModel.requests.collectAsState()
    val users by viewModel.users.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val quota by viewModel.requestQuota.collectAsState()

    val favoriteIds = remember(favorites) { favorites.map { it.postId }.toSet() }

    var selectedNavIndex by remember { mutableIntStateOf(0) }
    var selectedPostForDetail by remember { mutableStateOf<PostEntity?>(null) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var requestIdeaPreFill by remember { mutableStateOf("") }

    val isPaidOrLegendary = userProfile.tier.equals("legendary", ignoreCase = true) ||
            userProfile.tier.equals("pro", ignoreCase = true) || userProfile.isAdmin

    JustFanTheme(
        themeVariant = preferences.themeVariant,
        darkTheme = preferences.isDarkMode
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Custom Wallpaper Layer
            if (!preferences.customWallpaperUri.isNullOrBlank()) {
                AsyncImage(
                    model = preferences.customWallpaperUri,
                    contentDescription = "Background Wallpaper",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = preferences.wallpaperDim))
                )
            }

            Scaffold(
                containerColor = if (!preferences.customWallpaperUri.isNullOrBlank()) Color.Transparent else MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "JUSTFAN",
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Surface(
                                    color = if (isPaidOrLegendary) GoldAccent else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        if (isPaidOrLegendary) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.size(4.dp))
                                        }
                                        Text(
                                            text = userProfile.tier.uppercase(),
                                            color = if (isPaidOrLegendary) Color.Black else MaterialTheme.colorScheme.onSurface,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = { viewModel.refreshAll() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh Feed")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        )
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    ) {
                        NavigationBarItem(
                            selected = selectedNavIndex == 0,
                            onClick = { selectedNavIndex = 0 },
                            icon = { Icon(Icons.Default.Home, contentDescription = "Feed") },
                            label = { Text("Feed") },
                            modifier = Modifier.testTag("nav_feed")
                        )
                        NavigationBarItem(
                            selected = selectedNavIndex == 1,
                            onClick = { selectedNavIndex = 1 },
                            icon = { Icon(Icons.Default.Mail, contentDescription = "Requests") },
                            label = { Text("Requests") },
                            modifier = Modifier.testTag("nav_requests")
                        )
                        NavigationBarItem(
                            selected = selectedNavIndex == 2,
                            onClick = { selectedNavIndex = 2 },
                            icon = { Icon(Icons.Default.Favorite, contentDescription = "Saved") },
                            label = { Text("Saved") },
                            modifier = Modifier.testTag("nav_saved")
                        )
                        NavigationBarItem(
                            selected = selectedNavIndex == 3,
                            onClick = { selectedNavIndex = 3 },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Themes") },
                            label = { Text("Themes") },
                            modifier = Modifier.testTag("nav_themes")
                        )
                        if (userProfile.isAdmin) {
                            NavigationBarItem(
                                selected = selectedNavIndex == 4,
                                onClick = { selectedNavIndex = 4 },
                                icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = "Admin") },
                                label = { Text("Admin") },
                                modifier = Modifier.testTag("nav_admin")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    when (selectedNavIndex) {
                        0 -> HomeScreen(
                            posts = posts,
                            favoriteIds = favoriteIds,
                            isPaidOrLegendary = isPaidOrLegendary,
                            onPostClick = { selectedPostForDetail = it },
                            onToggleFavorite = { viewModel.toggleFavorite(it) }
                        )
                        1 -> RequestScreen(
                            userProfile = userProfile,
                            quota = quota,
                            requests = requests,
                            initialIdea = requestIdeaPreFill,
                            onSubmitRequest = { name, email, telegram, message, image ->
                                viewModel.createRequest(name, email, telegram, message, image) { success, _ ->
                                    if (success) requestIdeaPreFill = ""
                                }
                            }
                        )
                        2 -> FavoritesScreen(
                            allPosts = posts,
                            favoriteIds = favoriteIds,
                            isPaidOrLegendary = isPaidOrLegendary,
                            onPostClick = { selectedPostForDetail = it },
                            onToggleFavorite = { viewModel.toggleFavorite(it) }
                        )
                        3 -> PreferencesScreen(
                            userProfile = userProfile,
                            preferences = preferences,
                            onUpdateTheme = { variant, isDark ->
                                viewModel.updateTheme(variant, isDark)
                            },
                            onUpdateWallpaper = { uri, dim ->
                                viewModel.updateWallpaper(uri, dim)
                            },
                            onSignInClick = { showLoginDialog = true },
                            onSignOutClick = { viewModel.signOut() }
                        )
                        4 -> if (userProfile.isAdmin) {
                            AdminScreen(
                                users = users,
                                requests = requests,
                                onUpdateUserTier = { idOrEmail, tier ->
                                    viewModel.updateUserTier(idOrEmail, tier)
                                },
                                onUpdateRequestStatus = { id, status, link, reason ->
                                    viewModel.updateRequestStatus(id, status, link, reason)
                                },
                                onDeleteRequest = { viewModel.deleteRequest(it) },
                                onCreatePost = { post ->
                                    viewModel.createPost(post) { _, _ -> }
                                },
                                onRefresh = { viewModel.refreshAll() }
                            )
                        }
                    }
                }
            }

            // Post Details Dialog
            if (selectedPostForDetail != null) {
                PostDetailDialog(
                    post = selectedPostForDetail!!,
                    isPaidOrLegendary = isPaidOrLegendary,
                    onDismiss = { selectedPostForDetail = null },
                    onRequestCustomContent = { idea ->
                        requestIdeaPreFill = "Regarding post: $idea"
                        selectedNavIndex = 1
                    }
                )
            }

            // Login Dialog
            if (showLoginDialog) {
                LoginDialog(
                    onDismiss = { showLoginDialog = false },
                    onLogin = { email, pass ->
                        viewModel.signInWithEmail(email, pass)
                    }
                )
            }
        }
    }
}
