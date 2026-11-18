// One Click demo API — Supabase Edge Function (Deno). All business + money logic server-side.
import postgres from "npm:postgres@3.4.5";
import { jwtVerify, SignJWT } from "npm:jose@5.9.6";

const sql = postgres(Deno.env.get("SUPABASE_DB_URL")!, {
  prepare: false,
  max: 3,
  types: { bigint: { to: 20, from: [20], parse: (x: string) => Number(x), serialize: (x: number) => String(x) } },
});
const secret = new TextEncoder().encode(Deno.env.get("DEMO_JWT_SECRET") ?? "");
const MIN_PROVIDER_BALANCE = 200;
const REQUEST_TIMEOUT_S = 30;
const DEMO_TRAVEL_S = 90; // simulated en-route travel time
const ACTIVE = ["pending", "accepted", "en_route", "arrived", "in_progress", "disputed"];

class HttpError extends Error {
  constructor(public status: number, public code: string, message: string) { super(message); }
}
const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json" } });
const commissionOf = (gross: number) => Math.floor((gross * 15 + 50) / 100);

type Me = { id: string; role: string };
async function auth(req: Request): Promise<Me> {
  const h = req.headers.get("authorization") ?? "";
  if (!h.startsWith("Bearer ")) throw new HttpError(401, "missing_token", "Login required");
  try {
    const { payload } = await jwtVerify(h.slice(7), secret, { algorithms: ["HS256"] });
    return { id: String(payload.sub), role: String(payload.role) };
  } catch {
    throw new HttpError(401, "invalid_token", "Session expired, login again");
  }
}
function activeMode(req: Request): "CUSTOMER" | "PROVIDER" {
  const m = (req.headers.get("x-active-mode") ?? "CUSTOMER").toUpperCase();
  if (m !== "CUSTOMER" && m !== "PROVIDER") throw new HttpError(400, "invalid_active_mode", "Bad X-Active-Mode");
  return m;
}
function requireProvider(me: Me, req: Request) {
  if (activeMode(req) !== "PROVIDER" || me.role === "customer") {
    throw new HttpError(403, "mode_not_allowed", "Switch to Provider mode");
  }
}
const int = (v: unknown, name: string, min: number, max: number) => {
  if (typeof v !== "number" || !Number.isSafeInteger(v) || v < min || v > max) {
    throw new HttpError(400, "invalid_" + name, `${name} must be a whole number ${min}-${max}`);
  }
  return v;
};
const num = (v: unknown, name: string) => {
  if (typeof v !== "number" || !Number.isFinite(v)) throw new HttpError(400, "invalid_" + name, `${name} required`);
  return v;
};

async function loadBooking(id: string, me: Me) {
  const [b] = await sql`select * from bookings where id = ${id}`;
  if (!b || (b.customer_id !== me.id && b.provider_id !== me.id)) throw new HttpError(404, "not_found", "Booking not found");
  return b;
}

async function expireStale() {
  await sql`update bookings set status = 'cancelled', cancel_reason = 'timeout'
            where status = 'pending' and expires_at < now()`;
}

// Booking view with counterpart info + simulated live position / ETA while en_route.
async function bookingView(b: any, me: Me) {
  const otherId = b.customer_id === me.id ? b.provider_id : b.customer_id;
  const [other] = await sql`select u.name, u.user_role, pp.rating_avg from users u
                            left join provider_profiles pp on pp.user_id = u.id where u.id = ${otherId}`;
  const [pp] = await sql`select lat, lng from provider_profiles where user_id = ${b.provider_id}`;
  let pLat = pp.lat, pLng = pp.lng, eta: number | null = null;
  if (b.status === "en_route" && b.start_lat != null) {
    const t = Math.min(1, (Date.now() - new Date(b.en_route_at).getTime()) / 1000 / DEMO_TRAVEL_S);
    pLat = b.start_lat + (b.customer_lat - b.start_lat) * t;
    pLng = b.start_lng + (b.customer_lng - b.start_lng) * t;
    eta = Math.max(0, Math.ceil(((1 - t) * DEMO_TRAVEL_S) / 60));
  }
  if (b.status === "arrived" || b.status === "in_progress") { pLat = b.customer_lat; pLng = b.customer_lng; }
  const [rated] = await sql`select 1 from ratings where booking_id = ${b.id} and reviewer_id = ${me.id}`;
  return {
    id: b.id, status: b.status, category: b.category, description: b.description,
    agreed_fare: b.agreed_fare, payment_method: b.payment_method,
    my_side: b.customer_id === me.id ? "customer" : "provider",
    other_name: other?.name ?? "", other_role: b.customer_id === me.id ? "Provider" : "Customer",
    other_rating: other?.rating_avg != null ? Number(other.rating_avg) : null,
    customer_lat: b.customer_lat, customer_lng: b.customer_lng, provider_lat: pLat, provider_lng: pLng,
    eta_min: eta, awaiting_confirmation: b.status === "in_progress" && !!b.completion_photo_url,
    needs_rating: b.status === "completed" && !rated, cancel_reason: b.cancel_reason,
    expires_in_s: b.status === "pending" ? Math.max(0, Math.round((new Date(b.expires_at).getTime() - Date.now()) / 1000)) : null,
    created_at: b.created_at,
  };
}

async function route(req: Request, path: string): Promise<Response> {
  const m = req.method;
  const body = m === "POST" || m === "PUT" ? await req.json().catch(() => ({})) : {};
  const q = new URL(req.url).searchParams;

  if (m === "GET" && path === "/health") return json({ status: "ok" });

  if (m === "GET" && path === "/demo/users") {
    const rows = await sql`select u.id, u.name, u.user_role, pp.categories from users u
                           left join provider_profiles pp on pp.user_id = u.id
                           order by (u.user_role = 'customer') desc, u.name`;
    return json({ users: rows });
  }

  if (m === "POST" && path === "/auth/demo") {
    const [u] = await sql`select id, user_role from users where id = ${String(body.user_id ?? "")}::uuid and is_active`
      .catch(() => []);
    if (!u) throw new HttpError(404, "user_not_found", "Demo user not found");
    const token = await new SignJWT({ role: u.user_role }).setProtectedHeader({ alg: "HS256" })
      .setSubject(u.id).setIssuedAt().setExpirationTime("30d").sign(secret);
    return json({ token });
  }

  const me = await auth(req);

  if (m === "GET" && path === "/me") {
    const [u] = await sql`select id, name, phone, user_role from users where id = ${me.id}`;
    const [w] = await sql`select balance, escrow_held, total_earned from wallets where user_id = ${me.id}`;
    const [pp] = await sql`select categories, is_online, is_on_job, tier, rating_avg, total_jobs, is_verified
                           from provider_profiles where user_id = ${me.id}`;
    return json({ user: u, wallet: w, provider: pp ? { ...pp, rating_avg: Number(pp.rating_avg) } : null });
  }

  if (m === "GET" && path === "/categories") {
    return json({ categories: await sql`select slug, name_en, name_ur, pin_color from categories where is_visible order by sort_order` });
  }

  if (m === "GET" && path === "/providers/nearby") {
    const lat = Number(q.get("lat")), lng = Number(q.get("lng"));
    if (!Number.isFinite(lat) || !Number.isFinite(lng)) throw new HttpError(400, "invalid_location", "lat/lng required");
    const cat = q.get("category") || null;
    const pt = sql`extensions.st_setsrid(extensions.st_makepoint(${lng}, ${lat}), 4326)::extensions.geography`;
    const find = (withinRadius: boolean) => sql`
      select u.id, u.name, pp.categories, pp.rating_avg::float as rating_avg, pp.total_jobs, pp.tier, pp.is_verified,
             pp.lat, pp.lng, round((extensions.st_distance(pp.geog, ${pt}) / 1000)::numeric, 1)::float as distance_km
      from provider_profiles pp join users u on u.id = pp.user_id join wallets w on w.user_id = u.id
      where pp.is_online and pp.is_verified and not pp.is_on_job and w.balance >= ${MIN_PROVIDER_BALANCE}
        and u.id <> ${me.id} and (${cat}::text is null or ${cat} = any(pp.categories))
        and extensions.st_dwithin(pp.geog, ${pt}, ${withinRadius ? sql`pp.service_radius_km * 1000` : sql`30000`})
      order by distance_km limit 50`;
    let providers = await find(true), fallback = false;
    if (providers.length === 0) { providers = await find(false); fallback = true; }
    return json({ providers, fallback });
  }

  if (m === "PUT" && path === "/providers/online") {
    requireProvider(me, req);
    const online = body.online === true;
    if (online) {
      const [r] = await sql`select pp.is_verified, w.balance from provider_profiles pp
                            join wallets w on w.user_id = pp.user_id where pp.user_id = ${me.id}`;
      if (!r?.is_verified) throw new HttpError(403, "not_verified", "Account not verified yet");
      if (r.balance < MIN_PROVIDER_BALANCE) {
        throw new HttpError(400, "low_wallet", `Top up: Rs. ${MIN_PROVIDER_BALANCE} minimum needed to go online`);
      }
    }
    await sql`update provider_profiles set is_online = ${online} where user_id = ${me.id}`;
    return json({ is_online: online });
  }

  if (m === "GET" && path === "/wallet") {
    const [w] = await sql`select balance, escrow_held, total_earned, total_withdrawn from wallets where user_id = ${me.id}`;
    const txns = await sql`select id, type, amount, status, external_ref, created_at,
                             (to_user = ${me.id}) as incoming from transactions
                           where from_user = ${me.id} or to_user = ${me.id} order by created_at desc limit 30`;
    return json({ wallet: w, transactions: txns });
  }

  if (m === "POST" && path === "/wallet/topup") {
    const amount = int(body.amount, "amount", 100, 50000);
    const tid = String(body.tid ?? "").trim().toUpperCase();
    if (!/^[A-Z0-9-]{6,20}$/.test(tid)) throw new HttpError(400, "invalid_tid", "Enter the 6-20 character TID");
    // DEMO: auto-approved. Production: admin matches TID + amount on business statement first.
    try {
      await sql.begin(async (tx) => {
        await tx`select 1 from wallets where user_id = ${me.id} for update`;
        await tx`insert into transactions (to_user, amount, type, status, external_ref)
                 values (${me.id}, ${amount}, 'wallet_topup', 'approved', ${tid})`;
        await tx`update wallets set balance = balance + ${amount} where user_id = ${me.id}`;
      });
    } catch (e) {
      if ((e as { code?: string }).code === "23505") throw new HttpError(409, "duplicate_tid", "This TID was already used");
      throw e;
    }
    return json({ ok: true });
  }

  if (m === "GET" && path === "/bookings/active") {
    await expireStale();
    const prov = activeMode(req) === "PROVIDER";
    const col = prov ? sql`provider_id` : sql`customer_id`;
    const [b] = await sql`select * from bookings b where ${col} = ${me.id} and (
        status = any(${sql.array(ACTIVE)})
        or (status = 'completed' and completed_at > now() - interval '24 hours'
            and not exists (select 1 from ratings r where r.booking_id = b.id and r.reviewer_id = ${me.id}))
        or (status = 'cancelled' and created_at > now() - interval '15 seconds'))
      order by created_at desc limit 1`;
    return json({ booking: b ? await bookingView(b, me) : null });
  }

  if (m === "GET" && path === "/bookings") {
    const rows = await sql`select * from bookings where customer_id = ${me.id} or provider_id = ${me.id}
                           order by created_at desc limit 30`;
    return json({ bookings: await Promise.all(rows.map((b) => bookingView(b, me))) });
  }

  if (m === "POST" && path === "/bookings") {
    if (activeMode(req) !== "CUSTOMER") throw new HttpError(403, "mode_not_allowed", "Switch to Customer mode");
    const fare = int(body.offer_fare, "offer_fare", 100, 100000);
    const lat = num(body.lat, "lat"), lng = num(body.lng, "lng");
    const providerId = String(body.provider_id ?? "");
    await expireStale();
    const [busy] = await sql`select 1 from bookings where customer_id = ${me.id} and status = any(${sql.array(ACTIVE)})`;
    if (busy) throw new HttpError(409, "booking_exists", "You already have an active booking");
    const [p] = await sql`select pp.categories, pp.is_online, pp.is_on_job from provider_profiles pp
                          where pp.user_id = ${providerId}::uuid`.catch(() => []);
    if (!p || !p.is_online || p.is_on_job) throw new HttpError(409, "provider_unavailable", "Provider is not available");
    const category = String(body.category ?? p.categories[0]);
    const [w] = await sql`select balance from wallets where user_id = ${me.id}`;
    if (w.balance < fare) throw new HttpError(400, "insufficient_funds", "Wallet balance too low — top up first");
    const [b] = await sql`insert into bookings (customer_id, provider_id, category, description, agreed_fare,
                            customer_lat, customer_lng, expires_at)
                          values (${me.id}, ${providerId}, ${category}, ${String(body.description ?? "").slice(0, 300)},
                            ${fare}, ${lat}, ${lng}, now() + make_interval(secs => ${REQUEST_TIMEOUT_S}))
                          returning *`;
    return json({ booking: await bookingView(b, me) }, 201);
  }

  const bm = path.match(/^\/bookings\/([0-9a-f-]{36})\/(accept|reject|status|complete|confirm|cancel|dispute|rate)$/);
  if (m === "POST" && bm) {
    const [, id, action] = bm;
    await expireStale();
    const b0 = await loadBooking(id, me);
    const isProv = b0.provider_id === me.id;
    if (["accept", "reject", "status", "complete"].includes(action)) {
      requireProvider(me, req);
      if (!isProv) throw new HttpError(403, "not_your_booking", "Not your booking");
    }
    if (["confirm", "dispute"].includes(action) && b0.customer_id !== me.id) {
      throw new HttpError(403, "not_your_booking", "Only the customer can do this");
    }
    const bad = (msg = "Not allowed in current status") => new HttpError(409, "invalid_status", msg);

    if (action === "accept") {
      await sql.begin(async (tx) => {
        const [b] = await tx`select * from bookings where id = ${id} for update`;
        if (b.status !== "pending") throw bad("Request expired or already handled");
        const [w] = await tx`select balance from wallets where user_id = ${b.customer_id} for update`;
        if (w.balance < b.agreed_fare) {
          await tx`update bookings set status = 'cancelled', cancel_reason = 'insufficient_funds' where id = ${id}`;
          return;
        }
        await tx`update wallets set balance = balance - ${b.agreed_fare}, escrow_held = escrow_held + ${b.agreed_fare}
                 where user_id = ${b.customer_id}`;
        await tx`insert into transactions (booking_id, from_user, amount, type, status)
                 values (${id}, ${b.customer_id}, ${b.agreed_fare}, 'escrow_hold', 'held')`;
        await tx`update bookings set status = 'accepted', accepted_at = now() where id = ${id}`;
        await tx`update provider_profiles set is_on_job = true where user_id = ${me.id}`;
      });
    } else if (action === "reject") {
      const r = await sql`update bookings set status = 'cancelled', cancel_reason = 'rejected'
                          where id = ${id} and status = 'pending' returning id`;
      if (!r.length) throw bad();
    } else if (action === "status") {
      const next = String(body.status);
      const from: Record<string, string> = { en_route: "accepted", arrived: "en_route", in_progress: "arrived" };
      if (!from[next]) throw new HttpError(400, "invalid_status_value", "status must be en_route|arrived|in_progress");
      const r = await sql`update bookings b set status = ${next},
          en_route_at = case when ${next} = 'en_route' then now() else en_route_at end,
          started_at = case when ${next} = 'in_progress' then now() else started_at end,
          start_lat = coalesce(start_lat, pp.lat), start_lng = coalesce(start_lng, pp.lng)
        from provider_profiles pp where pp.user_id = b.provider_id and b.id = ${id} and b.status = ${from[next]}
        returning b.id`;
      if (!r.length) throw bad();
    } else if (action === "complete") {
      const photo = String(body.photo_url ?? "");
      if (!photo) throw new HttpError(400, "photo_required", "Photo proof is required");
      const r = await sql`update bookings set completion_photo_url = ${photo}
                          where id = ${id} and status = 'in_progress' returning id`;
      if (!r.length) throw bad();
    } else if (action === "confirm") {
      await sql.begin(async (tx) => {
        const [b] = await tx`select * from bookings where id = ${id} for update`;
        if (b.status !== "in_progress" || !b.completion_photo_url) throw bad("Provider has not completed the job yet");
        const gross = b.agreed_fare, fee = commissionOf(gross), net = gross - fee;
        const [a, c] = [b.customer_id, b.provider_id].sort();
        await tx`select 1 from wallets where user_id in (${a}, ${c}) order by user_id for update`;
        await tx`update wallets set escrow_held = escrow_held - ${gross} where user_id = ${b.customer_id}`;
        await tx`update wallets set balance = balance + ${net}, total_earned = total_earned + ${net}
                 where user_id = ${b.provider_id}`;
        await tx`update transactions set status = 'released' where booking_id = ${id} and type = 'escrow_hold'`;
        await tx`insert into transactions (booking_id, from_user, to_user, amount, type) values
                 (${id}, ${b.customer_id}, ${b.provider_id}, ${net}, 'release'),
                 (${id}, ${b.customer_id}, null, ${fee}, 'commission')`;
        await tx`update bookings set status = 'completed', completed_at = now() where id = ${id}`;
        await tx`update provider_profiles set is_on_job = false, total_jobs = total_jobs + 1 where user_id = ${b.provider_id}`;
      });
    } else if (action === "cancel") {
      await sql.begin(async (tx) => {
        const [b] = await tx`select * from bookings where id = ${id} for update`;
        if (!["pending", "accepted", "en_route"].includes(b.status)) throw bad("Cannot cancel after provider arrived");
        if (b.status !== "pending") {
          await tx`select 1 from wallets where user_id = ${b.customer_id} for update`;
          await tx`update wallets set balance = balance + ${b.agreed_fare}, escrow_held = escrow_held - ${b.agreed_fare}
                   where user_id = ${b.customer_id}`;
          await tx`update transactions set status = 'refunded' where booking_id = ${id} and type = 'escrow_hold'`;
          await tx`insert into transactions (booking_id, to_user, amount, type) values (${id}, ${b.customer_id}, ${b.agreed_fare}, 'refund')`;
          await tx`update provider_profiles set is_on_job = false where user_id = ${b.provider_id}`;
        }
        await tx`update bookings set status = 'cancelled',
                   cancel_reason = ${b.customer_id === me.id ? "customer_cancelled" : "provider_cancelled"} where id = ${id}`;
      });
    } else if (action === "dispute") {
      const reason = String(body.reason ?? "").slice(0, 300);
      if (!reason) throw new HttpError(400, "reason_required", "Describe the problem");
      const r = await sql`update bookings set status = 'disputed', dispute_reason = ${reason}
                          where id = ${id} and status in ('arrived', 'in_progress') returning id`;
      if (!r.length) throw bad();
    } else if (action === "rate") {
      const score = int(body.score, "score", 1, 5);
      if (b0.status !== "completed") throw bad("Rate after completion");
      const reviewee = isProv ? b0.customer_id : b0.provider_id;
      await sql.begin(async (tx) => {
        await tx`insert into ratings (booking_id, reviewer_id, reviewee_id, score, comment)
                 values (${id}, ${me.id}, ${reviewee}, ${score}, ${String(body.comment ?? "").slice(0, 300)})
                 on conflict (booking_id, reviewer_id) do nothing`;
        await tx`update provider_profiles set rating_avg =
                   (select round(avg(score)::numeric, 1) from ratings where reviewee_id = ${reviewee})
                 where user_id = ${reviewee}`;
      });
    }
    const [b] = await sql`select * from bookings where id = ${id}`;
    return json({ booking: await bookingView(b, me) });
  }

  if (m === "POST" && path === "/sos") {
    await sql`insert into sos_alerts (user_id, booking_id, lat, lng)
              values (${me.id}, ${body.booking_id ?? null}, ${body.lat ?? null}, ${body.lng ?? null})`;
    return json({ ok: true, message: "SOS sent. Admin and contacts notified." });
  }

  throw new HttpError(404, "not_found", "Route not found");
}

Deno.serve(async (req) => {
  const path = new URL(req.url).pathname.replace(/^.*?\/api(?=\/|$)/, "") || "/";
  try {
    return await route(req, path);
  } catch (e) {
    if (e instanceof HttpError) return json({ error: { code: e.code, message: e.message } }, e.status);
    console.error("unhandled", (e as Error).message);
    return json({ error: { code: "server_error", message: "Something went wrong" } }, 500);
  }
});
