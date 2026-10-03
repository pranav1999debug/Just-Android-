# JUSTFAN — Trending Creator Content Gallery & Showcase (Android)

JUSTFAN is a modern, high-performance Android mobile application built with **Kotlin** and **Jetpack Compose** that allows users to discover, search, bookmark, and download trending creator content and cosplay galleries.

## Features

- **Home Feed**: Live community delivered requests ticker, trending creator carousel, discover section with instant shuffle, hashtag chips filter, and responsive content grid.
- **Post Detail Screen**: High-res featured imagery, lightbox photo viewer for multi-image sets (`content_images`), expandable descriptions, direct free & 4K premium download links, view & like counters, previous/next gallery navigation, similar recommendations, and community comments.
- **Categories**: Browse creator content organized by hashtags (#cosplay, #fashion, #portrait, #fitness, #anime, #exclusive) with expandable rows and count badges.
- **Trending**: Real-time popularity ranking based on download clicks and user likes.
- **100% Free**: Dedicated catalog of freely downloadable creator sets with instant search.
- **Community Request Hub**: Submit requests for creators or photosets with image references, request limit tracker (3/week for Free, Unlimited for Pro), and status tracking (Pending, In Progress, Delivered).
- **Delivered Requests Gallery**: Public archive of fulfilled community requests with verified download links.
- **Favorites & Collections**: Save favorite sets locally and organize them into custom named collections.
- **Preferences & Themes**: Supports OLED Dark Mode, 7 accent theme swatches (Cyan, Orange, Blue, Golden, Rose, Violet, Emerald), SFW / NSFW content filtering, and preferred tag prioritization.
- **Membership & Pricing**: Detailed tier comparison (Free, Pro, Legendary) with instant upgrade simulation.
- **Activity & Notifications**: Real-time stream of newly published galleries, completed requests, and community interactions.
- **Admin Control Center**: Built-in management suite to publish new galleries, update request fulfillment statuses, and inspect live analytics.

## Tech Stack & Architecture

- **Language**: Kotlin 2.0
- **UI Toolkit**: Jetpack Compose with Material Design 3 (M3)
- **Local Persistence**: Android Room Database with reactive Kotlin Flows
- **Architecture**: MVVM (Model-View-ViewModel) with Clean Repository pattern
- **Image Loading**: Coil Compose
- **Adaptive Layout**: Responsive adaptation for standard phones, foldables, and wide-screen tablets (NavigationRail)
- **App Launcher Icon**: Custom adaptive icon with generated high-resolution assets and circular launcher mask

## Project Structure

```
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/justfan/
│       │   ├── JustFanApp.kt
│       │   ├── MainActivity.kt
│       │   ├── data/
│       │   │   ├── local/ (AppDatabase, Daos, Converters)
│       │   │   ├── model/ (PostEntity, RequestEntity, FavoriteEntity, etc.)
│       │   │   └── repository/ (JustFanRepository)
│       │   └── ui/
│       │       ├── JustFanAppRoot.kt
│       │       ├── MainViewModel.kt
│       │       ├── components/ (ContentCard, TrendingCarousel, RequestTicker, TopHeader)
│       │       ├── screens/ (HomeScreen, PostDetailScreen, CategoriesScreen, FreeScreen, TrendingScreen, RequestScreen, GalleryScreen, FavoritesScreen, PreferencesScreen, PricingScreen, NotificationsScreen, AdminScreen)
│       │       └── theme/ (Color, Theme, Type)
│       └── res/
│           ├── drawable/
│           ├── mipmap-*/
│           └── values/ (strings.xml, colors.xml, themes.xml)
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── build.gradle.kts
├── settings.gradle.kts
└── metadata.json
```
