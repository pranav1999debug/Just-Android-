# JustFan Version 2.0 Update Plan

## Overview
Ensure the JustFan Android app build configuration, version gate header constant, and Supabase SQL migration script are cleanly configured and synchronized for **Version 2.0** (`versionCode = 2`, `versionName = "2.0"`, `APP_VERSION = 2`) without adding extra version labels to the main UI screens.

---

## 1. Android Build Configuration & Version Gate Header
- **`app/build.gradle.kts`**:
  - Verify `versionCode = 2` and `versionName = "2.0"` in `defaultConfig`.
- **`app/src/main/java/com/example/justfan/data/remote/AppVersionConfig.kt`**:
  - Keep `const val APP_VERSION: Int = 2` and add `const val APP_VERSION_NAME: String = "2.0"` so version gate headers (`x-app-version: 2`) and version checks match `versionCode = 2`.
  - Default `min_version` and `latest_version` fallbacks in `fetchAppConfigFromSupabase()` to `AppVersionConfig.APP_VERSION` (`2`).

---

## 2. Supabase SQL Seed & Upsert (`supabase/migrations/20261026000000_version_gate.sql`)
- Update the `public.app_config` seed statement so that both fresh installs and existing databases where `id = 1` was previously created with `1` are updated to `min_version = 2` and `latest_version = 2`:
  ```sql
  insert into public.app_config (id, min_version, latest_version, update_url)
  values (1, 2, 2, 'https://github.com/pranav1999debug/Just-Android-/releases/latest/download/app-debug.apk')
  on conflict (id) do update set
    min_version = excluded.min_version,
    latest_version = excluded.latest_version,
    update_url = excluded.update_url;
  ```

---

## 3. Verification
- Run `compile_applet` to verify the Version 2.0 build compiles cleanly with no errors.
