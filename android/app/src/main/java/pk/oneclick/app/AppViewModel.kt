package pk.oneclick.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Demo customer location: Gulberg, Lahore. */
const val DEMO_LAT = 31.5204
const val DEMO_LNG = 74.3587

data class UiState(
    val loggedIn: Boolean = false,
    val demoUsers: List<DemoUserDto> = emptyList(),
    val mode: ActiveMode = ActiveMode.CUSTOMER,
    val me: MeRes? = null,
    val active: BookingDto? = null,
    val categories: List<CategoryDto> = emptyList(),
    val selectedCategory: String? = null,
    val providers: List<ProviderDto> = emptyList(),
    val fallback: Boolean = false,
    val wallet: WalletRes? = null,
    val history: List<BookingDto> = emptyList(),
    val message: String? = null,
    val busy: Boolean = false,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val session = Session(app)
    private val api = LocalBackend(java.io.File(app.filesDir, "oneclick-db.json"), { session.token }, { session.mode })
    private val _s = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _s
    private var poller: Job? = null

    init {
        if (session.token != null) onLoggedIn() else loadDemoUsers()
    }

    fun loadDemoUsers() = run { _s.update { it.copy(demoUsers = api.demoUsers().users) } }

    fun login(user: DemoUserDto) = run {
        session.token = api.login(LoginReq(user.id)).token
        session.mode = if (user.userRole == "provider") ActiveMode.PROVIDER else ActiveMode.CUSTOMER
        onLoggedIn()
    }

    fun logout() {
        poller?.cancel()
        session.token = null
        _s.value = UiState()
        loadDemoUsers()
    }

    private fun onLoggedIn() {
        _s.update { it.copy(loggedIn = true, mode = session.mode) }
        run {
            val me = api.me()
            if (me.user.userRole == "provider") session.mode = ActiveMode.PROVIDER
            _s.update { it.copy(me = me, mode = session.mode, categories = api.categories().categories) }
            refreshMap()
        }
        poller?.cancel()
        poller = viewModelScope.launch {
            while (isActive) {
                runCatching {
                    val a = api.activeBooking().booking
                    val me = api.me()
                    _s.update { it.copy(active = a, me = me) }
                }
                delay(1000)
            }
        }
    }

    fun switchMode(mode: ActiveMode) {
        session.mode = mode
        _s.update { it.copy(mode = mode, active = null) }
        refreshMap()
    }

    fun selectCategory(slug: String?) {
        _s.update { it.copy(selectedCategory = slug) }
        refreshMap()
    }

    fun refreshMap() = run {
        if (session.mode == ActiveMode.CUSTOMER) {
            val r = api.nearby(DEMO_LAT, DEMO_LNG, _s.value.selectedCategory)
            _s.update { it.copy(providers = r.providers, fallback = r.fallback) }
        }
    }

    fun loadWallet() = run { _s.update { it.copy(wallet = api.wallet()) } }
    fun loadHistory() = run { _s.update { it.copy(history = api.bookings().bookings) } }

    fun topup(amount: Long, tid: String) = run {
        api.topup(TopupReq(amount, tid)); toast("${rs(amount)} added to your wallet."); loadWallet()
    }

    fun setOnline(online: Boolean) = run {
        api.setOnline(OnlineReq(online)); _s.update { it.copy(me = api.me()) }
    }

    fun book(p: ProviderDto, description: String, fare: Long) = run {
        val b = api.createBooking(CreateBookingReq(p.id, p.categories.first(), description, fare, DEMO_LAT, DEMO_LNG))
        _s.update { it.copy(active = b.booking) }
        toast("Request sent to ${p.name}")
    }

    fun act(block: suspend ApiService.(String) -> BookingRes, success: String? = null) {
        val id = _s.value.active?.id ?: return
        run {
            val b = api.block(id).booking
            _s.update { it.copy(active = b) }
            success?.let { toast(it) }
            refreshMap()
        }
    }

    fun sos() = run {
        val a = _s.value.active
        toast(api.sos(SosReq(a?.id, a?.customerLat ?: DEMO_LAT, a?.customerLng ?: DEMO_LNG)).message ?: "SOS sent")
    }

    fun resetDemo() { api.resetDemo(); logout() }

    fun toast(m: String) = _s.update { it.copy(message = m) }
    fun clearMessage() = _s.update { it.copy(message = null) }

    private fun run(block: suspend () -> Unit) = viewModelScope.launch {
        _s.update { it.copy(busy = true) }
        try { block() } catch (e: Exception) { toast(e.userMessage()) }
        _s.update { it.copy(busy = false) }
    }
}
