package pk.oneclick.app

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LocalBackendTest {
    private val hamza = "00000000-0000-0000-0000-000000000001"
    private val shahid = "00000000-0000-0000-0000-000000000011"
    private val ahmad = "00000000-0000-0000-0000-000000000012"
    private var t = 1_000_000L
    private var user: String? = hamza
    private var mode = ActiveMode.CUSTOMER
    private val api = LocalBackend(File.createTempFile("oneclick", ".json").apply { delete() }, { user }, { mode }, { t })

    private suspend fun advance(ms: Long) { t += ms; api.activeBooking() }

    @Test fun customerBooksBotProvider_escrowCommissionRelease() = runTest {
        val p = api.nearby(DEMO_LAT, DEMO_LNG, "plumber").providers.first()
        assertEquals(ahmad, p.id)
        api.createBooking(CreateBookingReq(ahmad, "plumber", "Leak", 800, DEMO_LAT, DEMO_LNG))
        advance(5_000)
        assertEquals("accepted", api.activeBooking().booking!!.status)
        assertEquals(4200, api.wallet().wallet.balance); assertEquals(800, api.wallet().wallet.escrowHeld)
        advance(4_000); assertEquals("en_route", api.activeBooking().booking!!.status)
        advance(30_000); assertTrue(api.activeBooking().booking!!.etaMin!! in 1..1)
        advance(31_000); assertEquals("arrived", api.activeBooking().booking!!.status)
        advance(5_000); advance(16_000)
        val b = api.activeBooking().booking!!
        assertTrue(b.awaitingConfirmation)
        api.confirm(b.id)
        assertEquals(0, api.wallet().wallet.escrowHeld)
        user = ahmad; mode = ActiveMode.PROVIDER
        assertEquals(1200 + 680, api.wallet().wallet.balance)
        user = hamza; mode = ActiveMode.CUSTOMER
        assertTrue(api.activeBooking().booking!!.needsRating)
        api.rate(b.id, RateReq(5))
        assertNull(api.activeBooking().booking)
    }

    @Test fun cannotConfirmBeforePhoto_andCancelRefunds() = runTest {
        api.createBooking(CreateBookingReq(ahmad, "plumber", "", 800, DEMO_LAT, DEMO_LNG))
        advance(5_000)
        val id = api.activeBooking().booking!!.id
        assertThrows(AppError::class.java) { kotlinx.coroutines.runBlocking { api.confirm(id) } }
        api.cancel(id)
        assertEquals(5000, api.wallet().wallet.balance); assertEquals(0, api.wallet().wallet.escrowHeld)
    }

    @Test fun pendingRequestTimesOutAfter30s() = runTest {
        user = shahid; mode = ActiveMode.PROVIDER
        api.topup(TopupReq(500, "ABC12345"))
        api.setOnline(OnlineReq(true))
        advance(6_000)
        val b = api.activeBooking().booking!!
        assertEquals("pending", b.status); assertEquals("provider", b.mySide)
        advance(31_000)
        assertTrue(api.bookings().bookings.any { it.id == b.id && it.cancelReason == "timeout" })
    }

    @Test fun humanProviderFullJob_botCustomerConfirms() = runTest {
        user = shahid; mode = ActiveMode.PROVIDER
        api.topup(TopupReq(500, "ABC12345")); api.setOnline(OnlineReq(true)); advance(6_000)
        val id = api.activeBooking().booking!!.id
        val fare = api.activeBooking().booking!!.agreedFare
        api.accept(id)
        listOf("en_route", "arrived", "in_progress").forEach { api.status(id, StatusReq(it)) }
        api.complete(id, CompleteReq("/proof.jpg"))
        advance(6_000)
        assertEquals("completed", api.activeBooking().booking!!.status)
        assertEquals(950 + fare - (fare * 15 + 50) / 100, api.wallet().wallet.balance)
    }

    @Test fun rules_lowWalletDuplicateTidModeGuard() = runTest {
        user = hamza; mode = ActiveMode.PROVIDER
        assertTrue(runCatching { api.topup(TopupReq(500, "DEMO-TID-0001")) }.exceptionOrNull()!!.message!!.contains("already used"))
        assertTrue(runCatching { api.createBooking(CreateBookingReq(ahmad, "plumber", "", 800, 0.0, 0.0)) }.isFailure)
        mode = ActiveMode.CUSTOMER
        assertTrue(runCatching { api.createBooking(CreateBookingReq(ahmad, "plumber", "", 99_999, 0.0, 0.0)) }.exceptionOrNull()!!.message!!.contains("too low"))
        assertTrue(api.nearby(DEMO_LAT, DEMO_LNG, "mechanic").providers.isNotEmpty())
    }
}
