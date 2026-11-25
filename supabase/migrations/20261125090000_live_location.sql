-- Live location: customer request address, provider live position + per-job trail (for disputes and audits).
alter table public.bookings add column address text not null default '';
update public.bookings set address = 'Gulberg III, Lahore' where address = '';
alter table public.provider_profiles add column location_updated_at timestamptz;

create table public.locations (
  id bigint generated always as identity primary key,
  booking_id uuid references public.bookings(id),
  provider_id uuid not null references public.users(id),
  lat double precision not null,
  lng double precision not null,
  recorded_at timestamptz not null default now()
);
create index locations_booking_idx on public.locations (booking_id, recorded_at);
create index locations_provider_idx on public.locations (provider_id, recorded_at desc);
alter table public.locations enable row level security;
revoke all on public.locations from anon, authenticated;
