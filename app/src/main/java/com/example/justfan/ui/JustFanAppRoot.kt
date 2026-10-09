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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
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
import com.example.justfan.ui.theme.DangerRed
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
    val requestQuota by viewModel.requestQuota.collectAsStateWithLifecycle()

    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    var currentSubScreen by remember { mutableStateOf(SubScreen.NONE) }
    var selectedPostId by remember { mutableStateOf<String?>(null) }
    var isAuthDialogOpen by remember { mutableStateOf(false) }
    var authDialogTitle by remember { mutableStateOf<String?>(null) }
    var pendingRequestNavigationAfterAuth by remember { mutableStateOf(false) }

    var showEnableFingerprintDialog by remember { mutableStateOf(false) }
    var pendingBioRefreshToken by remember { mutableStateOf<String?>(null) }
    var pendingBioEmail by remember { mutableStateOf<String?>(null) }
    var hasPromptedBioOnStart by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current

    // Biometric Shortcut on App Start
    LaunchedEffect(Unit) {
        if (!hasPromptedBioOnStart && !userProfile.isSignedIn && com.example.justfan.util.BiometricAuthManager.hasSavedBiometricAccount(context)) {
            hasPromptedBioOnStart = true
            val act = context as? androidx.fragment.app.FragmentActivity
            if (act != null) {
                com.example.justfan.util.BiometricAuthManager.promptBiometricLogin(
                    activity = act,
                    onSuccess = { decryptedRefreshToken, email ->
                        viewModel.refreshBiometricSession(decryptedRefreshToken, email) { success, _ ->
                            if (!success) {
                                isAuthDialogOpen = true
                            }
                        }
                    },
                    onInvalidatedOrFailed = { _ ->
                        isAuthDialogOpen = true
                    },
                    onCancel = {
                        // User chose "Use email or Google instead"
                    }
                )
            }
        }
    }

    LaunchedEffect(userProfile.isSignedIn, userProfile.id) {
        if (userProfile.isSignedIn && userProfile.id != "guest_user" && pendingRequestNavigationAfterAuth) {
            pendingRequestNavigationAfterAuth = false
            currentScreen = Screen.REQUEST
            currentSubScreen = SubScreen.NONE
            isAuthDialogOpen = false
        }
    }

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
                                        },
                                        onDownloadClick = { id, downloadType ->
                                            viewModel.recordDownloadClick(id, downloadType)
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
                                        isAdmin = userProfile.isAdmin && userProfile.email.equals("reytherapper12@gmail.com", ignoreCase = true),
                                        userProfile = userProfile,
                                        onSelectPlan = { plan -> viewModel.updateTier(plan) },
                                        onSubmitPaymentProof = { tier, method, amount, proofUrl, note ->
                                            val proofName = "PAYMENT: $tier ($amount)"
                                            val proofMessage = "[PAYMENT_PROOF] Tier: $tier | Method: $method | Amount: $amount | User Note: ${note.ifBlank { "None" }}"
                                            val userEmail = userProfile.email.ifBlank { "guest_user@justfan.app" }
                                            viewModel.submitRequest(
                                                name = proofName,
                                                email = userEmail,
                                                telegram = null,
                                                message = proofMessage,
                                                imageUrl = proofUrl
                                            )
                                        }
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
                                    val isAuthorized = userProfile.isAdmin && userProfile.email.equals("reytherapper12@gmail.com", ignoreCase = true)
                                    if (!isAuthorized) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(24.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp)
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(24.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Shield,
                                                        contentDescription = null,
                                                        tint = DangerRed,
                                                        modifier = Modifier.size(48.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    Text(
                                                        text = "Admin Access Restricted",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(
                                                        text = "The Executive Dashboard is strictly reserved for the platform administrator (reytherapper12@gmail.com).\n\nYour account (${userProfile.email.ifBlank { "Guest" }}) does not have administrative rights.",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                    )
                                                    Spacer(modifier = Modifier.height(20.dp))
                                                    Button(
                                                        onClick = { currentSubScreen = SubScreen.NONE },
                                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                                    ) {
                                                        Text("Return to Feed")
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        AdminScreen(
                                            posts = posts,
                                            requests = requests,
                                            users = users,
                                            onCreatePost = { title, desc, img, contentImages, link, premLink, tags, isFree, isNsfw ->
                                                viewModel.createPost(title, desc, img, contentImages, link, premLink, tags, isFree, isNsfw)
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
                                            onBack = { currentSubScreen = SubScreen.NONE },
                                            userProfile = userProfile
                                        )
                                    }
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
                                            },
                                            onDownloadClick = { id ->
                                                viewModel.recordDownloadClick(id, "trending_download")
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
                                            if (!userProfile.isSignedIn || userProfile.id == "guest_user") {
                                                LaunchedEffect(Unit) {
                                                    authDialogTitle = "Sign in to submit a request"
                                                    pendingRequestNavigationAfterAuth = true
                                                    isAuthDialogOpen = true
                                                }
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "Sign in to submit a request",
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            } else {
                                                RequestScreen(
                                                    userProfile = userProfile,
                                                    requestQuota = requestQuota,
                                                    requests = requests,
                                                    onSubmitRequest = { name, email, telegram, message, imageUrl, onResult ->
                                                        viewModel.submitRequest(name, email, telegram, message, imageUrl, onResult)
                                                    },
                                                    onViewGallery = { currentSubScreen = SubScreen.GALLERY },
                                                    onUpgradeClick = { currentSubScreen = SubScreen.PRICING },
                                                    isSyncing = isSyncing,
                                                    onSyncRequests = { viewModel.refreshRequestsFromSupabase() },
                                                    onSignInRequired = {
                                                        authDialogTitle = "Sign in to submit a request"
                                                        pendingRequestNavigationAfterAuth = true
                                                        isAuthDialogOpen = true
                                                    },
                                                    onUserMismatch = {
                                                        viewModel.signOut()
                                                        authDialogTitle = "Sign in to submit a request"
                                                        pendingRequestNavigationAfterAuth = true
                                                        isAuthDialogOpen = true
                                                    }
                                                )
                                            }
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
                                                requestQuota = requestQuota,
                                                onUpdateTheme = { theme, dark -> viewModel.updateTheme(theme, dark) },
                                                onUpdateContentFilter = { filter -> viewModel.updateContentFilter(filter) },
                                                onAddPreferredTag = { tag -> viewModel.addPreferredTag(tag) },
                                                onRemovePreferredTag = { tag -> viewModel.removePreferredTag(tag) },
                                                onUpdatePlan = { plan -> viewModel.updateTier(plan) },
                                                onUpdateWallpaper = { uri, dim -> viewModel.updateWallpaper(uri, dim) },
                                                onOpenAuth = { isAuthDialogOpen = true },
                                                onSignOut = { viewModel.signOut() },
                                                onOpenAdmin = {
                                                    if (userProfile.isAdmin && userProfile.email.equals("reytherapper12@gmail.com", ignoreCase = true)) {
                                                        currentSubScreen = SubScreen.ADMIN
                                                    }
                                                }
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
                                                onAdminClick = {
                                                 if (userProfile.isAdmin && userProfile.email.equals("reytherapper12@gmail.com", ignoreCase = true)) {
                                                     currentSubScreen = SubScreen.ADMIN
                                                 }
                                             },
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

            val openRequestScreenWithGate: () -> Unit = {
                if (!userProfile.isSignedIn || userProfile.id == "guest_user") {
                    authDialogTitle = "Sign in to submit a request"
                    pendingRequestNavigationAfterAuth = true
                    isAuthDialogOpen = true
                } else {
                    currentScreen = Screen.REQUEST
                    currentSubScreen = SubScreen.NONE
                }
            }

            // More Bottom Sheet Dialog (matching user screenshot)
            if (isMoreSheetOpen) {
                MoreBottomSheet(
                    onDismissRequest = { isMoreSheetOpen = false },
                    onRequestClick = {
                        isMoreSheetOpen = false
                        openRequestScreenWithGate()
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
                    isAdmin = userProfile.isAdmin && userProfile.email.equals("reytherapper12@gmail.com", ignoreCase = true),
                    onAdminClick = {
                        isMoreSheetOpen = false
                        if (userProfile.isAdmin && userProfile.email.equals("reytherapper12@gmail.com", ignoreCase = true)) {
                            currentSubScreen = SubScreen.ADMIN
                        }
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
                requestQuota = requestQuota,
                titleText = authDialogTitle,
                isOpen = isAuthDialogOpen,
                onDismiss = {
                    isAuthDialogOpen = false
                    authDialogTitle = null
                    if (pendingRequestNavigationAfterAuth || currentScreen == Screen.REQUEST) {
                        currentScreen = Screen.HOME
                        currentSubScreen = SubScreen.NONE
                    }
                    pendingRequestNavigationAfterAuth = false
                },
                onSignInWithGoogle = { email, name ->
                    val res = viewModel.signInWithGoogle(email, name)
                    if (res.isSuccess) {
                        isAuthDialogOpen = false
                        authDialogTitle = null
                        if (pendingRequestNavigationAfterAuth) {
                            pendingRequestNavigationAfterAuth = false
                            currentScreen = Screen.REQUEST
                            currentSubScreen = SubScreen.NONE
                        }
                        if (com.example.justfan.util.BiometricAuthManager.canAuthenticate(context) &&
                            !com.example.justfan.util.BiometricAuthManager.isBiometricEnabled(context)) {
                            val signedUser = res.getOrNull()
                            val rToken = signedUser?.refreshToken ?: userProfile.refreshToken ?: java.util.UUID.randomUUID().toString()
                            val mail = signedUser?.email?.ifBlank { email } ?: email
                            pendingBioRefreshToken = rToken
                            pendingBioEmail = mail
                            showEnableFingerprintDialog = true
                        }
                    }
                },
                onSignInWithPasskey = { name ->
                    val res = viewModel.signInWithPasskey(name)
                    if (res.isSuccess) {
                        isAuthDialogOpen = false
                        authDialogTitle = null
                        if (pendingRequestNavigationAfterAuth) {
                            pendingRequestNavigationAfterAuth = false
                            currentScreen = Screen.REQUEST
                            currentSubScreen = SubScreen.NONE
                        }
                    }
                },
                onSignInWithEmail = { email, pass ->
                    val res = viewModel.signInWithEmail(email, pass)
                    if (res.isSuccess) {
                        isAuthDialogOpen = false
                        authDialogTitle = null
                        if (pendingRequestNavigationAfterAuth) {
                            pendingRequestNavigationAfterAuth = false
                            currentScreen = Screen.REQUEST
                            currentSubScreen = SubScreen.NONE
                        }
                        if (com.example.justfan.util.BiometricAuthManager.canAuthenticate(context) &&
                            !com.example.justfan.util.BiometricAuthManager.isBiometricEnabled(context)) {
                            val signedUser = res.getOrNull()
                            val rToken = signedUser?.refreshToken ?: userProfile.refreshToken ?: java.util.UUID.randomUUID().toString()
                            val mail = signedUser?.email?.ifBlank { email } ?: email
                            pendingBioRefreshToken = rToken
                            pendingBioEmail = mail
                            showEnableFingerprintDialog = true
                        }
                    }
                    res
                },
                onSignOut = {
                    viewModel.signOut()
                },
                onUpgradeClick = {
                    currentSubScreen = SubScreen.PRICING
                },
                onBiometricSignIn = { refreshToken, email, onResult ->
                    viewModel.refreshBiometricSession(refreshToken, email) { success, err ->
                        if (success && pendingRequestNavigationAfterAuth) {
                            pendingRequestNavigationAfterAuth = false
                            currentScreen = Screen.REQUEST
                            currentSubScreen = SubScreen.NONE
                        }
                        onResult(success, err)
                    }
                }
            )

            // Enable Fingerprint Login Dialog
            if (showEnableFingerprintDialog && pendingBioEmail != null && pendingBioRefreshToken != null) {
                val act = context as? androidx.fragment.app.FragmentActivity
                AlertDialog(
                    onDismissRequest = { showEnableFingerprintDialog = false },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    },
                    title = { Text("Enable fingerprint login?") },
                    text = { Text("Use fingerprint authentication for quick and secure sign-in next time.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                showEnableFingerprintDialog = false
                                if (act != null) {
                                    com.example.justfan.util.BiometricAuthManager.promptEnableBiometric(
                                        activity = act,
                                        email = pendingBioEmail!!,
                                        refreshToken = pendingBioRefreshToken!!,
                                        onSuccess = {},
                                        onError = {}
                                    )
                                }
                            }
                        ) {
                            Text("Enable")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showEnableFingerprintDialog = false }
                        ) {
                            Text("Not now")
                        }
                    }
                )
            }
        }
    }
}
