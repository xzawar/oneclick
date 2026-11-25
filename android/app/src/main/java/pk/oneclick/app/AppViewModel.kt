package pk.oneclick.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** Signed-in account, accounts remembered on this device, mode and first-run flag (EncryptedSharedPreferences). */
class Session(ctx: Context) {
    // JVM screenshot tests (Robolectric) have no Android Keystore; real devices always use encrypted prefs.
    private val prefs: SharedPreferences = if (isJvmTest) ctx.getSharedPreferences("session_test", 0)
    else EncryptedSharedPreferences.create(
        ctx, "session", MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    var userId: String?
        get() = prefs.getString("user", null)
        set(v) = prefs.edit().putString("user", v).apply()
    var mode: ActiveMode
        get() = ActiveMode.valueOf(prefs.getString("mode", ActiveMode.CUSTOMER.name)!!)
        set(v) = prefs.edit().putString("mode", v.name).apply()
    var accounts: List<String>
        get() = prefs.getString("accounts", "")!!.split(",").filter { it.isNotBlank() }
        set(v) = prefs.edit().putString("accounts", v.distinct().joinToString(",")).apply()
    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(v) = prefs.edit().putBoolean("onboarded", v).apply()
    var notifications: Boolean
        get() = prefs.getBoolean("notifications", true)
        set(v) = prefs.edit().putBoolean("notifications", v).apply()
    var dismissed: Set<String>
        get() = prefs.getStringSet("dismissed", emptySet())!!.toSet()
        set(v) = prefs.edit().putStringSet("dismissed", v).apply()
    var pushToken: String?
        get() = prefs.getString("push_token", null)
        set(v) = prefs.edit().putString("push_token", v).apply()

    /** Signed-in accounts on this phone, each with its own server session token. */
    var profiles: List<SignedIn>
        get() = runCatching { kotlinx.serialization.json.Json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(SignedIn.serializer()), prefs.getString("profiles", "[]")!!) }.getOrDefault(emptyList())
        set(v) = prefs.edit().putString("profiles", kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(SignedIn.serializer()), v)).apply()
    val token: String? get() = profiles.firstOrNull { it.user.id == userId }?.token
}

/** App clock. Tests move [skew] forward to fast-forward the other side. */
object DemoClock { @Volatile var skew = 0L; fun now() = System.currentTimeMillis() + skew }

enum class Stage { Onboarding, SignIn, App }
enum class Tab { Home, Bookings, Wallet, Profile }
enum class Page { Book, Track, Safety, Help, About, AddAccount }

data class UiState(
    val stage: Stage = Stage.Onboarding,
    val launching: Boolean = true,
    val tab: Tab = Tab.Home,
    val page: Page? = null,
    val mode: ActiveMode = ActiveMode.CUSTOMER,
    val me: Me? = null,
    val job: JobView? = null,
    val requests: List<RequestView> = emptyList(),
    val mapProviders: List<MapProvider> = emptyList(),
    val wallet: DbWallet? = null,
    val txns: List<TxnView> = emptyList(),
    val history: List<JobView> = emptyList(),
    val accounts: List<DbUser> = emptyList(),
    val notifications: Boolean = true,
    val local: Boolean = false,
    val dismissed: Set<String> = emptySet(),
    val bookCategory: String? = null,
    /** Provider allowed One Click to use the phone's location (so customers see them coming). */
    val locationOk: Boolean = true,
    val message: String? = null,
    val busy: Boolean = false,
)

class AppViewModel(private val app: Application) : AndroidViewModel(app) {
    private val session = Session(app)
    // Live server on phones; the on-device engine only for JVM tests (no network there).
    private val api: Api = if (isJvmTest) Backend(File(app.filesDir, "oneclick-db.json"), { session.userId }, { session.mode }, { DemoClock.now() })
        else RemoteApi(BuildConfig.API_BASE_URL, { session.token }, { session.mode })
    private val _s = MutableStateFlow(UiState(notifications = session.notifications, local = isJvmTest, dismissed = session.dismissed))
    val state: StateFlow<UiState> = _s
    private var poller: Job? = null
    private var lastJobKey: String? = null
    private var lastRequestIds: Set<String> = emptySet()
    private var ticks = 0

    init {
        val user = session.userId
        _s.update { it.copy(stage = when { !session.onboarded -> Stage.Onboarding; user == null -> Stage.SignIn; else -> Stage.App }) }
        if (user != null) start()
        registerPush()
    }

    fun launchDone() = _s.update { it.copy(launching = false) }
    fun finishOnboarding() { session.onboarded = true; _s.update { it.copy(stage = Stage.SignIn) } }

    // ---------- accounts ----------
    suspend fun lookup(phone: String): DbUser? = runCatching { api.lookup(phone) }.getOrNull()

    fun signIn(phone: String, name: String) = op {
        val signed = api.signIn(phone, name)
        val u = signed.user
        session.profiles = session.profiles.filter { it.user.id != u.id } + signed
        session.userId = u.id
        session.accounts = session.accounts + u.id
        session.mode = if (u.role == "provider") ActiveMode.PROVIDER else ActiveMode.CUSTOMER
        _s.update { it.copy(stage = Stage.App, page = null, tab = Tab.Home) }
        start()
        toast("Signed in as ${u.name}")
    }

    fun switchAccount(id: String) {
        val u = session.profiles.firstOrNull { it.user.id == id }?.user ?: return
        Tracking.sync(app, on = false, fast = false)
        session.userId = u.id
        session.mode = if (u.role == "provider") ActiveMode.PROVIDER else ActiveMode.CUSTOMER
        _s.update { it.copy(tab = Tab.Home, page = null, job = null, requests = emptyList(), me = null) }
        start()
        toast("Switched to ${u.name}")
    }

    fun signOut() {
        poller?.cancel()
        Tracking.sync(app, on = false, fast = false)
        val left = session.accounts - (session.userId ?: "")
        session.profiles = session.profiles.filter { it.user.id != session.userId }
        session.accounts = left
        session.userId = null
        _s.update { UiState(stage = Stage.SignIn, launching = false, notifications = session.notifications, local = isJvmTest) }
        left.firstOrNull()?.let { switchAccount(it); _s.update { s -> s.copy(stage = Stage.App) } }
    }

    fun setNotifications(on: Boolean) { session.notifications = on; _s.update { it.copy(notifications = on) } }

    private fun start() {
        _s.update { it.copy(mode = session.mode, accounts = session.profiles.map { p -> p.user }) }
        lastJobKey = null; lastRequestIds = emptySet()
        poller?.cancel()
        poller = viewModelScope.launch {
            session.pushToken?.let { runCatching { api.registerPush(it) } }
            while (isActive) {
                runCatching { refresh() }.onFailure { e -> if (e is AppError && e.message?.startsWith("Session expired") == true) signOut() }
                delay(if (isJvmTest) 1000 else 2000)
            }
        }
    }

    private suspend fun refresh() {
        if (session.userId == null) return
        val me = api.me()
        val job = api.activeJob()
        val prevJob = _s.value.job
        if (session.mode == ActiveMode.PROVIDER) {
            val req = api.requests()
            notifyRequests(req)
            _s.update { it.copy(me = me, job = job, requests = req) }
        } else _s.update { it.copy(me = me, job = job, mapProviders = api.mapProviders()) }
        notifyJob(job)
        // Share the provider's GPS while online or on a job (every 5 s on a job, 20 s otherwise).
        val p = me.provider
        Tracking.sync(app, on = session.mode == ActiveMode.PROVIDER && p != null && (p.isOnline || p.isOnJob), fast = job?.status in ON_JOB)
        _s.update { it.copy(locationOk = isJvmTest || hasLocationPermission(app)) }
        // A provider whose offer was just accepted goes straight to the job screen.
        if (session.mode == ActiveMode.PROVIDER && job != null && job.status == "accepted" && prevJob?.id != job.id && _s.value.page == null)
            _s.update { it.copy(page = Page.Track) }
        val historyDue = isJvmTest || ticks++ % 5 == 0 || prevJob?.status != job?.status
        when (_s.value.tab) {
            Tab.Wallet -> api.wallet().let { (w, t) -> _s.update { it.copy(wallet = w, txns = t) } }
            Tab.Bookings -> api.history().let { h -> _s.update { it.copy(history = h) } }
            Tab.Home -> if (historyDue) api.history().let { h -> _s.update { it.copy(history = h) } }
            else -> Unit
        }
    }

    // ---------- notifications: only when the app is in the background ----------
    private fun inBackground() = !isJvmTest &&
        !ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    private fun notifyJob(job: JobView?) {
        val key = job?.let { "${it.id}:${it.status}:${it.offers.size}:${it.awaitingConfirmation}" }
        if (key == lastJobKey) return
        val first = lastJobKey == null
        lastJobKey = key
        if (first || job == null || !session.notifications || !inBackground()) return
        val name = firstName(job.otherName)
        val (title, body) = when {
            job.status == "pending" && job.offers.isNotEmpty() -> "New offer" to "${job.offers.last().providerName} offers ${rs(job.offers.last().amount)}"
            job.status == "accepted" -> "Booking confirmed" to "$name is getting ready"
            job.status == "en_route" -> "On the way" to "$name is coming to you"
            job.status == "arrived" -> "$name has arrived" to "Meet them at the door"
            job.awaitingConfirmation -> "Job finished" to "Check the work and confirm payment"
            job.status == "completed" -> "Payment released" to rs(if (job.isCustomer) job.fare else job.net)
            else -> return
        }
        Notifier.show(app, title, body)
    }

    private fun notifyRequests(req: List<RequestView>) {
        val ids = req.map { it.id }.toSet()
        val fresh = req.filter { it.id !in lastRequestIds }
        lastRequestIds = ids
        if (fresh.isNotEmpty() && session.notifications && inBackground()) {
            val r = fresh.first()
            Notifier.show(app, "New request · ${rs(r.fare)}", "${categoryName(r.category)} · ${r.description}")
        }
    }

    private fun registerPush() {
        if (isJvmTest) return
        runCatching {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnSuccessListener { t ->
                session.pushToken = t
                if (session.userId != null) viewModelScope.launch { runCatching { api.registerPush(t) } }
            }
        }
    }

    // ---------- navigation ----------
    fun tab(t: Tab) { _s.update { it.copy(tab = t) }; op { } }
    fun open(p: Page?) {
        if (p == Page.Safety) dismiss("task-safety")
        _s.update { it.copy(page = p) }
    }
    fun startBooking(category: String?) = _s.update { it.copy(page = Page.Book, bookCategory = category) }
    fun dismiss(id: String) { session.dismissed = session.dismissed + id; _s.update { it.copy(dismissed = session.dismissed) } }

    // ---------- customer ----------
    fun createRequest(category: String, description: String, fare: Long, at: Place = Place.HOME) = op {
        api.createRequest(category, description, fare, at)
        _s.update { it.copy(page = Page.Track) }
    }
    fun raiseFare(newFare: Long) = job { api.raiseFare(it, newFare); toast("Fare raised to ${rs(newFare)}") }
    fun acceptOffer(offerId: String) = job { api.acceptOffer(it, offerId) }
    fun declineOffer(offerId: String) = job { api.declineOffer(it, offerId) }
    fun cancel() = job { api.cancel(it); toast("Cancelled") }
    fun confirm() = job { api.confirm(it) }
    fun dispute(reason: String) = job { api.dispute(it, reason); toast("Report sent") }
    fun rate(score: Int, tags: List<String>) = job { api.rate(it, score, tags) }
    fun skipRating() = job { api.skipRating(it) }

    // ---------- provider ----------
    fun setOnline(on: Boolean) = op { api.setOnline(on) }
    fun makeOffer(bookingId: String, amount: Long) = op { api.makeOffer(bookingId, amount); toast("Offer sent") }
    fun advance(next: String) = job { api.advance(it, next) }
    fun complete(photo: String) = job { api.complete(it, photo); toast("Photo proof sent") }

    // ---------- wallet & safety ----------
    fun topup(amount: Long, tid: String) = op { api.topup(amount, tid); toast("${rs(amount)} added") }
    fun withdraw(amount: Long) = op { api.withdraw(amount); toast("Withdrawal requested") }
    fun sos() = op { api.sos(); toast("SOS sent. Our team and your contacts are notified") }
    fun reset() = viewModelScope.launch {
        poller?.cancel(); (api as? Backend)?.reset(); session.accounts = emptyList(); session.profiles = emptyList(); session.userId = null
        _s.value = UiState(stage = Stage.SignIn, launching = false, notifications = session.notifications, local = isJvmTest)
    }

    fun toast(m: String) = _s.update { it.copy(message = m) }
    fun clearMessage() = _s.update { it.copy(message = null) }

    private fun job(block: suspend (String) -> Unit) { val id = _s.value.job?.id ?: return; op { block(id) } }

    private fun op(block: suspend () -> Unit) = viewModelScope.launch {
        _s.update { it.copy(busy = true) }
        try { block(); refresh() } catch (e: AppError) { toast(e.message ?: "Something went wrong") } catch (e: Exception) { toast("Something went wrong. Try again") }
        _s.update { it.copy(busy = false) }
    }
}
