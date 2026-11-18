---
name: oneclick-money
description: Use when writing or reviewing any code that touches wallets, escrow, commission, top-ups, withdrawals, cash job fees, refunds, the transactions table, or booking state changes that move money in One Click.
---

# One Click money rules

Money bug = real loss + SBP compliance risk. Every rule below is mandatory.

## Data
- Amounts: `BIGINT` whole PKR rupees (DEFAULT, see docs/SPEC_GAPS.md). No floats anywhere (Kotlin `Long`, TS `number` checked `Number.isSafeInteger`).
- Balance source of truth: `wallets.balance` (+ `wallets.escrow_held`). `provider_profiles` has NO balance column.
- Every movement = one row in `transactions` (`type` ∈ `wallet_topup | escrow_hold | release | commission | refund | withdrawal | cash_job_fee | additional_fee`).
- `transactions.external_ref` UNIQUE (top-up TID). DB CHECK `wallets.balance >= 0`.

## How to move money (only pattern allowed)
```ts
await withTx(pool, async (c) => {
  const w = await c.query('SELECT balance FROM wallets WHERE user_id=$1 FOR UPDATE', [userId]);
  // validate against w.rows[0].balance — never a client value, never Redis
  await c.query('UPDATE wallets SET balance=balance-$1, escrow_held=escrow_held+$1 WHERE user_id=$2', [fare, userId]);
  await c.query('INSERT INTO transactions(booking_id,from_user,to_user,amount,type,status) VALUES ($1,$2,NULL,$3,\'escrow_hold\',\'held\')', [...]);
}); // throws → ROLLBACK
```
Lock order when two wallets: lower `user_id` first (deadlock safety).

## Lifecycle
| Event | Effect |
|---|---|
| booking accepted (in-app pay) | customer `balance -= agreed_fare`, `escrow_held += agreed_fare`, txn `escrow_hold` |
| additional fee accepted | same as hold, txn `additional_fee` |
| provider completes (photo required) + customer confirms | commission = `(gross*15+50) div 100`; provider `balance += gross-commission` (`release`), platform `commission` txn |
| dispute raised | escrow frozen; booking `disputed`; only admin resolution moves it |
| cancel before `en_route` | full `refund` |
| cash job completed | provider `cash_job_fee` debit |

## Never
- Release while status ≠ awaiting confirm, or without both confirmations.
- Change `agreed_fare` after `in_progress`.
- Approve top-up from screenshot alone — TID+amount must match statement.
- Call JazzCash/EasyPaisa APIs (V2).

## Tests required for any money change
Concurrent double-spend (2 parallel holds on balance for 1), replayed TID, rollback on mid-tx error, non-integer/negative amount rejected.
