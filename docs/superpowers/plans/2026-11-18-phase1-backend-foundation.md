# Phase 1 — Backend Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Load skills `oneclick-backend` + `oneclick-naming` (+ `oneclick-money` for Task 4).

**Goal:** Running Fastify API with env validation, migration runner, full MVP schema, Firebase→JWT auth, mode guard, and `/api/users/me`.

**Architecture:** `buildApp(deps)` factory with injected `pool`, `firebaseVerifier`, `clock`, `env` so every test runs via `app.inject()` against a real PostGIS DB (docker) and a fake verifier. SQL lives in `src/models`, logic in `src/services`.

**Tech Stack:** Node.js 24 LTS, TypeScript strict ESM, Fastify 5, `fastify-type-provider-zod`, Zod, `pg`, `jose` (JWT HS256), `firebase-admin`, `@fastify/rate-limit`, vitest, `tsx`.

**Spec:** `docs/AGENTS.md` §2,§4,§6 · `docs/PLAN.md` §5 · `docs/SETUP.md` endpoints · `docs/SPEC_GAPS.md` D1–D10, M1–M3, M10

## Global Constraints
- ESM only (`"type": "module"`), `strict: true`, no `require`, no `.then` chains.
- pg pool `max: 10`. Parameterized SQL only.
- JWT expiry 30 days, `sub` = `users.id` (uuid), claim `role` = `user_role`.
- `user_role` ∈ `customer|provider|both`; header `X-Active-Mode` ∈ `CUSTOMER|PROVIDER`.
- Migrations: `backend/migrations/NNN_verb_noun.sql`, applied in numeric order, each once.
- Error body: `{ "error": { "code": string, "message": string } }`.
- Money columns `BIGINT` rupees; `wallets.balance >= 0`, `wallets.escrow_held >= 0` CHECKs.
- Tests use DB `oneclick_test`; `DATABASE_URL_TEST` env; each test file truncates tables it touches.

## Review Focus
1. Two concurrent first logins with same `firebase_uid` → exactly one user + one wallet (upsert `ON CONFLICT`). Test in Task 5.
2. Phone input in local forms (`03001234567`, `0300-1234567`, `+923001234567`, `923001234567`) → all stored `+923001234567`; landline/short → 400. Test in Task 7.
3. Expired / wrong-secret / `alg:none` JWT → 401, never 500. Test in Task 6.
4. `X-Active-Mode` missing, lowercase, or garbage → defaults/400 consistently (missing → `CUSTOMER`; `provider` lowercase accepted; other → 400). Test in Task 6.
5. Migration file added out of order or edited after apply → runner refuses (checksum mismatch) instead of silently skipping. Test in Task 3.

---

### Task 1: Scaffold + health route

**Files:**
- Create: `backend/package.json`, `backend/tsconfig.json`, `backend/vitest.config.ts`, `backend/src/app.ts`, `backend/src/index.ts`, `backend/src/routes/health.ts`, `.gitignore`
- Test: `backend/test/health.test.ts`

**Interfaces:**
- Produces: `buildApp(deps: AppDeps): Promise<FastifyInstance>`; `type AppDeps = { env: Env; pool: pg.Pool; firebaseVerifier: FirebaseVerifier; clock: () => Date }` (fields optional until their task lands).

- [ ] **Step 1:** Test `GET /health` → 200 `{ status: "ok" }`.
- [ ] **Step 2:** `npm test` → FAIL (module missing).
- [ ] **Step 3:** Implement. Scripts: `dev` (`tsx watch src/index.ts`), `build` (`tsc`), `test` (`vitest run`), `migrate` (`tsx src/db/migrate.ts`). Zod type provider registered in `buildApp`.
- [ ] **Step 4:** `npm test` → PASS; `npx tsc --noEmit` → no errors.
- [ ] **Step 5:** Commit `chore(backend): scaffold fastify app with health route`.

### Task 2: Env config + docker-compose

**Files:**
- Create: `docker-compose.yml`, `backend/.env.example`, `backend/src/config/env.ts`, `backend/src/config/db.ts`
- Test: `backend/test/env.test.ts`

**Interfaces:**
- Produces: `loadEnv(src: Record<string,string|undefined>): Env` (throws `Error` listing every missing/invalid key); `createPool(url: string): pg.Pool` (`max: 10`).

- [ ] **Step 1:** Tests: missing `JWT_SECRET` → throws message containing `JWT_SECRET`; `JWT_SECRET` shorter than 32 chars → throws; `PORT` default `3000`; `ADMIN_ALLOWED_IPS="1.2.3.4, 5.6.7.8"` → `["1.2.3.4","5.6.7.8"]`.
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement with Zod. Keys from `docs/AGENTS.md` §6 + `DATABASE_URL_TEST`. Compose: `postgis/postgis:16-3.4` (:5432, init creates `oneclick` + `oneclick_test`), `redis:7-alpine` (:6379).
- [ ] **Step 4:** `docker compose up -d && npm test` → PASS.
- [ ] **Step 5:** Commit `feat(backend): validated env config and local compose`.

### Task 3: Migration runner

**Files:**
- Create: `backend/src/db/migrate.ts`, `backend/src/db/tx.ts`
- Test: `backend/test/migrate.test.ts`

**Interfaces:**
- Produces: `runMigrations(pool: pg.Pool, dir: string): Promise<string[]>` (returns filenames applied this run); `withTx<T>(pool: pg.Pool, fn: (c: pg.PoolClient) => Promise<T>): Promise<T>` (BEGIN/COMMIT, ROLLBACK + rethrow on error).

- [ ] **Step 1:** Tests (temp dir fixtures): applies `001_a.sql`,`002_b.sql` in order; second run returns `[]`; edited applied file → rejects `/checksum mismatch: 001_a.sql/`; failing SQL in `002` rolls back `002` only, `schema_migrations` has only `001`; `withTx` rolls back on throw.
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement. Table `schema_migrations(filename text pk, checksum text, applied_at timestamptz)`; sha256 checksum; each file in its own tx; `pg_advisory_lock(7101)` around run.
- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit `feat(backend): sql migration runner with checksums`.

### Task 4: MVP schema migrations

**Files:**
- Create: `backend/migrations/001_enable_extensions.sql` … `014_create_admin_users.sql` (one table group per file: extensions(postgis, pgcrypto) · users · categories · provider_profiles + provider_categories · wallets · bookings · fare_offers · transactions · messages · ratings · disputes · locations · notifications · safety (blocks, reports, emergency_contacts, sos_alerts) · admin_users)
- Test: `backend/test/schema.test.ts`

**Interfaces:**
- Produces: tables/columns exactly per `docs/PLAN.md` §5 with SPEC_GAPS changes: no `provider_profiles.wallet_balance`; `wallets.escrow_held BIGINT NOT NULL DEFAULT 0`; `provider_profiles.geog geography(Point,4326) GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(lng,lat),4326)::geography) STORED` + GIST index; enums as `CHECK (... IN (...))` using glossary values; `transactions.type` includes `refund`,`additional_fee`; uuid PKs `gen_random_uuid()`.

- [ ] **Step 1:** Tests after `runMigrations`: insert wallet with `balance=-1` → error code `23514`; duplicate `transactions.external_ref` → `23505`; `bookings.status='done'` → `23514`; `users.user_role` accepts `both`; `ST_DWithin` query returns a provider 1 km away for radius 2000 m and not for 500 m; deleting a user with bookings is blocked (`23503`).
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Write SQL files. Seed categories (6 + Other) from `oneclick-design` pin table; only `electrician`,`plumber` `is_visible=true` (GTM).
- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit `feat(db): mvp schema migrations`.

### Task 5: Auth — Firebase token → JWT

**Files:**
- Create: `backend/src/config/firebase.ts`, `backend/src/services/authService.ts`, `backend/src/models/userModel.ts`, `backend/src/controllers/authController.ts`, `backend/src/routes/auth.ts`, `backend/src/utils/jwt.ts`
- Test: `backend/test/auth.test.ts`

**Interfaces:**
- Consumes: `withTx`, `AppDeps`.
- Produces: `interface FirebaseVerifier { verify(idToken: string): Promise<{ uid: string; email?: string; name?: string; picture?: string }> }`; `signJwt(payload: { sub: string; role: UserRole }, secret: string, now: Date): Promise<string>`; `verifyJwt(token, secret, now): Promise<{ sub: string; role: UserRole }>`; `POST /api/auth/firebase` body `{ id_token: string }` → 200 `{ token, user: UserDto, is_new: boolean }`.

- [ ] **Step 1:** Tests with fake verifier: new uid → 200, `is_new=true`, one `users` row (`user_role='customer'`) + one `wallets` row; same uid again → `is_new=false`, still one row each; `Promise.all` of two first logins same uid → one user, one wallet; verifier throws → 401 `code:"invalid_firebase_token"`; missing `id_token` → 400; 11th request in 1 min from same IP → 429.
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement. Upsert `INSERT … ON CONFLICT (firebase_uid) DO UPDATE SET firebase_uid=EXCLUDED.firebase_uid RETURNING *, (xmax = 0) AS is_new`; wallet `ON CONFLICT (user_id) DO NOTHING`; both in one `withTx`. Rate limit 10/min on `/api/auth/*`.
- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit `feat(auth): exchange firebase token for jwt`.

### Task 6: Auth middleware + mode guard

**Files:**
- Create: `backend/src/middleware/auth.ts`
- Test: `backend/test/authMiddleware.test.ts`

**Interfaces:**
- Consumes: `verifyJwt`.
- Produces: Fastify preHandlers `requireAuth` (sets `req.user: { id: string; role: UserRole }`, `req.activeMode: 'CUSTOMER'|'PROVIDER'`) and `requireMode(mode)`.

- [ ] **Step 1:** Tests on a test-only route: no header → 401 `missing_token`; expired (clock +31 days) → 401 `invalid_token`; wrong secret → 401; `alg:none` token → 401; no `X-Active-Mode` → `activeMode='CUSTOMER'`; `provider` → `PROVIDER`; `admin` → 400 `invalid_active_mode`; `requireMode('PROVIDER')` with role `customer` → 403 `mode_not_allowed`; role `both` → passes.
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement; `jose.jwtVerify` with `algorithms: ['HS256']` and injected clock (`currentDate`).
- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit `feat(auth): jwt middleware and active mode guard`.

### Task 7: Users — `/api/users/me`

**Files:**
- Create: `backend/src/routes/users.ts`, `backend/src/controllers/userController.ts`, `backend/src/services/userService.ts`, `backend/src/utils/phone.ts`
- Modify: `backend/src/models/userModel.ts`
- Test: `backend/test/users.test.ts`, `backend/test/phone.test.ts`

**Interfaces:**
- Produces: `normalizePkMobile(input: string): string | null` (returns `+923XXXXXXXXX` or null); `GET /api/users/me` → `UserDto { id, name, phone, email, avatar_url, user_role, is_active, created_at }`; `PUT /api/users/me` body `{ name?: string(2..60), phone?: string, avatar_url?: string(url) }` → updated `UserDto`.

- [ ] **Step 1:** Tests: `normalizePkMobile` → `+923001234567` for `03001234567`, `0300-1234567`, `+923001234567`, `923001234567`, `0300 1234567`; null for `0421234567`, `0300123`, `abc`. Route: GET without token → 401; GET → own user; PUT invalid phone → 400 `invalid_phone`; PUT name 1 char → 400; PUT valid → 200 and persisted; PUT cannot change `user_role` (extra field stripped/400).
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** `npm test` (all) → PASS; `npx tsc --noEmit` clean.
- [ ] **Step 5:** Commit `feat(users): get and update current user`.

---

**Phase 1 done when:** all tests green, `npm run migrate` on fresh DB works, `npm run dev` serves `/health`. Then write Phase 2 plan (money core) from real interfaces.
