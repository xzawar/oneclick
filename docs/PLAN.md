**PLAN.md**

Strategic Blueprint & Product Requirements Document

────────────────────────────────────────────────────────────────────────────────

**1. Product Overview**

One Click is an on-demand local services marketplace for Pakistan,
starting in Lahore. It connects customers needing tradespeople
(plumbers, electricians, welders, mechanics, etc.) with verified local
providers in real time --- similar to InDrive but for home and roadside
services.

A customer opens the app, sees available providers nearby on a map,
contacts them via in-app call, negotiates a fare, and books instantly or
for later. Payment is handled via an in-app escrow wallet. Both parties
rate each other after job completion.

**2. Target Market**

-   Geography: Lahore, Pakistan (MVP). Expand to other cities
    post-traction.

-   Customers: Homeowners, renters, car owners needing urgent or
    scheduled repairs.

-   Providers: Self-employed plumbers, electricians, welders, mechanics,
    carpenters, painters, and more.

-   Platform: Android (MVP). iOS and web customer portal are post-MVP.

**3. Tech Stack (Final Decisions)**

**3.1 Mobile App**

-   Language: Kotlin \| IDE: Android Studio

-   Single APK --- one app, Customer Mode / Provider Mode toggle
    (InDrive model)

-   Maps: Google Maps SDK for Android (free). Directions / Places /
    Distance Matrix and the admin Maps JS API are billable --- keep
    usage minimal and cache results

-   Auth: Firebase Auth --- Google Sign-In + phone number field (no OTP
    for MVP)

-   Calling: Agora Voice SDK (free up to 10,000 min/month) --- shows
    name + role tag only

-   Messaging: Socket.io real-time chat \| Background: FCM push (high
    priority --- required for job requests and incoming calls)

-   UI: Jetpack Compose + Navigation Compose \| Local cache: Room
    (lists, history) + DataStore (settings) \| JWT in Android Keystore

-   Image cache: Glide \| HTTP client: Retrofit + OkHttp \| DI: Hilt

**3.2 Backend**

-   Runtime: Node.js 20 LTS + TypeScript (strict) \| Framework: Fastify
    \| Real-time: Socket.io (with Redis adapter)

-   Process manager: PM2 \| Reverse proxy: Nginx (SSL + routing)

**3.3 Database & Storage**

-   Primary DB: PostgreSQL (PostGIS extension for location radius
    queries)

-   Cache & real-time: Redis (provider live locations, sessions,
    pub/sub)

-   Object storage: Cloudflare R2 (profile photos, CNIC, selfies, job
    photos, portfolio)

**3.4 Admin Dashboard**

-   Framework: Next.js \| UI: shadcn/ui + Recharts \| Auth: NextAuth.js
    (email + password)

-   Maps: Google Maps JavaScript API \| IP-whitelisted for security

**3.5 Third-Party Services**

-   Firebase Auth --- Google Sign-In + phone collection

-   Firebase Admin SDK (Node.js) --- Firebase token verification

-   Firebase Cloud Messaging (FCM, HTTP v1 via Firebase Admin SDK) ---
    push notifications

-   Agora Voice SDK --- in-app audio calls

-   Google Maps SDK for Android --- map display

-   Cloudflare R2 --- object storage (S3-compatible)

-   JazzCash / EasyPaisa API --- payment gateways (integrated in V2,
    manual flow for MVP)

**3.6 VPS Deployment**

-   Spec: 2 vCPU / 4GB RAM (DigitalOcean, Hetzner, or Contabo)

-   OS: Ubuntu 22.04 LTS \| SSL: Lets Encrypt via Certbot

-   Domains: oneclick.pk / api.oneclick.pk / dashboard.oneclick.pk

**4. System Architecture**

> ┌─────────────────────────────────────────────────────┐
>
> │ CLIENTS │
>
> │ \[Customer App\] \[Provider App\] \[Admin Web\] │
>
> │ (Kotlin) (Kotlin) (Next.js) │
>
> │ └──── same APK, mode toggle ────┘ │
>
> └───────────────────────┬─────────────────────────────┘
>
> REST API │ WebSocket (Socket.io)
>
> ▼
>
> ┌─────────────────────────────────────────────────────┐
>
> │ NGINX --- Reverse Proxy + SSL │
>
> └───────────────────────┬─────────────────────────────┘
>
> ┌─────────┴─────────┐
>
> ▼ ▼
>
> \[REST API\] \[Socket.io\]
>
> Node.js Node.js
>
> └─────────┬─────────┘
>
> ▼
>
> ┌───────────────┬────────────────┬────────────────────┐
>
> │ PostgreSQL │ Redis │ Cloudflare R2 │
>
> │ (main DB) │ (cache+RT) │ (files/photos) │
>
> └───────────────┴────────────────┴────────────────────┘
>
> │
>
> ┌───────────────────────────────────────────────────── ┐
>
> │ Google Maps │ FCM │ Agora │ Firebase Auth │
>
> └─────────────────────────────────────────────────────┘

**5. Database Schema**

**Core Tables**

**users** id, firebase_uid, phone, name, email, avatar_url, user_role,
is_active, created_at

**provider_profiles** id, user_id, categories\[\], bio, shop_address,
wallet_balance, is_online, is_on_job, tier, rating_avg, total_jobs, lat,
lng, service_radius_km, cnic_url, selfie_url, is_verified, verified_at

**bookings** id, customer_id, provider_id, category, description,
status, agreed_fare, additional_fee, additional_fee_status,
payment_method, booking_type, completion_photo_url, scheduled_at,
started_at, completed_at, created_at

**messages** id, booking_id, sender_id, message, is_read, created_at

**transactions** id, booking_id, from_user, to_user, amount, type,
status, external_ref (UNIQUE --- JazzCash/EasyPaisa TID for top-ups),
created_at

**wallets** id, user_id, balance, total_earned, total_withdrawn,
created_at

**ratings** id, booking_id, reviewer_id, reviewee_id, score, comment,
created_at

**disputes** id, booking_id, raised_by, reason, evidence_urls\[\],
status, resolution, resolved_at

**locations** id, booking_id, provider_id, lat, lng, recorded_at

**notifications** id, user_id, type, title, body, data_json, is_read,
created_at

**6. Real-Time Event Reference**

location_update Client → Server --- Provider GPS ping: every 5 s during
an active job, every 20 s while idle online

provider_location Server → Customer --- Broadcast provider position
during job tracking

new_job_request Server → Provider --- New booking pushed to provider
device

booking_accepted Server → Customer --- Provider accepted the booking

booking_rejected Server → Customer --- Provider rejected the booking

job_status Server → Both --- Booking status changed (en_route, arrived,
etc.)

send_message Client → Server --- Chat message sent in booking room

receive_message Server → Client --- Chat message delivered to recipient

additional_fee_request Server → Customer --- Provider requests extra
charge mid-job

additional_fee_response Server → Provider --- Customer accepted or
rejected extra charge

call_initiated Server → Both --- Agora token and channel name ready

sos_triggered Server → Admin --- SOS alert with user ID and live
location

**7. Payment Architecture**

**Escrow Flow (In-App Payment)**

-   Step 1: Customer confirms booking → agreed_fare moved from wallet to
    escrow (LOCKED)

-   Step 2: Job in progress → escrow stays locked, nobody can touch it

-   Step 3: Provider marks complete + uploads photo proof → customer
    confirms

-   Step 4: Platform takes 15% commission → remainder released to
    provider wallet

**Manual Top-Up Flow (MVP)**

-   Customer sends money to business JazzCash/EasyPaisa number manually

-   Takes screenshot of successful transfer and notes the transaction ID
    (TID)

-   Uploads screenshot + enters TID and amount in One Click app

-   Admin matches TID + amount against the business account statement
    (never the screenshot alone) → approves → wallet credited. Each TID
    can be used only once

-   Customer notified via push notification

-   JazzCash/EasyPaisa API replaces this flow in V2 --- zero
    restructuring needed

**8. Complete Feature List (38 Features)**

**Auth & Onboarding**

-   F01 --- Customer signup (Google Sign-In + phone number field)

-   F02 --- Provider signup (form + CNIC + live camera selfie + admin
    verification)

**Discovery**

-   F03 --- Service categories (dynamic, admin-managed, no app update
    required)

-   F04 --- Location-based provider map (real-time via Redis + Google
    Maps SDK)

-   F05 --- Service radius with fallback (show providers if none nearby)

-   F06 --- Boost / promoted listing (shown as Recommended badge)

**Communication**

-   F07 --- In-app audio calling (Agora Voice SDK, shows name + role
    tag)

-   F08 --- Push notifications (FCM --- bookings, messages, wallet, SOS)

-   F09 --- WhatsApp integration (share job details externally)

**Booking**

-   F10 --- Booking system (instant + scheduled)

-   F11 --- Fare negotiation (propose → counter → accept, InDrive-style)

-   F12 --- Job photos (customer uploads what needs fixing)

-   F13 --- Cancellation policy (fee if provider already en route)

-   F14 --- Job queue (provider receives requests while active)

-   F15 --- Additional fee request (mid-job extra charge, customer
    approves/rejects)

-   F16 --- ETA display (provider en route screen)

**Payments**

-   F17 --- Escrow payment system (platform holds until job confirmed by
    both)

-   F18 --- Platform commission (15% per completed transaction)

-   F19 --- Cash job service fee (charged to provider for cash jobs)

-   F20 --- Provider wallet (top-up to appear on map + receive earnings)

-   F21 --- Wallet withdrawal (to JazzCash/EasyPaisa, manual admin for
    MVP)

-   F22 --- Earnings dashboard (provider earnings history + breakdown)

-   F23 --- JazzCash / EasyPaisa API (V2 --- replaces manual top-up)

**Provider**

-   F24 --- Provider profile (name, categories, rating, portfolio,
    verification badge)

-   F25 --- Provider verification (CNIC + selfie, admin review)

-   F26 --- Availability toggle (Go Online / Go Offline)

-   F27 --- Portfolio + shop photos (up to 10 photos)

-   F28 --- Provider tier badge system (Bronze / Silver / Gold)

**Trust & Safety**

-   F29 --- Ratings & reviews (1-5 stars, comment, 24-hour window
    post-job)

-   F30 --- Safety suite (live location sharing + SOS + emergency
    contacts)

-   F31 --- Platform monitors all active jobs (admin sees all on map)

-   F32 --- Job completion confirmation (both confirm before escrow
    releases)

-   F33 --- Photo proof of completion (provider uploads before marking
    done)

-   F34 --- Dispute system (escrow frozen, admin reviews)

-   F35 --- Block & report (blocked = invisible, 3+ reports = auto-flag)

-   F36 --- Live job tracking (customer sees provider moving en route)

**Admin --- Web Only**

-   F37 --- Admin dashboard (live map, verifications, disputes, top-ups,
    analytics)

-   F38 --- Service category management (dynamic, add/remove without app
    update)

**9. MVP Priority Tiers**

**Critical --- Must Have at Launch**

F01, F02, F03, F04, F05, F07, F08, F10, F11, F17, F18, F20, F25, F30,
F31, F32, F33, F34, F37, F38

**High Priority --- Add Within First Month**

F29 (Ratings), F36 (Live tracking)

**Medium Priority --- V1.1**

F06 (Boost), F15 (Additional fee), F16 (ETA), F27 (Portfolio), F28
(Tiers)

**V2 --- Post-Traction**

F22 (Earnings dashboard), F23 (JazzCash/EasyPaisa API), F09 (WhatsApp),
F14 (Job queue)

**10. Explicitly Out of Scope (MVP)**

-   iOS app

-   Web customer portal

-   Surge / dynamic pricing

-   Subscription plans for providers

-   Multi-city expansion

-   Automated JazzCash/EasyPaisa payouts

-   In-app video calling

-   AI-based job matching

-   Third-party background check API

-   Provider analytics dashboard

**11. Business Model**

-   Commission --- 15% on every completed in-app payment transaction

-   Cash job fee --- flat service fee charged to provider for cash jobs

-   Wallet minimum --- provider must maintain Rs. 200 minimum balance to
    appear on map

-   Retention --- to discourage off-platform deals, reward in-app jobs
    (e.g. lower commission or priority placement for high-rated
    providers); exact terms set after launch data

-   Pre-launch compliance --- obtain legal advice on State Bank of
    Pakistan rules for holding customer funds (escrow / wallet) and on
    storing CNIC data

-   Boost listings --- providers pay to appear as Recommended (V2)

-   GTM: Manual provider onboarding in Lahore, promo credits for first
    100 providers. Launch with 2 categories (Electrician, Plumber) in 3
    neighborhoods (DHA, Gulberg, Johar Town); keep other categories
    hidden in the admin panel until provider density is reached
