**GLOSSARY.md**

Unified Vocabulary --- Canonical Names for Humans & AI

────────────────────────────────────────────────────────────────────────────────

All code, database columns, API fields, UI labels, and AI-generated code
must use these exact terms. Inconsistent naming is a bug.

**A. User Concepts**

**User** \[user / users table\]

Any person with a One Click account regardless of role.

**Customer** \[user_role=customer\]

A user in Customer Mode requesting a service. Not a separate account.

**Provider** \[user_role=provider / provider_profiles\]

A verified user in Provider Mode offering services. Not a separate
account.

**User Role** \[user_role column\]

Enum: customer \| provider \| both. Controls which mode can be
activated.

**Active Mode** \[X-Active-Mode header\]

Current UI mode per request. Values: CUSTOMER or PROVIDER.

**Provider Profile** \[provider_profiles table\]

Extended record for provider-specific data.

**Verification** \[is_verified / provider_profiles\]

Admin-approved after CNIC + selfie review.

**Tier** \[provider_tier / provider_profiles.tier\]

Enum: bronze \| silver \| gold. Based on jobs + rating.

**B. Booking Concepts**

**Booking** \[booking / bookings table\]

A service request created by customer and assigned to provider.

**Job** \[booking (use booking in code)\]

Informal synonym. Always use booking in code.

**Agreed Fare** \[agreed_fare / bookings\]

Price both parties agreed before job starts. Immutable once job begins.

**Additional Fee** \[additional_fee / bookings\]

Extra charge requested by provider mid-job (e.g. parts cost).

**Booking Status** \[status / bookings.status\]

pending \| accepted \| en_route \| arrived \| in_progress \| completed
\| cancelled \| disputed

**Instant Booking** \[booking_type=instant\]

Provider comes as soon as possible.

**Scheduled Booking** \[booking_type=scheduled\]

Job booked for a specific future date/time.

**Photo Proof** \[completion_photo_url\]

Photo uploaded by provider before marking job complete.

**Fare Negotiation** \[negotiation (Socket.io flow)\]

Customer proposes → provider counters → both accept/reject.

**C. Payment Concepts**

**Wallet** \[wallet / wallets table\]

In-app balance. Used to pay for services and receive earnings.

**Escrow** \[escrow_hold / transactions.type=escrow_hold\]

Funds held by platform during active job. Released only after both
confirm.

**Commission** \[commission / transactions.type=commission\]

15% of agreed_fare taken by platform at job completion.

**Top-Up** \[wallet_topup / transactions.type=wallet_topup\]

Adding money to wallet. Manual screenshot flow for MVP.

**Withdrawal** \[withdrawal / transactions.type=withdrawal\]

Provider requesting wallet funds to JazzCash/EasyPaisa/bank.

**Cash Job Fee** \[cash_job_fee / transactions.type=cash_job_fee\]

Fee charged to provider for jobs paid in cash.

**Payout** \[release / transactions.type=release\]

Amount released to provider wallet after commission.

**D. Location Concepts**

**Live Location** \[location_update / Redis: location:provider:{id}\]

Real-time GPS. Stored in Redis (TTL 45s), synced to PostgreSQL every
30s.

**Location History** \[locations table\]

Periodic snapshots during active job. Used for disputes and admin audit.

**Service Radius** \[service_radius_km / provider_profiles\]

Max distance provider will travel for a job.

**Provider Pin** \[provider_pin / Google Maps Marker\]

Visual map indicator of a provider. Color-coded by category.

**E. Safety Concepts**

**SOS** \[sos_alert\]

Emergency signal from long-press SOS button. Sends GPS to admin +
emergency contacts.

**Dispute** \[dispute / disputes table\]

Formal complaint post-job. Freezes escrow pending admin review.

**Block** \[block / blocks table\]

Prevents blocked user from contacting, calling, or booking.

**Report** \[report / reports table\]

User flags another for policy violation. 3+ = auto-flag for admin.

**F. Technical Concepts**

**Booking Room** \[booking:{bookingId} (Socket.io room)\]

Private room for exactly 2 users in an active booking.

**JWT** \[jwt_token / Authorization header\]

Server-issued token after Firebase verification. Expires 30 days.

**Firebase Token** \[firebase_token / id_token\]

Short-lived Firebase Auth SDK token, verified by Firebase Admin SDK.

**Signed URL** \[signed_url (Cloudflare R2)\]

Temporary URL for private files (CNIC, selfie). Expires 5 minutes.

**Provider Online Key** \[location:provider:{id} (Redis)\]

Stores lat, lng, status, category. TTL: 45 seconds.

**G. Booking Status Values**

**pending** \[bookings.status\]

Booking created, awaiting provider response.

**accepted** \[bookings.status\]

Provider accepted, preparing to go.

**en_route** \[bookings.status\]

Provider traveling to customer location.

**arrived** \[bookings.status\]

Provider at customer location.

**in_progress** \[bookings.status\]

Job actively being performed. Escrow locked.

**completed** \[bookings.status\]

Both parties confirmed. Escrow released.

**cancelled** \[bookings.status\]

Cancelled by customer or provider.

**disputed** \[bookings.status\]

Dispute raised. Escrow frozen pending admin.

**H. Canonical Socket.io Event Names**

> location_update Client→Server Provider GPS ping every 5s on active booking, 20s idle online
>
> provider_location Server→Customer Provider position during job
> tracking
>
> new_job_request Server→Provider New booking pushed to provider
>
> booking_accepted Server→Customer Provider accepted booking
>
> booking_rejected Server→Customer Provider rejected booking
>
> job_status Server→Both Booking status changed
>
> send_message Client→Server Chat message sent
>
> receive_message Server→Client Chat message delivered
>
> additional_fee_request Server→Customer Provider requests extra charge
>
> additional_fee_response Server→Provider Customer accepted/rejected
>
> call_initiated Server→Both Agora token + channel ready
>
> sos_triggered Server→Admin SOS alert with location
