---
name: oneclick-naming
description: Use when naming or reviewing any One Click database column, table, enum value, API field, route, Socket.io event, Redis key, Kotlin class, or UI label — inconsistent naming is treated as a bug.
---

# One Click canonical names

Full list: `docs/GLOSSARY.md`. Grep it (`grep -n -i "<term>" docs/GLOSSARY.md`) before inventing a name.

## Quick reference
- Code says `booking`, never `job` (UI copy may say "Job").
- `user_role`: `customer | provider | both`. Header `X-Active-Mode`: `CUSTOMER | PROVIDER`.
- `bookings.status`: `pending | accepted | en_route | arrived | in_progress | completed | cancelled | disputed`.
- `booking_type`: `instant | scheduled`. `tier`: `bronze | silver | gold`.
- Money fields: `agreed_fare`, `additional_fee`, `additional_fee_status`.
- Transaction types: `wallet_topup, escrow_hold, release, commission, refund, withdrawal, cash_job_fee, additional_fee`.
- Redis live location: `location:provider:{id}` (TTL 45s). Socket room: `booking:{bookingId}`.
- Socket events: `location_update, provider_location, new_job_request, booking_accepted, booking_rejected, job_status, send_message, receive_message, additional_fee_request, additional_fee_response, call_initiated, sos_triggered` (+ proposed `fare_offer`, `fare_response` — see SPEC_GAPS).
- Kotlin: `*ViewModel`, `*Repository`, `*Screen`, `*Dto`; `ApiService`; enums `UserRole, ActiveMode, BookingStatus, ProviderTier`.
- Migrations: `backend/migrations/NNN_verb_noun.sql` (e.g. `001_create_users.sql`).

## New term needed?
Add it to `docs/GLOSSARY.md` in same commit, matching section format. Never use a synonym of an existing term.
