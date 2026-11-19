package pk.oneclick.app

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import kotlin.math.*

class AppError(message: String) : Exception(message)
enum class ActiveMode { CUSTOMER, PROVIDER }

// ---------- stored data (names per docs/GLOSSARY.md) ----------
@Serializable data class DbUser(val id: String, val name: String, val phone: String, val role: String)
@Serializable data class DbProvider(
    val userId: String, val categories: List<String>, val isOnline: Boolean, val isOnJob: Boolean = false,
    val tier: String, val rating: Double, val ratingCount: Int, val totalJobs: Int,
    val lat: Double, val lng: Double, val radiusKm: Int, val verified: Boolean = true,
)
@Serializable data class DbWallet(val userId: String, val balance: Long, val escrow: Long = 0, val earned: Long = 0)
@Serializable data class DbOffer(
    val id: String, val providerId: String, val amount: Long, val createdAt: Long, val expiresAt: Long,
    val status: String = "open", // open | accepted | declined | expired
)
@Serializable data class DbBooking(
    val id: String, val customerId: String, val providerId: String? = null, val category: String,
    val description: String, val status: String, val fare: Long, val offers: List<DbOffer> = emptyList(),
    val lat: Double, val lng: Double, val startLat: Double? = null, val startLng: Double? = null,
    val photo: String? = null, val cancelReason: String? = null, val createdAt: Long, val expiresAt: Long,
    val enRouteAt: Long? = null, val completedAt: Long? = null, val stepAt: Long, val byBot: Boolean = false,
)
@Serializable data class DbTxn(
    val id: String, val bookingId: String?, val from: String?, val to: String?, val amount: Long,
    val type: String, val status: String, val ref: String? = null, val createdAt: Long,
)
@Serializable data class DbRating(val bookingId: String, val reviewer: String, val reviewee: String, val score: Int, val tags: List<String> = emptyList())
@Serializable data class Db(
    val users: List<DbUser>, val providers: List<DbProvider>, val wallets: List<DbWallet>,
    val bookings: List<DbBooking> = emptyList(), val txns: List<DbTxn> = emptyList(),
    val ratings: List<DbRating> = emptyList(), val sosCount: Int = 0, val lastBotRequestAt: Long = 0,
)

// ---------- views for the UI ----------
data class Category(val slug: String, val name: String, val recommendedFare: Long)
data class MapProvider(val id: String, val lat: Double, val lng: Double)
data class OfferView(
    val id: String, val providerName: String, val amount: Long, val etaMin: Int, val distanceKm: Double,
    val rating: Double, val ratingCount: Int, val jobs: Int, val secondsLeft: Int, val isCounter: Boolean,
)
data class JobView(
    val id: String, val status: String, val category: String, val description: String, val fare: Long,
    val isCustomer: Boolean, val otherName: String, val otherRating: Double, val otherRatingCount: Int,
    val lat: Double, val lng: Double, val providerLat: Double, val providerLng: Double,
    val etaMin: Int?, val distanceKm: Double, val awaitingConfirmation: Boolean, val needsRating: Boolean,
    val photo: String?, val cancelReason: String?, val offers: List<OfferView>, val searchSeconds: Int,
    val net: Long,
)
data class RequestView(
    val id: String, val customerName: String, val customerRating: Double, val category: String,
    val description: String, val fare: Long, val distanceKm: Double, val postedSecondsAgo: Int,
    val myOffer: Long?, val myOfferStatus: String?, val myOfferSecondsLeft: Int,
)
data class TxnView(val title: String, val amount: Long, val incoming: Boolean, val status: String, val ref: String?, val createdAt: Long)
data class Me(val user: DbUser, val wallet: DbWallet, val provider: DbProvider?)

/**
 * On-device backend. Same rules as the server spec: escrow hold on accept, 15% commission on release,
 * Rs. 200 minimum to go online, one-time TIDs, photo proof before completion, both-party confirmation.
 * Each call works on a copy of the data under a lock and commits only if it succeeds (all-or-nothing).
 * The account not signed in acts on its own, so one phone can run the whole flow.
 */
class Backend(
    private val file: File,
    private val user: () -> String?,
    private val mode: () -> ActiveMode,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lock = Mutex()
    private var db: Db = runCatching { JSON.decodeFromString(Db.serializer(), file.readText()) }.getOrElse { seed(clock()) }
    private val me get() = user() ?: throw AppError("Sign in first")
    private val now get() = clock()

    private suspend fun <T> tx(block: (Db) -> Pair<Db, T>): T = lock.withLock {
        val (next, result) = block(tick(db, now))
        db = next
        file.writeText(JSON.encodeToString(Db.serializer(), next))
        result
    }

    // ---------- pure helpers ----------
    private fun Db.wallet(id: String) = wallets.first { it.userId == id }
    private fun Db.user(id: String) = users.first { it.id == id }
    private fun Db.provider(id: String) = providers.first { it.userId == id }
    private fun Db.booking(id: String) = bookings.firstOrNull { it.id == id } ?: throw AppError("Booking not found")
    private fun Db.put(w: DbWallet) = copy(wallets = wallets.map { if (it.userId == w.userId) w else it })
    private fun Db.put(p: DbProvider) = copy(providers = providers.map { if (it.userId == p.userId) p else it })
    private fun Db.put(b: DbBooking) = copy(bookings = bookings.map { if (it.id == b.id) b else it })
    private fun Db.txn(b: String?, from: String?, to: String?, amount: Long, type: String, status: String = "completed", ref: String? = null) =
        copy(txns = txns + DbTxn(UUID.randomUUID().toString(), b, from, to, amount, type, status, ref, now))
    private fun Db.txnStatus(bookingId: String, type: String, status: String) =
        copy(txns = txns.map { if (it.bookingId == bookingId && it.type == type) it.copy(status = status) else it })
    private fun Db.customerRating(id: String): Pair<Double, Int> {
        val r = ratings.filter { it.reviewee == id }
        return if (r.isEmpty()) 4.9 to 31 else ((4.9 * 31 + r.sumOf { it.score }) / (31 + r.size) * 10).roundToInt() / 10.0 to 31 + r.size
    }

    private fun Db.acceptOffer(b: DbBooking, offerId: String): Db {
        if (b.status != "pending") throw AppError("Request is no longer active")
        val o = b.offers.firstOrNull { it.id == offerId && it.status == "open" && it.expiresAt > now }
            ?: throw AppError("Offer expired")
        val w = wallet(b.customerId)
        if (w.balance < o.amount) throw AppError("Not enough in wallet. Top up ${rs(o.amount - w.balance)}")
        val p = provider(o.providerId)
        if (p.isOnJob) throw AppError("Provider just took another job")
        return put(w.copy(balance = w.balance - o.amount, escrow = w.escrow + o.amount))
            .txn(b.id, b.customerId, null, o.amount, "escrow_hold", "held")
            .put(b.copy(status = "accepted", providerId = o.providerId, fare = o.amount, stepAt = now,
                offers = b.offers.map { if (it.id == offerId) it.copy(status = "accepted") else if (it.status == "open") it.copy(status = "declined") else it }))
            .put(p.copy(isOnJob = true))
    }

    private fun Db.step(b: DbBooking, next: String): Db {
        val from = mapOf("en_route" to "accepted", "arrived" to "en_route", "in_progress" to "arrived")[next]
            ?: throw AppError("Unknown status")
        if (b.status != from) throw AppError("Not available right now")
        val p = provider(b.providerId!!)
        return put(b.copy(status = next, stepAt = now, enRouteAt = if (next == "en_route") now else b.enRouteAt,
            startLat = b.startLat ?: p.lat, startLng = b.startLng ?: p.lng))
    }

    private fun Db.release(b: DbBooking): Db {
        if (b.status != "in_progress" || b.photo == null) throw AppError("Provider hasn't finished yet")
        val fee = commission(b.fare); val net = b.fare - fee
        val pid = b.providerId!!
        val cw = wallet(b.customerId); val pw = wallet(pid); val p = provider(pid)
        return put(cw.copy(escrow = cw.escrow - b.fare))
            .put(pw.copy(balance = pw.balance + net, earned = pw.earned + net))
            .txnStatus(b.id, "escrow_hold", "released")
            .txn(b.id, b.customerId, pid, net, "release")
            .txn(b.id, b.customerId, null, fee, "commission")
            .put(b.copy(status = "completed", completedAt = now, stepAt = now))
            .put(p.copy(isOnJob = false, totalJobs = p.totalJobs + 1))
    }

    private fun Db.cancel(b: DbBooking, reason: String): Db {
        if (b.status !in listOf("pending", "accepted", "en_route")) throw AppError("Can't cancel after arrival")
        var d = this
        if (b.status != "pending") {
            val w = wallet(b.customerId)
            d = put(w.copy(balance = w.balance + b.fare, escrow = w.escrow - b.fare))
                .txnStatus(b.id, "escrow_hold", "refunded")
                .txn(b.id, null, b.customerId, b.fare, "refund")
                .put(provider(b.providerId!!).copy(isOnJob = false))
        }
        return d.put(b.copy(status = "cancelled", cancelReason = reason, stepAt = now,
            offers = b.offers.map { if (it.status == "open") it.copy(status = "declined") else it }))
    }

    private fun Db.rateInternal(b: DbBooking, reviewer: String, score: Int, tags: List<String>): Db {
        if (ratings.any { it.bookingId == b.id && it.reviewer == reviewer }) return this
        val reviewee = if (reviewer == b.customerId) b.providerId!! else b.customerId
        val d = copy(ratings = ratings + DbRating(b.id, reviewer, reviewee, score, tags))
        val p = d.providers.firstOrNull { it.userId == reviewee } ?: return d
        val avg = (p.rating * p.ratingCount + score) / (p.ratingCount + 1)
        return d.put(p.copy(rating = (avg * 10).roundToInt() / 10.0, ratingCount = p.ratingCount + 1))
    }

    // ---------- the other account acts on its own ----------
    private fun tick(d0: Db, t: Long): Db {
        var d = d0
        val human = user() ?: return d
        // Requests a simulated customer left open while you used the provider account.
        d.bookings.filter { it.byBot && it.status == "pending" && it.customerId == human }.forEach {
            d = d.put(it.copy(status = "cancelled", cancelReason = "expired", stepAt = t))
        }
        for (id in d.bookings.filter { it.status in ACTIVE }.map { it.id }) {
            var b = d.booking(id)
            // expire offers and requests
            if (b.offers.any { it.status == "open" && it.expiresAt <= t }) {
                b = b.copy(offers = b.offers.map { if (it.status == "open" && it.expiresAt <= t) it.copy(status = "expired") else it })
                d = d.put(b)
            }
            if (b.status == "pending" && b.expiresAt <= t) {
                d = d.put(b.copy(status = "cancelled", cancelReason = "no_offers", stepAt = t)); continue
            }
            val botCustomer = b.customerId != human
            val botProvider = b.providerId?.let { it != human } ?: true
            val age = t - b.stepAt
            d = runCatching {
                when {
                    b.status == "pending" && !botCustomer -> botMakesOffer(d, b, t)
                    b.status == "pending" && botCustomer -> {
                        val o = b.offers.firstOrNull { it.status == "open" && t - it.createdAt > 3_000 }
                        when {
                            o == null -> d
                            o.amount <= b.fare + 200 -> d.ensureFunds(b.customerId, o.amount).acceptOffer(b, o.id)
                            else -> d.put(b.copy(offers = b.offers.map { if (it.id == o.id) it.copy(status = "declined") else it }))
                        }
                    }
                    botProvider && b.status == "accepted" && age > 3_000 -> d.step(b, "en_route")
                    botProvider && b.status == "en_route" && age > TRAVEL_MS -> d.step(b, "arrived")
                    botProvider && b.status == "arrived" && age > 4_000 -> d.step(b, "in_progress")
                    botProvider && b.status == "in_progress" && b.photo == null && age > 12_000 -> d.put(b.copy(photo = "provider", stepAt = t))
                    botCustomer && b.status == "in_progress" && b.photo != null && age > 4_000 ->
                        d.release(b).let { it.rateInternal(it.booking(b.id), b.customerId, 5, listOf("On time", "Polite")) }
                    else -> d
                }
            }.getOrDefault(d)
        }
        // Signed in as provider, online and free → the customer posts a new request now and then.
        val hp = d.providers.firstOrNull { it.userId == human }
        if (hp != null && mode() == ActiveMode.PROVIDER && hp.isOnline && !hp.isOnJob &&
            d.bookings.none { it.status in ACTIVE && (it.providerId == human || it.byBot) } && t - d.lastBotRequestAt > 10_000) {
            val cust = d.users.first { it.role == "customer" }
            val cat = CATEGORIES.filter { it.slug in hp.categories }.random()
            val fare = cat.recommendedFare + listOf(-100L, 0L, 100L, 200L).random()
            d = d.ensureFunds(cust.id, fare + 200).copy(lastBotRequestAt = t, bookings = d.bookings + DbBooking(
                UUID.randomUUID().toString(), cust.id, null, cat.slug, ISSUES.getValue(cat.slug).random(), "pending", fare,
                lat = HOME_LAT, lng = HOME_LNG, createdAt = t, expiresAt = t + 60_000, stepAt = t, byBot = true))
        }
        return d
    }

    private fun botMakesOffer(d: Db, b: DbBooking, t: Long): Db {
        val p = d.providers.firstOrNull { it.userId != user() && it.isOnline && !it.isOnJob && b.category in it.categories &&
            d.wallet(it.userId).balance >= MIN_BALANCE } ?: return d
        val mine = b.offers.filter { it.providerId == p.userId }
        if (mine.any { it.status == "open" } || mine.size >= 3) return d
        val since = t - max(b.stepAt, mine.maxOfOrNull { it.createdAt } ?: 0)
        if (since < if (mine.isEmpty()) 4_000 else 8_000) return d
        val rec = CATEGORIES.first { it.slug == b.category }.recommendedFare
        val amount = if (b.fare >= rec || mine.isNotEmpty()) b.fare else ((b.fare + 100) / 50) * 50
        return d.put(b.copy(offers = b.offers + DbOffer(UUID.randomUUID().toString(), p.userId, amount, t, t + OFFER_MS)))
    }

    private fun Db.ensureFunds(userId: String, need: Long): Db {
        val w = wallet(userId)
        return if (w.balance >= need) this else put(w.copy(balance = w.balance + 3000))
            .txn(null, null, userId, 3000, "wallet_topup", "approved", "AUTO-" + (now / 1000))
    }

    // ---------- views ----------
    private fun Db.offerView(b: DbBooking, o: DbOffer): OfferView {
        val p = provider(o.providerId)
        val km = km(p.lat, p.lng, b.lat, b.lng)
        return OfferView(o.id, user(o.providerId).name, o.amount, max(1, ceil(km * 3).toInt()), round1(km),
            p.rating, p.ratingCount, p.totalJobs, max(0, ((o.expiresAt - now) / 1000).toInt()), o.amount > b.fare)
    }

    private fun Db.jobView(b: DbBooking, meId: String): JobView {
        val isCustomer = b.customerId == meId
        val pid = b.providerId
        val p = pid?.let { provider(it) }
        var pLat = p?.lat ?: b.lat; var pLng = p?.lng ?: b.lng; var eta: Int? = null
        if (b.status == "en_route" && b.startLat != null && b.enRouteAt != null) {
            val f = min(1.0, (now - b.enRouteAt).toDouble() / TRAVEL_MS)
            pLat = b.startLat + (b.lat - b.startLat) * f; pLng = b.startLng!! + (b.lng - b.startLng) * f
            eta = max(1, ceil((1 - f) * TRAVEL_MS / 60_000.0).toInt())
        } else if (b.status == "accepted" && p != null) eta = max(1, ceil(km(p.lat, p.lng, b.lat, b.lng) * 3).toInt())
        if (b.status == "arrived" || b.status == "in_progress") { pLat = b.lat; pLng = b.lng }
        val otherName = if (isCustomer) pid?.let { user(it).name } ?: "" else user(b.customerId).name
        val (oRating, oCount) = if (isCustomer) (p?.rating ?: 0.0) to (p?.ratingCount ?: 0) else customerRating(b.customerId)
        return JobView(
            b.id, b.status, b.category, b.description, b.fare, isCustomer, otherName, oRating, oCount,
            b.lat, b.lng, pLat, pLng, eta, round1(km(pLat, pLng, b.lat, b.lng)),
            b.status == "in_progress" && b.photo != null,
            b.status == "completed" && ratings.none { it.bookingId == b.id && it.reviewer == meId },
            b.photo, b.cancelReason,
            b.offers.filter { it.status == "open" }.map { offerView(b, it) },
            ((now - b.createdAt) / 1000).toInt(), b.fare - commission(b.fare),
        )
    }

    // ---------- API used by the UI ----------
    fun accounts(): List<DbUser> = db.users

    suspend fun me(): Me = tx { d ->
        d to Me(d.user(me), d.wallet(me), d.providers.firstOrNull { it.userId == me })
    }

    suspend fun mapProviders(): List<MapProvider> = tx { d ->
        d to d.providers.filter { it.userId != me && it.isOnline && !it.isOnJob && d.wallet(it.userId).balance >= MIN_BALANCE }
            .map { MapProvider(it.userId, it.lat, it.lng) }
    }

    suspend fun activeJob(): JobView? = tx { d ->
        val prov = mode() == ActiveMode.PROVIDER
        val b = d.bookings.sortedByDescending { it.createdAt }.firstOrNull {
            (if (prov) it.providerId == me else it.customerId == me) && (it.status in ACTIVE ||
                (it.status == "completed" && now - (it.completedAt ?: 0) < 86_400_000 && d.ratings.none { r -> r.bookingId == it.id && r.reviewer == me }) ||
                (!prov && !it.byBot && it.status == "cancelled" && now - it.stepAt < 6_000))
        }
        d to b?.let { d.jobView(it, me) }
    }

    suspend fun createRequest(category: String, description: String, fare: Long): JobView = tx { d ->
        if (mode() != ActiveMode.CUSTOMER) throw AppError("Switch to customer mode")
        if (fare !in MIN_FARE..MAX_FARE) throw AppError("Fare must be ${rs(MIN_FARE)}–${rs(MAX_FARE)}")
        if (d.bookings.any { it.customerId == me && it.status in ACTIVE }) throw AppError("You already have an active request")
        if (d.wallet(me).balance < fare) throw AppError("Not enough in wallet. Top up first")
        val b = DbBooking(UUID.randomUUID().toString(), me, null, category, description.trim().take(200), "pending", fare,
            lat = HOME_LAT, lng = HOME_LNG, createdAt = now, expiresAt = now + SEARCH_MS, stepAt = now)
        d.copy(bookings = d.bookings + b).let { it to it.jobView(b, me) }
    }

    suspend fun raiseFare(id: String, newFare: Long) = tx { d ->
        val b = d.booking(id)
        if (b.customerId != me || b.status != "pending") throw AppError("Request is no longer active")
        if (newFare <= b.fare || newFare > MAX_FARE) throw AppError("Maximum fare reached")
        if (d.wallet(me).balance < newFare) throw AppError("Not enough in wallet. Top up first")
        d.put(b.copy(fare = newFare, stepAt = now, expiresAt = now + SEARCH_MS)) to Unit
    }

    suspend fun acceptOffer(id: String, offerId: String) = tx { d ->
        val b = d.booking(id)
        if (b.customerId != me) throw AppError("Not your request")
        d.acceptOffer(b, offerId) to Unit
    }

    suspend fun declineOffer(id: String, offerId: String) = tx { d ->
        val b = d.booking(id)
        if (b.customerId != me) throw AppError("Not your request")
        d.put(b.copy(offers = b.offers.map { if (it.id == offerId && it.status == "open") it.copy(status = "declined") else it })) to Unit
    }

    suspend fun cancel(id: String) = tx { d ->
        val b = d.booking(id)
        if (b.customerId != me && b.providerId != me) throw AppError("Not your booking")
        d.cancel(b, if (b.customerId == me) "customer_cancelled" else "provider_cancelled") to Unit
    }

    // provider
    suspend fun setOnline(online: Boolean) = tx { d ->
        if (mode() != ActiveMode.PROVIDER) throw AppError("Switch to provider mode")
        val p = d.provider(me)
        if (online && !p.verified) throw AppError("Verification pending")
        if (online && d.wallet(me).balance < MIN_BALANCE) throw AppError("Top up to get requests — ${rs(MIN_BALANCE)} minimum")
        d.put(p.copy(isOnline = online)).copy(lastBotRequestAt = now - 7_000) to Unit
    }

    suspend fun requests(): List<RequestView> = tx { d ->
        val p = d.providers.firstOrNull { it.userId == me }
        if (p == null || !p.isOnline || p.isOnJob) return@tx d to emptyList()
        d to d.bookings.filter { it.status == "pending" && it.customerId != me && it.category in p.categories &&
            km(p.lat, p.lng, it.lat, it.lng) <= p.radiusKm }
            .sortedByDescending { it.createdAt }
            .map { b ->
                val mine = b.offers.lastOrNull { it.providerId == me }
                RequestView(b.id, d.user(b.customerId).name, d.customerRating(b.customerId).first, b.category, b.description,
                    b.fare, round1(km(p.lat, p.lng, b.lat, b.lng)), ((now - b.createdAt) / 1000).toInt(),
                    mine?.amount, mine?.status, mine?.let { max(0, ((it.expiresAt - now) / 1000).toInt()) } ?: 0)
            }
    }

    suspend fun makeOffer(id: String, amount: Long) = tx { d ->
        if (mode() != ActiveMode.PROVIDER) throw AppError("Switch to provider mode")
        val p = d.provider(me)
        if (!p.isOnline || p.isOnJob) throw AppError("Go online to send offers")
        if (d.wallet(me).balance < MIN_BALANCE) throw AppError("Top up to get requests")
        val b = d.booking(id)
        if (b.status != "pending") throw AppError("Request is no longer active")
        if (amount < b.fare || amount > b.fare * 3) throw AppError("Offer must be ${rs(b.fare)}–${rs(b.fare * 3)}")
        if (b.offers.any { it.providerId == me && it.status == "open" }) throw AppError("Offer already sent")
        d.put(b.copy(offers = b.offers + DbOffer(UUID.randomUUID().toString(), me, amount, now, now + OFFER_MS))) to Unit
    }

    suspend fun advance(id: String, next: String) = tx { d ->
        val b = d.booking(id)
        if (b.providerId != me) throw AppError("Not your job")
        d.step(b, next) to Unit
    }

    suspend fun complete(id: String, photoPath: String) = tx { d ->
        val b = d.booking(id)
        if (b.providerId != me) throw AppError("Not your job")
        if (b.status != "in_progress") throw AppError("Start the job first")
        if (photoPath.isBlank()) throw AppError("Photo proof is required")
        d.put(b.copy(photo = photoPath, stepAt = now)) to Unit
    }

    suspend fun confirm(id: String) = tx { d ->
        val b = d.booking(id)
        if (b.customerId != me) throw AppError("Only the customer can confirm")
        val paid = d.release(b)
        // A simulated provider rates the customer on its own, so no rating prompt waits on that account.
        (if (b.providerId != me) paid.rateInternal(paid.booking(id), b.providerId!!, 5, listOf("Friendly")) else paid) to Unit
    }

    suspend fun dispute(id: String, reason: String) = tx { d ->
        val b = d.booking(id)
        if (b.customerId != me) throw AppError("Only the customer can report a problem")
        if (b.status !in listOf("arrived", "in_progress")) throw AppError("Not available right now")
        d.put(b.copy(status = "disputed", cancelReason = reason, stepAt = now)) to Unit
    }

    suspend fun rate(id: String, score: Int, tags: List<String>) = tx { d ->
        val b = d.booking(id)
        if (b.status != "completed") throw AppError("Rate after the job is done")
        if (score !in 1..5) throw AppError("Choose 1–5 stars")
        d.rateInternal(b, me, score, tags) to Unit
    }

    suspend fun skipRating(id: String) = rate(id, 5, emptyList())

    // wallet
    suspend fun wallet(): Pair<DbWallet, List<TxnView>> = tx { d ->
        // Each person sees only their own money movements: the customer sees the held payment (and refunds),
        // the provider sees net earnings. The service fee is internal to the platform.
        d to (d.wallet(me) to d.txns.filter {
            (it.to == me && it.type != "commission") || (it.from == me && it.type in listOf("escrow_hold", "withdrawal", "cash_job_fee"))
        }.sortedByDescending { it.createdAt }.take(40).map {
            val title = when {
                it.type == "escrow_hold" -> when (it.status) { "held" -> "Payment on hold"; "refunded" -> "Payment (refunded)"; else -> "Paid for job" }
                else -> TXN_TITLES[it.type] ?: it.type
            }
            TxnView(title, it.amount, it.to == me, it.status, it.ref, it.createdAt)
        })
    }

    suspend fun topup(amount: Long, tid: String) = tx { d ->
        if (amount !in 100..50_000) throw AppError("Amount must be Rs. 100–50,000")
        val ref = tid.trim().uppercase()
        if (!Regex("^[A-Z0-9-]{6,20}$").matches(ref)) throw AppError("Enter the 6–20 character TID")
        if (d.txns.any { it.ref == ref }) throw AppError("This TID was already used")
        val w = d.wallet(me)
        d.put(w.copy(balance = w.balance + amount)).txn(null, null, me, amount, "wallet_topup", "approved", ref) to Unit
    }

    suspend fun withdraw(amount: Long) = tx { d ->
        val w = d.wallet(me)
        if (amount < 500) throw AppError("Minimum withdrawal is Rs. 500")
        if (amount > w.balance) throw AppError("Not enough balance")
        d.put(w.copy(balance = w.balance - amount)).txn(null, me, null, amount, "withdrawal", "pending") to Unit
    }

    suspend fun history(): List<JobView> = tx { d ->
        d to d.bookings.filter { (it.customerId == me && !(it.byBot && it.status == "cancelled")) || it.providerId == me }
            .sortedByDescending { it.createdAt }.take(30).map { d.jobView(it, me) }
    }

    suspend fun sos() = tx { d -> d.copy(sosCount = d.sosCount + 1) to Unit }

    suspend fun reset() = lock.withLock { db = seed(now); file.delete() }

    companion object {
        val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val ACTIVE = listOf("pending", "accepted", "en_route", "arrived", "in_progress", "disputed")
        const val SEARCH_MS = 180_000L
        const val OFFER_MS = 30_000L
        const val TRAVEL_MS = 60_000L
        const val MIN_BALANCE = 200L
        const val MIN_FARE = 300L
        const val MAX_FARE = 20_000L
        const val HOME_LAT = 31.5204
        const val HOME_LNG = 74.3587
        const val HOME_ADDRESS = "Gulberg III, Lahore"
        const val CUSTOMER_ID = "u-customer"
        const val PROVIDER_ID = "u-provider"
        val CATEGORIES = listOf(Category("plumber", "Plumber", 800), Category("electrician", "Electrician", 700))
        val ISSUES = mapOf(
            "electrician" to listOf("Fan not working", "UPS wiring", "Socket sparking", "DB board tripping"),
            "plumber" to listOf("Geyser leaking", "Kitchen tap broken", "Water motor not starting"),
        ).withDefault { listOf("Repair needed") }
        val TXN_TITLES = mapOf(
            "wallet_topup" to "Top-up", "escrow_hold" to "Payment held", "release" to "Job earnings",
            "commission" to "Service fee", "refund" to "Refund", "withdrawal" to "Withdrawal",
        )

        fun commission(gross: Long) = (gross * 15 + 50) / 100
        fun round1(x: Double) = (x * 10).roundToInt() / 10.0
        fun km(a: Double, b: Double, c: Double, d: Double): Double {
            val dLat = Math.toRadians(c - a); val dLng = Math.toRadians(d - b)
            val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a)) * cos(Math.toRadians(c)) * sin(dLng / 2).pow(2)
            return 2 * 6371.0 * asin(sqrt(h))
        }

        fun seed(t: Long) = Db(
            users = listOf(
                DbUser(CUSTOMER_ID, "Hamza Ali", "+923001110001", "customer"),
                DbUser(PROVIDER_ID, "Ahmad Khan", "+923001110012", "provider"),
            ),
            providers = listOf(DbProvider(PROVIDER_ID, listOf("plumber", "electrician"), isOnline = true, tier = "silver",
                rating = 4.8, ratingCount = 127, totalJobs = 127, lat = 31.5236, lng = 74.3562, radiusKm = 8)),
            wallets = listOf(DbWallet(CUSTOMER_ID, 5000), DbWallet(PROVIDER_ID, 1200, earned = 21_000)),
            txns = listOf(
                DbTxn("seed-1", null, null, CUSTOMER_ID, 5000, "wallet_topup", "approved", "JC-58210443", t - 259_200_000),
                DbTxn("seed-2", null, null, PROVIDER_ID, 1200, "wallet_topup", "approved", "EP-77120931", t - 172_800_000),
            ),
        )
    }
}

fun rs(amount: Long) = "Rs " + "%,d".format(amount)
