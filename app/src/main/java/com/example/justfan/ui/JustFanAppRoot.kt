package com.example.justfan.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.justfan.data.model.CommentEntity
import com.example.justfan.ui.components.AuthDialog
import com.example.justfan.ui.components.MoreBottomSheet
import com.example.justfan.ui.screens.*
import com.example.justfan.ui.theme.JustFanTheme

enum class Screen(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    TRENDING("Trending", Icons.Default.Whatshot),
    FREE("Free", Icons.Default.CardGiftcard),
    MORE("More", Icons.Default.Menu),
    REQUEST("Request", Icons.AutoMirrored.Filled.Send),
    FAVORITES("Favorites", Icons.Default.Favorite),
    SETTINGS("Settings", Icons.Default.Settings)
}

enum class SubScreen {
    NONE,
    POST_DETAIL,
    GALLERY,
    PRICING,
    NOTIFICATIONS,
    ADMIN
}

@Composable
fun JustFanAppRoot(
    viewModel: MainViewModel = viewModel()
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val posts by viewModel.posts.collectAsStateWithLifecycle()
    val freePosts by viewModel.freePosts.collectAsStateWithLifecycle()
    val trendingPosts by viewModel.trendingPosts.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val requests by viewModel.requests.collectAsStateWithLifecycle()
    val deliveredRequests by viewModel.deliveredRequests.collectAsStateWithLifecycle()
    val activities by viewModel.activities.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    var currentSubScreen by remember { mutableStateOf(SubScreen.NONE) }
    var selectedPostId by remember { mutableStateOf<String?>(null) }
    var isAuthDialogOpen by remember { mutableStateOf(false) }

    val favoritePostIds = remember(favorites) { favorites.map { it.postId }.toSet() }
    val hasCustomWallpaper = !preferences.customWallpaperUri.isNullOrBlank()

    JustFanTheme(
        themeVariant = preferences.themeVariant,
        darkTheme = preferences.isDarkMode
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideScreen = maxWidth >= 600.dp

            // Dynamic User Uploaded Background Wallpaper
            if (hasCustomWallpaper) {
                val wallpaperModel = remember(preferences.customWallpaperUri) {
                    val uriStr = preferences.customWallpaperUri!!
                    if (uriStr.startsWith("/")) java.io.File(uriStr) else uriStr
                }
                AsyncImage(
                    model = wallpaperModel,
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

            val footerTabs = remember {
                listOf(Screen.HOME, Screen.TRENDING, Screen.FREE, Screen.MORE)
            }
            var isMoreSheetOpen by remember { mutableStateOf(false) }

            Row(modifier = Modifier.fillMaxSize()) {
                if (isWideScreen && currentSubScreen == SubScreen.NONE) {
                    NavigationRail(
                        containerColor = if (hasCustomWallpaper) Color.Black.copy(alpha = 0.65f) else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        footerTabs.forEach { screen ->
                            val isSelected = when (screen) {
                                Screen.HOME -> currentScreen == Screen.HOME && currentSubScreen == SubScreen.NONE && !isMoreSheetOpen
                                Screen.TRENDING -> currentScreen == Screen.TRENDING && currentSubScreen == SubScreen.NONE && !isMoreSheetOpen
                                Screen.FREE -> currentScreen == Screen.FREE && currentSubScreen == SubScreen.NONE && !isMoreSheetOpen
                                Screen.MORE -> isMoreSheetOpen || currentScreen in listOf(Screen.REQUEST, Screen.FAVORITES, Screen.SETTINGS) || currentSubScreen in listOf(SubScreen.GALLERY, SubScreen.PRICING, SubScreen.NOTIFICATIONS, SubScreen.ADMIN)
                                else -> false
                            }
                            NavigationRailItem(
                                selected = isSelected,
                                onClick = {
                                    if (screen == Screen.MORE) {
                                        isMoreSheetOpen = true
                                    } else {
                                        currentScreen = screen
                                        currentSubScreen = SubScreen.NONE
                                    }
                                },
                                icon = { Icon(screen.icon, contentDescription = screen.title) },
                                label = { Text(screen.title) },
                                modifier = Modifier.testTag("nav_rail_${screen.name.lowercase()}")
                            )
                        }
                    }
                }

                Scaffold(
                    containerColor = if (hasCustomWallpaper) Color.Transparent else MaterialTheme.colorScheme.background,
                    bottomBar = {
                        if (!isWideScreen && currentSubScreen == SubScreen.NONE) {
                            NavigationBar(
                                containerColor = if (hasCustomWallpaper) Color.Black.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surface,
                                tonalElevation = 8.dp
                            ) {
                                footerTabs.forEach { screen ->
                                    val isSelected = when (screen) {
                                        Screen.HOME -> currentScreen == Screen.HOME && currentSubScreen == SubScreen.NONE && !isMoreSheetOpen
                                        Screen.TRENDING -> currentScreen == Screen.TRENDING && currentSubScreen == SubScreen.NONE && !isMoreSheetOpen
                                        Screen.FREE -> currentScreen == Screen.FREE && currentSubScreen == SubScreen.NONE && !isMoreSheetOpen
                                        Screen.MORE -> isMoreSheetOpen || currentScreen in listOf(Screen.REQUEST, Screen.FAVORITES, Screen.SETTINGS) || currentSubScreen in listOf(SubScreen.GALLERY, SubScreen.PRICING, SubScreen.NOTIFICATIONS, SubScreen.ADMIN)
                                        else -> false
                                    }
                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = {
                                            if (screen == Screen.MORE) {
                                                isMoreSheetOpen = true
                                            } else {
                                                currentScreen = screen
                                                currentSubScreen = SubScreen.NONE
                                            }
                                        },
                                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                                        label = { Text(screen.title, maxLines = 1, fontSize = 11.sp) },
                                        alwaysShowLabel = true,
                                        modifier = Modifier.testTag("nav_bottom_${screen.name.lowercase()}")
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        Crossfade(targetState = currentSubScreen) { sub ->
                            when (sub) {
                                SubScreen.POST_DETAIL -> {
                                    val currentPost = posts.find { it.id == selectedPostId }
                                    val commentsFlow = remember(selectedPostId) {
                                        if (selectedPostId != null) viewModel.getCommentsForPost(selectedPostId!!)
                                        else kotlinx.coroutines.flow.flowOf(emptyList())
                                    }
                                    val comments by commentsFlow.collectAsStateWithLifecycle(emptyList())

                                    val canDirectDownload = userProfile.isAdmin ||
                                            userProfile.tier.equals("pro", ignoreCase = true) ||
                                            userProfile.tier.equals("legendary", ignoreCase = true)

                                    PostDetailScreen(
                                        post = currentPost,
                                        allPosts = posts,
                                        comments = comments,
                                        isFavorite = favoritePostIds.contains(selectedPostId),
                                        canAccessDirectLink = canDirectDownload,
                                        onNavigateToPricing = { currentSubScreen = SubScreen.PRICING },
                                        onBack = { currentSubScreen = SubScreen.NONE },
                                        onToggleFavorite = {
                                            selectedPostId?.let { id -> viewModel.toggleFavorite(id) }
                                        },
                                        onAddComment = { text ->
                                            selectedPostId?.let { id -> viewModel.addComment(id, text) }
                                        },
                                        onSelectPost = { id ->
                                            selectedPostId = id
                                            viewModel.incrementClicks(id)
                                        },
                                        onShare = {
                                            selectedPostId?.let { id -> viewModel.recordShare(id) }
                                        }
                                    )
                                }
                                SubScreen.GALLERY -> {
                                    BackHandler { currentSubScreen = SubScreen.NONE }
                                    GalleryScreen(
                                        deliveredRequests = deliveredRequests
                                    )
                                }
                                SubScreen.PRICING -> {
                                    BackHandler { currentSubScreen = SubScreen.NONE }
                                    PricingScreen(
                                        currentPlan = userProfile.tier,
                                        isAdmin = userProfile.isAdmin,
                                        onSelectPlan = { plan -> viewModel.updateTier(plan) }
                                    )
                                }
                                SubScreen.NOTIFICATIONS -> {
                                    BackHandler { currentSubScreen = SubScreen.NONE }
                                    NotificationsScreen(
                                        activities = activities,
                                        onSelectPost = { id ->
                                             selectedPostId = id
                                             currentSubScreen = SubScreen.POST_DETAIL
                                        }
                                    )
                                }
                                SubScreen.ADMIN -> {
                                    BackHandler { currentSubScreen = SubScreen.NONE }
                                    AdminScreen(
                                        posts = posts,
                                        requests = requests,
                                        users = users,
                                        onCreatePost = { title, desc, img, link, premLink, tags, isFree, isNsfw ->
                                            viewModel.createPost(title, desc, img, link, premLink, tags, isFree, isNsfw)
                                        },
                                        onUpdatePost = { updated -> viewModel.updatePost(updated) },
                                        onDeletePost = { id -> viewModel.deletePost(id) },
                                        onUpdateUserTier = { id, tier -> viewModel.updateUserTier(id, tier) },
                                        onUpdateUserRequestsCount = { id, count -> viewModel.updateUserRequestsCount(id, count) },
                                        onUpdateUserStatus = { id, status -> viewModel.updateUserStatus(id, status) },
                                        onDeleteUser = { id -> viewModel.deleteUser(id) },
                                        onFulfillRequest = { id, link -> viewModel.fulfillRequest(id, link) },
                                        onRejectRequest = { id, reason -> viewModel.rejectRequest(id, reason) },
                                        onDeleteRequest = { id -> viewModel.deleteRequest(id) },
                                        onBack = { currentSubScreen = SubScreen.NONE }
                                    )
                                }
                                SubScreen.NONE -> {
                                    when (currentScreen) {
                                        Screen.HOME -> HomeScreen(
                                            posts = posts,
                                            trendingPosts = trendingPosts,
                                            requests = requests,
                                            favoritePostIds = favoritePostIds,
                                            contentFilter = preferences.contentFilter,
                                            onPostClick = { id ->
                                                selectedPostId = id
                                                viewModel.incrementClicks(id)
                                                currentSubScreen = SubScreen.POST_DETAIL
                                            },
                                            onToggleFavorite = { id -> viewModel.toggleFavorite(id) },
                                            onIncrementClicks = { id -> viewModel.incrementClicks(id) },
                                            onNotificationsClick = { currentSubScreen = SubScreen.NOTIFICATIONS },
                                            onAdminClick = { currentSubScreen = SubScreen.ADMIN },
                                            onPricingClick = { currentSubScreen = SubScreen.PRICING },
                                            onViewGallery = { currentSubScreen = SubScreen.GALLERY },
                                            isSyncing = isSyncing,
                                            onSyncClick = { viewModel.refreshFromSupabase() },
                                            userProfile = userProfile,
                                            onAuthClick = { isAuthDialogOpen = true },
                                            hasCustomWallpaper = hasCustomWallpaper,
                                            isScreenActive = (currentSubScreen == SubScreen.NONE && currentScreen == Screen.HOME)
                                        )
                                        Screen.TRENDING -> TrendingScreen(
                                            trendingPosts = trendingPosts,
                                            onPostClick = { id ->
                                                selectedPostId = id
                                                viewModel.incrementClicks(id)
                                                currentSubScreen = SubScreen.POST_DETAIL
                                            }
                                        )
                                        Screen.FREE -> {
                                            BackHandler { currentScreen = Screen.HOME }
                                            FreeScreen(
                                                freePosts = freePosts,
                                                favoritePostIds = favoritePostIds,
                                                onPostClick = { id ->
                                                    selectedPostId = id
                                                    viewModel.incrementClicks(id)
                                                    currentSubScreen = SubScreen.POST_DETAIL
                                                },
                                                onToggleFavorite = { id -> viewModel.toggleFavorite(id) },
                                                onIncrementClicks = { id -> viewModel.incrementClicks(id) }
                                            )
                                        }
                                        Screen.REQUEST -> {
                                            BackHandler { currentScreen = Screen.HOME }
                                            RequestScreen(
                                                userProfile = userProfile,
                                                requests = requests,
                                                onSubmitRequest = { name, email, telegram, message, imageUrl ->
                                                    viewModel.submitRequest(name, email, telegram, message, imageUrl)
                                                },
                                                onViewGallery = { currentSubScreen = SubScreen.GALLERY },
                                                onUpgradeClick = { currentSubScreen = SubScreen.PRICING },
                                                isSyncing = isSyncing,
                                                onSyncRequests = { viewModel.refreshRequestsFromSupabase() }
                                            )
                                        }
                                        Screen.FAVORITES -> {
                                            BackHandler { currentScreen = Screen.HOME }
                                            val favPosts = posts.filter { favoritePostIds.contains(it.id) }
                                            FavoritesScreen(
                                                favoritePosts = favPosts,
                                                collections = collections,
                                                onPostClick = { id ->
                                                    selectedPostId = id
                                                    viewModel.incrementClicks(id)
                                                    currentSubScreen = SubScreen.POST_DETAIL
                                                },
                                                onToggleFavorite = { id -> viewModel.toggleFavorite(id) },
                                                onCreateCollection = { name -> viewModel.createCollection(name) },
                                                onDeleteCollection = { id -> viewModel.deleteCollection(id) }
                                            )
                                        }
                                        Screen.SETTINGS -> {
                                            BackHandler { currentScreen = Screen.HOME }
                                            PreferencesScreen(
                                                preferences = preferences,
                                                userProfile = userProfile,
                                                onUpdateTheme = { theme, dark -> viewModel.updateTheme(theme, dark) },
                                                onUpdateContentFilter = { filter -> viewModel.updateContentFilter(filter) },
                                                onAddPreferredTag = { tag -> viewModel.addPreferredTag(tag) },
                                                onRemovePreferredTag = { tag -> viewModel.removePreferredTag(tag) },
                                                onUpdatePlan = { plan -> viewModel.updateTier(plan) },
                                                onUpdateWallpaper = { uri, dim -> viewModel.updateWallpaper(uri, dim) },
                                                onOpenAuth = { isAuthDialogOpen = true },
                                                onSignOut = { viewModel.signOut() },
                                                onOpenAdmin = { currentSubScreen = SubScreen.ADMIN }
                                            )
                                        }
                                        Screen.MORE -> {
                                            HomeScreen(
                                                posts = posts,
                                                trendingPosts = trendingPosts,
                                                requests = requests,
                                                favoritePostIds = favoritePostIds,
                                                contentFilter = preferences.contentFilter,
                                                onPostClick = { id ->
                                                    selectedPostId = id
                                                    viewModel.incrementClicks(id)
                                                    currentSubScreen = SubScreen.POST_DETAIL
                                                },
                                                onToggleFavorite = { id -> viewModel.toggleFavorite(id) },
                                                onIncrementClicks = { id -> viewModel.incrementClicks(id) },
                                                onNotificationsClick = { currentSubScreen = SubScreen.NOTIFICATIONS },
                                                onAdminClick = { currentSubScreen = SubScreen.ADMIN },
                                                onPricingClick = { currentSubScreen = SubScreen.PRICING },
                                                onViewGallery = { currentSubScreen = SubScreen.GALLERY },
                                                isSyncing = isSyncing,
                                                onSyncClick = { viewModel.refreshFromSupabase() },
                                                userProfile = userProfile,
                                                onAuthClick = { isAuthDialogOpen = true },
                                                hasCustomWallpaper = hasCustomWallpaper
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // More Bottom Sheet Dialog (matching user screenshot)
            if (isMoreSheetOpen) {
                MoreBottomSheet(
                    onDismissRequest = { isMoreSheetOpen = false },
                    onRequestClick = {
                        isMoreSheetOpen = false
                        currentSubScreen = SubScreen.NONE
                        currentScreen = Screen.REQUEST
                    },
                    onFavoritesClick = {
                        isMoreSheetOpen = false
                        currentSubScreen = SubScreen.NONE
                        currentScreen = Screen.FAVORITES
                    },
                    onGalleryClick = {
                        isMoreSheetOpen = false
                        currentSubScreen = SubScreen.GALLERY
                    },
                    onMembershipClick = {
                        isMoreSheetOpen = false
                        currentSubScreen = SubScreen.PRICING
                    },
                    onActivityClick = {
                        isMoreSheetOpen = false
                        currentSubScreen = SubScreen.NOTIFICATIONS
                    },
                    onAdminClick = {
                        isMoreSheetOpen = false
                        currentSubScreen = SubScreen.ADMIN
                    },
                    onSettingsClick = {
                        isMoreSheetOpen = false
                        currentSubScreen = SubScreen.NONE
                        currentScreen = Screen.SETTINGS
                    }
                )
            }

            // Global Authentication & Account Dialog
            AuthDialog(
                userProfile = userProfile,
                isOpen = isAuthDialogOpen,
                onDismiss = { isAuthDialogOpen = false },
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
                    currentSubScreen = SubScreen.PRICING
                }
            )
        }
    }
}
