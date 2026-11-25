package pk.oneclick.app

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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

/** Drives the real app through first launch, a customer booking and a provider job; saves PNGs to build/shots. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class ScreensTest {
    @get:Rule val rule = createComposeRule()

    private fun settle(ms: Long = 1500) {
        repeat((ms / 250).toInt()) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250)); rule.mainClock.advanceTimeBy(250) }
    }
    private fun skip(ms: Long) { DemoClock.skew += ms; settle() }
    private fun shot(name: String) { settle(700); rule.onRoot().captureRoboImage("build/shots/$name.png") }
    private fun click(text: String) { rule.onAllNodesWithText(text).onFirst().performClick(); settle() }

    @Test fun journey() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        File(app.filesDir, "oneclick-db.json").delete()
        app.getSharedPreferences("session_test", 0).edit().clear().commit()
        DemoClock.skew = 0
        val vm = AppViewModel(app)
        rule.mainClock.autoAdvance = false
        rule.setContent { OneClickTheme { Root(vm) } }
        settle(500); rule.onRoot().captureRoboImage("build/shots/00_launch.png")
        shot("01_onboarding")
        click("Continue"); click("Continue")
        shot("02_onboarding_last")
        click("Get started")
        shot("03_sign_in")
        click("Hamza Ali"); settle(1500)
        shot("04_sign_in_known")
        click("Continue"); settle(2000)
        shot("05_home")
        click("Plumber")
        shot("06_book_details")
        click("Leaking tap"); click("Continue")
        shot("07_book_where")
        click("Confirm address")
        shot("08_book_price")
        click("Find pros")
        shot("09_searching")
        skip(5_000)
        shot("10_offer")
        click("Accept Rs 800"); skip(4_000); skip(20_000)
        shot("11_on_the_way")
        skip(41_000); skip(5_000); skip(13_000)
        shot("12_confirm")
        click("Confirm and pay Rs 800")
        shot("13_success")
        click("Rate Ahmad")
        rule.onNodeWithContentDescription("5 stars").performClick(); settle(); click("On time")
        shot("14_rating")
        click("Submit"); settle(2000)
        shot("15_home_after")
        rule.onNodeWithContentDescription("Bookings").performClick(); settle(); click("Past")
        shot("16_bookings")
        rule.onNodeWithContentDescription("Wallet").performClick(); settle()
        shot("17_wallet")
        rule.onNodeWithContentDescription("Profile").performClick(); settle()
        shot("18_profile")
        click("Add account")
        rule.onNodeWithText("0300 1234567").performTextInput("03001110012"); settle()
        click("Continue"); settle(1500)
        click("Continue"); settle(2000)
        shot("19_provider_home")
        skip(4_000)
        shot("20_provider_request")
        rule.onAllNodesWithText("Accept for", substring = true).onFirst().performClick(); settle(); skip(4_000)
        shot("21_provider_job")
        click("I’m on my way"); skip(20_000)
        shot("22_provider_driving")
        rule.onNodeWithContentDescription("Back").performClick(); settle()
        rule.onNodeWithContentDescription("Profile").performClick(); settle(); click("Switch account")
        shot("23_switch_account")
    }
}
