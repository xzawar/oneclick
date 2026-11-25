// One Click API — Supabase Edge Function (Deno). All business and money rules live here, server-side.
// Each request runs in ONE transaction behind an advisory lock: the simulation tick, then the action.
// Anything that throws rolls the whole request back (no half-moved money).
import postgres from "npm:postgres@3.4.5";
import { jwtVerify, SignJWT } from "npm:jose@5.9.6";
import { createClient } from "npm:@supabase/supabase-js@2.45.4";

// deno-lint-ignore no-explicit-any
type Tx = any;
type Row = Record<string, any>;

const sql = postgres(Deno.env.get("SUPABASE_DB_URL")!, {
  prepare: false, max: 3,
  types: { bigint: { to: 20, from: [20], parse: (x: string) => Number(x), serialize: (x: number) => String(x) } },
});
const storage = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!).storage.from("proofs");

// Session signing key: SESSION_SECRET if set, else derived from the project's service key (never leaves the server).
const keyPromise = crypto.subtle.digest("SHA-256",
  new TextEncoder().encode("oneclick-session-v1:" + (Deno.env.get("SESSION_SECRET") ?? Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "")))
  .then((b) => new Uint8Array(b));

const SEARCH_S = 180, OFFER_S = 30, TRAVEL_S = 60, MIN_BALANCE = 200, MIN_FARE = 300, MAX_FARE = 20000, WELCOME = 1000;
const HOME = { lat: 31.5204, lng: 74.3587 };
const inLahore = (lat: number, lng: number) => lat >= 31.35 && lat <= 31.65 && lng >= 74.15 && lng <= 74.5;
const inPakistan = (lat: number, lng: number) => lat >= 23 && lat <= 37.5 && lng >= 60 && lng <= 78;
const ARRIVE_KM = 0.3;
const ACTIVE = ["pending", "accepted", "en_route", "arrived", "in_progress", "disputed"];
const CATEGORIES: Record<string, number> = { plumber: 800, electrician: 700, mechanic: 1000, carpenter: 900, painter: 1500, welder: 1200 };
const ISSUES: Record<string, string[]> = {
  electrician: ["Fan not working", "UPS wiring", "Socket sparking", "DB board tripping"],
  plumber: ["Geyser leaking", "Kitchen tap broken", "Water motor not starting"],
};
const TXN_TITLES: Record<string, string> = {
  wallet_topup: "Top-up", release: "Job earnings", refund: "Refund", withdrawal: "Withdrawal", commission: "Service fee",
};

class AppError extends Error { constructor(public status: number, msg: string) { super(msg); } }
const fail = (msg: string, status = 400): never => { throw new AppError(status, msg); };
const json = (d: unknown, status = 200) => new Response(JSON.stringify(d), { status, headers: { "content-type": "application/json" } });
const rs = (n: number) => "Rs " + n.toLocaleString("en-US");
const commission = (gross: number) => Math.floor((gross * 15 + 50) / 100);
const round1 = (x: number) => Math.round(x * 10) / 10;
const ms = (d: Date | string | null) => (d ? new Date(d).getTime() : 0);
function km(a: number, b: number, c: number, d: number) {
  const r = (x: number) => (x * Math.PI) / 180;
  const h = Math.sin(r(c - a) / 2) ** 2 + Math.cos(r(a)) * Math.cos(r(c)) * Math.sin(r(d - b) / 2) ** 2;
  return 2 * 6371 * Math.asin(Math.sqrt(h));
}
export function normalizePhone(input: string): string | null {
  const d = String(input ?? "").replace(/\D/g, "");
  const local = d.startsWith("92") && d.length === 12 ? d.slice(2) : d.startsWith("03") && d.length === 11 ? d.slice(1)
    : d.startsWith("3") && d.length === 10 ? d : null;
  return local ? "+92" + local : null;
}
const int = (v: unknown, min: number, max: number, msg: string) => {
  if (typeof v !== "number" || !Number.isSafeInteger(v) || v < min || v > max) fail(msg);
  return v as number;
};

// ---------------- shared money/booking steps (all inside the request transaction) ----------------
async function ensureFunds(tx: Tx, userId: string, need: number) {
  const [w] = await tx`select balance from wallets where user_id = ${userId} for update`;
  if (w.balance >= need) return;
  await tx`update wallets set balance = balance + 3000 where user_id = ${userId}`;
  await tx`insert into transactions (to_user, amount, type, status, external_ref) values (${userId}, 3000, 'wallet_topup', 'approved', ${"AUTO-" + crypto.randomUUID().slice(0, 8)})`;
}

async function acceptOffer(tx: Tx, b: Row, offerId: string) {
  if (b.status !== "pending") fail("Request is no longer active", 409);
  const [o] = await tx`select * from fare_offers where id = ${offerId} and booking_id = ${b.id} and status = 'open' and expires_at > now() for update`;
  if (!o) fail("Offer expired", 409);
  const [w] = await tx`select balance from wallets where user_id = ${b.customer_id} for update`;
  if (w.balance < o.amount) fail(`Not enough in wallet. Top up ${rs(o.amount - w.balance)}`);
  const [p] = await tx`select is_on_job from provider_profiles where user_id = ${o.provider_id} for update`;
  if (p.is_on_job) fail("Provider just took another job", 409);
  await tx`update wallets set balance = balance - ${o.amount}, escrow_held = escrow_held + ${o.amount} where user_id = ${b.customer_id}`;
  await tx`insert into transactions (booking_id, from_user, amount, type, status) values (${b.id}, ${b.customer_id}, ${o.amount}, 'escrow_hold', 'held')`;
  await tx`update bookings set status = 'accepted', provider_id = ${o.provider_id}, agreed_fare = ${o.amount}, step_at = now() where id = ${b.id}`;
  await tx`update fare_offers set status = case when id = ${offerId} then 'accepted' else 'declined' end where booking_id = ${b.id} and status = 'open'`;
  await tx`update provider_profiles set is_on_job = true where user_id = ${o.provider_id}`;
}

async function step(tx: Tx, b: Row, next: string) {
  const from: Record<string, string> = { en_route: "accepted", arrived: "en_route", in_progress: "arrived" };
  if (!from[next]) fail("Unknown status");
  const r = await tx`update bookings bk set status = ${next}, step_at = now(),
      en_route_at = case when ${next} = 'en_route' then now() else en_route_at end,
      start_lat = coalesce(start_lat, pp.lat), start_lng = coalesce(start_lng, pp.lng)
    from provider_profiles pp where pp.user_id = bk.provider_id and bk.id = ${b.id} and bk.status = ${from[next]} returning bk.id`;
  if (!r.length) fail("Not available right now", 409);
}

async function release(tx: Tx, b: Row) {
  if (b.status !== "in_progress" || !b.completion_photo_path) fail("Provider hasn’t finished yet", 409);
  const gross = b.agreed_fare, fee = commission(gross), net = gross - fee;
  await tx`select 1 from wallets where user_id in (${b.customer_id}, ${b.provider_id}) order by user_id for update`;
  await tx`update wallets set escrow_held = escrow_held - ${gross} where user_id = ${b.customer_id}`;
  await tx`update wallets set balance = balance + ${net}, total_earned = total_earned + ${net} where user_id = ${b.provider_id}`;
  await tx`update transactions set status = 'released' where booking_id = ${b.id} and type = 'escrow_hold'`;
  await tx`insert into transactions (booking_id, from_user, to_user, amount, type) values
    (${b.id}, ${b.customer_id}, ${b.provider_id}, ${net}, 'release'), (${b.id}, ${b.customer_id}, null, ${fee}, 'commission')`;
  await tx`update bookings set status = 'completed', completed_at = now(), step_at = now() where id = ${b.id}`;
  await tx`update provider_profiles set is_on_job = false, total_jobs = total_jobs + 1 where user_id = ${b.provider_id}`;
}

async function cancel(tx: Tx, b: Row, reason: string) {
  if (!["pending", "accepted", "en_route"].includes(b.status)) fail("Can’t cancel after arrival", 409);
  if (b.status !== "pending") {
    await tx`select 1 from wallets where user_id = ${b.customer_id} for update`;
    await tx`update wallets set balance = balance + ${b.agreed_fare}, escrow_held = escrow_held - ${b.agreed_fare} where user_id = ${b.customer_id}`;
    await tx`update transactions set status = 'refunded' where booking_id = ${b.id} and type = 'escrow_hold'`;
    await tx`insert into transactions (booking_id, to_user, amount, type) values (${b.id}, ${b.customer_id}, ${b.agreed_fare}, 'refund')`;
    await tx`update provider_profiles set is_on_job = false where user_id = ${b.provider_id}`;
  }
  await tx`update fare_offers set status = 'declined' where booking_id = ${b.id} and status = 'open'`;
  await tx`update bookings set status = 'cancelled', cancel_reason = ${reason}, step_at = now() where id = ${b.id}`;
}

async function rateInternal(tx: Tx, b: Row, reviewer: string, score: number, tags: string[]) {
  const reviewee = reviewer === b.customer_id ? b.provider_id : b.customer_id;
  const ins = await tx`insert into ratings (booking_id, reviewer_id, reviewee_id, score, tags)
    values (${b.id}, ${reviewer}, ${reviewee}, ${score}, ${tags}) on conflict (booking_id, reviewer_id) do nothing returning id`;
  if (!ins.length) return;
  await tx`update provider_profiles set rating_avg = round(((rating_avg * rating_count + ${score}) / (rating_count + 1))::numeric, 1),
    rating_count = rating_count + 1 where user_id = ${reviewee}`;
}

// ---------------- the simulated people (is_bot) act here, on every request ----------------
async function tick(tx: Tx, me: string | null, mode: string) {
  await tx`select pg_advisory_xact_lock(4242)`;
  // One clock for every timing rule: the database's (edge and DB clocks can drift).
  tx.__now = Number((await tx`select (extract(epoch from now()) * 1000)::bigint as t`)[0].t);
  await tx`update fare_offers set status = 'expired' where status = 'open' and expires_at <= now()`;
  await tx`update bookings set status = 'cancelled', cancel_reason = 'no_offers', step_at = now() where status = 'pending' and expires_at <= now()`;
  const now = tx.__now;
  const active = await tx`select b.*, cu.is_bot as customer_bot, pu.is_bot as provider_bot from bookings b
    join users cu on cu.id = b.customer_id left join users pu on pu.id = b.provider_id where b.status = any(${tx.array(ACTIVE)})`;
  for (const b of active) {
    const age = now - ms(b.step_at);
    try {
      if (b.status === "pending" && !b.customer_bot) await botOffers(tx, b, now);
      else if (b.status === "pending" && b.customer_bot) {
        const [o] = await tx`select * from fare_offers where booking_id = ${b.id} and status = 'open' and created_at < now() - interval '3 seconds' order by created_at limit 1`;
        if (o && o.amount <= b.agreed_fare + 200) { await ensureFunds(tx, b.customer_id, o.amount); await acceptOffer(tx, b, o.id); }
        else if (o) await tx`update fare_offers set status = 'declined' where id = ${o.id}`;
      } else if (b.provider_bot && b.status === "accepted" && age > 3000) await step(tx, b, "en_route");
      else if (b.provider_bot && b.status === "en_route" && age > TRAVEL_S * 1000) await step(tx, b, "arrived");
      else if (b.provider_bot && b.status === "arrived" && age > 4000) await step(tx, b, "in_progress");
      else if (b.provider_bot && b.status === "in_progress" && !b.completion_photo_path && age > 12000)
        await tx`update bookings set completion_photo_path = 'provider', step_at = now() where id = ${b.id}`;
      else if (b.customer_bot && b.status === "in_progress" && b.completion_photo_path && age > 4000) {
        await release(tx, b);
        await rateInternal(tx, b, b.customer_id, 5, ["On time", "Polite"]);
      }
    } catch (e) { if (!(e instanceof AppError)) throw e; }
  }
  // A simulated customer posts a request near a real provider who is online and free (only for the caller).
  if (me && mode === "PROVIDER") {
    const [p] = await tx`select pp.*, w.balance from provider_profiles pp join users u on u.id = pp.user_id join wallets w on w.user_id = pp.user_id
      where pp.user_id = ${me} and not u.is_bot and pp.is_online and not pp.is_on_job and pp.last_bot_request_at < now() - interval '10 seconds'`;
    if (p && p.balance >= MIN_BALANCE) {
      const [busy] = await tx`select 1 from bookings where status = any(${tx.array(ACTIVE)}) and (provider_id = ${me} or (by_bot and status = 'pending'))`;
      if (!busy) {
        const [cust] = await tx`select id from users where is_bot and user_role = 'customer' limit 1`;
        const cat = p.categories[Math.floor(Math.random() * p.categories.length)];
        const fare = CATEGORIES[cat] + [-100, 0, 100, 200][Math.floor(Math.random() * 4)];
        const issue = (ISSUES[cat] ?? ["Repair needed"])[Math.floor(Math.random() * (ISSUES[cat]?.length ?? 1))];
        await ensureFunds(tx, cust.id, fare + 200);
        await tx`insert into bookings (customer_id, category, description, agreed_fare, customer_lat, customer_lng, address, expires_at, by_bot)
          values (${cust.id}, ${cat}, ${issue}, ${fare}, ${p.lat + 0.005}, ${p.lng - 0.003}, 'Pinned location near you', now() + interval '60 seconds', true)`;
        await tx`update provider_profiles set last_bot_request_at = now() where user_id = ${me}`;
      }
    }
  }
}

/** Every free simulated provider in range answers, nearest first, a few seconds apart. */
async function botOffers(tx: Tx, b: Row, now: number) {
  const rec = CATEGORIES[b.category];
  const here = tx`extensions.st_setsrid(extensions.st_makepoint(${b.customer_lng}, ${b.customer_lat}), 4326)::extensions.geography`;
  const [near] = await tx`select 1 from provider_profiles pp join users u on u.id = pp.user_id
    where u.is_bot and pp.is_online and not pp.is_on_job and ${b.category} = any(pp.categories)
      and extensions.st_dwithin(pp.geog, ${here}, pp.service_radius_km * 1000) limit 1`;
  if (!near) {
    const [far] = await tx`select pp.user_id from provider_profiles pp join users u on u.id = pp.user_id
      where u.is_bot and pp.is_online and not pp.is_on_job and ${b.category} = any(pp.categories)
      order by extensions.st_distance(pp.geog, ${here}) limit 1`;
    if (far) {
      const a = Math.random() * 2 * Math.PI, d = 0.008 + Math.random() * 0.006;
      await tx`update provider_profiles set lat = ${b.customer_lat + d * Math.cos(a)}, lng = ${b.customer_lng + d * Math.sin(a)} where user_id = ${far.user_id}`;
    }
  }
  const pros = await tx`select pp.user_id from provider_profiles pp join users u on u.id = pp.user_id join wallets w on w.user_id = pp.user_id
    where u.is_bot and pp.is_online and not pp.is_on_job and ${b.category} = any(pp.categories) and w.balance >= ${MIN_BALANCE}
      and extensions.st_dwithin(pp.geog, extensions.st_setsrid(extensions.st_makepoint(${b.customer_lng}, ${b.customer_lat}), 4326)::extensions.geography, pp.service_radius_km * 1000)
    order by extensions.st_distance(pp.geog, extensions.st_setsrid(extensions.st_makepoint(${b.customer_lng}, ${b.customer_lat}), 4326)::extensions.geography)`;
  for (let i = 0; i < pros.length; i++) {
    const pid = pros[i].user_id;
    const mine = await tx`select status, created_at from fare_offers where booking_id = ${b.id} and provider_id = ${pid} order by created_at`;
    if (mine.some((o: Row) => o.status === "open") || mine.length >= 3) continue;
    const since = now - Math.max(ms(b.step_at), ...mine.map((o: Row) => ms(o.created_at)));
    if (since < (mine.length ? 8000 : 4000 + 3000 * i)) continue;
    const amount = i === 0 ? (b.agreed_fare >= rec || mine.length ? b.agreed_fare : Math.floor((b.agreed_fare + 100) / 50) * 50)
      : Math.floor((Math.max(b.agreed_fare, rec) + 50 * i) / 50) * 50;
    await tx`insert into fare_offers (booking_id, provider_id, amount, expires_at) values (${b.id}, ${pid}, ${amount}, now() + make_interval(secs => ${OFFER_S}))`;
  }
}

// ---------------- views (field names match the Android data classes) ----------------
async function customerRating(tx: Tx, id: string): Promise<[number, number]> {
  const [r] = await tx`select count(*)::int n, coalesce(sum(score), 0)::int s from ratings where reviewee_id = ${id}`;
  return r.n === 0 ? [4.9, 31] : [round1((4.9 * 31 + r.s) / (31 + r.n)), 31 + r.n];
}

async function jobView(tx: Tx, b: Row, me: string) {
  const isCustomer = b.customer_id === me;
  const [p] = b.provider_id ? await tx`select pp.*, u.name, u.is_bot from provider_profiles pp join users u on u.id = pp.user_id where pp.user_id = ${b.provider_id}` : [null];
  let pLat = p?.lat ?? b.customer_lat, pLng = p?.lng ?? b.customer_lng, eta: number | null = null;
  if (p && !p.is_bot) {
    // Real provider: the pin is their phone's last GPS fix; ETA from the remaining distance (~20 km/h in city traffic).
    if (b.status === "accepted" || b.status === "en_route") eta = Math.max(1, Math.ceil(km(pLat, pLng, b.customer_lat, b.customer_lng) * 3));
  } else if (b.status === "en_route" && b.start_lat != null && b.en_route_at) {
    const f = Math.min(1, (tx.__now - ms(b.en_route_at)) / 1000 / TRAVEL_S);
    pLat = b.start_lat + (b.customer_lat - b.start_lat) * f; pLng = b.start_lng + (b.customer_lng - b.start_lng) * f;
    eta = Math.max(1, Math.ceil(((1 - f) * TRAVEL_S) / 60));
  } else if (b.status === "accepted" && p) eta = Math.max(1, Math.ceil(km(p.lat, p.lng, b.customer_lat, b.customer_lng) * 3));
  if (p?.is_bot && (b.status === "arrived" || b.status === "in_progress")) { pLat = b.customer_lat; pLng = b.customer_lng; }
  const [cu] = await tx`select name from users where id = ${b.customer_id}`;
  const [oRating, oCount] = isCustomer ? [p?.rating_avg ?? 0, p?.rating_count ?? 0] : await customerRating(tx, b.customer_id);
  const [rated] = await tx`select 1 from ratings where booking_id = ${b.id} and reviewer_id = ${me}`;
  const offers = b.status === "pending" ? await tx`select o.*, u.name, pp.lat, pp.lng, pp.rating_avg, pp.rating_count, pp.total_jobs
      from fare_offers o join users u on u.id = o.provider_id join provider_profiles pp on pp.user_id = o.provider_id
      where o.booking_id = ${b.id} and o.status = 'open' order by o.created_at` : [];
  const awaiting = b.status === "in_progress" && !!b.completion_photo_path;
  const needsRating = b.status === "completed" && !rated;
  let photo: string | null = null;
  if ((awaiting || needsRating) && b.completion_photo_path && b.completion_photo_path !== "provider") {
    const { data } = await storage.createSignedUrl(b.completion_photo_path, 300);
    photo = data?.signedUrl ?? null;
  }
  return {
    id: b.id, status: b.status, category: b.category, description: b.description, fare: b.agreed_fare, isCustomer,
    otherName: isCustomer ? (p?.name ?? "") : cu.name, otherRating: oRating, otherRatingCount: oCount,
    lat: b.customer_lat, lng: b.customer_lng, providerLat: pLat, providerLng: pLng, etaMin: eta,
    distanceKm: round1(km(pLat, pLng, b.customer_lat, b.customer_lng)), awaitingConfirmation: awaiting, needsRating, photo,
    cancelReason: b.cancel_reason, address: b.address,
    offers: offers.map((o: Row) => {
      const d = km(o.lat, o.lng, b.customer_lat, b.customer_lng);
      return { id: o.id, providerName: o.name, amount: o.amount, etaMin: Math.max(1, Math.ceil(d * 3)), distanceKm: round1(d),
        rating: o.rating_avg, ratingCount: o.rating_count, jobs: o.total_jobs,
        secondsLeft: Math.max(0, Math.floor((ms(o.expires_at) - tx.__now) / 1000)), isCounter: o.amount > b.agreed_fare };
    }),
    searchSeconds: Math.floor((tx.__now - ms(b.created_at)) / 1000), net: b.agreed_fare - commission(b.agreed_fare),
  };
}

const userView = (u: Row) => ({ id: u.id, name: u.name, phone: u.phone, role: u.user_role });

async function loadBooking(tx: Tx, id: string, me: string) {
  if (!/^[0-9a-f-]{36}$/.test(id)) fail("Booking not found", 404);
  const [b] = await tx`select * from bookings where id = ${id} for update`;
  if (!b || (b.customer_id !== me && b.provider_id !== me && b.status !== "pending")) fail("Booking not found", 404);
  return b;
}

// ---------------- routes ----------------
async function route(req: Request, path: string): Promise<Response> {
  const m = req.method;
  const body: Row = m === "POST" || m === "PUT" ? await req.json().catch(() => ({})) : {};
  const q = new URL(req.url).searchParams;
  if (m === "GET" && path === "/health") return json({ status: "ok" });

  if (m === "GET" && path === "/lookup") {
    const n = normalizePhone(q.get("phone") ?? "");
    if (!n) return json({ user: null });
    const [u] = await sql`select id, name, phone, user_role from users where phone = ${n} and not is_bot and is_active`;
    return json({ user: u ? userView(u) : null });
  }

  if (m === "POST" && path === "/auth/signin") {
    const n = normalizePhone(body.phone) ?? fail("Enter a valid mobile number, e.g. 0300 1234567");
    const u = await sql.begin(async (tx) => {
      const [existing] = await tx`select * from users where phone = ${n}`;
      if (existing) { if (existing.is_bot || !existing.is_active) fail("This number can’t sign in"); return existing; }
      const name = String(body.name ?? "").trim().replace(/\s+/g, " ");
      if (name.length < 2) fail("Enter your full name");
      const [created] = await tx`insert into users (name, phone) values (${name.slice(0, 40)}, ${n}) returning *`;
      await tx`insert into wallets (user_id, balance) values (${created.id}, ${WELCOME})`;
      await tx`insert into transactions (to_user, amount, type, status, external_ref) values (${created.id}, ${WELCOME}, 'wallet_topup', 'approved', ${"WELCOME-" + created.id.slice(0, 8).toUpperCase()})`;
      return created;
    });
    const token = await new SignJWT({ role: u.user_role }).setProtectedHeader({ alg: "HS256" }).setSubject(u.id)
      .setIssuedAt().setExpirationTime("30d").sign(await keyPromise);
    return json({ token, user: userView(u) });
  }

  // ---- everything below needs a session ----
  const h = req.headers.get("authorization") ?? "";
  let me: string, role: string;
  try {
    const { payload } = await jwtVerify(h.replace(/^Bearer /, ""), await keyPromise, { algorithms: ["HS256"] });
    me = String(payload.sub); role = String(payload.role);
  } catch { fail("Session expired. Sign in again", 401); }
  const mode = (req.headers.get("x-active-mode") ?? "CUSTOMER").toUpperCase();
  if (mode !== "CUSTOMER" && mode !== "PROVIDER") fail("Bad X-Active-Mode");
  const needProvider = () => { if (mode !== "PROVIDER" || role === "customer") fail("Switch to provider mode", 403); };
  const needCustomer = () => { if (mode !== "CUSTOMER") fail("Switch to customer mode", 403); };

  return await sql.begin(async (tx: Tx) => {
    await tick(tx, me!, mode);
    const [u] = await tx`select * from users where id = ${me!} and is_active`;
    if (!u) fail("Session expired. Sign in again", 401);

    if (m === "GET" && path === "/me") {
      const [w] = await tx`select * from wallets where user_id = ${me!}`;
      const [p] = await tx`select * from provider_profiles where user_id = ${me!}`;
      return json({ user: userView(u), wallet: { userId: me!, balance: w.balance, escrow: w.escrow_held, earned: w.total_earned },
        provider: p ? { userId: me!, categories: p.categories, isOnline: p.is_online, isOnJob: p.is_on_job, tier: p.tier,
          rating: p.rating_avg, ratingCount: p.rating_count, totalJobs: p.total_jobs, lat: p.lat, lng: p.lng,
          radiusKm: p.service_radius_km, verified: p.is_verified } : null });
    }

    if (m === "GET" && path === "/map-providers") {
      const rows = await tx`select pp.*, u.name from provider_profiles pp join users u on u.id = pp.user_id join wallets w on w.user_id = pp.user_id
        where pp.user_id <> ${me!} and pp.is_online and not pp.is_on_job and pp.is_verified and w.balance >= ${MIN_BALANCE}`;
      return json(rows.map((p: Row) => ({ id: p.user_id, name: p.name, category: p.categories[0], rating: p.rating_avg, lat: p.lat, lng: p.lng })));
    }

    if (m === "GET" && path === "/job") {
      const prov = mode === "PROVIDER";
      const col = prov ? tx`provider_id` : tx`customer_id`;
      const [b] = await tx`select * from bookings b where ${col} = ${me!} and (status = any(${tx.array(ACTIVE)})
          or (status = 'completed' and completed_at > now() - interval '24 hours'
              and not exists (select 1 from ratings r where r.booking_id = b.id and r.reviewer_id = ${me!}))
          or (${!prov} and not by_bot and status = 'cancelled' and step_at > now() - interval '6 seconds'))
        order by created_at desc limit 1`;
      return json({ job: b ? await jobView(tx, b, me!) : null });
    }

    if (m === "POST" && path === "/requests") {
      needCustomer();
      const cat = String(body.category);
      if (!(cat in CATEGORIES)) fail("Choose a service");
      const fare = int(body.fare, MIN_FARE, MAX_FARE, `Fare must be ${rs(MIN_FARE)}–${rs(MAX_FARE)}`);
      const [busy] = await tx`select 1 from bookings where customer_id = ${me!} and status = any(${tx.array(ACTIVE)})`;
      if (busy) fail("You already have an active request", 409);
      const [w] = await tx`select balance from wallets where user_id = ${me!}`;
      if (w.balance < fare) fail("Not enough in wallet. Top up first");
      const lat = typeof body.lat === "number" ? body.lat : HOME.lat, lng = typeof body.lng === "number" ? body.lng : HOME.lng;
      if (!Number.isFinite(lat) || !Number.isFinite(lng) || !inLahore(lat, lng)) fail("One Click works in Lahore for now. Choose a place in Lahore.");
      const address = String(body.address ?? "").trim().slice(0, 120) || "Pinned location";
      await tx`insert into bookings (customer_id, category, description, agreed_fare, customer_lat, customer_lng, address, expires_at)
        values (${me!}, ${cat}, ${String(body.description ?? "").trim().slice(0, 200)}, ${fare}, ${lat}, ${lng}, ${address}, now() + make_interval(secs => ${SEARCH_S}))`;
      return json({ ok: true }, 201);
    }

    if (m === "GET" && path === "/requests") {
      const [p] = await tx`select * from provider_profiles where user_id = ${me!}`;
      if (!p || !p.is_online || p.is_on_job) return json([]);
      const rows = await tx`select b.*, u.name from bookings b join users u on u.id = b.customer_id
        where b.status = 'pending' and b.customer_id <> ${me!} and b.category = any(${tx.array(p.categories)}) order by b.created_at desc`;
      const out = [];
      for (const b of rows) {
        const d = km(p.lat, p.lng, b.customer_lat, b.customer_lng);
        if (d > p.service_radius_km) continue;
        const [mine] = await tx`select * from fare_offers where booking_id = ${b.id} and provider_id = ${me!} order by created_at desc limit 1`;
        out.push({ id: b.id, customerName: b.name, customerRating: (await customerRating(tx, b.customer_id))[0], category: b.category,
          description: b.description, address: b.address, fare: b.agreed_fare, distanceKm: round1(d), postedSecondsAgo: Math.floor((tx.__now - ms(b.created_at)) / 1000),
          myOffer: mine?.amount ?? null, myOfferStatus: mine?.status ?? null,
          myOfferSecondsLeft: mine ? Math.max(0, Math.floor((ms(mine.expires_at) - tx.__now) / 1000)) : 0 });
      }
      return json(out);
    }

    const rm = path.match(/^\/bookings\/([0-9a-f-]{36})\/(raise|accept-offer|decline-offer|cancel|offer|advance|complete|confirm|dispute|rate)$/);
    if (m === "POST" && rm) {
      const [, id, action] = rm;
      const b = await loadBooking(tx, id, me!);
      const isCust = b.customer_id === me!, isProv = b.provider_id === me!;
      if (["raise", "accept-offer", "decline-offer", "confirm", "dispute"].includes(action) && !isCust) fail("Not your request", 403);
      if (["advance", "complete"].includes(action) && (!isProv || mode !== "PROVIDER")) fail("Not your job", 403);
      switch (action) {
        case "raise": {
          const f = int(body.fare, 1, MAX_FARE, "Maximum fare reached");
          if (b.status !== "pending") fail("Request is no longer active", 409);
          if (f <= b.agreed_fare) fail("Maximum fare reached");
          const [w] = await tx`select balance from wallets where user_id = ${me!}`;
          if (w.balance < f) fail("Not enough in wallet. Top up first");
          await tx`update bookings set agreed_fare = ${f}, step_at = now(), expires_at = now() + make_interval(secs => ${SEARCH_S}) where id = ${id}`;
          break;
        }
        case "accept-offer": await acceptOffer(tx, b, String(body.offerId)); break;
        case "decline-offer": await tx`update fare_offers set status = 'declined' where id = ${String(body.offerId)} and booking_id = ${id} and status = 'open'`; break;
        case "cancel":
          if (!isCust && !isProv) fail("Not your booking", 403);
          await cancel(tx, b, isCust ? "customer_cancelled" : "provider_cancelled");
          break;
        case "offer": {
          needProvider();
          const [p] = await tx`select pp.*, w.balance from provider_profiles pp join wallets w on w.user_id = pp.user_id where pp.user_id = ${me!}`;
          if (!p.is_online || p.is_on_job) fail("Go online to send offers");
          if (p.balance < MIN_BALANCE) fail("Top up to get requests");
          if (b.status !== "pending") fail("Request is no longer active", 409);
          if (!b.category || !p.categories.includes(b.category)) fail("Not your service");
          const amount = int(body.amount, b.agreed_fare, b.agreed_fare * 3, `Offer must be ${rs(b.agreed_fare)}–${rs(b.agreed_fare * 3)}`);
          const [open] = await tx`select 1 from fare_offers where booking_id = ${id} and provider_id = ${me!} and status = 'open'`;
          if (open) fail("Offer already sent", 409);
          await tx`insert into fare_offers (booking_id, provider_id, amount, expires_at) values (${id}, ${me!}, ${amount}, now() + make_interval(secs => ${OFFER_S}))`;
          break;
        }
        case "advance": {
          if (String(body.status) === "arrived") {
            // Real customers only: a simulated customer's pin is near the provider, so demos still work at a desk.
            const [pp] = await tx`select pp.lat, pp.lng, pp.location_updated_at, cu.is_bot as customer_bot from provider_profiles pp
              join users cu on cu.id = ${b.customer_id} where pp.user_id = ${me!}`;
            const fresh = pp.location_updated_at && tx.__now - ms(pp.location_updated_at) < 120_000;
            const d = km(pp.lat, pp.lng, b.customer_lat, b.customer_lng);
            if (fresh && !pp.customer_bot && d > ARRIVE_KM) fail(`You’re still ${round1(d)} km away. Tap again when you’re at the door.`);
          }
          await step(tx, b, String(body.status));
          break;
        }
        case "complete": {
          if (b.status !== "in_progress") fail("Start the job first", 409);
          const b64 = String(body.photoBase64 ?? "");
          if (b64.length < 100 || b64.length > 2_700_000) fail("Photo proof is required");
          const bytes = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
          if (bytes[0] !== 0xff || bytes[1] !== 0xd8) fail("Photo must be a JPEG");
          const path = `${id}.jpg`;
          const { error } = await storage.upload(path, bytes, { contentType: "image/jpeg", upsert: true });
          if (error) fail("Couldn’t upload the photo. Try again", 502);
          await tx`update bookings set completion_photo_path = ${path}, step_at = now() where id = ${id}`;
          break;
        }
        case "confirm":
          await release(tx, b);
          // A simulated provider rates the customer on its own.
          if ((await tx`select is_bot from users where id = ${b.provider_id}`)[0]?.is_bot) await rateInternal(tx, b, b.provider_id, 5, ["Friendly"]);
          break;
        case "dispute":
          if (!["arrived", "in_progress"].includes(b.status)) fail("Not available right now", 409);
          await tx`update bookings set status = 'disputed', cancel_reason = ${String(body.reason ?? "").slice(0, 200)}, step_at = now() where id = ${id}`;
          break;
        case "rate": {
          if (!isCust && !isProv) fail("Not your booking", 403);
          if (b.status !== "completed") fail("Rate after the job is done", 409);
          const score = int(body.score, 1, 5, "Choose 1–5 stars");
          const tags = Array.isArray(body.tags) ? body.tags.map(String).slice(0, 6) : [];
          await rateInternal(tx, b, me!, score, tags);
          break;
        }
      }
      return json({ ok: true });
    }

    if (m === "PUT" && path === "/online") {
      needProvider();
      const on = body.online === true;
      const [p] = await tx`select pp.*, w.balance from provider_profiles pp join wallets w on w.user_id = pp.user_id where pp.user_id = ${me!}`;
      if (!p) fail("Not a provider", 403);
      if (on && !p.is_verified) fail("Verification pending");
      if (on && p.balance < MIN_BALANCE) fail(`Top up to get requests — ${rs(MIN_BALANCE)} minimum`);
      await tx`update provider_profiles set is_online = ${on}, last_bot_request_at = now() - interval '7 seconds' where user_id = ${me!}`;
      return json({ ok: true });
    }

    if (m === "PUT" && path === "/location") {
      needProvider();
      const lat = Number(body.lat), lng = Number(body.lng);
      if (!Number.isFinite(lat) || !Number.isFinite(lng) || !inPakistan(lat, lng)) fail("Location looks wrong");
      const r = await tx`update provider_profiles set lat = ${lat}, lng = ${lng}, location_updated_at = now() where user_id = ${me!} returning user_id`;
      if (!r.length) fail("Not a provider", 403);
      const [job] = await tx`select id from bookings where provider_id = ${me!} and status in ('accepted','en_route','arrived','in_progress') limit 1`;
      if (job) await tx`insert into locations (booking_id, provider_id, lat, lng) values (${job.id}, ${me!}, ${lat}, ${lng})`;
      return json({ ok: true });
    }

    if (m === "GET" && path === "/wallet") {
      const [w] = await tx`select * from wallets where user_id = ${me!}`;
      const rows = await tx`select * from transactions where (to_user = ${me!} and type <> 'commission')
          or (from_user = ${me!} and type in ('escrow_hold', 'withdrawal', 'cash_job_fee')) order by created_at desc limit 40`;
      return json({ wallet: { userId: me!, balance: w.balance, escrow: w.escrow_held, earned: w.total_earned },
        txns: rows.map((t: Row) => ({
          title: t.type === "escrow_hold" ? (t.status === "held" ? "Payment on hold" : t.status === "refunded" ? "Payment (refunded)" : "Paid for job") : (TXN_TITLES[t.type] ?? t.type),
          amount: t.amount, incoming: t.to_user === me!, status: t.status, ref: t.external_ref, createdAt: ms(t.created_at) })) });
    }

    if (m === "POST" && path === "/wallet/topup") {
      const amount = int(body.amount, 100, 50000, "Amount must be Rs 100–50,000");
      const ref = String(body.tid ?? "").trim().toUpperCase();
      if (!/^[A-Z0-9-]{6,20}$/.test(ref)) fail("Enter the 6–20 character TID");
      const [dup] = await tx`select 1 from transactions where external_ref = ${ref}`;
      if (dup) fail("This TID was already used", 409);
      // Demo: approved at once. Live: our team matches TID + amount with the business statement first.
      await tx`select 1 from wallets where user_id = ${me!} for update`;
      await tx`insert into transactions (to_user, amount, type, status, external_ref) values (${me!}, ${amount}, 'wallet_topup', 'approved', ${ref})`;
      await tx`update wallets set balance = balance + ${amount} where user_id = ${me!}`;
      return json({ ok: true });
    }

    if (m === "POST" && path === "/wallet/withdraw") {
      const amount = int(body.amount, 500, 1_000_000, "Minimum withdrawal is Rs 500");
      const [w] = await tx`select balance from wallets where user_id = ${me!} for update`;
      if (amount > w.balance) fail("Not enough balance");
      await tx`update wallets set balance = balance - ${amount}, total_withdrawn = total_withdrawn + ${amount} where user_id = ${me!}`;
      await tx`insert into transactions (from_user, amount, type, status) values (${me!}, ${amount}, 'withdrawal', 'pending')`;
      return json({ ok: true });
    }

    if (m === "GET" && path === "/history") {
      const rows = await tx`select * from bookings where (customer_id = ${me!} and not (by_bot and status = 'cancelled')) or provider_id = ${me!}
        order by created_at desc limit 30`;
      const out = [];
      for (const b of rows) out.push(await jobView(tx, b, me!));
      return json(out);
    }

    if (m === "POST" && path === "/sos") {
      const [b] = await tx`select id from bookings where (customer_id = ${me!} or provider_id = ${me!}) and status = any(${tx.array(ACTIVE)}) order by created_at desc limit 1`;
      await tx`insert into sos_alerts (user_id, booking_id) values (${me!}, ${b?.id ?? null})`;
      return json({ ok: true });
    }

    if (m === "POST" && path === "/push-token") {
      await tx`update users set push_token = ${String(body.token ?? "").slice(0, 400) || null} where id = ${me!}`;
      return json({ ok: true });
    }

    return fail("Not found", 404);
  }) as Response;
}

Deno.serve(async (req) => {
  const path = new URL(req.url).pathname.replace(/^.*?\/api(?=\/|$)/, "") || "/";
  try {
    return await route(req, path);
  } catch (e) {
    if (e instanceof AppError) return json({ error: { message: e.message } }, e.status);
    const code = (e as { code?: string }).code;
    if (code === "23505") return json({ error: { message: "Already used" } }, 409);
    if (code === "23514") return json({ error: { message: "Not enough balance" } }, 409);
    console.error("unhandled", (e as Error).message);
    return json({ error: { message: "Something went wrong. Try again" } }, 500);
  }
});
