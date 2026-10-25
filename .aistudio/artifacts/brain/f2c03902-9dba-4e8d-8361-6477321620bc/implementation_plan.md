# Implementation Plan: Main Feed Pull-to-Refresh & OneSignal v5 Push Notifications

## 1. Main Feed Pull-to-Refresh (Paginated Content Re-Fetch)
- **Pull-to-Refresh Container**: Wrap the Home Feed (`HomeScreen`) in a Material 3 `PullToRefreshBox` bound to the live `isRefreshing` state and `onRefresh = { viewModel.refreshAll() }`.
- **Full Paginated Sync**: Pulling down from the top of the feed triggers a full multi-page re-fetch of `public.posts` (all 1,576+ items), `post_click_counts`, `upload_requests`, and user subscription status, displaying a themed loading indicator at the top of the feed until sync completes.

---

## 2. OneSignal Android SDK v5 Integration (`1fbba4bb-4446-4bae-829d-36a74cd95adb`)
- **Gradle Dependency Setup**:
  - Add the OneSignal Android SDK v5 dependency (`com.onesignal:OneSignal:[5.1.6, 5.1.99]`) to the version catalog and app module dependencies.
- **Application Initialization (`JustFanApp`)**:
  - Configure `OneSignal.consentRequired = false` (or configurable privacy consent) and call `OneSignal.initWithContext(this, "1fbba4bb-4446-4bae-829d-36a74cd95adb")` inside `Application.onCreate()`.
  - Register a notification click listener and foreground lifecycle listener so incoming push notifications are also logged into the in-app **Activity & Notifications** feed.
- **Manifest & Android 13+ Runtime Permission (`POST_NOTIFICATIONS`)**:
  - Ensure `android:name=".JustFanApp"` and `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` are declared in the manifest.
  - Automatically invoke `OneSignal.Notifications.requestPermission(true)` inside a coroutine (`LaunchedEffect`) right after the user signs in or unlocks the app security gate, and bind the signed-in user's identity/email (`OneSignal.login(userId)`) and tags.

---

## 3. OneSignal Triggers & Broadcast Push Notifications (All Subscribed Users)
Trigger both OneSignal SDK In-App Triggers (`OneSignal.InAppMessages.addTriggers(...)`) and OneSignal Push Notification broadcasts to all subscribed users (plus local Android system notification & in-app Activity feed entry) on all three events:
1. **New Post Published (`new_post`)**:
   - Fired automatically when a new gallery post is created.
   - Sets OneSignal trigger `new_post` and broadcasts a push notification with the post title, creator name, and cover image to all subscribed users.
2. **New Content Request Submitted (`new_request`)**:
   - Fired automatically when a user submits a new creator request.
   - Sets OneSignal trigger `new_request` and broadcasts a push notification with the requester name and request summary to all subscribed users.
3. **New Payment Submitted (`new_payment`)**:
   - Fired automatically when a user submits a membership payment proof (`Pro` or `Legendary`).
   - Sets OneSignal trigger `new_payment` and broadcasts a push notification with the membership tier, payment method, and amount to all subscribed users.

---

## 4. Reference Code & Android 13+ Documentation
- Along with implementing all changes directly in the app, provide the exact copy-pasteable snippets you requested for:
  1. Gradle dependency configuration
  2. `Application` subclass initialization with `OneSignal.initWithContext` and `OneSignal.consentRequired`
  3. `AndroidManifest.xml` registration
  4. Coroutine-based `OneSignal.Notifications.requestPermission(true)` call
  5. Notes on Android 13+ (API 33+) `POST_NOTIFICATIONS` behavior
