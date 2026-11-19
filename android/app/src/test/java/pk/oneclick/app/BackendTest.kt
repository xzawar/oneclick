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

    @Test fun onlyTwoAccounts() {
        assertEquals(listOf("customer", "provider"), api.accounts().map { it.role })
    }

    @Test fun customerFullFlow_offerAtPrice_escrow_commission_rating() = runTest {
        assertEquals(1, api.mapProviders().size)
        api.createRequest("plumber", "Geyser leaking", 800)
        assertTrue(api.activeJob()!!.offers.isEmpty())
        wait(5_000)
        val o = api.activeJob()!!.offers.single()
        assertEquals(800, o.amount); assertFalse(o.isCounter)
        api.acceptOffer(api.activeJob()!!.id, o.id)
        assertEquals("accepted", api.activeJob()!!.status)
        api.wallet().first.let { assertEquals(4200, it.balance); assertEquals(800, it.escrow) }
        assertTrue(api.mapProviders().isEmpty()) // provider busy → hidden from map
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
        api.createRequest("electrician", "", 500)
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
        assertEquals(1, api.mapProviders().size)
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

    @Test fun commissionRounding() {
        assertEquals(120, Backend.commission(800)); assertEquals(105, Backend.commission(700)); assertEquals(53, Backend.commission(350))
    }
}
