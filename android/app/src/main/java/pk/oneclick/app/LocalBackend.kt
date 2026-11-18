package pk.oneclick.app

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.math.*

class AppError(message: String) : Exception(message)

@Serializable data class DbUser(val id: String, val name: String, val phone: String, val role: String, val bot: Boolean = false)
@Serializable data class DbProvider(
    val userId: String, val categories: List<String>, val isOnline: Boolean, val isOnJob: Boolean = false,
    val tier: String, val rating: Double, val totalJobs: Int, val lat: Double, val lng: Double,
    val radiusKm: Int, val verified: Boolean = true,
)
@Serializable data class DbWallet(val userId: String, val balance: Long, val escrow: Long = 0, val earned: Long = 0)
@Serializable data class DbBooking(
    val id: String, val customerId: String, val providerId: String, val category: String, val description: String,
    val status: String, val fare: Long, val cLat: Double, val cLng: Double,
    val startLat: Double? = null, val startLng: Double? = null, val photo: String? = null,
    val cancelReason: String? = null, val createdAt: Long, val expiresAt: Long,
    val enRouteAt: Long? = null, val completedAt: Long? = null, val stepAt: Long,
)
@Serializable data class DbTxn(
    val id: String, val bookingId: String?, val from: String?, val to: String?, val amount: Long,
    val type: String, val status: String, val ref: String? = null, val createdAt: Long,
)
@Serializable data class DbRating(val bookingId: String, val reviewer: String, val reviewee: String, val score: Int)
@Serializable data class Db(
    val users: List<DbUser>, val providers: List<DbProvider>, val wallets: List<DbWallet>,
    val bookings: List<DbBooking> = emptyList(), val txns: List<DbTxn> = emptyList(),
    val ratings: List<DbRating> = emptyList(), val sosCount: Int = 0, val lastBotRequestAt: Long = 0,
)

/**
 * On-device backend: same rules as supabase/functions/api (escrow, 15% commission, Rs. 200 minimum,
 * 30 s request timeout, one-time TIDs). Every other party is simulated so one phone can run every flow.
 * Each call mutates a copy of the DB under a lock and commits only on success (all-or-nothing).
 */
class LocalBackend(private val file: File, private val user: () -> String?, private val mode: () -> ActiveMode,
    private val clock: () -> Long = System::currentTimeMillis,
) : ApiService {
    private val lock = Mutex()
    private var db: Db = runCatching { json.decodeFromString<Db>(file.readText()) }.getOrElse { seed() }

    private val me get() = user() ?: throw AppError("Login required")
    private val now get() = clock()

    private suspend fun <T> tx(block: (Db) -> Pair<Db, T>): T = lock.withLock {
        val (next, result) = block(tick(db, now))
        db = next
        file.writeText(json.encodeToString(Db.serializer(), next))
        result
    }

    // ---------- money helpers (pure: return new Db) ----------
    private fun Db.wallet(id: String) = wallets.first { it.userId == id }
    private fun Db.putWallet(w: DbWallet) = copy(wallets = wallets.map { if (it.userId == w.userId) w else it })
    private fun Db.putProvider(p: DbProvider) = copy(providers = providers.map { if (it.userId == p.userId) p else it })
    private fun Db.putBooking(b: DbBooking) = copy(bookings = bookings.map { if (it.id == b.id) b else it })
    private fun Db.addTxn(b: String?, from: String?, to: String?, amount: Long, type: String, status: String = "completed", ref: String? = null) =
        copy(txns = txns + DbTxn(UUID.randomUUID().toString(), b, from, to, amount, type, status, ref, now))
    private fun Db.setTxnStatus(bookingId: String, type: String, status: String) =
        copy(txns = txns.map { if (it.bookingId == bookingId && it.type == type) it.copy(status = status) else it })
    private fun Db.provider(id: String) = providers.first { it.userId == id }
    private fun Db.booking(id: String) = bookings.firstOrNull { it.id == id } ?: throw AppError("Booking not found")

    private fun commissionOf(gross: Long) = (gross * 15 + 50) / 100

    private fun Db.accept(b: DbBooking): Db {
        if (b.status != "pending") throw AppError("Request expired or already handled")
        val w = wallet(b.customerId)
        if (w.balance < b.fare) return putBooking(b.copy(status = "cancelled", cancelReason = "insufficient_funds", stepAt = now))
        return putWallet(w.copy(balance = w.balance - b.fare, escrow = w.escrow + b.fare))
            .addTxn(b.id, b.customerId, null, b.fare, "escrow_hold", "held")
            .putBooking(b.copy(status = "accepted", stepAt = now))
            .putProvider(provider(b.providerId).copy(isOnJob = true))
    }

    private fun Db.confirm(b: DbBooking): Db {
        if (b.status != "in_progress" || b.photo == null) throw AppError("Provider has not completed the job yet")
        val fee = commissionOf(b.fare); val net = b.fare - fee
        val cw = wallet(b.customerId); val pw = wallet(b.providerId); val p = provider(b.providerId)
        return putWallet(cw.copy(escrow = cw.escrow - b.fare))
            .putWallet(pw.copy(balance = pw.balance + net, earned = pw.earned + net))
            .setTxnStatus(b.id, "escrow_hold", "released")
            .addTxn(b.id, b.customerId, b.providerId, net, "release")
            .addTxn(b.id, b.customerId, null, fee, "commission")
            .putBooking(b.copy(status = "completed", completedAt = now, stepAt = now))
            .putProvider(p.copy(isOnJob = false, totalJobs = p.totalJobs + 1))
    }

    private fun Db.step(b: DbBooking, next: String): Db {
        val from = mapOf("en_route" to "accepted", "arrived" to "en_route", "in_progress" to "arrived")[next]
            ?: throw AppError("Bad status")
        if (b.status != from) throw AppError("Not allowed in current status")
        val p = provider(b.providerId)
        return putBooking(b.copy(status = next, stepAt = now,
            enRouteAt = if (next == "en_route") now else b.enRouteAt,
            startLat = b.startLat ?: p.lat, startLng = b.startLng ?: p.lng))
    }

    // ---------- simulation of the other party ----------
    private fun tick(d0: Db, t: Long): Db {
        var d = d0
        val human = user()
        d.bookings.filter { it.status == "pending" && it.expiresAt < t }.forEach {
            d = d.putBooking(it.copy(status = "cancelled", cancelReason = "timeout", stepAt = t))
        }
        for (b0 in d.bookings.filter { it.status in ACTIVE }) {
            val b = d.booking(b0.id)
            val age = t - b.stepAt
            val botProvider = b.providerId != human
            val botCustomer = b.customerId != human
            d = runCatching {
                when {
                    botProvider && b.status == "pending" && age > 4_000 -> d.accept(b)
                    botProvider && b.status == "accepted" && age > 3_000 -> d.step(b, "en_route")
                    botProvider && b.status == "en_route" && age > TRAVEL_MS -> d.step(b, "arrived")
                    botProvider && b.status == "arrived" && age > 4_000 -> d.step(b, "in_progress")
                    botProvider && b.status == "in_progress" && b.photo == null && age > 15_000 -> d.putBooking(b.copy(photo = "bot", stepAt = t))
                    botCustomer && b.status == "in_progress" && b.photo != null && age > 5_000 -> d.confirm(b)
                    else -> d
                }
            }.getOrDefault(d)
        }
        // Human provider online & free → simulated customer sends a request every ~20 s.
        val hp = d.providers.firstOrNull { it.userId == human }
        if (hp != null && mode() == ActiveMode.PROVIDER && hp.isOnline && !hp.isOnJob &&
            d.bookings.none { it.providerId == human && it.status in ACTIVE } && t - d.lastBotRequestAt > 20_000) {
            val cust = d.users.filter { it.bot }.random()
            val cw = d.wallet(cust.id)
            if (cw.balance < 3000) d = d.putWallet(cw.copy(balance = cw.balance + 5000)).addTxn(null, null, cust.id, 5000, "wallet_topup", "approved", "BOT-" + t)
            val cat = hp.categories.random()
            d = d.copy(lastBotRequestAt = t, bookings = d.bookings + DbBooking(
                UUID.randomUUID().toString(), cust.id, hp.userId, cat, ISSUES.getValue(cat).random(), "pending",
                listOf(600L, 800L, 1000L, 1200L, 1500L).random(), hp.lat + 0.006, hp.lng - 0.004,
                createdAt = t, expiresAt = t + REQUEST_MS, stepAt = t))
        }
        return d
    }

    // ---------- views ----------
    private fun Db.view(b: DbBooking, meId: String): BookingDto {
        val other = users.first { it.id == if (b.customerId == meId) b.providerId else b.customerId }
        val p = provider(b.providerId)
        var pLat = p.lat; var pLng = p.lng; var eta: Int? = null
        if (b.status == "en_route" && b.startLat != null && b.enRouteAt != null) {
            val f = min(1.0, (now - b.enRouteAt).toDouble() / TRAVEL_MS)
            pLat = b.startLat + (b.cLat - b.startLat) * f; pLng = b.startLng!! + (b.cLng - b.startLng) * f
            eta = ceil((1 - f) * TRAVEL_MS / 60_000.0).toInt()
        }
        if (b.status == "arrived" || b.status == "in_progress") { pLat = b.cLat; pLng = b.cLng }
        return BookingDto(
            id = b.id, status = b.status, category = b.category, description = b.description, agreedFare = b.fare,
            mySide = if (b.customerId == meId) "customer" else "provider", otherName = other.name,
            otherRole = if (b.customerId == meId) "Provider" else "Customer",
            otherRating = providers.firstOrNull { it.userId == other.id }?.rating,
            customerLat = b.cLat, customerLng = b.cLng, providerLat = pLat, providerLng = pLng, etaMin = eta,
            awaitingConfirmation = b.status == "in_progress" && b.photo != null,
            needsRating = b.status == "completed" && ratings.none { it.bookingId == b.id && it.reviewer == meId },
            cancelReason = b.cancelReason, completionPhotoUrl = b.photo,
            expiresInS = if (b.status == "pending") max(0, ((b.expiresAt - now) / 1000).toInt()) else null,
            createdAt = Instant.ofEpochMilli(b.createdAt).toString(),
        )
    }

    // ---------- ApiService ----------
    override suspend fun demoUsers() = DemoUsersRes(db.users.filterNot { it.bot }.sortedBy { if (it.role == "customer") 0 else 1 }
        .map { u -> DemoUserDto(u.id, u.name, u.role, db.providers.firstOrNull { it.userId == u.id }?.categories) })

    override suspend fun login(b: LoginReq): TokenRes {
        db.users.firstOrNull { it.id == b.userId } ?: throw AppError("User not found")
        return TokenRes(b.userId)
    }

    override suspend fun me() = tx { d ->
        val u = d.users.first { it.id == me }; val w = d.wallet(me)
        d to MeRes(UserDto(u.id, u.name, u.phone, u.role), WalletDto(w.balance, w.escrow, w.earned),
            d.providers.firstOrNull { it.userId == me }?.let {
                ProviderInfoDto(it.categories, it.isOnline, it.isOnJob, it.tier, it.rating, it.totalJobs, it.verified)
            })
    }

    override suspend fun categories() = CategoriesRes(CATEGORIES)

    override suspend fun nearby(lat: Double, lng: Double, category: String?) = tx { d ->
        fun find(radius: Boolean) = d.providers.filter { p ->
            p.isOnline && p.verified && !p.isOnJob && d.wallet(p.userId).balance >= 200 && p.userId != me &&
                (category == null || category in p.categories) &&
                km(lat, lng, p.lat, p.lng) <= if (radius) p.radiusKm.toDouble() else 30.0
        }.map { p ->
            ProviderDto(p.userId, d.users.first { it.id == p.userId }.name, p.categories, p.rating, p.totalJobs,
                p.tier, p.verified, p.lat, p.lng, (km(lat, lng, p.lat, p.lng) * 10).roundToInt() / 10.0)
        }.sortedBy { it.distanceKm }
        val near = find(true)
        d to if (near.isNotEmpty()) NearbyRes(near, false) else NearbyRes(find(false), true)
    }

    override suspend fun setOnline(b: OnlineReq) = tx { d ->
        if (mode() != ActiveMode.PROVIDER) throw AppError("Switch to Provider mode")
        val p = d.provider(me)
        if (b.online && !p.verified) throw AppError("Account not verified yet")
        if (b.online && d.wallet(me).balance < 200) throw AppError("Top up: Rs. 200 minimum needed to go online")
        d.putProvider(p.copy(isOnline = b.online)).copy(lastBotRequestAt = now - 15_000) to OkRes()
    }

    override suspend fun wallet() = tx { d ->
        val w = d.wallet(me)
        d to WalletRes(WalletDto(w.balance, w.escrow, w.earned), d.txns.filter { it.from == me || it.to == me }
            .sortedByDescending { it.createdAt }.take(30)
            .map { TxnDto(it.id, it.type, it.amount, it.status, it.ref, Instant.ofEpochMilli(it.createdAt).toString(), it.to == me) })
    }

    override suspend fun topup(b: TopupReq) = tx { d ->
        if (b.amount !in 100..50_000) throw AppError("Amount must be Rs. 100 – 50,000")
        val tid = b.tid.trim().uppercase()
        if (!Regex("^[A-Z0-9-]{6,20}$").matches(tid)) throw AppError("Enter the 6-20 character TID")
        if (d.txns.any { it.ref == tid }) throw AppError("This TID was already used")
        val w = d.wallet(me)
        d.putWallet(w.copy(balance = w.balance + b.amount)).addTxn(null, null, me, b.amount, "wallet_topup", "approved", tid) to OkRes()
    }

    override suspend fun activeBooking() = tx { d ->
        val col: (DbBooking) -> String = if (mode() == ActiveMode.PROVIDER) { b -> b.providerId } else { b -> b.customerId }
        val b = d.bookings.filter { col(it) == me }.sortedByDescending { it.createdAt }.firstOrNull {
            it.status in ACTIVE ||
                (it.status == "completed" && now - (it.completedAt ?: 0) < 86_400_000 && d.ratings.none { r -> r.bookingId == it.id && r.reviewer == me }) ||
                (it.status == "cancelled" && now - it.stepAt < 8_000)
        }
        d to BookingRes(b?.let { d.view(it, me) })
    }

    override suspend fun bookings() = tx { d ->
        d to BookingsRes(d.bookings.filter { it.customerId == me || it.providerId == me }
            .sortedByDescending { it.createdAt }.take(30).map { d.view(it, me) })
    }

    override suspend fun createBooking(b: CreateBookingReq) = tx { d ->
        if (mode() != ActiveMode.CUSTOMER) throw AppError("Switch to Customer mode")
        if (b.offerFare !in 100..100_000) throw AppError("Offer must be Rs. 100 – 100,000")
        if (d.bookings.any { it.customerId == me && it.status in ACTIVE }) throw AppError("You already have an active booking")
        val p = d.providers.firstOrNull { it.userId == b.providerId }
        if (p == null || !p.isOnline || p.isOnJob) throw AppError("Provider is not available")
        if (d.wallet(me).balance < b.offerFare) throw AppError("Wallet balance too low — top up first")
        val nb = DbBooking(UUID.randomUUID().toString(), me, p.userId, b.category, b.description.take(300), "pending",
            b.offerFare, b.lat, b.lng, createdAt = now, expiresAt = now + REQUEST_MS, stepAt = now)
        d.copy(bookings = d.bookings + nb).let { it to BookingRes(it.view(nb, me)) }
    }

    private suspend fun act(id: String, block: Db.(DbBooking) -> Db) = tx { d ->
        val b = d.booking(id)
        if (b.customerId != me && b.providerId != me) throw AppError("Not your booking")
        d.block(b).let { it to BookingRes(it.view(it.booking(id), me)) }
    }
    private fun requireProvider(b: DbBooking) {
        if (mode() != ActiveMode.PROVIDER || b.providerId != me) throw AppError("Switch to Provider mode")
    }

    override suspend fun accept(id: String, b: ConfirmReq) = act(id) { requireProvider(it); accept(it) }
    override suspend fun reject(id: String, b: ConfirmReq) = act(id) {
        requireProvider(it)
        if (it.status != "pending") throw AppError("Request already handled")
        putBooking(it.copy(status = "cancelled", cancelReason = "rejected", stepAt = now))
    }
    override suspend fun status(id: String, b: StatusReq) = act(id) { requireProvider(it); step(it, b.status) }
    override suspend fun complete(id: String, b: CompleteReq) = act(id) {
        requireProvider(it)
        if (it.status != "in_progress") throw AppError("Start the job first")
        if (b.photoUrl.isBlank()) throw AppError("Photo proof is required")
        putBooking(it.copy(photo = b.photoUrl, stepAt = now))
    }
    override suspend fun confirm(id: String, b: ConfirmReq) = act(id) {
        if (it.customerId != me) throw AppError("Only the customer can confirm")
        confirm(it)
    }
    override suspend fun cancel(id: String, b: ConfirmReq) = act(id) {
        if (it.status !in listOf("pending", "accepted", "en_route")) throw AppError("Cannot cancel after provider arrived")
        var d = this
        if (it.status != "pending") {
            val w = wallet(it.customerId)
            d = putWallet(w.copy(balance = w.balance + it.fare, escrow = w.escrow - it.fare))
                .setTxnStatus(it.id, "escrow_hold", "refunded")
                .addTxn(it.id, null, it.customerId, it.fare, "refund")
                .putProvider(provider(it.providerId).copy(isOnJob = false))
        }
        d.putBooking(it.copy(status = "cancelled", cancelReason = if (it.customerId == me) "customer_cancelled" else "provider_cancelled", stepAt = now))
    }
    override suspend fun dispute(id: String, b: DisputeReq) = act(id) {
        if (it.customerId != me) throw AppError("Only the customer can dispute")
        if (it.status !in listOf("arrived", "in_progress")) throw AppError("Not allowed in current status")
        putBooking(it.copy(status = "disputed", cancelReason = b.reason, stepAt = now))
    }
    override suspend fun rate(id: String, b: RateReq) = act(id) {
        if (it.status != "completed") throw AppError("Rate after completion")
        if (b.score !in 1..5) throw AppError("Score 1-5")
        val reviewee = if (it.providerId == me) it.customerId else it.providerId
        val d = copy(ratings = ratings + DbRating(it.id, me, reviewee, b.score))
        val mine = d.ratings.filter { r -> r.reviewee == reviewee }
        d.providers.firstOrNull { p -> p.userId == reviewee }?.let { p ->
            val total = p.rating * p.totalJobs + mine.last().score
            d.putProvider(p.copy(rating = (total / (p.totalJobs + 1) * 10).roundToInt() / 10.0))
        } ?: d
    }
    override suspend fun sos(b: SosReq) = tx { d -> d.copy(sosCount = d.sosCount + 1) to OkRes(true, "SOS sent. Admin and contacts notified.") }

    fun resetDemo() { db = seed(); file.delete() }

    companion object {
        val ACTIVE = listOf("pending", "accepted", "en_route", "arrived", "in_progress", "disputed")
        const val REQUEST_MS = 30_000L
        const val TRAVEL_MS = 60_000L
        val CATEGORIES = listOf(
            CategoryDto("plumber", "Plumber", "پلمبر", "#1565C0"), CategoryDto("electrician", "Electrician", "الیکٹریشن", "#F9A825"),
            CategoryDto("welder", "Welder", "ویلڈر", "#BF360C"), CategoryDto("mechanic", "Mechanic", "مکینک", "#37474F"),
            CategoryDto("carpenter", "Carpenter", "کارپینٹر", "#4E342E"), CategoryDto("painter", "Painter", "پینٹر", "#6A1B9A"),
        )
        val ISSUES = mapOf(
            "electrician" to listOf("Fan not working", "UPS wiring", "Short circuit in kitchen", "DB board tripping"),
            "plumber" to listOf("Geyser leaking", "Bathroom tap broken", "Water motor issue"),
        ).withDefault { listOf("Repair needed") }

        fun km(a: Double, b: Double, c: Double, d: Double): Double {
            val r = 6371.0; val dLat = Math.toRadians(c - a); val dLng = Math.toRadians(d - b)
            val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a)) * cos(Math.toRadians(c)) * sin(dLng / 2).pow(2)
            return 2 * r * asin(sqrt(h))
        }

        fun seed(): Db {
            val t = System.currentTimeMillis()
            fun id(n: Int) = "00000000-0000-0000-0000-%012d".format(n)
            val users = listOf(
                DbUser(id(1), "Hamza Ali", "+923001110001", "customer"), DbUser(id(2), "Ayesha Malik", "+923001110002", "customer"),
                DbUser(id(11), "Shahid Rana", "+923001110011", "both"), DbUser(id(12), "Ahmad Khan", "+923001110012", "provider"),
                DbUser(id(13), "Sara Iqbal", "+923001110013", "provider"), DbUser(id(14), "Bilal Hussain", "+923001110014", "provider"),
                DbUser(id(15), "Usman Tariq", "+923001110015", "provider"), DbUser(id(16), "Kashif Mehmood", "+923001110016", "provider"),
                DbUser(id(17), "Imran Butt", "+923001110017", "provider"), DbUser(id(18), "Faisal Javed", "+923001110018", "provider"),
                DbUser(id(91), "Zainab Akhtar", "+923001110091", "customer", bot = true),
                DbUser(id(92), "Omar Farooq", "+923001110092", "customer", bot = true),
            )
            val providers = listOf(
                DbProvider(id(11), listOf("electrician"), false, false, "silver", 4.5, 52, 31.5190, 74.3570, 6),
                DbProvider(id(12), listOf("plumber"), true, false, "silver", 4.8, 127, 31.5235, 74.3550, 6),
                DbProvider(id(13), listOf("electrician"), true, false, "gold", 4.9, 214, 31.5170, 74.3620, 8),
                DbProvider(id(14), listOf("welder"), true, false, "silver", 4.3, 64, 31.5260, 74.3640, 6),
                DbProvider(id(15), listOf("mechanic"), true, false, "bronze", 4.6, 38, 31.5150, 74.3500, 10),
                DbProvider(id(16), listOf("carpenter"), true, false, "bronze", 4.1, 22, 31.5290, 74.3520, 5),
                DbProvider(id(17), listOf("painter"), true, false, "silver", 4.7, 95, 31.5120, 74.3660, 6),
                DbProvider(id(18), listOf("plumber"), true, false, "silver", 4.4, 80, 31.4800, 74.4000, 3),
            )
            val bal = mapOf(1 to 5000L, 2 to 3000L, 11 to 450L, 12 to 1200L, 13 to 1500L, 14 to 800L, 15 to 600L, 16 to 300L, 17 to 900L, 18 to 700L, 91 to 5000L, 92 to 5000L)
            return Db(users, providers, bal.map { (k, v) -> DbWallet(id(k), v) }, txns = listOf(
                DbTxn("seed-1", null, null, id(1), 5000, "wallet_topup", "approved", "DEMO-TID-0001", t - 259_200_000),
                DbTxn("seed-2", null, null, id(2), 3000, "wallet_topup", "approved", "DEMO-TID-0002", t - 172_800_000),
            ))
        }
    }
}
