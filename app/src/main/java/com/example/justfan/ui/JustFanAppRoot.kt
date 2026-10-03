package com.example.justfan.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.justfan.data.model.CommentEntity
import com.example.justfan.ui.screens.*
import com.example.justfan.ui.theme.JustFanTheme

enum class Screen(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    CATEGORIES("Categories", Icons.Default.Category),
    FREE("Free", Icons.Default.CardGiftcard),
    TRENDING("Trending", Icons.Default.Whatshot),
    REQUEST("Request", Icons.Default.Send),
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

    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    var currentSubScreen by remember { mutableStateOf(SubScreen.NONE) }
    var selectedPostId by remember { mutableStateOf<String?>(null) }

    val favoritePostIds = remember(favorites) { favorites.map { it.postId }.toSet() }

    JustFanTheme(
        themeVariant = preferences.themeVariant,
        darkTheme = preferences.isDarkMode
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideScreen = maxWidth >= 600.dp

            Row(modifier = Modifier.fillMaxSize()) {
                if (isWideScreen && currentSubScreen == SubScreen.NONE) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Screen.values().forEach { screen ->
                            NavigationRailItem(
                                selected = currentScreen == screen,
                                onClick = { currentScreen = screen },
                                icon = { Icon(screen.icon, contentDescription = screen.title) },
                                label = { Text(screen.title) },
                                modifier = Modifier.testTag("nav_rail_${screen.name.lowercase()}")
                            )
                        }
                    }
                }

                Scaffold(
                    bottomBar = {
                        if (!isWideScreen && currentSubScreen == SubScreen.NONE) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 8.dp
                            ) {
                                Screen.values().forEach { screen ->
                                    NavigationBarItem(
                                        selected = currentScreen == screen,
                                        onClick = { currentScreen = screen },
                                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                                        label = { Text(screen.title) },
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

                                    PostDetailScreen(
                                        post = currentPost,
                                        allPosts = posts,
                                        comments = comments,
                                        isFavorite = favoritePostIds.contains(selectedPostId),
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
                                        currentPlan = userProfile.plan,
                                        onSelectPlan = { plan -> viewModel.updatePlan(plan) }
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
                                        onCreatePost = { title, desc, img, link, premLink, tags, isFree, isNsfw ->
                                            viewModel.createPost(title, desc, img, link, premLink, tags, isFree, isNsfw)
                                        },
                                        onDeletePost = { id -> viewModel.deletePost(id) },
                                        onUpdateRequestStatus = { id, status, link ->
                                            viewModel.updateRequestStatus(id, status, link)
                                        },
                                        onBack = { currentSubScreen = SubScreen.NONE }
                                    )
                                }
                                SubScreen.NONE -> {
                                    when (currentScreen) {
                                        Screen.HOME -> HomeScreen(
                                            posts = posts,
                                            trendingPosts = trendingPosts,
                                            deliveredRequests = deliveredRequests,
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
                                            onViewGallery = { currentSubScreen = SubScreen.GALLERY }
                                        )
                                        Screen.CATEGORIES -> CategoriesScreen(
                                            posts = posts,
                                            onPostClick = { id ->
                                                selectedPostId = id
                                                viewModel.incrementClicks(id)
                                                currentSubScreen = SubScreen.POST_DETAIL
                                            }
                                        )
                                        Screen.FREE -> FreeScreen(
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
                                        Screen.TRENDING -> TrendingScreen(
                                            trendingPosts = trendingPosts,
                                            onPostClick = { id ->
                                                selectedPostId = id
                                                viewModel.incrementClicks(id)
                                                currentSubScreen = SubScreen.POST_DETAIL
                                            }
                                        )
                                        Screen.REQUEST -> RequestScreen(
                                            userProfile = userProfile,
                                            requests = requests,
                                            onSubmitRequest = { name, email, telegram, message, imageUrl ->
                                                viewModel.submitRequest(name, email, telegram, message, imageUrl)
                                            },
                                            onViewGallery = { currentSubScreen = SubScreen.GALLERY },
                                            onUpgradeClick = { currentSubScreen = SubScreen.PRICING }
                                        )
                                        Screen.FAVORITES -> {
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
                                        Screen.SETTINGS -> PreferencesScreen(
                                            preferences = preferences,
                                            userProfile = userProfile,
                                            onUpdateTheme = { theme, dark -> viewModel.updateTheme(theme, dark) },
                                            onUpdateContentFilter = { filter -> viewModel.updateContentFilter(filter) },
                                            onAddPreferredTag = { tag -> viewModel.addPreferredTag(tag) },
                                            onRemovePreferredTag = { tag -> viewModel.removePreferredTag(tag) },
                                            onUpdatePlan = { plan -> viewModel.updatePlan(plan) }
                                        )
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
