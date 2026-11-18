---
name: oneclick-backend
description: Use when writing, testing, or reviewing code under backend/ in One Click — Fastify routes, controllers, services, SQL models, migrations, Socket.io handlers, Redis, Cloudflare R2, FCM, Agora token generation.
---

# One Click backend rules

Detail: `docs/AGENTS.md` §2 + §4, endpoints in `docs/SETUP.md` ("Key API Endpoints"). Money → also load `oneclick-money`.

## Stack
Node.js 24 LTS (DEFAULT — spec said 20, EOL Apr 2026), TypeScript strict, ESM only, Fastify, `pg` pool size 10, Zod via `fastify-type-provider-zod`, vitest + `app.inject()`, Socket.io + `@socket.io/redis-adapter`, ioredis, `@aws-sdk/client-s3` for R2, `firebase-admin` (auth + FCM HTTP v1), `agora-token`, `@fastify/rate-limit` on `/api/auth/*`.

## Layout
`src/routes` (schema + wiring) → `src/controllers` (HTTP in/out) → `src/services` (business logic) → `src/models` (SQL only). `src/middleware`, `src/socket`, `src/config`, `src/utils`. `buildApp(deps)` in `src/app.ts` takes injected deps (pool, redis, verifier, clock) so tests swap fakes.

## Rules
- `async/await` only; no `.then` chains. Parameterized SQL only.
- Validate body/params/query on every route. Return `{ error: { code, message } }` on failure.
- Auth: `requireAuth` verifies our JWT (30d) on every protected route; `requireMode('PROVIDER')` checks `X-Active-Mode` vs `user_role`.
- Admin routes: `requireAdmin` (admin JWT) AND IP allowlist `ADMIN_ALLOWED_IPS`.
- Location: PostGIS `ST_DWithin(geog, ST_MakePoint(lng,lat)::geography, radius_m)` for DB; Redis key `location:provider:{id}` TTL 45s; flush to PG every 30s.
- Socket: authenticate on handshake; join only `booking:{id}` rooms user belongs to; re-check membership per event.
- Uploads: presigned PUT to R2; CNIC/selfie to private bucket, signed GET 5 min. Never write to disk.
- Never log tokens, phone, CNIC. Secrets only from `src/config/env.ts` (Zod-parsed).
- Every route change ships with an `inject()` test: happy path + 401 + 400 invalid body.

## Test loop
```bash
docker compose up -d && cd backend && npm test -- <file>
```
