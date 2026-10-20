# JustFan — Full Post CRUD, Biometric Device Tracking, Successful Requests Showcase & Trending Fix

Deliver complete Post CRUD (Create, Edit, Update, Delete) with Imgchest cover and multi-media gallery uploads, Google Authentication paired with Biometric and hardware device-fingerprint tracking in Supabase (enforcing a 3-request limit per device for free users), a Homepage showcase of fulfilled user requests with reference links, and an accurate "Trending Now" carousel backed by the `post_click_counts` table.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following product and architectural choices have been confirmed from your answers and the live Supabase database schema:

- **Confirmed Decision 1 — Post CRUD Accessibility & Media Hosting**: Full Post Create, Edit, Update, and Delete capabilities will be accessible both directly from the **Post Detail page** (via an Edit Post modal matching your web form and a Delete action) and within the **Admin Dashboard (Upload & Manage tabs)** for admin accounts (`reytherapper12@gmail.com`). Cover media (single image/video) and content media (multiple images/videos) will upload directly to the **Imgchest API**, retrieve the hosted URLs, and persist them to the `posts` table (`image_url` and `content_images` array).
- **Confirmed Decision 2 — Google Auth, Biometric Prompt & 3-Request Device Limit**: Users first authenticate with **Supabase Google Auth**, then are prompted to enable **Biometric Authentication** (`BiometricPrompt`). Upon verification, the app collects a deterministic hardware device fingerprint (Android ID, model, manufacturer, OS version, and biometric status) and syncs it to Supabase (`RequestfromApp.device_id` and user device metadata) so free users are strictly limited to **3 requests per device** across sessions.
- **Confirmed Decision 3 — Free Users "Successful Uploads" Homepage Section**: A dedicated compact carousel on the **Homepage** will display fulfilled/replied requests (`status = "replied"` or `"completed"`) from `RequestfromApp`, rendering the user-submitted request thumbnail (`image_url`) alongside a clickable reference link extracted from the request's social/reference URL (`message` / `download_link`).
- **Confirmed Decision 4 — Trending Now Bug Fix & Click Tracking**: The `post_click_counts` table in Supabase stores counts under the `count` column (`post_id`, `count`). The app will query `post_click_counts?select=post_id,count&order=count.desc`, record every post open/click into `post_clicks` (and increment the local & remote count immediately), and style the **Trending Now** cards to match your reference screenshot (`#1` / `#2` green rank badges, `🔥 901` / `🔥 338` pill badges in the top-right, emerald border, and "View All >" header action).

---

## 1. Overview & Core Concept

- **What It Does**: Empowers administrators to create, edit, and delete rich media posts with direct Imgchest hosting for cover and gallery images/videos, secures user requests via Google OAuth + Android Biometrics + device fingerprinting (capping free users at 3 requests per device), showcases fulfilled community requests on the Home screen, and ranks trending posts accurately from live click telemetry.
- **Target Audience / Persona**:
  - **Creators / Admins**: Need fast, mobile-native content publishing and editing with multi-file Imgchest uploads and link management (Free Download, Premium, and Direct/No-Ads links).
  - **Free & VIP Members**: Browse trending galleries, verify their device via Google Sign-In and Biometrics to submit up to 3 free creator requests, and see proof of fulfilled community requests directly on the homepage.
- **Key Value**: Eliminates broken trending counts, prevents request spam across account resets via hardware device tracking, and unifies web and Android admin workflows.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Post CRUD with Cover & Multi-Content Imgchest Uploads**:
   - **From Post Detail**: When signed in as Admin, the top action bar displays **Edit Post** and **Delete Post** buttons. Tapping **Edit Post** opens a full-screen editor sheet pre-populated with the post's title, cover image/video, content images/videos list, description, Free Download Link, Premium Link, Direct Download / No Ads Link, comma-separated tags, section dropdown (`home`, `free`, etc.), Free post toggle, and NSFW toggle—matching your uploaded screenshot.
   - **Cover & Content Media Pickers**: Tapping **Choose File** under Cover Media uploads the selected image/video to `api.imgchest.com/v1/post` and sets `image_url`. Tapping **Choose Files** under Content Gallery allows selecting multiple images/videos at once, batch-uploading them to Imgchest, displaying removable preview thumbnails, and saving the URL array into `content_images`.
   - **From Admin Upload & Manage Tabs**: Admins can create new posts or edit/delete existing posts with the exact same multi-media Imgchest workflow.

2. **Google Sign-In -> Biometric Setup -> Device Fingerprint & Request Quota**:
   - **Step 1 (Google Auth)**: User signs in via Google OAuth (deep-linked back into the app with a Supabase JWT session) or Google account credential selector.
   - **Step 2 (Biometric Enrollment Prompt)**: Immediately after Google login (or before submitting a request), the app presents an "Enable Biometric Verification" dialog triggering Android's native `BiometricPrompt` (Fingerprint / Face Unlock / Device Credential fallback).
   - **Step 3 (Device Details & Quota Tracking)**: Once verified, the app captures the device fingerprint (`ANDROID_ID`, brand, model, SDK level, biometric status) and syncs it with Supabase. On the **Request Page**, the user sees a live badge showing **`X / 3 Free Requests Used on This Device`**. Submitting a request attaches `device_id` to `RequestfromApp`; once 3 requests are recorded for that `device_id`, free users see an upgrade prompt instead of submitting additional requests.

3. **Free Users "Successful Uploads" Homepage Section**:
   - Positioned on the **Homepage** for free users, a compact horizontal section titled **Successful Uploads (Community Requests)** displays fulfilled requests from `RequestfromApp` (`status = "replied"` or `"completed"`).
   - Each compact card shows the small user-provided request image (`image_url`), a **"Fulfilled"** emerald badge, the creator handle/name, and a clickable **Reference Link** chip that opens the social/reference URL provided by the requester.

4. **Trending Now Section & Live Click Count Updates**:
   - Matches the uploaded reference design: **"Trending Now"** header with a green trending arrow icon and **"View All >"** action on the right.
   - Cards feature a rounded emerald border (`#10B981`), a circular emerald rank badge (`#1`, `#2`, `#3`) at the top-left, a dark translucent pill badge at the top-right with a flame icon and exact click count (`🔥 901`, `🔥 338`), and bold two-line title overlay at the bottom.
   - Whenever any user clicks on a post (from Trending, Discover, Home grid, or Free page), the app immediately records the click to Supabase (`post_clicks` / `post_click_counts`) and increments the in-memory counter so rankings stay real-time.

### Visual Identity & Theme
- **Aesthetic Direction**: Luxury Dark Vault & Emerald Neon (`#0B0F17` deep obsidian surfaces, `#10B981` emerald accents, `#F59E0B` warm flame/gold highlights).
- **Component Styling**: Dark slate cards (`#151C24`) with `1.5.dp` emerald borders on Trending cards, pill-shaped status badges, and clean outlined form controls matching your web admin editor screenshot.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Fixing `post_click_counts` Schema Mapping**
  - *Chosen Approach*: Query `post_click_counts?select=post_id,count&order=count.desc` and read `obj.optInt("count", 0)` instead of `clicks_count`. When a user clicks any post, insert into `post_clicks` (`post_id`, `user_id`) which triggers the Supabase count aggregation, and also support direct upsert/increment fallback on `post_click_counts`.
  - *Why*: Live inspection of your Supabase database confirmed the column in `post_click_counts` is named `count` (with top rows `901`, `338`, `276`), whereas the app previously queried a non-existent `clicks_count` column and fell back to `0`.

- **Decision 2: Hardware Device Tracking + Biometric Binding in Supabase**
  - *Chosen Approach*: Combine hardware `Settings.Secure.ANDROID_ID` + `Build.MANUFACTURER` + `Build.MODEL` with `BiometricPrompt` verification, storing the device record in Supabase (`user_preferences` / `profiles` metadata and `RequestfromApp.device_id`) and counting rows in `RequestfromApp?device_id=eq.<deviceId>` to enforce the 3-request limit for free users.
  - *Why*: `RequestfromApp` already has a `device_id` column in your live Supabase schema, making per-device request counting immediate, tamper-resistant across account sign-outs, and compatible with existing RLS policies.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌───────────────────────────────────────────────────────────────────────────┐
│                         UI LAYER (Jetpack Compose)                        │
│                                                                           │
│  ┌──────────────────────┐  ┌──────────────────────┐  ┌─────────────────┐  │
│  │      HomeScreen      │  │   PostDetailScreen   │  │  RequestScreen  │  │
│  │  • TrendingNowRow    │  │  • Media Gallery     │  │  • Biometric    │  │
│  │    (#1, 🔥 901)      │  │  • Admin Edit Modal  │  │    Gate Banner  │  │
│  │  • SuccessfulUploads │  │  • Admin Delete Btn  │  │  • Device Quota │  │
│  │    (Free Users Row)  │  │  • 3 Download Links  │  │    (X/3 Used)   │  │
│  │  • Discover & Grid   │  └──────────┬───────────┘  └────────┬────────┘  │
│  └──────────┬───────────┘             │                       │           │
│             │                         ▼                       │           │
│  ┌──────────┴─────────────────────────────────────────────────┴────────┐  │
│  │              AdminScreen & PostEditorModal + AuthDialog             │  │
│  │  • Cover Image/Video Picker ──► Imgchest API (`/v1/post`)           │  │
│  │  • Multi-Content Picker     ──► Imgchest Batch API (`images[]`)     │  │
│  │  • Google OAuth + BiometricPrompt Enrollment Dialog                 │  │
│  └────────────────────────────────────┬────────────────────────────────┘  │
└───────────────────────────────────────┼───────────────────────────────────┘
                                        ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                   STATE & REPOSITORY LAYER (MVVM)                         │
│                                                                           │
│  MainViewModel ◄──► JustFanRepository ◄──► DeviceBiometricManager         │
│   • posts / trendingPosts                  • Hardware Device ID & Model   │
│   • successfulRequests                     • BiometricPrompt Verification │
│   • deviceRequestCount (max 3 for Free)    • Supabase Device Sync         │
└───────────────────┬───────────────────────────────────────┬───────────────┘
                    ▼                                       ▼
┌────────────────────────────────────────┐ ┌────────────────────────────────┐
│        SUPABASE REST & AUTH API        │ │         IMGCHEST V1 API        │
│  • `posts` (CRUD + `content_images`)   │ │  • `POST /v1/post`             │
│  • `post_click_counts` (`post_id,count`)│ │  • `POST /v1/post/{id}/add`    │
│  • `post_clicks` (insert on any click) │ │  • Returns hosted media URLs   │
│  • `RequestfromApp` (`device_id`,      │ └────────────────────────────────┘
│     `image_url`, `message`, `status`)  │
└────────────────────────────────────────┘
```

### Interactive Component & State Mapping

1. **Trending Click Telemetry Flow**:
   - Any post click on `HomeScreen`, `TrendingCarousel`, `DiscoverSection`, or `FreeContentScreen` invokes `viewModel.recordPostClick(post.id)`.
   - `JustFanRepository.recordPostClick` immediately increments `clicksCount` in the local `StateFlow` (instant UI feedback) and asynchronously posts `{ "post_id": postId, "user_id": currentUserId }` to `post_clicks` (plus updates `post_click_counts`), keeping `TrendingCarousel` sorted by descending `count`.
2. **Post CRUD & Imgchest Upload Flow**:
   - Selecting a cover image/video calls `ImgchestUploader.uploadImageDirect(context, uri)` and populates `imageUrl`.
   - Selecting multiple content images/videos calls `ImgchestUploader.uploadImagesDirect(context, uris)` and appends all returned Imgchest URLs to `contentImages`.
   - Saving calls `SupabaseClient.insertPost` (new) or `SupabaseClient.updatePost` (existing `posts?id=eq.<id>`) with `image_url`, `content_images`, `link_url`, `premium_link_url`, `direct_link_url`, `tags`, `section`, `is_free`, and `is_nsfw`, then refreshes the repository state.
   - Deleting calls `SupabaseClient.deletePost(postId)` and removes the post from memory and Room cache.
3. **Google Auth + Biometric + Device Quota Flow**:
   - After Google Sign-In completes, if biometric verification is not yet enabled for the user, the app shows the **Enable Biometric Security** prompt using `androidx.biometric.BiometricPrompt`.
   - Upon success, `DeviceBiometricManager` computes the device fingerprint (`ANDROID_ID` + device metadata), stores the verified state in encrypted/shared prefs and Supabase, and queries `RequestfromApp?select=id&device_id=eq.<deviceId>` to compute the user's remaining free requests out of 3.
4. **Homepage Successful Uploads Flow**:
   - `SupabaseClient.fetchRequests()` loads `RequestfromApp` rows; `HomeScreen` filters for rows with `status in ("replied", "completed")` and a valid `image_url`, parses the reference URL from `message` (e.g., `Social: https://...`), and renders a compact horizontal showcase strip with one-tap external link opening.
