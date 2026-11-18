**README.md**

Repository Front Page & Setup Guide

────────────────────────────────────────────────────────────────────────────────

**One Click --- On-Demand Services Marketplace**

Android app connecting customers with verified local tradespeople in
real time. Lahore, Pakistan --- MVP.

**Repository Structure**

> oneclick/
>
> ├── android/ --- Kotlin Android app (Android Studio project)
>
> │ ├── app/src/main/
>
> │ │ ├── java/com/oneclick/
>
> │ │ │ ├── ui/ --- Compose screens, ViewModels, theme
>
> │ │ │ ├── data/ --- Repositories, DTOs, API service
>
> │ │ │ ├── domain/ --- Use cases, models
>
> │ │ │ └── utils/ --- Extensions, helpers
>
> │ │ └── res/
>
> ├── backend/ --- Node.js + TypeScript + Fastify API server
>
> │ ├── src/
>
> │ │ ├── routes/
>
> │ │ ├── controllers/
>
> │ │ ├── services/
>
> │ │ ├── models/
>
> │ │ ├── middleware/
>
> │ │ ├── socket/
>
> │ │ ├── utils/
>
> │ │ └── config/
>
> │ ├── migrations/
>
> │ └── .env.example
>
> ├── admin/ --- Next.js admin dashboard
>
> ├── docs/ --- PLAN.md, CLAUDE.md, DESIGN.md, GLOSSARY.md, README.md
>
> └── docker-compose.yml --- Local: PostgreSQL + Redis

**Prerequisites**

-   Android Studio Hedgehog or later + Kotlin 1.9+

-   Node.js 20+ LTS

-   PostgreSQL 15+ with PostGIS extension

-   Redis 7+

-   Docker & Docker Compose (for local dev)

-   Google Maps API key (Google Cloud Console)

-   Firebase project with Auth + FCM enabled

-   Agora account + App ID + App Certificate

-   Cloudflare R2 bucket (public + private)

**Local Development Setup**

**1. Clone**

> git clone https://github.com/yourname/oneclick.git && cd oneclick

**2. Start Database & Cache**

> docker-compose up -d
>
> \# PostgreSQL on :5432 \| Redis on :6379

**3. Backend**

> cd backend
>
> npm install
>
> cp .env.example .env \# Fill all values --- see CLAUDE.md section 6
>
> npm run migrate \# Run all SQL migrations
>
> npm run dev \# Hot reload dev server on :3000

**4. Admin Dashboard**

> cd admin
>
> npm install
>
> cp .env.local.example .env.local
>
> npm run dev \# Runs on :3001

**5. Android App**

-   Open the android/ folder in Android Studio

-   Add google-services.json from Firebase console to android/app/

-   Add to android/local.properties: MAPS_API_KEY=your_key and
    AGORA_APP_ID=your_id

-   Update BASE_URL in ApiConfig.kt to your local IP (e.g.
    http://192.168.1.x:3000)

-   Sync Gradle → Run on emulator or device

**Production Deployment (VPS)**

**Recommended VPS: 2 vCPU / 4GB RAM --- Ubuntu 22.04 LTS**

> sudo apt update && sudo apt install -y nginx nodejs npm postgresql
> redis-server
>
> npm install -g pm2
>
> git clone https://github.com/yourname/oneclick.git
>
> cd oneclick/backend && npm ci && npm run build && npm prune
> \--production
>
> pm2 start dist/index.js \--name oneclick-api
>
> pm2 startup && pm2 save
>
> sudo certbot \--nginx -d api.oneclick.pk -d dashboard.oneclick.pk

**Key API Endpoints**

**Auth**

> POST /api/auth/firebase --- Verify Firebase token, receive JWT

**Users**

> GET /api/users/me --- Current user profile
>
> PUT /api/users/me --- Update name, phone, avatar
>
> POST /api/users/provider-profile --- Submit provider form for
> verification

**Providers**

> GET /api/providers/nearby --- Providers near coordinates (Redis)
>
> PUT /api/providers/online --- Toggle online/offline
>
> GET /api/providers/:id --- Provider public profile

**Bookings**

> POST /api/bookings --- Create booking
>
> GET /api/bookings --- User booking history
>
> PATCH /api/bookings/:id/accept --- Provider accepts
>
> PATCH /api/bookings/:id/reject --- Provider rejects
>
> PATCH /api/bookings/:id/complete --- Provider marks complete (+ photo)
>
> PATCH /api/bookings/:id/confirm --- Customer confirms completion
>
> POST /api/bookings/:id/dispute --- Raise dispute

**Wallet**

> GET /api/wallet --- Balance + transaction history
>
> POST /api/wallet/topup --- Submit top-up (screenshot + TID + amount)
>
> POST /api/wallet/withdraw --- Request withdrawal

**Communication**

> GET /api/messages/:bookingId --- Load chat history
>
> POST /api/calls/initiate --- Generate Agora token + channel

**Admin (IP-restricted)**

> GET /api/admin/verifications --- Pending provider queue
>
> POST /api/admin/verifications/:id --- Approve or reject provider
>
> GET /api/admin/topups --- Pending wallet top-ups
>
> POST /api/admin/topups/:id --- Approve or reject top-up
>
> GET /api/admin/disputes --- Open disputes
>
> POST /api/admin/disputes/:id --- Resolve dispute
>
> GET /api/admin/withdrawals --- Pending withdrawals
>
> GET /api/admin/stats --- Dashboard analytics
>
> GET /api/admin/live --- Live map data (all active providers)
