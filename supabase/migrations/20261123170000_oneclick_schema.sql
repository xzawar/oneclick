-- One Click schema. Names per docs/GLOSSARY.md. Money = whole rupees (bigint).
-- The app never reads tables directly: everything goes through the `api` Edge Function (server-side rules),
-- so RLS is enabled with no policies and anon/authenticated have no grants.
create extension if not exists postgis with schema extensions;

create table public.users (
  id uuid primary key default gen_random_uuid(),
  name text not null check (length(name) between 2 and 40),
  phone text not null unique check (phone ~ '^\+923[0-9]{9}$'),
  user_role text not null default 'customer' check (user_role in ('customer','provider','both')),
  is_bot boolean not null default false,
  is_active boolean not null default true,
  push_token text,
  created_at timestamptz not null default now()
);

create table public.provider_profiles (
  user_id uuid primary key references public.users(id),
  categories text[] not null,
  is_online boolean not null default false,
  is_on_job boolean not null default false,
  tier text not null default 'bronze' check (tier in ('bronze','silver','gold')),
  rating_avg double precision not null default 0,
  rating_count int not null default 0,
  total_jobs int not null default 0,
  lat double precision not null,
  lng double precision not null,
  geog extensions.geography(Point,4326) generated always as
    (extensions.st_setsrid(extensions.st_makepoint(lng, lat), 4326)::extensions.geography) stored,
  service_radius_km int not null default 8,
  is_verified boolean not null default false,
  verified_at timestamptz,
  last_bot_request_at timestamptz not null default 'epoch'
);
create index provider_profiles_geog_idx on public.provider_profiles using gist (geog);

create table public.wallets (
  user_id uuid primary key references public.users(id),
  balance bigint not null default 0 check (balance >= 0),
  escrow_held bigint not null default 0 check (escrow_held >= 0),
  total_earned bigint not null default 0,
  total_withdrawn bigint not null default 0
);

create table public.bookings (
  id uuid primary key default gen_random_uuid(),
  customer_id uuid not null references public.users(id),
  provider_id uuid references public.users(id),
  category text not null,
  description text not null default '',
  status text not null default 'pending' check (status in
    ('pending','accepted','en_route','arrived','in_progress','completed','cancelled','disputed')),
  agreed_fare bigint not null check (agreed_fare > 0),
  customer_lat double precision not null,
  customer_lng double precision not null,
  start_lat double precision,
  start_lng double precision,
  completion_photo_path text,
  cancel_reason text,
  by_bot boolean not null default false,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  step_at timestamptz not null default now(),
  en_route_at timestamptz,
  completed_at timestamptz
);
create index bookings_customer_idx on public.bookings (customer_id, created_at desc);
create index bookings_provider_idx on public.bookings (provider_id, created_at desc);
create index bookings_active_idx on public.bookings (status)
  where status in ('pending','accepted','en_route','arrived','in_progress','disputed');

create table public.fare_offers (
  id uuid primary key default gen_random_uuid(),
  booking_id uuid not null references public.bookings(id),
  provider_id uuid not null references public.users(id),
  amount bigint not null check (amount > 0),
  status text not null default 'open' check (status in ('open','accepted','declined','expired')),
  created_at timestamptz not null default now(),
  expires_at timestamptz not null
);
create index fare_offers_booking_idx on public.fare_offers (booking_id);
create index fare_offers_provider_idx on public.fare_offers (provider_id);

create table public.transactions (
  id uuid primary key default gen_random_uuid(),
  booking_id uuid references public.bookings(id),
  from_user uuid references public.users(id),
  to_user uuid references public.users(id),
  amount bigint not null check (amount > 0),
  type text not null check (type in ('wallet_topup','escrow_hold','release','commission','refund','withdrawal','cash_job_fee','additional_fee')),
  status text not null default 'completed',
  external_ref text unique,
  created_at timestamptz not null default now()
);
create index transactions_booking_idx on public.transactions (booking_id);
create index transactions_from_idx on public.transactions (from_user, created_at desc);
create index transactions_to_idx on public.transactions (to_user, created_at desc);

create table public.ratings (
  id uuid primary key default gen_random_uuid(),
  booking_id uuid not null references public.bookings(id),
  reviewer_id uuid not null references public.users(id),
  reviewee_id uuid not null references public.users(id),
  score int not null check (score between 1 and 5),
  tags text[] not null default '{}',
  created_at timestamptz not null default now(),
  unique (booking_id, reviewer_id)
);
create index ratings_reviewee_idx on public.ratings (reviewee_id);
create index ratings_reviewer_idx on public.ratings (reviewer_id);

create table public.sos_alerts (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.users(id),
  booking_id uuid references public.bookings(id),
  status text not null default 'open',
  created_at timestamptz not null default now()
);
create index sos_alerts_user_idx on public.sos_alerts (user_id);
create index sos_alerts_booking_idx on public.sos_alerts (booking_id);

do $$ declare t text; begin
  foreach t in array array['users','provider_profiles','wallets','bookings','fare_offers','transactions','ratings','sos_alerts'] loop
    execute format('alter table public.%I enable row level security', t);
    execute format('revoke all on public.%I from anon, authenticated', t);
  end loop;
end $$;

-- Private bucket for job photo proof; served only through short-lived signed URLs.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('proofs', 'proofs', false, 2097152, array['image/jpeg']) on conflict (id) do nothing;
