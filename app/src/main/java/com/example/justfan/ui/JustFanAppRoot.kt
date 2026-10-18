package com.example.justfan.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import coil.compose.AsyncImage
import com.example.justfan.data.model.ActivityEntity
import com.example.justfan.data.model.CommentEntity
import com.example.justfan.data.model.PostEntity
import com.example.justfan.ui.components.AuthDialog
import com.example.justfan.ui.components.MoreBottomSheet
import com.example.justfan.ui.components.TopHeader
import com.example.justfan.ui.screens.AdminScreen
import com.example.justfan.ui.screens.CategoriesScreen
import com.example.justfan.ui.screens.FavoritesScreen
import com.example.justfan.ui.screens.FreeScreen
import com.example.justfan.ui.screens.GalleryScreen
import com.example.justfan.ui.screens.HomeScreen
import com.example.justfan.ui.screens.NotificationsScreen
import com.example.justfan.ui.screens.PostDetailScreen
import com.example.justfan.ui.screens.PreferencesScreen
import com.example.justfan.ui.screens.PricingScreen
import com.example.justfan.ui.screens.RequestScreen
import com.example.justfan.ui.screens.TrendingScreen
import com.example.justfan.ui.theme.JustFanTheme

sealed interface AppScreen {
    object Feed : AppScreen
    object Categories : AppScreen
    object Free : AppScreen
    object Trending : AppScreen
    object Requests : AppScreen
    object Saved : AppScreen
    object Gallery : AppScreen
    object Membership : AppScreen
    object Activity : AppScreen
    object Admin : AppScreen
    object Themes : AppScreen
    data class PostDetail(val post: PostEntity) : AppScreen
}

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
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    val favoriteIds = remember(favorites) { favorites.map { it.postId }.toSet() }

    var screenBackstack by remember { mutableStateOf(listOf<AppScreen>(AppScreen.Feed)) }
    val currentScreen = screenBackstack.lastOrNull() ?: AppScreen.Feed

    var showAuthDialog by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var globalSearchQuery by remember { mutableStateOf("") }
    var requestIdeaPreFill by remember { mutableStateOf("") }

    val isOwnerOrAdmin = userProfile.isAdmin || userProfile.email.equals("reytherapper12@gmail.com", ignoreCase = true)
    val isPaidOrLegendary = userProfile.tier.equals("legendary", ignoreCase = true) ||
            userProfile.tier.equals("pro", ignoreCase = true) || isOwnerOrAdmin

    fun navigateTo(screen: AppScreen) {
        if (screenBackstack.lastOrNull() != screen) {
            screenBackstack = screenBackstack + screen
        }
    }

    fun navigateRoot(screen: AppScreen) {
        screenBackstack = listOf(screen)
    }

    fun navigateBack() {
        if (screenBackstack.size > 1) {
            screenBackstack = screenBackstack.dropLast(1)
        }
    }

    BackHandler(enabled = screenBackstack.size > 1) {
        navigateBack()
    }

    val deliveredRequests = remember(requests) {
        requests.filter { it.status.equals("fulfilled", ignoreCase = true) || it.status.equals("delivered", ignoreCase = true) }
    }

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
                    if (currentScreen !is AppScreen.PostDetail) {
                        TopHeader(
                            searchQuery = globalSearchQuery,
                            onSearchChange = {
                                globalSearchQuery = it
                                if (currentScreen !is AppScreen.Feed) {
                                    navigateRoot(AppScreen.Feed)
                                }
                            },
                            onNotificationsClick = { navigateTo(AppScreen.Activity) },
                            onAdminClick = { navigateTo(AppScreen.Admin) },
                            onPricingClick = { navigateTo(AppScreen.Membership) },
                            unreadNotificationsCount = 0,
                            isSyncing = isRefreshing,
                            onSyncClick = { viewModel.refreshAll() },
                            userProfile = userProfile,
                            onAuthClick = { showAuthDialog = true },
                            hasCustomWallpaper = !preferences.customWallpaperUri.isNullOrBlank()
                        )
                    }
                },
                bottomBar = {
                    if (currentScreen !is AppScreen.PostDetail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        ) {
                            NavigationBarItem(
                                selected = currentScreen is AppScreen.Feed,
                                onClick = { navigateRoot(AppScreen.Feed) },
                                icon = { Icon(Icons.Default.Home, contentDescription = "Feed") },
                                label = { Text("Feed") },
                                modifier = Modifier.testTag("nav_feed")
                            )
                            NavigationBarItem(
                                selected = currentScreen is AppScreen.Categories,
                                onClick = { navigateRoot(AppScreen.Categories) },
                                icon = { Icon(Icons.Default.Category, contentDescription = "Categories") },
                                label = { Text("Categories") },
                                modifier = Modifier.testTag("nav_categories")
                            )
                            NavigationBarItem(
                                selected = currentScreen is AppScreen.Free,
                                onClick = { navigateRoot(AppScreen.Free) },
                                icon = { Icon(Icons.Default.CardGiftcard, contentDescription = "Free") },
                                label = { Text("Free") },
                                modifier = Modifier.testTag("nav_free")
                            )
                            NavigationBarItem(
                                selected = currentScreen is AppScreen.Trending,
                                onClick = { navigateRoot(AppScreen.Trending) },
                                icon = { Icon(Icons.Default.Whatshot, contentDescription = "Trending") },
                                label = { Text("Trending") },
                                modifier = Modifier.testTag("nav_trending")
                            )
                            if (isOwnerOrAdmin) {
                                NavigationBarItem(
                                    selected = currentScreen is AppScreen.Admin,
                                    onClick = { navigateRoot(AppScreen.Admin) },
                                    icon = { Icon(Icons.Default.Shield, contentDescription = "Admin") },
                                    label = { Text("Admin") },
                                    modifier = Modifier.testTag("nav_admin")
                                )
                            }
                            NavigationBarItem(
                                selected = false,
                                onClick = { showMoreSheet = true },
                                icon = { Icon(Icons.Default.Menu, contentDescription = "More") },
                                label = { Text("More") },
                                modifier = Modifier.testTag("nav_more")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    when (val scr = currentScreen) {
                        is AppScreen.Feed -> HomeScreen(
                            posts = posts,
                            favoriteIds = favoriteIds,
                            isPaidOrLegendary = isPaidOrLegendary,
                            externalSearchQuery = globalSearchQuery,
                            onPostClick = {
                                viewModel.recordPostClick(it.id)
                                navigateTo(AppScreen.PostDetail(it))
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(it) }
                        )
                        is AppScreen.Categories -> CategoriesScreen(
                            posts = posts,
                            onPostClick = { postId ->
                                posts.find { it.id == postId }?.let {
                                    viewModel.recordPostClick(it.id)
                                    navigateTo(AppScreen.PostDetail(it))
                                }
                            }
                        )
                        is AppScreen.Free -> FreeScreen(
                            freePosts = posts.filter { it.isFree },
                            favoritePostIds = favoriteIds,
                            onPostClick = { postId ->
                                posts.find { it.id == postId }?.let {
                                    viewModel.recordPostClick(it.id)
                                    navigateTo(AppScreen.PostDetail(it))
                                }
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onIncrementClicks = { viewModel.recordPostClick(it) }
                        )
                        is AppScreen.Trending -> TrendingScreen(
                            trendingPosts = posts.sortedByDescending { it.clicksCount },
                            onPostClick = { postId ->
                                posts.find { it.id == postId }?.let {
                                    viewModel.recordPostClick(it.id)
                                    navigateTo(AppScreen.PostDetail(it))
                                }
                            }
                        )
                        is AppScreen.Requests -> RequestScreen(
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
                        is AppScreen.Saved -> FavoritesScreen(
                            allPosts = posts,
                            favoriteIds = favoriteIds,
                            isPaidOrLegendary = isPaidOrLegendary,
                            onPostClick = { navigateTo(AppScreen.PostDetail(it)) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) }
                        )
                        is AppScreen.Gallery -> GalleryScreen(
                            deliveredRequests = deliveredRequests
                        )
                        is AppScreen.Membership -> PricingScreen(
                            currentPlan = userProfile.tier,
                            isAdmin = isOwnerOrAdmin,
                            userProfile = userProfile,
                            onSelectPlan = { selectedTier ->
                                viewModel.updateUserTier(userProfile.id, selectedTier)
                            }
                        )
                        is AppScreen.Activity -> NotificationsScreen(
                            activities = listOf(
                                ActivityEntity(
                                    type = "welcome",
                                    title = "Welcome to JUSTFAN",
                                    body = "Explore trending 4K galleries, exclusive creator drops, and custom request fulfillment."
                                )
                            ),
                            onSelectPost = { postId ->
                                posts.find { it.id == postId }?.let { navigateTo(AppScreen.PostDetail(it)) }
                            }
                        )
                        is AppScreen.Admin -> AdminScreen(
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
                        is AppScreen.Themes -> PreferencesScreen(
                            userProfile = userProfile,
                            preferences = preferences,
                            onUpdateTheme = { variant, isDark ->
                                viewModel.updateTheme(variant, isDark)
                            },
                            onUpdateWallpaper = { uri, dim ->
                                viewModel.updateWallpaper(uri, dim)
                            },
                            onSignInClick = { showAuthDialog = true },
                            onSignOutClick = { viewModel.signOut() }
                        )
                        is AppScreen.PostDetail -> PostDetailScreen(
                            post = scr.post,
                            allPosts = posts,
                            comments = emptyList(),
                            isFavorite = scr.post.id in favoriteIds,
                            canAccessDirectLink = isPaidOrLegendary,
                            onNavigateToPricing = { navigateTo(AppScreen.Membership) },
                            onBack = { navigateBack() },
                            onToggleFavorite = { viewModel.toggleFavorite(scr.post.id) },
                            onAddComment = { /* In-app discussion */ },
                            onSelectPost = { postId ->
                                posts.find { it.id == postId }?.let { navigateTo(AppScreen.PostDetail(it)) }
                            }
                        )
                    }
                }
            }

            // More Bottom Sheet (Navigation drawer)
            if (showMoreSheet) {
                MoreBottomSheet(
                    onDismissRequest = { showMoreSheet = false },
                    onRequestClick = {
                        showMoreSheet = false
                        navigateTo(AppScreen.Requests)
                    },
                    onFavoritesClick = {
                        showMoreSheet = false
                        navigateTo(AppScreen.Saved)
                    },
                    onGalleryClick = {
                        showMoreSheet = false
                        navigateTo(AppScreen.Gallery)
                    },
                    onMembershipClick = {
                        showMoreSheet = false
                        navigateTo(AppScreen.Membership)
                    },
                    onActivityClick = {
                        showMoreSheet = false
                        navigateTo(AppScreen.Activity)
                    },
                    onAdminClick = {
                        showMoreSheet = false
                        navigateTo(AppScreen.Admin)
                    },
                    onSettingsClick = {
                        showMoreSheet = false
                        navigateTo(AppScreen.Themes)
                    },
                    isAdmin = isOwnerOrAdmin
                )
            }

            // Authentic Full Auth Dialog
            AuthDialog(
                userProfile = userProfile,
                requestQuota = quota,
                isOpen = showAuthDialog,
                onDismiss = { showAuthDialog = false },
                onSignInWithGoogle = { email, name ->
                    viewModel.signInWithGoogle(email, name)
                },
                onSignInWithPasskey = { name ->
                    viewModel.signInWithPasskey(name)
                },
                onSignInWithEmail = { email, pass ->
                    viewModel.signInWithEmail(email, pass)
                },
                onSignOut = {
                    viewModel.signOut()
                },
                onUpgradeClick = {
                    showAuthDialog = false
                    navigateTo(AppScreen.Membership)
                }
            )
        }
    }
}
