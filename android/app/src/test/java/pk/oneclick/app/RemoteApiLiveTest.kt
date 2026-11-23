package pk.oneclick.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Hits the live Supabase server. Runs only with -Dlive=true (needs internet). */
class RemoteApiLiveTest {
    private val url = "https://diyipvjaxjkacwrszmda.supabase.co/functions/v1/api"

    @Test fun liveCustomerAndProviderReads() = runBlocking {
        assumeTrue(System.getProperty("live") == "true")
        var token: String? = null
        var mode = ActiveMode.CUSTOMER
        val api = RemoteApi(url, { token }, { mode })
        assertEquals("Hamza Ali", api.lookup("0300 1110001")?.name)
        assertNull(api.lookup("0300 9999999"))
        token = api.signIn("03001110001", "").token
        assertEquals("customer", api.me().user.role)
        assertTrue(api.mapProviders().size >= 5)
        val (w, txns) = api.wallet()
        assertTrue(w.balance >= 0); assertTrue(txns.isNotEmpty())
        api.history(); api.activeJob()
        val e = runCatching { api.createRequest("plumber", "", 99_999) }.exceptionOrNull()
        assertTrue(e is AppError && e.message!!.contains("Fare must be"))
        token = api.signIn("03001110012", "").token; mode = ActiveMode.PROVIDER
        assertEquals("provider", api.me().user.role)
        api.setOnline(true)
        Thread.sleep(4500)
        api.requests(); val reqs = api.requests()
        assertTrue("expected a simulated request", reqs.isNotEmpty())
        api.setOnline(false)
        val bad = RemoteApi(url, { "bad" }, { ActiveMode.CUSTOMER })
        assertTrue(runCatching { bad.me() }.exceptionOrNull()?.message!!.startsWith("Session expired"))
    }
}
