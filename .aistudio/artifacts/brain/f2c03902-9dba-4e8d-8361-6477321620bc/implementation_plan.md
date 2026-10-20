# Payment Screenshot Verification & Calendar-Month Request Quota Reset Plan

This plan resolves the two reported issues:
1. **Payment Failure & Proof Verification**: Ensures users cannot upgrade their tier without a verified payment, asks users to upload a payment screenshot (via Gallery to Imgchest API) whenever PayPal automatic verification fails or manual checkout is used, and provides the Admin with a dedicated **Payment Proofs** verification tab (plus Request list badges) to cross-verify screenshots and upgrade user tiers with 1 tap.
2. **Monthly Request Quota Reset on the 1st of Every Calendar Month**: Fixes why requests from a previous month remained stuck at `3/3` after a month change or when signing in with a different Gmail account.

---

## 1. Payment Screenshot Upload & Admin Cross-Verification

### Root Cause
- In `PricingScreen.kt`, clicking `"I Have Paid (Verify & Activate)"` in the PayPal dialog unconditionally called `onSelectPlan(target.name)` even when `PayPalClient.captureOrder` failed or when standard web checkout was used.
- `onSubmitPaymentProof` in `PricingScreen` was not wired in `JustFanAppRoot.kt` to persist payment proof records to Supabase.

### Solution & User Flow
- **PayPal Checkout Verification & Fallback Screenshot Upload (`PricingScreen.kt`)**:
  - When a user initiates PayPal payment, clicking **"Verify Automatic Payment"** checks `PayPalClient.captureOrder(orderId)`.
  - If the REST capture succeeds (`COMPLETED`), the user's tier is upgraded automatically.
  - If the payment fails, cannot be automatically verified, or uses the standard PayPal web checkout fallback, the dialog prompts the user to **Upload Payment Screenshot from Gallery** (automatically uploaded to the **Imgchest API** via `ImgchestUploader.uploadImageDirect`) along with an optional **Transaction ID / Payer Email**, and submit it via **"Submit Screenshot for Admin Verification"**.
- **Supabase Persistence (`JustFanRepository.kt` & `SupabaseClient.kt`)**:
  - Add `submitPaymentProof(tier, method, amount, proofImageUrl, note)` in `JustFanRepository` and `MainViewModel`, and wire it in `JustFanAppRoot.kt`.
  - Save payment proofs to `RequestfromApp` (bypassing the 3-request content quota) with `image_url = proofImageUrl` (Imgchest URL) and a structured message prefix:
    `[PAYMENT_PROOF] Tier: <Pro|Legendary> | Method: <PayPal|Google Pay UPI> | Amount: <amount> | Ref: <note>`
  - Filter out `[PAYMENT_PROOF]` entries from public/community content request feeds on `HomeScreen` and `RequestScreen`, while displaying the user's pending/verified payment submissions in `PricingScreen`.
- **Admin Cross-Verification UI (`AdminScreen.kt`)**:
  - Add a dedicated **Payments (`Payment Proofs`)** tab in `AdminScreen` (alongside `Users`, `Requests`, and `Posts CRUD`) and also display a distinct **Payment Proof** badge inside the `Requests` tab.
  - Each payment proof card displays:
    - User Name, Email, Requested Tier (`Pro` or `Legendary`), Payment Method (`PayPal` or `Google Pay UPI`), Amount, and Transaction Note.
    - Clickable **Imgchest Payment Screenshot thumbnail** + **"View Full Screenshot"** button so the Admin can inspect the receipt before approving.
    - **1-Tap "Verify & Upgrade to <Pro/Legendary>" button**: Immediately upgrades the user's tier in Supabase (`subscriptions` table with 31-day expiration for Pro or lifetime for Legendary, plus `users`/`profiles` tables) and marks the payment proof status as `fulfilled`.
    - **"Reject Payment"** button: Marks the payment proof as `rejected` with a reason without upgrading the user's tier.

---

## 2. Calendar-Month Request Quota Reset (1st of Each Month)

### Root Cause Analysis
Four issues caused previous-month requests to remain at `3/3` after the calendar month changed or when switching Gmail accounts:
1. **Stale Server RPC (`rpc/get_request_quota`)**: `SupabaseClient.fetchRequestQuota` called `rpc/get_request_quota` and computed `maxOf(rpcUsed, monthlyDeviceCount)`. Because the remote RPC counts historical device/user rows without resetting on the 1st of the calendar month, `rpcUsed` stayed at `3` and overrode the new month's `0` count.
2. **Rolling 31-Day Window vs. Calendar Month**: The client calculated `System.currentTimeMillis() - 31 days` instead of the **1st day of the current calendar month** (`YYYY-MM-01T00:00:00Z`), so requests made late in the previous month still counted in the new month.
3. **PostgreSQL Timestamp Parsing Fallback (`parseIsoToMillis`)**: Supabase returns timestamps like `2026-09-25 14:22:10.123+00` or `2026-09-25T14:22:10+00:00`. When `Instant.parse` failed on space-separated or short-offset strings, `parseIsoToMillis` defaulted to `System.currentTimeMillis()`, making old rows from previous months look like they were created *today*.
4. **Retroactive Device ID Patching on Login**: `syncDeviceBiometricToSupabase` patched all historical `RequestfromApp` rows for the email on every login.

### Solution
- **Strict 1st-of-Calendar-Month Window (`SupabaseClient.kt`)**:
  - Calculate the exact start of the current calendar month (`1st of current month at 00:00:00`) and the next reset date (`1st of next month`).
  - Query `RequestfromApp` filtering only rows created on or after the 1st of the current calendar month (`created_at=gte.<firstDayOfMonthIso>`) matching the user's `device_id`, `user_id`, or `email` (excluding `[PAYMENT_PROOF]` rows).
- **Authoritative Monthly Count**:
  - Use the verified current-calendar-month count from `RequestfromApp` as the authoritative `used` count for Free tier users (`limit = 3`, `remaining = (3 - currentMonthCount).coerceAtLeast(0)`), removing the `maxOf(rpcUsed, ...)` override that kept stale previous-month counts.
- **Robust Timestamp Parser**:
  - Update `parseIsoToMillis` in `SupabaseClient.kt` to normalize PostgreSQL timestamps (replacing space with `T`, normalizing `+00` to `Z` / `+00:00`, supporting `OffsetDateTime` and `LocalDateTime`) and return `0L` when a timestamp is blank or invalid so legacy rows are never misclassified as current-month requests.
- **Account Switch & Login Quota Refresh**:
  - When a user logs in with a different Gmail account or opens the app in a new calendar month, immediately refresh `fetchRequestQuota()` against the current calendar month (`YYYY-MM`) and display the next reset date (`Resets on <Month> 1`).
