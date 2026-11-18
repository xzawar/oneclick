-- One Click demo schema (names per docs/GLOSSARY.md, defaults per docs/SPEC_GAPS.md)
create extension if not exists postgis with schema extensions;

create table users (
  id uuid primary key default gen_random_uuid(),
  firebase_uid text unique,
  phone text, name text not null, email text, avatar_url text,
  user_role text not null default 'customer' check (user_role in ('customer','provider','both')),
  is_active boolean not null default true,
  created_at timestamptz not null default now()
);

create table categories (
  id serial primary key, slug text unique not null, name_en text not null, name_ur text not null,
  pin_color text not null, is_visible boolean not null default true, sort_order int not null default 0
);

create table provider_profiles (
  id uuid primary key default gen_random_uuid(),
  user_id uuid unique not null references users(id),
  categories text[] not null default '{}', bio text, shop_address text,
  is_online boolean not null default false, is_on_job boolean not null default false,
  tier text not null default 'bronze' check (tier in ('bronze','silver','gold')),
  rating_avg numeric(2,1) not null default 0, total_jobs int not null default 0,
  lat double precision not null, lng double precision not null,
  geog extensions.geography(Point,4326) generated always as
    (extensions.st_setsrid(extensions.st_makepoint(lng, lat), 4326)::extensions.geography) stored,
  service_radius_km int not null default 5,
  cnic_url text, selfie_url text, is_verified boolean not null default false, verified_at timestamptz
);
create index provider_profiles_geog_idx on provider_profiles using gist (geog);

create table wallets (
  id uuid primary key default gen_random_uuid(),
  user_id uuid unique not null references users(id),
  balance bigint not null default 0 check (balance >= 0),
  escrow_held bigint not null default 0 check (escrow_held >= 0),
  total_earned bigint not null default 0, total_withdrawn bigint not null default 0,
  created_at timestamptz not null default now()
);

create table bookings (
  id uuid primary key default gen_random_uuid(),
  customer_id uuid not null references users(id), provider_id uuid not null references users(id),
  category text not null, description text not null default '',
  status text not null default 'pending' check (status in
    ('pending','accepted','en_route','arrived','in_progress','completed','cancelled','disputed')),
  agreed_fare bigint not null check (agreed_fare > 0),
  additional_fee bigint not null default 0, additional_fee_status text,
  payment_method text not null default 'wallet' check (payment_method in ('wallet','cash')),
  booking_type text not null default 'instant' check (booking_type in ('instant','scheduled')),
  customer_lat double precision not null, customer_lng double precision not null,
  start_lat double precision, start_lng double precision,
  completion_photo_url text, cancel_reason text, dispute_reason text,
  scheduled_at timestamptz, expires_at timestamptz, accepted_at timestamptz, en_route_at timestamptz,
  started_at timestamptz, completed_at timestamptz, created_at timestamptz not null default now()
);

create table transactions (
  id uuid primary key default gen_random_uuid(),
  booking_id uuid references bookings(id), from_user uuid references users(id), to_user uuid references users(id),
  amount bigint not null check (amount > 0),
  type text not null check (type in ('wallet_topup','escrow_hold','release','commission','refund','withdrawal','cash_job_fee','additional_fee')),
  status text not null default 'completed', external_ref text unique,
  created_at timestamptz not null default now()
);

create table ratings (
  id uuid primary key default gen_random_uuid(),
  booking_id uuid not null references bookings(id), reviewer_id uuid not null references users(id),
  reviewee_id uuid not null references users(id), score int not null check (score between 1 and 5),
  comment text, created_at timestamptz not null default now(), unique (booking_id, reviewer_id)
);

create table sos_alerts (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references users(id), booking_id uuid references bookings(id),
  lat double precision, lng double precision, status text not null default 'open',
  created_at timestamptz not null default now()
);

-- API goes through the edge function (direct DB role); block PostgREST access.
alter table users enable row level security;
alter table categories enable row level security;
alter table provider_profiles enable row level security;
alter table wallets enable row level security;
alter table bookings enable row level security;
alter table transactions enable row level security;
alter table ratings enable row level security;
alter table sos_alerts enable row level security;
