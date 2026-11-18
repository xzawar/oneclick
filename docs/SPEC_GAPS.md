# Spec gaps & chosen defaults (v1.1 review, 2026-11-18)

Status: **DEFAULT** = chosen so work can proceed, owner may override. **ASK** = needs owner answer before that phase.

## Money (blocks Phase 2)
| # | Gap | Resolution |
|---|---|---|
| M1 | Balance in both `wallets.balance` and `provider_profiles.wallet_balance` | DEFAULT: `wallets` only; drop the provider_profiles column |
| M2 | Where escrow sits | DEFAULT: `wallets.escrow_held` column + `escrow_hold` txn per booking |
| M3 | Money type/rounding | DEFAULT: BIGINT whole rupees; commission = `(gross*15+50) div 100` (half-up) |
| M4 | Commission on `additional_fee`? | DEFAULT: yes, 15% on agreed_fare + accepted additional_fee |
| M5 | Customer never confirms → escrow stuck forever | DEFAULT: auto-confirm 24h after provider completes, unless disputed |
| M6 | Dispute "post-job" but escrow already released after confirm | DEFAULT: dispute allowed from `arrived` until customer confirm / auto-confirm |
| M7 | Cash job fee amount | ASK (admin-configurable setting, default Rs. 0 until set) |
| M8 | Cancellation fee amount + who receives it (F13) | ASK |
| M9 | Rs. 200 min — counts escrow? | DEFAULT: provider `balance` ≥ 200 to go online; escrow not counted |
| M10 | Missing txn types | DEFAULT: add `refund`, `additional_fee` |
| M11 | SBP rules for holding customer funds | ASK legal before launch (spec already notes) |

## Data model (blocks Phase 1 migrations)
| # | Gap | Resolution |
|---|---|---|
| D1 | No `categories` table but F03/F38 dynamic | DEFAULT: `categories(id, slug, name_en, name_ur, pin_color, icon_key, is_visible, sort_order)`; `provider_profiles.categories` → join table `provider_categories` |
| D2 | `blocks`, `reports` referenced in glossary, absent from schema | DEFAULT: add both |
| D3 | Emergency contacts (F30) no table | DEFAULT: `emergency_contacts(id, user_id, name, phone)` max 3 |
| D4 | SOS no table | DEFAULT: `sos_alerts(id, user_id, booking_id, lat, lng, status, created_at)` |
| D5 | Fare negotiation (F11) no table, no socket events | DEFAULT: `fare_offers(id, booking_id, offered_by, amount, status)`; events `fare_offer`, `fare_response` (added to glossary when built) |
| D6 | Admin users / admin API auth | DEFAULT: `admin_users` table, admin JWT, + IP allowlist |
| D7 | lat/lng plain columns vs PostGIS | DEFAULT: keep `lat,lng` + generated `geog geography(Point,4326)` + GIST index |
| D8 | Location ping: glossary "every 5s" vs plan 5s/20s | DEFAULT: 5s active booking, 20s idle online (plan wins); fix glossary |
| D9 | Tier boundary: 200 jobs is both Silver and Gold; Gold needs ≥4.5 but 200 jobs @4.2? | DEFAULT: gold = jobs ≥200 & avg ≥4.5; silver = jobs ≥50 & avg ≥4.0; else bronze |
| D10 | Phone without OTP → not unique-trustworthy | DEFAULT: store E.164 `+92…`, not unique for MVP; OTP V2 |
| D11 | Job request 30s timer — client-only? | DEFAULT: server expires `pending` → `cancelled` reason `timeout` after 30s (instant) |
| D12 | Scheduled booking: reminders, when provider must accept | ASK |

## Stack (outdated as of Nov 2026)
| # | Spec says | DEFAULT |
|---|---|---|
| S1 | Node.js 20 LTS (EOL Apr 2026) | Node.js 24 LTS |
| S2 | Kotlin 1.9 / Android Studio Hedgehog | Kotlin 2.x + current stable Android Studio/AGP |
| S3 | Ubuntu 22.04 | Ubuntu 24.04 LTS |
| S4 | `apt install nodejs postgresql` | Node via NodeSource; `postgresql-16-postgis-3` (apt nodejs too old, PostGIS missing) |
| S5 | JWT "in Android Keystore" (Keystore holds keys, not strings) | Keystore AES key → Tink AEAD → ciphertext in DataStore |
| S6 | Selfie via `ACTION_IMAGE_CAPTURE` (hands off to any camera app) | In-app CameraX capture |
| S7 | Postgres + Redis on same 4GB VPS, no backups mentioned | Nightly `pg_dump` → R2, 7-day retention |

## Product (ASK before Phase 4)
- P1: Can a customer pay cash? Flow shows escrow only; schema has `payment_method`. DEFAULT: `wallet | cash`, cash skips escrow, triggers M7.
- P2: Chat (Socket.io messages) exists in events but no F-number. DEFAULT: build with booking room (MVP).
- P3: F36 live tracking is "High, month 1" but F30 safety needs live location now. DEFAULT: build tracking in Phase 4 (same pipeline).
