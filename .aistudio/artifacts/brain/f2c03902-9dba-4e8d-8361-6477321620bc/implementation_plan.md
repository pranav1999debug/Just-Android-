# Version 3.0 Database Gate & In-App Update Installer Plan

## 1. Objective
Upgrade JustFan to **Version 3.0 (`versionCode = 3`, `APP_VERSION = 3`)** in both the Android app and the Supabase database so that all previous app versions (`v1` and `v2`) are blocked from accessing any database contents, and provide an automatic database version check with an in-app APK download progress tracker and installation flow.

---

## 2. Supabase Database Version Gate (`v3.0`) & Full Content Blocking
1. **Bump Database Version Records to `3` / `3.0`**:
   - Update `supabase/migrations/20261026000000_version_gate.sql` so `public.app_config (id = 1)` sets `min_version = 3` and `latest_version = 3` with `update_url = 'https://github.com/pranav1999debug/Just-Android-/releases/latest/download/app-debug.apk'`.
   - Update `supabase/migrations/20261011000000_create_app_version.sql` (and include in the main SQL script) so `public.app_version` records version `'3.0'` with the latest APK download link.
2. **Gate All Content Tables with Restrictive RLS (`public.app_version_ok()`)**:
   - Apply `create policy "version gate" on <table> as restrictive for all using (public.app_version_ok());` across **all** database tables so previous versions (`x-app-version < 3`) cannot read or write any database content:
     - `public.posts`
     - `public.post_clicks`
     - `public.profiles`
     - `public.subscriptions`
     - `public.users`
     - `public.user_preferences`
     - `public.app_secrets`
     - `public.upload_requests`
     - `public."RequestfromApp"`
     - `public.user_devices`
   - Keep `public.app_config` and `public.app_version` ungated (`for select using (true)`) so outdated clients can always check the latest database version and retrieve the download link.

---

## 3. App Version Bump & Automatic Database Version Check
1. **Bump Client Version Constants to `3` (`3.0`)**:
   - `app/build.gradle.kts`: Set `versionCode = 3` and `versionName = "3.0"`.
   - `AppVersionConfig.kt`: Set `APP_VERSION = 3` and `APP_VERSION_NAME = "3.0"`.
2. **Dual-Table & Live Database Version Verification (`VersionGateManager`)**:
   - Check `public.app_config` (`min_version`, `latest_version`, `update_url`, `update_message`) and also cross-check `public.app_version` (`version`, `download_link`) on app launch, on foreground resume, and before `JustFanRepository.syncData()`.
   - Add `syncDatabaseToLatestVersion()` so the database `app_config` (`min_version = 3`, `latest_version = 3`) and `app_version` (`3.0`) can also be updated directly from the app (automatically when an admin is signed in or via a 1-tap **"Lock Database to v3.0"** button in the Admin panel).
3. **Strict App-Side Content Guard**:
   - In `JustFanRepository.syncData()`, verify `VersionGateManager.checkVersionGate()` before loading or syncing posts, requests, or profiles. If the installed app version is lower than the database's `min_version` or `latest_version` requirement, abort all data fetches and display the full-screen Update Required prompt.

---

## 4. In-App APK Download Progress & Install Prompt (`VersionGateComponents.kt`)
1. **In-App Download Progress Tracker**:
   - Upgrade `UpdateRequiredScreen` and `UpdateAvailableBanner` so tapping **"Download & Install Latest Version (v3.0)"** starts an in-app APK download via Android `DownloadManager` while polling download progress (`bytesDownloaded / totalBytes`) in real time.
   - Display a live progress bar, percentage, and downloaded megabytes directly on the Update Required card.
2. **1-Tap Install & Browser Fallback**:
   - When the APK download completes, automatically trigger the package installation / Downloads view intent and display a prominent **"Install Downloaded APK"** button, alongside a **"Download via Browser"** fallback button.
