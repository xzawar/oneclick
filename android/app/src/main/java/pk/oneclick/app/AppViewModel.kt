package pk.oneclick.app

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
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

/** Signed-in account + mode, kept in EncryptedSharedPreferences (Android Keystore master key). */
class Session(ctx: Context) {
    // JVM screenshot tests (Robolectric) have no Android Keystore; real devices always use encrypted prefs.
    private val prefs = if (android.os.Build.FINGERPRINT == "robolectric") ctx.getSharedPreferences("session_test", 0)
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
}

/** App clock. Tests move [skew] forward to fast-forward the other account. */
object DemoClock { @Volatile var skew = 0L; fun now() = System.currentTimeMillis() + skew }

enum class Page { Home, Wallet, History, Safety }

data class UiState(
    val signedIn: Boolean = false,
    val mode: ActiveMode = ActiveMode.CUSTOMER,
    val me: Me? = null,
    val job: JobView? = null,
    val requests: List<RequestView> = emptyList(),
    val mapProviders: List<MapProvider> = emptyList(),
    val wallet: DbWallet? = null,
    val txns: List<TxnView> = emptyList(),
    val history: List<JobView> = emptyList(),
    val page: Page = Page.Home,
    val message: String? = null,
    val busy: Boolean = false,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val session = Session(app)
    private val api = Backend(File(app.filesDir, "oneclick-db.json"), { session.userId }, { session.mode }, { DemoClock.now() })
    private val _s = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _s
    private var poller: Job? = null

    val accounts get() = api.accounts()

    init { if (session.userId != null) start() }

    fun signIn(u: DbUser) {
        session.userId = u.id
        session.mode = if (u.role == "provider") ActiveMode.PROVIDER else ActiveMode.CUSTOMER
        start()
    }

    fun signOut() {
        poller?.cancel()
        session.userId = null
        _s.value = UiState()
    }

    private fun start() {
        _s.update { it.copy(signedIn = true, mode = session.mode, page = Page.Home) }
        poller?.cancel()
        poller = viewModelScope.launch {
            while (isActive) { runCatching { refresh() }; delay(1000) }
        }
    }

    private suspend fun refresh() {
        val me = api.me()
        val job = api.activeJob()
        if (session.mode == ActiveMode.PROVIDER) _s.update { it.copy(me = me, job = job, requests = api.requests()) }
        else _s.update { it.copy(me = me, job = job, mapProviders = api.mapProviders()) }
        when (_s.value.page) {
            Page.Wallet -> api.wallet().let { (w, t) -> _s.update { it.copy(wallet = w, txns = t) } }
            Page.History -> api.history().let { h -> _s.update { it.copy(history = h) } }
            else -> Unit
        }
    }

    fun open(p: Page) { _s.update { it.copy(page = p) }; op { } }

    fun createRequest(category: String, description: String, fare: Long) = op { api.createRequest(category, description, fare) }
    fun raiseFare(newFare: Long) = job { api.raiseFare(it, newFare); toast("Fare raised to ${rs(newFare)}") }
    fun acceptOffer(offerId: String) = job { api.acceptOffer(it, offerId) }
    fun declineOffer(offerId: String) = job { api.declineOffer(it, offerId) }
    fun cancel() = job { api.cancel(it); toast("Cancelled") }
    fun confirm() = job { api.confirm(it) }
    fun dispute(reason: String) = job { api.dispute(it, reason); toast("Report sent") }
    fun rate(score: Int, tags: List<String>) = job { api.rate(it, score, tags) }
    fun skipRating() = job { api.skipRating(it) }

    fun setOnline(on: Boolean) = op { api.setOnline(on) }
    fun makeOffer(bookingId: String, amount: Long) = op { api.makeOffer(bookingId, amount); toast("Offer sent") }
    fun advance(next: String) = job { api.advance(it, next) }
    fun complete(photo: String) = job { api.complete(it, photo); toast("Photo proof sent") }

    fun topup(amount: Long, tid: String) = op { api.topup(amount, tid); toast("${rs(amount)} added") }
    fun withdraw(amount: Long) = op { api.withdraw(amount); toast("Withdrawal requested") }
    fun sos() = op { api.sos(); toast("SOS sent. Our team and your contacts are notified") }
    fun reset() = viewModelScope.launch { api.reset(); signOut() }

    fun toast(m: String) = _s.update { it.copy(message = m) }
    fun clearMessage() = _s.update { it.copy(message = null) }

    private fun job(block: suspend (String) -> Unit) {
        val id = _s.value.job?.id ?: return
        op { block(id) }
    }

    private fun op(block: suspend () -> Unit) = viewModelScope.launch {
        _s.update { it.copy(busy = true) }
        try { block(); refresh() } catch (e: AppError) { toast(e.message ?: "Something went wrong") } catch (e: Exception) { toast("Something went wrong. Try again") }
        _s.update { it.copy(busy = false) }
    }
}
