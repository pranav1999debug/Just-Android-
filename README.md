# JUSTFAN — Trending Creator Content Gallery & Showcase (Android)

JUSTFAN is a high-performance Android mobile application built with **Kotlin** and **Jetpack Compose** that connects directly to a live **Supabase** backend to discover, stream, bookmark, and download creator content, videos, and cosplay galleries.

---

## 🌟 Key Features

- **Live Supabase Synchronization**: Real-time cloud sync with live tables (`posts`, `profiles`, `post_comments`, `post_clicks`, `favorites`, `requests`, `collections`).
- **Offline-First Architecture**: Android Room SQLite caching ensures instant screen loads, zero latency, and seamless offline browsing.
- **Built-in ExoPlayer Video Streaming**: Hardware-accelerated `.mp4` video playback with custom User-Agent, Referer, and Supabase authorization header injection for CDN compatibility (Catbox, Supabase Storage, ImageKit).
- **Multi-Image Lightbox Viewer**: Interactive gallery viewer with thumbnail strip and zoom capabilities.
- **Google Authentication & Account Management**:
  - One-tap sign in with Google account.
  - Interactive email selector for any `@gmail.com` address.
  - Supabase Browser Web OAuth flow with deep link redirect handling (`justfan://auth`).
  - Account switcher to toggle between user profiles.
- **Tier-Based Permissions & Quota Tracking**:
  - **Free Tier**: 3 requests quota with real-time remaining counter.
  - **Pro Tier**: 1-month unlimited requests with expiration date tracking.
  - **Legendary Tier**: Lifetime unlimited VIP access and Administrator badge.
- **Community Requests & Delivery Hub**: Submit requests with image references; track approval, progress, and download links for delivered requests.
- **Direct System Downloads**: Background downloading with Android system `DownloadManager`, MIME-type resolution, and completion notifications.
- **Admin Control Center**: Live management console for publishing posts, fulfilling requests, modifying user tiers, and inspecting system activity.
- **Customizable Personalization**: 7 Material 3 color themes, OLED dark mode, content safety filter (SFW/Unrestricted), and custom device wallpaper background with dimming controls.

---

## 🏗 Tech Stack & Architecture

- **Language**: Kotlin 2.0
- **UI Framework**: Jetpack Compose (Material Design 3)
- **Local Database**: Android Room with Kotlin Coroutines & Flow
- **Cloud Backend**: Supabase REST API & Auth (`https://zlboyxbqppoimhhbvrax.supabase.co`)
- **Video Engine**: AndroidX Media3 ExoPlayer (`androidx.media3.exoplayer`)
- **Image Pipeline**: Coil Compose (`io.coil-kt:coil-compose`)
- **System Downloads**: Android `DownloadManager` with broadcast receivers
- **Build System**: Gradle Kotlin DSL (`build.gradle.kts`) with Version Catalog (`libs.versions.toml`)

---

## 📁 Project Structure

```
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/justfan/
│       │   ├── JustFanApp.kt                  # Application initialization
│       │   ├── MainActivity.kt                # Main activity & deep-link receiver
│       │   ├── data/
│       │   │   ├── local/                     # Room Database, DAOs, and Converters
│       │   │   │   ├── AppDatabase.kt
│       │   │   │   ├── Converters.kt
│       │   │   │   └── dao/ (PostDao, RequestDao, ActivityDao, UserDao, etc.)
│       │   │   ├── model/                     # Data models and entities
│       │   │   │   ├── PostEntity.kt
│       │   │   │   ├── RequestEntity.kt
│       │   │   │   ├── CommentEntity.kt
│       │   │   │   ├── UserProfile.kt
│       │   │   │   └── UserPreferences.kt
│       │   │   ├── remote/                    # Supabase REST client & live sync engine
│       │   │   │   └── SupabaseClient.kt
│       │   │   └── repository/                # Two-way sync repository
│       │   │       └── JustFanRepository.kt
│       │   ├── ui/
│       │   │   ├── JustFanAppRoot.kt          # Scaffold, navigation rail, and bottom nav
│       │   │   ├── MainViewModel.kt           # State management
│       │   │   ├── components/                # Modular composables
│       │   │   │   ├── AuthDialog.kt          # Google Auth, Passkey, & Account Dialog
│       │   │   │   ├── VideoPlayerView.kt     # ExoPlayer video player
│       │   │   │   ├── ContentCard.kt         # Responsive post card
│       │   │   │   ├── TrendingCarousel.kt    # Top creator carousel
│       │   │   │   ├── RequestTicker.kt       # Live community delivered ticker
│       │   │   │   └── TopHeader.kt           # Search & filter bar
│       │   │   ├── screens/                   # Application screens
│       │   │   │   ├── HomeScreen.kt          # Feed & trending creators
│       │   │   │   ├── PostDetailScreen.kt    # Media player, download links, comments
│       │   │   │   ├── CategoriesScreen.kt    # Hashtag taxonomy browser
│       │   │   │   ├── FreeScreen.kt          # 100% Free content catalog
│       │   │   │   ├── TrendingScreen.kt      # Popularity-ranked leaderboard
│       │   │   │   ├── RequestScreen.kt       # User request submission & tracking
│       │   │   │   ├── GalleryScreen.kt       # Fulfilled requests gallery
│       │   │   │   ├── FavoritesScreen.kt     # Bookmarks & custom collections
│       │   │   │   ├── PreferencesScreen.kt   # Themes, wallpapers, & account
│       │   │   │   ├── PricingScreen.kt       # VIP membership tier benefits
│       │   │   │   ├── NotificationsScreen.kt # Live activity notifications
│       │   │   │   └── AdminScreen.kt         # Post & request admin console
│       │   │   └── theme/                     # Dynamic Material 3 color palettes
│       │   │       ├── Color.kt
│       │   │       ├── Theme.kt
│       │   │       └── Type.kt
│       │   └── util/
│       │       └── DownloadCompletedReceiver.kt # Download notification receiver
│       └── res/
│           ├── drawable/                      # Vector drawables & assets
│           ├── mipmap-*/                      # Adaptive launcher icons
│           └── values/                        # Strings, colors, styles
├── gradle/
│   ├── libs.versions.toml                     # Centralized dependency catalog
│   └── wrapper/
├── build.gradle.kts                           # Root Gradle build script
├── settings.gradle.kts                        # Settings & project name
├── metadata.json                              # AI Studio app metadata
└── README.md
```

---

## 🚀 How to Push to GitHub

### Option 1: Direct from Google AI Studio (Recommended)
1. In the top-right header of Google AI Studio, click the **Settings / More Options** menu (or the **GitHub** icon).
2. Select **Push to GitHub** / **Export to Repository**.
3. Select your GitHub account and repository name to push all files directly with full commit history.

### Option 2: Using Git CLI
If you exported the project as a ZIP or are running Git locally:
```bash
# Add your remote GitHub repository
git remote add origin https://github.com/<your-username>/<your-repo-name>.git

# Push the main branch
git push -u origin main
```
