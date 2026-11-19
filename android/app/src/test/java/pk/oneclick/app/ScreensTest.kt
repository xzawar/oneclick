package pk.oneclick.app

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/** Renders every main screen through a real customer + provider journey and saves PNGs to build/shots. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class ScreensTest {
    @get:Rule val rule = createComposeRule()

    private fun settle(ms: Long = 1500) {
        repeat((ms / 250).toInt()) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
            rule.mainClock.advanceTimeBy(250)
        }
    }
    private fun skip(ms: Long) { DemoClock.skew += ms; settle() }
    private fun shot(name: String) { settle(600); rule.onRoot().captureRoboImage("build/shots/$name.png") }

    @Test fun journey() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        File(app.filesDir, "oneclick-db.json").delete()
        DemoClock.skew = 0
        val vm = AppViewModel(app)
        rule.mainClock.autoAdvance = false
        rule.setContent { OneClickTheme { App(vm) } }
        shot("01_welcome")

        // ---- customer ----
        vm.signIn(vm.accounts.first { it.role == "customer" }); settle()
        shot("02_customer_home")
        rule.onNodeWithText("Rs 800").performClick(); settle()
        shot("03_fare_sheet")
        rule.onNodeWithText("Done").performClick(); settle()
        rule.onNodeWithText("Find providers").performClick(); settle()
        shot("04_searching")
        skip(5_000)
        shot("05_offer_card")
        rule.onNodeWithText("Accept").performClick(); settle()
        skip(4_000); skip(20_000)
        shot("06_on_the_way")
        skip(41_000); skip(5_000); skip(13_000)
        shot("07_confirm_job")
        rule.onNodeWithText("Confirm and pay Rs 800").performClick(); settle()
        shot("08_rating")
        rule.onNodeWithContentDescription("5 stars").performClick(); settle()
        shot("09_rating_selected")
        rule.onNodeWithText("Done").performClick(); settle()
        rule.onNodeWithContentDescription("Menu").performClick(); settle()
        shot("10_menu")
        rule.onNodeWithText("Wallet").performClick(); settle()
        shot("11_wallet")
        vm.open(Page.Home); settle()
        vm.signOut(); settle()

        // ---- provider ----
        vm.signIn(vm.accounts.first { it.role == "provider" }); settle()
        vm.setOnline(false); settle()
        shot("12_provider_offline")
        rule.onNodeWithText("Go online").performClick(); settle()
        skip(4_000)
        shot("13_provider_request")
        rule.onNodeWithText("Accept for", substring = true).performClick(); settle()
        skip(4_000)
        shot("14_provider_job")
        rule.onNodeWithText("I’m on my way").performClick(); settle(); skip(20_000)
        shot("15_provider_driving")
    }
}
