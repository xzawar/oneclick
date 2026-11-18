# ONE CLICK — agent rules

On-demand local services marketplace, Lahore. ONE Android APK (Customer/Provider mode toggle),
Node.js+TS+Fastify API, PostgreSQL+PostGIS, Redis, Next.js admin. Full spec lives in `docs/`.

## Token discipline (always)
- Chat replies: **caveman full** mode (skill `caveman`). Code, commits, docs, security warnings: normal clear English.
- Never read a whole doc. Find section first: `grep -n "^\*\*" docs/PLAN.md`, then `sed -n 'A,Bp'`.
- Load only the skill for the area you touch (table below). Skills hold the rules; docs hold detail.
- Batch independent tool calls in one message. No re-reading a file you just edited.
- No subagents unless user asks. No summaries of what you just did beyond 2–3 lines.
- Plans: `docs/superpowers/plans/`. Follow active phase plan task-by-task; tick checkboxes.

## Skills (`.claude/skills/`)
| Touching | Load |
|---|---|
| wallet, escrow, commission, top-up, withdrawal, transactions | `oneclick-money` |
| any name: column, enum, event, route, class | `oneclick-naming` |
| `backend/` (Fastify, SQL, Socket.io, Redis, R2, FCM, Agora) | `oneclick-backend` |
| `android/` (Kotlin, Compose) | `oneclick-android` |
| UI colors, type, spacing, screens, admin UI | `oneclick-design` |

## Non-negotiables (full list: docs/AGENTS.md §5)
1. One APK. Mode = `X-Active-Mode: CUSTOMER|PROVIDER`; server checks `users.user_role`.
2. All business logic server-side. Wallet balance read ONLY from PostgreSQL `wallets` — never Redis, never request body.
3. Money moves only inside one PG transaction (BEGIN/COMMIT/ROLLBACK) with row lock.
4. Escrow released only after provider completes (with photo) AND customer confirms. Frozen on dispute.
5. CNIC/selfie: private R2 bucket, signed URL 5 min. No files on VPS disk.
6. Parameterized SQL only. Secrets via env only. Validate every request body (Zod/JSON Schema).
7. Banned: Flutter, Hive, XML layouts/Fragments, RxJava, Twilio, masked numbers, OTP (MVP),
   JazzCash/EasyPaisa API (MVP), legacy FCM server key, CommonJS `require`.
8. Top-up approval needs TID + amount match on business statement. TID unique.

## Open decisions
`docs/SPEC_GAPS.md` lists spec holes + chosen defaults (marked DEFAULT). Don't silently invent
new rules — add to that file and tell user.

## Commands (once scaffolded)
```bash
docker compose up -d          # postgis + redis
cd backend && npm test        # vitest
cd backend && npm run migrate
```
