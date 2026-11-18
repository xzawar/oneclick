**CLAUDE.md / AGENTS.md**

AI Coding Assistant Guardrails & Rules

────────────────────────────────────────────────────────────────────────────────

**1. Project Identity**

You are working on ONE CLICK --- an on-demand local services marketplace
Android app. Tech: Kotlin + Jetpack Compose + Android Studio (mobile),
Node.js + TypeScript + Fastify (backend), PostgreSQL + Redis (data),
Next.js (admin). The app is one APK with Customer Mode / Provider Mode
toggle, inspired by InDrive but for home and roadside services in
Pakistan (Lahore-first).

**2. Core Architecture Rules**

-   ONE APK only. Never create a separate app for providers or
    customers.

-   Mode toggle is in-app. user_role stored in PostgreSQL. active_mode
    sent as X-Active-Mode header.

-   All business logic is server-side. Never trust client values for
    wallet, escrow, or payments.

-   Wallet balance must always be read from PostgreSQL directly ---
    never from Redis or request body.

-   All financial transactions must use PostgreSQL ACID transactions
    (BEGIN / COMMIT / ROLLBACK).

-   Escrow is immutable while booking status is in_progress. Release
    only after both parties confirm.

-   JWT tokens verified on every protected route. Firebase Admin SDK
    verifies Firebase tokens.

-   Provider live location stored in Redis (TTL: 45s). Ping every 5s
    during an active job, every 20s while idle online. Synced to
    PostgreSQL every 30 seconds.

-   Android location tracking runs in a foreground service (location
    type) only while the provider is online or on a job. Stop pings when
    offline.

-   Every active booking has its own Socket.io room:
    booking:{bookingId}.

-   Socket.io must use the Redis adapter so the API can run more than
    one instance.

-   FCM (HTTP v1, high priority) handles push when socket is
    disconnected. Incoming Agora calls arrive via FCM and show a
    full-screen notification.

-   All file uploads go to Cloudflare R2. Never store files on VPS disk.

-   CNIC and selfie files are PRIVATE in R2. Always serve via signed URL
    (expires 5 minutes).

**3. Kotlin (Android) Rules**

**Style & Patterns**

-   Follow official Kotlin coding conventions.

-   Use data classes for API response models (DTOs).

-   Use sealed classes for UI states: Loading, Success, Error.

-   Use coroutines + Flow for async. No RxJava.

-   Use ViewModel + StateFlow for UI state management.

-   Use Hilt for dependency injection.

-   Jetpack Compose UI, single Activity, Navigation Compose. Local
    storage: Room + DataStore (never Hive --- it is a Flutter package).

**Naming Conventions**

> ViewModels: CustomerMapViewModel, ProviderJobViewModel,
> WalletViewModel
>
> Repositories: BookingRepository, WalletRepository, LocationRepository
>
> API Interface: ApiService (Retrofit)
>
> Screens: CustomerMapScreen, ProviderOnlineScreen, BookingDetailScreen
>
> Data classes: BookingDto, ProviderDto, WalletDto, UserDto
>
> Enums: UserRole, ActiveMode, BookingStatus, ProviderTier

**Security Rules**

-   Store JWT in Android Keystore --- never in plain SharedPreferences.

-   Camera selfie: use Intent.ACTION_IMAGE_CAPTURE only. Never allow
    gallery picker for selfie.

-   Never log sensitive data (tokens, phone, CNIC) in Logcat.

-   Handle socket disconnect gracefully --- show reconnecting banner,
    re-sync state on reconnect.

**4. Node.js (Backend) Rules**

-   Use TypeScript (strict mode) with ES Modules. No CommonJS require.

-   Use async/await everywhere. No raw .then() chains.

-   Use Fastify JSON Schema (or Zod) validation on all endpoints.
    Validate before processing.

-   Use \@fastify/rate-limit on all auth endpoints.

-   Use dotenv for all secrets. Never hardcode.

-   Use parameterized SQL queries always. Never string-concatenate SQL.

-   All DB queries via pg (node-postgres) with connection pool size 10.

-   Use PostGIS for all location radius queries.

-   Migrations in /migrations folder, numbered: 001_create_users.sql

**File Structure**

> src/routes/ --- Fastify route definitions
>
> src/controllers/ --- Route handler logic
>
> src/services/ --- Business logic (escrow, wallet, location)
>
> src/models/ --- PostgreSQL query functions
>
> src/middleware/ --- auth.ts, rateLimit.ts, validate.ts
>
> src/socket/ --- Socket.io event handlers
>
> src/utils/ --- helpers, formatters
>
> src/config/ --- db.ts, redis.ts, firebase.ts, r2.ts
>
> migrations/ --- Numbered SQL migration files

**5. What NOT to Do**

-   DO NOT create a second Android APK for providers.

-   DO NOT serve CNIC or selfie files publicly --- always signed R2
    URLs.

-   DO NOT read wallet balance from Redis or client payload.

-   DO NOT release escrow without both-party confirmation.

-   DO NOT use Twilio for calling --- use Agora Voice SDK.

-   DO NOT use masked numbers --- show profile name + role tag
    (Customer/Provider).

-   DO NOT store uploaded files on VPS disk --- all go to Cloudflare R2.

-   DO NOT use Flutter --- app is Kotlin.

-   DO NOT add OTP verification to phone field (add in V2).

-   DO NOT add JazzCash/EasyPaisa API calls --- manual screenshot flow
    for MVP.

-   DO NOT approve a wallet top-up from a screenshot alone --- the TID
    must match the business account statement.

-   DO NOT use the legacy FCM server key API --- use HTTP v1 through the
    Firebase Admin SDK.

-   DO NOT use Hive or Fragments/XML layouts --- the app uses Room,
    DataStore, and Jetpack Compose.

-   DO NOT serve admin dashboard without IP whitelist.

**6. Required Environment Variables**

> PORT=3000
>
> JWT_SECRET=your_secret
>
> DATABASE_URL=postgresql://user:pass@localhost:5432/oneclick
>
> REDIS_URL=redis://localhost:6379
>
> FIREBASE_PROJECT_ID=\...
>
> FIREBASE_PRIVATE_KEY=\...
>
> FIREBASE_CLIENT_EMAIL=\...
>
> R2_ACCOUNT_ID=\...
>
> R2_ACCESS_KEY=\...
>
> R2_SECRET_KEY=\...
>
> R2_BUCKET_NAME=oneclick-bucket
>
> R2_PRIVATE_BUCKET=oneclick-private
>
> AGORA_APP_ID=\...
>
> AGORA_APP_CERTIFICATE=\...
>
> GOOGLE_MAPS_API_KEY=\...
>
> \# FCM uses the FIREBASE\_\* credentials above (HTTP v1) --- no server
> key
>
> ADMIN_ALLOWED_IPS=your_ip_here
