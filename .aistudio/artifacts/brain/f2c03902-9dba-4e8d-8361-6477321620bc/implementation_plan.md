# Persistent Supabase Sync for Admin Manual Membership Plan Upgrades

Fix the **Manual Membership Plan Upgrade (Specific User)** workflow in the **Admin Dashboard (`Users` tab)** so that upgrading or changing any subscriber's plan (`Free`, `Pro`, or `Legendary`) is durably written to the remote Supabase database and automatically restored even after uninstalling and reinstalling the app.

## User Review & Critical Decisions

> [!IMPORTANT]
> **Root Cause Identified**:
> 1. **Foreign Key (`auth.users`) & RLS Rejections on `subscriptions` / `profiles`**: When a subscriber signs in with Google (or is added by email in the Admin Dashboard), their client-generated UUID does not exist in Supabase's `auth.users` table. As a result, `POST /rest/v1/subscriptions` fails with an `auth.users` foreign-key constraint error, while `PATCH /rest/v1/subscriptions` returns HTTP `200 OK` with an empty array `[]` (0 rows updated).
> 2. **False-Positive `[]` Patch Success**: `executePatch` previously treated any `200 OK` response—even an empty `[]` array affecting 0 rows—as a success, showing a green confirmation banner in the Admin UI even when nothing was saved in Supabase.
> 3. **Solution — Dual Remote Persistence & Verified Row Writes**: In addition to updating `subscriptions` and `profiles` (and verifying that returned JSON arrays are non-empty), `updateUserTier` will persist a foreign-key-free `[TIER_ASSIGNMENT]` ledger record in Supabase (`RequestfromApp` with `user_id = null` and `email = <subscriber_email>`) so that any subscriber's upgraded tier is 100% stored in Supabase and restored after app uninstall/reinstall.

- **Confirmed Decision 1**: Only the Admin (`reytherapper12@gmail.com`) can upgrade or modify a user's Membership Plan from the **Admin Dashboard**.
- **Confirmed Decision 2**: Upgraded tiers (`Pro` with 31-day expiration, `Legendary` with lifetime access, or `Free`) must persist in the remote Supabase database across app uninstalls, reinstalls, and logins by email or user ID.

---

## 1. Overview & Core Concept

- **What It Does**: Ensures that whenever the Admin upgrades a specific user in the **Admin Dashboard (`Users`, `Payments`, or `Requests` tab)**—whether by typing an email address, typing a user ID, or tapping a user row—the tier change is verified and persisted in Supabase so it survives complete app uninstallation and reinstallation.
- **Target Audience / Persona**:
  - **Admin (`reytherapper12@gmail.com`)**: Needs reliable, verifiable database persistence when upgrading subscribers from `Free` to `Pro` or `Legendary`.
  - **Subscribers**: Expect their upgraded `Pro` or `Legendary` membership to remain active in their account even if they uninstall and reinstall the app or switch devices.
- **Key Value**: Eliminates silent 0-row database updates and foreign-key rejections so manual tier upgrades work reliably for every user account type (Google Sign-In, Email/Password, or pre-registration email).

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Admin Manual Upgrade (`Users` Tab)**:
     - Admin enters a user's email address (e.g., `user@gmail.com`) or User ID—or taps a user card in the list—and clicks **`Set Free`**, **`Upgrade Pro`**, or **`★ Legendary`**.
     - The Admin Dashboard displays a live `"Syncing tier to Supabase database..."` status and only shows `"Upgraded <email> to Legendary in Supabase database! ✓"` after confirming that a non-empty record was written to Supabase.
     - The subscriber list immediately re-fetches from Supabase so the Admin sees the real database state reflected in the `Subscriber Tier Breakdown` counters and user row badge.
  2. **Uninstall & Reinstall Recovery Flow**:
     - When the upgraded user (or Admin) uninstalls and reinstalls the app—wiping all local Room tables and `SharedPreferences`—and signs back in with their email or Google account, the app queries Supabase during startup and login (`syncData()` and `syncUserDataOnLogin()`), reads their remote tier record, and immediately unlocks their `Pro` or `Legendary` membership.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Verified Non-Empty Supabase Writes + Foreign-Key-Free Remote Tier Ledger**
  - *Chosen Approach*:
    1. Check that `PATCH` operations on `subscriptions` and `profiles` return at least 1 affected row (`JSONArray(responseBody).length() > 0`) before considering them successful.
    2. Always upsert a canonical `[TIER_ASSIGNMENT]` record in Supabase's `RequestfromApp` table keyed by the subscriber's lowercase email (with `user_id` omitted if not a registered `auth.users` UUID so PostgreSQL foreign-key constraints never block the write).
    3. Filter `[TIER_ASSIGNMENT]` rows out of the user-facing/Admin content requests list and monthly quota counter while reading them in `fetchSubscriptions()`, `fetchUserSubscriptionDetails()`, and `fetchProfiles()`.
  - *Why*: `subscriptions.user_id` enforces a strict foreign key to `auth.users(id)`, which rejects Google Sign-In accounts that use deterministic client UUIDs or emails upgraded prior to Supabase password signup. Backing `subscriptions` with a foreign-key-free remote ledger in Supabase guarantees 100% persistence across uninstall/reinstall for every email address.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

```
┌────────────────────────────────────────────────────────────────────────────┐
│                   ADMIN DASHBOARD (Users / Payments Tabs)                  │
│  • Enter subscriber Email/UUID or tap subscriber row                       │
│  • Click [Set Free] / [Upgrade Pro] / [★ Legendary]                        │
└───────────────────────────────────────┬────────────────────────────────────┘
                                        │
                                        ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                  JustFanRepository.updateUserTier(...)                     │
│  1. Update local Room UserDao + active UserProfile (if matching email/ID)  │
│  2. Call SupabaseClient.updateUserTier(targetIdOrEmail, tier, token, email)│
│  3. Re-fetch Supabase profiles & active subscription details to verify     │
└───────────────────────────────────────┬────────────────────────────────────┘
                                        │
                    ┌───────────────────┴───────────────────┐
                    ▼                                       ▼
┌───────────────────────────────────────┐ ┌──────────────────────────────────┐
│   Supabase `subscriptions` / `profiles`│ │ Supabase Foreign-Key-Free Ledger │
│  • Verify PATCH returns > 0 rows      │ │ (`RequestfromApp` [TIER_ASSIGN]) │
│  • Insert/Update by resolved UUID     │ │  • Keyed by subscriber `email`   │
│  • Sync `plan` & `expires_at`         │ │  • Stores `Tier`, `ExpiresAt`,   │
│                                       │ │    and `TargetId` without FK fail│
└───────────────────┬───────────────────┘ └─────────────────┬────────────────┘
                    └───────────────────┬───────────────────┘
                                        ▼
┌────────────────────────────────────────────────────────────────────────────┐
│          UNINSTALL & REINSTALL RECOVERY (Startup & Login Sync)             │
│  • `fetchSubscriptions()` + `fetchUserSubscriptionDetails()` +             │
│    `fetchProfiles()` merge `subscriptions`, `[TIER_ASSIGNMENT]` ledger,    │
│    and `profiles` so upgraded tier is restored on fresh install            │
└────────────────────────────────────────────────────────────────────────────┘
```

### Interactive Component & State Mapping
- **`SupabaseClient.updateUserTier`**:
  - Resolves both `cleanEmail` and `targetUuid` (inspecting existing `profiles`, `subscriptions`, and `RequestfromApp` rows if only a UUID or only an email was passed).
  - Writes to `subscriptions` and `profiles`, verifying `hasAffectedRows(responseBody)` (`JSONArray.length() > 0`) on `PATCH` before treating it as updated.
  - Upserts a `[TIER_ASSIGNMENT]` row in `RequestfromApp` (`email = cleanEmail`, `name = "TIER_ASSIGNMENT"`, `message = "[TIER_ASSIGNMENT] Tier: $normalizedTier | ExpiresAt: ${expiresAtStr ?: "never"} | TargetId: $targetUuid"`, `status = "tier_$lowerTier"`), retrying without `user_id` if a foreign-key constraint error occurs.
- **`SupabaseClient.fetchSubscriptions` & `fetchUserSubscriptionDetails` & `fetchProfiles`**:
  - Reads both `subscriptions` and `[TIER_ASSIGNMENT]` rows from `RequestfromApp`, indexing tiers by both `user_id` and lowercase `email` so `fetchProfiles()` (Admin `Users` tab) and `fetchUserSubscriptionDetails()` (logged-in subscriber session) always resolve the upgraded tier after an app uninstall and reinstall.
- **`SupabaseClient.fetchRequests` & `fetchRequestQuota`**:
  - Excludes `[TIER_ASSIGNMENT]` system rows from the Admin `Requests` / `Payments` lists and from the 3-request monthly quota calculation.
