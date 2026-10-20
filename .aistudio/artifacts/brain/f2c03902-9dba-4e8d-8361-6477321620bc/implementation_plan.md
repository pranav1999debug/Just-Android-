# PayPal Tier Sync, Blurred Startup Auth/Biometric Gate & Request Page Redesign

This plan resolves the PayPal checkout failure, automates tier synchronization and 1-month Pro expiration across the `subscriptions` and `users` tables in Supabase, enforces a blurred first-launch Login and Biometric unlock gate, and redesigns the Request screen with Gallery-only Imgchest uploads and card-styled Community and Personal request feeds.

### User Review & Critical Decisions

> [!IMPORTANT]
> Confirmed decisions from your responses:

- **Confirmed Decision 1 (PayPal Checkout & Automatic Tier Upgrade)**: Fix the PayPal REST V2 Orders API amount formatting (`10.00` USD for Pro, `20.00` USD for Legendary instead of raw `"10 USDT"` strings), load PayPal credentials from `app_secrets` when available, and replace the broken `checkoutnow?token=JUSTFAN_...` URL with a valid PayPal Standard Web Checkout (`https://www.paypal.com/cgi-bin/webscr?cmd=_xclick`) fallback so PayPal never shows the generic error screen. Upon payment verification, the user's tier is automatically updated in Supabase (`subscriptions` and `users`/`profiles` tables).
- **Confirmed Decision 2 (Startup Blurred Login & Biometric Gate Fallback)**: When the app opens, the main app content is blurred in the background. Unauthenticated users see the Login gate first; already signed-in users see the Biometric unlock gate first, with support for device PIN, pattern, or password (`BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL`) if no fingerprint is enrolled.

---

### 1. Overview & Core Concept

- **What It Does**:
  1. **PayPal Checkout & Automatic Tier Lifecycle**: Fixes PayPal order creation and checkout links, automatically updates the user's tier in Supabase (`subscriptions` and `users` tables) after payment, enforces 1-month automatic expiration for **Pro** back to **Free**, and ensures all 3 download buttons (**Download (Ads)**, **Premium**, and **Direct Download**) remain visible on the Post Detail page—routing **Free** users to the Membership payment page when they tap **Direct Download** and upgrading their tier upon payment.
  2. **Blurred Startup Login & Biometric Gate**: Displays a full-screen frosted/blurred backdrop over the app on launch, requiring unauthenticated users to sign in first and already signed-in users to pass Biometric / Device Credential verification before accessing app contents.
  3. **Tier-Aware Request Quotas (`subscriptions` & `users` tables)**: Enforces **3 requests per month** strictly for **Free** tier users while granting **Pro** users unlimited requests for 1 month (until auto-expiry) and **Legendary** users unlimited requests for a lifetime.
  4. **Request Page Redesign & Gallery-Only Imgchest Upload**: Removes the manual image URL text box so users upload exclusively from their device gallery (automatically uploaded to Imgchest API to retrieve the URL), and reorders the Request page from top to bottom:
     - **Community Requests** (top, styled like the Homepage Successful Uploads cards)
     - **Your Personal Requests** (middle, requests made by the current signed-in user, styled with the same visual card format)
     - **Submit a Request Form** (bottom, with Gallery-to-Imgchest upload).

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  - **App Launch Gate Flow**:
    1. User opens JUSTFAN; the underlying feed is rendered with a blur effect (`Modifier.blur(20.dp)`) and a dark translucent scrim.
    2. **If not signed in**: The full Login card (Google Auth, Email, Passkey) is presented immediately in the center over the blurred background.
    3. **If already signed in**: The Biometric Unlock prompt triggers automatically over the blurred background, allowing fingerprint/face unlock or device PIN/pattern fallback to unblur and enter the app.
  - **Post Detail 3-Button Download & Upgrade Flow**:
    1. Every post displays all 3 download buttons: **Download (Ads)**, **Premium**, and **Direct Download**.
    2. **Free Tier**: Can use **Download (Ads)** and **Premium** directly. Tapping **Direct Download** (never hidden) navigates them directly to the Membership payment screen; completing a **Pro** or **Legendary** payment immediately upgrades their tier in Supabase and unlocks **Direct Download**.
    3. **Pro Tier (1 Month) & Legendary Tier (Lifetime)**: Have immediate access to all 3 download buttons (**Download (Ads)**, **Premium**, and **Direct Download**).
  - **Request Screen Flow (Top-to-Bottom)**:
    1. **Community Requests** section at the top displays community requests in the rich thumbnail + status badge + clickable link card layout matching the Homepage's Successful Uploads section.
    2. **Your Personal Requests** section in the middle displays only the logged-in user's submitted requests in the same visual card format (showing status, thumbnail, admin reply/fulfilled link, or rejection reason).
    3. **Request Submission Form** at the bottom provides a single **Upload from Gallery** picker (no raw URL input field) that uploads the selected image to Imgchest API and automatically attaches the returned URL.
- **Visual Identity & Theme**:
  - Deep obsidian surfaces (`#0B0D12` / `#13161F`), emerald (`#10B981`) and gold (`#F59E0B`) tier badges, and frosted glass blur overlays on launch.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: PayPal Order Sanitization & Standard Checkout Fallback**
  - *Chosen Approach*: Strip non-numeric characters from plan prices (`"10 USDT"` $\rightarrow$ `"10.00"`, `"20 USDT"` $\rightarrow$ `"20.00"`), check `SecretsStore` (`paypal_client_id`, `paypal_client_secret`, `paypal_business_email`) in addition to configured credentials, and fall back to PayPal Standard Checkout (`https://www.paypal.com/cgi-bin/webscr?cmd=_xclick&item_name=...&amount=10.00&currency_code=USD`) instead of a synthetic `/checkoutnow?token=JUSTFAN_...` token.
  - *Why*: PayPal's `/checkoutnow?token=` endpoint strictly requires a server-minted EC-token; passing a custom string causes PayPal to redirect to `paypal.com/checkoutweb/genericError`. Standard `_xclick` checkout and properly formatted decimal USD amounts work reliably across mobile browsers.
- **Decision 2: Dual-Table Tier Verification & Automatic 1-Month Pro Expiration**
  - *Chosen Approach*: Query both `subscriptions` and `users` (alongside `profiles`) in Supabase during session initialization and quota checks. When a `Pro` plan's `expires_at` timestamp (created at +31 days / 1 month) is in the past, automatically patch both `subscriptions` and `users` back to `Free` in Supabase and update the local session state.
  - *Why*: Guarantees Pro memberships automatically revert to Free after 1 month without manual admin intervention, while Legendary memberships remain unlimited forever.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                       App Launch Blurred Gate (JustFanAppRoot)               │
│  ┌────────────────────────────────────┐  ┌────────────────────────────────┐  │
│  │ Guest User -> Full Login Gate      │  │ Signed-In User -> Biometric /  │  │
│  │ over Blurred Background            │  │ Device PIN Unlock Gate         │  │
│  └─────────────────┬──────────────────┘  └───────────────┬────────────────┘  │
└────────────────────┼─────────────────────────────────────┼───────────────────┘
                     ▼                                     ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                  Tier & Subscription Engine (SupabaseClient + Repo)          │
│  • Reads & syncs `subscriptions` + `users` + `profiles` tables               │
│  • Auto-downgrades expired `Pro` (> 1 month) -> `Free` in Supabase DB        │
│  • Free: 3 requests/month + 2 Download Buttons (Direct -> Payment Page)      │
│  • Pro (1 mo) & Legendary (Lifetime): Unlimited requests + All 3 Downloads   │
└──────────────┬───────────────────────────────────────────┬───────────────────┘
               ▼                                           ▼
┌─────────────────────────────────────────┐ ┌──────────────────────────────────┐
│ PricingScreen + PayPalClient            │ │ RequestScreen (Top-to-Bottom)    │
│ • Clean USD amount ("10.00" / "20.00")  │ │ 1. Community Requests Cards      │
│ • V2 Orders API + Standard Checkout     │ │ 2. Personal Requests Cards       │
│ • Auto-updates tier in Supabase DB      │ │ 3. Request Form (Gallery-only    │
│   on payment verification               │ │    Imgchest auto-URL upload)     │
└─────────────────────────────────────────┘ └──────────────────────────────────┘
```

- **Interactive Component & State Mapping**:
  - **Startup Security Gate**: `JustFanAppRoot` tracks `isSessionUnlocked` in state. While `!userProfile.isSignedIn || !isSessionUnlocked`, the main `Scaffold` is rendered with `Modifier.blur(20.dp)` and blocked by a full-screen gate overlay that either renders the Login flow (when signed out) or triggers `BiometricAuthManager.promptAppUnlock()` (when signed in, supporting `BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL`).
  - **PayPal & Tier Sync**: `PayPalClient.createOrder` normalizes the USD price to `10.00` or `20.00`. When the user completes PayPal checkout and taps verify/activate (or when `captureOrder` succeeds), `viewModel.updateUserTier(userProfile.id, target.name)` updates both `subscriptions` (setting `expires_at` = +1 month for `Pro`, `2099-12-31` for `Legendary`) and `users`/`profiles` in Supabase, immediately unlocking **Direct Download** and unlimited requests.
  - **Request Screen Layout & Gallery Upload**: `RequestScreen` renders **Community Requests** at the top and **Personal Requests** (filtered by `req.userId == userProfile.id || req.email.equals(userProfile.email, ignoreCase = true)`) second using the `SuccessfulUploadCard` visual design, followed by the **Submit a Request** form at the bottom with a gallery-only picker that uploads directly to `ImgchestUploader` and stores the returned URL automatically.
