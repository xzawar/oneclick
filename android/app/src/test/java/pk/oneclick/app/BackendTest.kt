package pk.oneclick.app

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BackendTest {
    private var t = 1_000_000_000L
    private var user: String? = Backend.CUSTOMER_ID
    private var mode = ActiveMode.CUSTOMER
    private val api = Backend(File.createTempFile("oneclick", ".json").apply { delete() }, { user }, { mode }, { t })

    private suspend fun wait(ms: Long) { t += ms; api.activeJob() }
    private fun asProvider() { user = Backend.PROVIDER_ID; mode = ActiveMode.PROVIDER }
    private fun asCustomer() { user = Backend.CUSTOMER_ID; mode = ActiveMode.CUSTOMER }

    @Test fun seededPeople() {
        assertEquals(1, api.accounts().count { it.role == "customer" })
        assertEquals(6, api.accounts().count { it.role == "provider" })
    }

    @Test fun signIn_existingNewAndInvalidNumbers() = runTest {
        assertEquals(Backend.CUSTOMER_ID, api.signInUser("0300 1110001", "").id)
        assertEquals(Backend.PROVIDER_ID, api.signInUser("+92 300 1110012", "").id)
        assertThrows(AppError::class.java) { runBlocking { api.signInUser("0421234567", "Ali") } }
        assertThrows(AppError::class.java) { runBlocking { api.signInUser("03214567890", "A") } }
        val u = api.signInUser("0321-4567890", "  Zara   Ahmed ")
        assertEquals("Zara Ahmed", u.name); assertEquals("+923214567890", u.phone); assertEquals("customer", u.role)
        user = u.id
        assertEquals(Backend.WELCOME_CREDIT, api.wallet().first.balance)
        assertEquals(u.id, api.findByPhone("03214567890")!!.id)
    }

    @Test fun electrician_getsOffersFromTwoProviders_acceptOneDeclinesOther() = runTest {
        api.createRequest("electrician", "Fan", 700)
        wait(4_500); assertEquals(1, api.activeJob()!!.offers.size)
        wait(3_000)
        val offers = api.activeJob()!!.offers
        assertEquals(setOf("Ahmad Khan", "Sara Iqbal"), offers.map { it.providerName }.toSet())
        assertEquals(listOf(700L, 750L), offers.map { it.amount }.sorted())
        api.acceptOffer(api.activeJob()!!.id, offers.minBy { it.amount }.id)
        val j = api.activeJob()!!
        assertEquals("accepted", j.status); assertTrue(j.offers.isEmpty())
    }

    @Test fun customerFullFlow_offerAtPrice_escrow_commission_rating() = runTest {
        assertEquals(6, api.mapProviders().size)
        api.createRequest("plumber", "Geyser leaking", 800)
        assertTrue(api.activeJob()!!.offers.isEmpty())
        wait(5_000)
        val o = api.activeJob()!!.offers.single()
        assertEquals(800, o.amount); assertFalse(o.isCounter)
        api.acceptOffer(api.activeJob()!!.id, o.id)
        assertEquals("accepted", api.activeJob()!!.status)
        api.wallet().first.let { assertEquals(4200, it.balance); assertEquals(800, it.escrow) }
        assertEquals(5, api.mapProviders().size) // busy provider hidden from map
        wait(4_000); assertEquals("en_route", api.activeJob()!!.status)
        wait(30_000); assertEquals(1, api.activeJob()!!.etaMin)
        wait(31_000); assertEquals("arrived", api.activeJob()!!.status)
        wait(5_000); assertEquals("in_progress", api.activeJob()!!.status)
        assertFalse(api.activeJob()!!.awaitingConfirmation)
        wait(13_000); assertTrue(api.activeJob()!!.awaitingConfirmation)
        api.confirm(api.activeJob()!!.id)
        api.wallet().first.let { assertEquals(4200, it.balance); assertEquals(0, it.escrow) }
        val job = api.activeJob()!!
        assertTrue(job.needsRating); assertEquals(680, job.net)
        api.rate(job.id, 4, listOf("On time"))
        assertNull(api.activeJob())
        asProvider()
        assertEquals(1200 + 680, api.wallet().first.balance)
        assertEquals(4.8, api.me().provider!!.rating, 0.05)
        assertNull(api.activeJob()) // simulated provider already rated → no prompt on this account
        assertEquals(listOf("Job earnings", "Top-up"), api.wallet().second.map { it.title })
        asCustomer()
        assertEquals(listOf("Paid for job", "Top-up"), api.wallet().second.map { it.title })
    }

    @Test fun lowFare_getsCounterOffer_declineThenReofferAtPrice() = runTest {
        api.createRequest("plumber", "", 600)
        wait(5_000)
        val counter = api.activeJob()!!.offers.single()
        assertEquals(700, counter.amount); assertTrue(counter.isCounter)
        api.declineOffer(api.activeJob()!!.id, counter.id)
        assertTrue(api.activeJob()!!.offers.isEmpty())
        wait(9_000)
        val again = api.activeJob()!!.offers.single()
        assertEquals(600, again.amount); assertFalse(again.isCounter)
    }

    @Test fun raiseFare_offerExpires_requestTimesOut() = runTest {
        api.createRequest("plumber", "", 500)
        api.raiseFare(api.activeJob()!!.id, 550)
        assertEquals(550, api.activeJob()!!.fare)
        wait(5_000); assertEquals(1, api.activeJob()!!.offers.size)
        wait(31_000); assertTrue(api.activeJob()!!.offers.isEmpty() || api.activeJob()!!.offers.single().secondsLeft > 0)
        wait(Backend.SEARCH_MS)
        val j = api.activeJob()!!
        assertEquals("cancelled", j.status); assertEquals("no_offers", j.cancelReason)
        wait(7_000); assertNull(api.activeJob())
    }

    @Test fun cancelAfterAccept_refundsFully() = runTest {
        api.createRequest("plumber", "", 800); wait(5_000)
        api.acceptOffer(api.activeJob()!!.id, api.activeJob()!!.offers.single().id)
        api.cancel(api.activeJob()!!.id)
        api.wallet().first.let { assertEquals(5000, it.balance); assertEquals(0, it.escrow) }
        assertEquals(6, api.mapProviders().size)
    }

    @Test fun cannotConfirmBeforePhoto_orAcceptWithoutFunds() = runTest {
        api.createRequest("plumber", "", 800); wait(5_000)
        val id = api.activeJob()!!.id
        api.acceptOffer(id, api.activeJob()!!.offers.single().id)
        assertThrows(AppError::class.java) { runBlocking { api.confirm(id) } }
        api.cancel(id)
        assertThrows(AppError::class.java) { runBlocking { api.createRequest("plumber", "", 6000) } }
    }

    @Test fun providerFlow_botCustomerRequest_counterAccepted_jobPaid() = runTest {
        asProvider()
        api.setOnline(false); api.setOnline(true)
        wait(4_000)
        val r = api.requests().single()
        assertEquals("Hamza Ali", r.customerName)
        api.makeOffer(r.id, r.fare + 200)
        assertEquals("open", api.requests().single().myOfferStatus)
        wait(4_000)
        val job = api.activeJob()!!
        assertEquals("accepted", job.status); assertEquals(r.fare + 200, job.fare)
        listOf("en_route", "arrived", "in_progress").forEach { api.advance(job.id, it) }
        api.complete(job.id, "/proof.jpg")
        assertTrue(api.activeJob()!!.awaitingConfirmation)
        wait(5_000)
        val done = api.activeJob()!!
        assertEquals("completed", done.status); assertTrue(done.needsRating)
        assertEquals(1200 + done.net, api.wallet().first.balance)
        assertEquals(4.8, api.me().provider!!.rating, 0.05)
    }

    @Test fun providerHighCounter_declined() = runTest {
        asProvider(); api.setOnline(true); wait(4_000)
        val r = api.requests().single()
        api.makeOffer(r.id, r.fare + 300)
        wait(4_000)
        assertEquals("declined", api.requests().single().myOfferStatus)
        assertNull(api.activeJob())
    }

    @Test fun switchingAccounts_cleansBotRequests() = runTest {
        asProvider(); api.setOnline(true); wait(4_000)
        assertEquals(1, api.requests().size)
        asCustomer()
        assertNull(api.activeJob())
        assertTrue(api.history().none { it.status == "pending" })
    }

    @Test fun walletRules() = runTest {
        assertThrows(AppError::class.java) { runBlocking { api.topup(500, "JC-58210443") } } // seeded TID reused
        api.topup(500, "abc12345")
        assertThrows(AppError::class.java) { runBlocking { api.topup(500, "ABC12345") } }
        assertEquals(5500, api.wallet().first.balance)
        asProvider()
        api.withdraw(1100)
        assertThrows(AppError::class.java) { runBlocking { api.setOnline(true) } } // 100 < 200 minimum
        assertThrows(AppError::class.java) { runBlocking { api.withdraw(5000) } }
    }

    @Test fun modeGuards() = runTest {
        assertThrows(AppError::class.java) { runBlocking { api.setOnline(true) } }
        asProvider()
        assertThrows(AppError::class.java) { runBlocking { api.createRequest("plumber", "", 800) } }
    }

    @Test fun location_requestsInLahoreOnly_addressShown_providerFixMovesPin() = runTest {
        assertThrows(AppError::class.java) { runBlocking { api.createRequest("plumber", "", 800, Place(33.6844, 73.0479, "Islamabad")) } }
        val johar = Place(31.4697, 74.2728, "Johar Town, Lahore")
        api.createRequest("plumber", "Tap", 800, johar)
        val j = api.activeJob()!!
        assertEquals("Johar Town, Lahore", j.address); assertEquals(johar.lat, j.lat, 1e-9)
        asProvider()
        assertThrows(AppError::class.java) { runBlocking { api.updateLocation(51.5, 0.1) } }
        api.updateLocation(31.5300, 74.3500)
        assertEquals(31.5300, api.me().provider!!.lat, 1e-9)
        asCustomer()
        assertThrows(AppError::class.java) { runBlocking { api.updateLocation(31.53, 74.35) } }
    }

    @Test fun commissionRounding() {
        assertEquals(120, Backend.commission(800)); assertEquals(105, Backend.commission(700)); assertEquals(53, Backend.commission(350))
    }
}
