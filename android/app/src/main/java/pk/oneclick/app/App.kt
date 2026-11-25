package pk.oneclick.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Notifier.createChannel(this)
        setContent { OneClickTheme { Root(vm) } }
    }
}

@Composable
fun Root(vm: AppViewModel) {
    val s by vm.state.collectAsState()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(s.message) { s.message?.let { vm.clearMessage(); snack.showSnackbar(it) } }
    Box(Modifier.fillMaxSize().background(OC.bg)) {
        AnimatedContent(s.stage, label = "stage", transitionSpec = {
            (fadeIn(tween(450, easing = Motion.enter)) + slideInHorizontally(tween(450, easing = Motion.enter)) { it / 6 }) togetherWith
                fadeOut(tween(200, easing = Motion.exit))
        }) { stage ->
            when (stage) {
                Stage.Onboarding -> Onboarding(vm)
                Stage.SignIn -> SignIn(vm, onBack = null)
                Stage.App -> Shell(s, vm)
            }
        }
        // Toasts use the inverse theme.
        SnackbarHost(snack, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp, start = 16.dp, end = 16.dp)) {
            Snackbar(it, containerColor = OC.ink, contentColor = Color.White, shape = Radius.md)
        }
        if (s.launching) LaunchCurtain { vm.launchDone() }
    }
}

// ---------------- Onboarding: info screens, display headline once per page ----------------
private data class Slide(val icon: ImageVector, val title: String, val body: String)
private val SLIDES = listOf(
    Slide(Icons.Outlined.VerifiedUser, "Pros you can trust", "Every plumber, electrician and mechanic is checked with CNIC and a live selfie."),
    Slide(Icons.Outlined.Sell, "You set the price", "Offer what you want to pay. Pros say yes or send a counter-offer."),
    Slide(Icons.Outlined.Lock, "Pay when it’s done", "We hold your money safely and only pay the pro after you say the job is done."),
)

@Composable
fun Onboarding(vm: AppViewModel) {
    val pager = rememberPagerState { SLIDES.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == SLIDES.lastIndex
    Column(Modifier.fillMaxSize().background(OC.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = Gap.gutter)) {
        Row(Modifier.fillMaxWidth().height(72.dp), verticalAlignment = Alignment.CenterVertically) {
            Wordmark(32.dp)
            Spacer(Modifier.weight(1f))
            if (!last) PillButton("Skip", { vm.finishOnboarding() }, Modifier, Btn.Tertiary)
        }
        HorizontalPager(pager, Modifier.weight(1f)) { i ->
            val sl = SLIDES[i]
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Box(Modifier.fillMaxWidth().aspectRatio(1.2f).clip(Radius.xxl).background(OC.forest), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(176.dp).clip(CircleShape).background(OC.forestElevated), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(112.dp).clip(CircleShape).background(OC.gold), contentAlignment = Alignment.Center) {
                            Icon(sl.icon, null, Modifier.size(48.dp), tint = OC.deep)
                        }
                    }
                }
                Spacer(Modifier.height(Gap.sections))
                Text(sl.title.uppercase(), style = T.displaySmall)
                Spacer(Modifier.height(Gap.textToComponent))
                Text(sl.body, style = T.largeBody)
            }
        }
        Row(Modifier.padding(vertical = Gap.contentToButton), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(SLIDES.size) { i ->
                val w by animateDpAsState(if (i == pager.currentPage) 24.dp else 8.dp, tween(300, easing = Motion.standard), label = "dot")
                Box(Modifier.height(8.dp).width(w).clip(CircleShape).background(if (i == pager.currentPage) OC.deep else OC.cardStrong))
            }
        }
        PillButton(if (last) "Get started" else "Continue", {
            if (last) vm.finishOnboarding() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
        })
        Spacer(Modifier.height(Space.lg))
    }
}

// ---------------- Sign in: one question per step ----------------
@Composable
fun SignIn(vm: AppViewModel, onBack: (() -> Unit)?) {
    val s by vm.state.collectAsState()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var phone by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var known by remember { mutableStateOf<DbUser?>(null) }
    var checking by remember { mutableStateOf(false) }
    val valid = Backend.normalizePhone(phone) != null
    val scope = rememberCoroutineScope()
    val back: (() -> Unit)? = if (step > 0) ({ step = 0 }) else onBack
    if (back != null) BackHandler(onBack = back)
    fun next() = scope.launch { checking = true; known = vm.lookup(phone); checking = false; step = 1 }

    AnimatedContent(step, label = "signin", transitionSpec = {
        (slideInHorizontally(tween(450, easing = Motion.enter)) { if (targetState > initialState) it else -it } + fadeIn()) togetherWith
            (slideOutHorizontally(tween(300, easing = Motion.exit)) { if (targetState > initialState) -it / 3 else it / 3 } + fadeOut())
    }) { st ->
        if (st == 0) Screen(
            title = "What’s your mobile number?", subtitle = "We’ll use it to sign you in on this phone.", onBack = back,
            right = if (back == null) ({ OneClickMark(40.dp) }) else null,
            footer = { PillButton("Continue", { next() }, enabled = valid, loading = checking) },
        ) {
            Field(phone, { phone = it.filter { c -> c.isDigit() || c == ' ' }.take(14) }, "0300 1234567", label = "Mobile number",
                phone = true, error = if (phone.length >= 11 && !valid) "Enter a Pakistani mobile number, like 0300 1234567." else null)
            Header("Or try a demo account")
            ListRow("Hamza Ali", subtitle = "Customer · 0300 1110001", left = { Avatar("Hamza Ali") }, chevron = true, onClick = { phone = "0300 1110001"; next() })
            ListRow("Ahmad Khan", subtitle = "Provider · 0300 1110012", left = { Avatar("Ahmad Khan", ring = true) }, chevron = true, onClick = { phone = "0300 1110012"; next() })
        } else {
            val k = known
            if (k != null) Screen(
                title = "Welcome back, ${firstName(k.name)}", subtitle = "Signing in as ${k.name} · ${formatPhone(k.phone)}", onBack = { step = 0 },
                footer = {
                    PillButton("Continue", { vm.signIn(phone, "") }, loading = s.busy)
                    PillButton("Use another number", { step = 0 }, kind = Btn.Tertiary)
                },
            ) { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Avatar(k.name, 96.dp, ring = k.role != "customer") } }
            else Screen(
                title = "What’s your name?", subtitle = "This is how pros will see you.", onBack = { step = 0 },
                footer = { PillButton("Create account", { vm.signIn(phone, name) }, enabled = name.trim().length >= 2, loading = s.busy) },
            ) {
                Field(name, { name = it.take(40) }, "Full name", label = "Full name")
                Alert("You’ll get Rs 1,000 in your wallet to try One Click.", Tone.Good)
                Text("By continuing you agree to our Terms and Privacy Policy.", style = T.defaultBody)
            }
        }
    }
}

// ---------------- App shell: labelled tabs + pages that slide over ----------------
@Composable
fun Shell(s: UiState, vm: AppViewModel) {
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= 33 && !isJvmTest) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS) }
    BackHandler(s.page != null) { vm.open(null) }
    BackHandler(s.page == null && s.tab != Tab.Home) { vm.tab(Tab.Home) }
    val provider = s.mode == ActiveMode.PROVIDER
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                AnimatedContent(s.tab, label = "tab", transitionSpec = {
                    (fadeIn(tween(300, easing = Motion.standard)) + slideInVertically(tween(300, easing = Motion.standard)) { 24 }) togetherWith fadeOut(tween(150))
                }) { tab ->
                    when (tab) {
                        Tab.Home -> if (provider) ProviderHome(s, vm) else CustomerHome(s, vm)
                        Tab.Bookings -> BookingsTab(s, vm)
                        Tab.Wallet -> WalletTab(s, vm)
                        Tab.Profile -> ProfileTab(s, vm)
                    }
                }
            }
            if (Overlays.open == 0) TabBar(s.tab, provider) { vm.tab(it) }
        }
        AnimatedVisibility(s.page != null, enter = slideInHorizontally(tween(450, easing = Motion.enter)) { it },
            exit = slideOutHorizontally(tween(300, easing = Motion.exit)) { it }) {
            Box(Modifier.fillMaxSize().background(OC.bg)) {
                when (s.page) {
                    Page.Book -> BookFlow(s, vm)
                    Page.Track -> if (provider) ProviderJobPage(s, vm) else TrackPage(s, vm)
                    Page.Safety -> SafetyPage(vm)
                    Page.Help -> HelpPage(vm)
                    Page.About -> AboutPage(vm)
                    Page.AddAccount -> SignIn(vm) { vm.open(null) }
                    null -> Unit
                }
            }
        }
    }
}

@Composable
fun TabBar(current: Tab, provider: Boolean, onTab: (Tab) -> Unit) {
    val items = listOf(
        Triple(Tab.Home, Icons.Outlined.Home, "Home"),
        Triple(Tab.Bookings, Icons.Outlined.EventNote, if (provider) "Jobs" else "Bookings"),
        Triple(Tab.Wallet, Icons.Outlined.AccountBalanceWallet, if (provider) "Earnings" else "Wallet"),
        Triple(Tab.Profile, Icons.Outlined.Person, "Profile"),
    )
    Column(Modifier.fillMaxWidth().background(OC.bg)) {
        Hr()
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            items.forEach { (tab, icon, label) ->
                val on = tab == current
                Column(Modifier.weight(1f).fillMaxHeight().tap { onTab(tab) }, horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Box(Modifier.size(width = 56.dp, height = 28.dp).clip(CircleShape).background(if (on) OC.goldSoft else Color.Transparent),
                        contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(24.dp), tint = if (on) OC.deep else OC.muted) }
                    Spacer(Modifier.height(2.dp))
                    Text(label, style = if (on) T.navBold else T.nav)
                }
            }
        }
    }
}

// ---------------- Bookings / Jobs ----------------
@Composable
fun BookingsTab(s: UiState, vm: AppViewModel) {
    val provider = s.mode == ActiveMode.PROVIDER
    var past by rememberSaveable { mutableStateOf(false) }
    val active = s.history.filter { it.status in Backend.ACTIVE }
    val list = if (past) s.history.filter { it.status !in Backend.ACTIVE } else active
    Screen(title = if (provider) "Your jobs" else "Your bookings") {
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.chips)) {
            Chip("Active", !past) { past = false }
            Chip("Past", past) { past = true }
        }
        if (list.isEmpty()) Empty(if (past) "Nothing here yet" else "Nothing active", if (provider) "Go online to get requests." else "Book a pro from Home.", Icons.Outlined.EventNote)
        else Column {
            list.forEach { b ->
                ListRow(if (b.otherName.isBlank()) categoryName(b.category) else "${categoryName(b.category)} · ${b.otherName}",
                    subtitle = b.description.ifBlank { "No details" }, icon = categoryIcon(b.category),
                    right = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(rs(if (provider) b.net else b.fare), style = T.largeBodyBold)
                            StatusPill(b.status)
                        }
                    }, onClick = if (b.status in Backend.ACTIVE) ({ vm.open(Page.Track) }) else null)
            }
        }
    }
}

// ---------------- Wallet ----------------
@Composable
fun WalletTab(s: UiState, vm: AppViewModel) {
    var topup by remember { mutableStateOf(false) }
    var withdraw by remember { mutableStateOf(false) }
    val w = s.wallet ?: s.me?.wallet
    val provider = s.mode == ActiveMode.PROVIDER
    Box(Modifier.fillMaxSize()) {
        Screen(title = if (provider) "Earnings" else "Wallet") {
            Column {
                Text("Balance", style = T.groupTitle.copy(color = OC.text))
                Spacer(Modifier.height(Gap.betweenText))
                Text(rs(w?.balance ?: 0), style = T.number)
                if ((w?.escrow ?: 0) > 0) Text("${rs(w!!.escrow)} held for your booking", style = T.defaultBody, modifier = Modifier.padding(top = 4.dp))
                if (provider) Text("You’ve earned ${rs(w?.earned ?: 0)} with One Click", style = T.defaultBody, modifier = Modifier.padding(top = 4.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
                ActionButton(Icons.Outlined.Add, "Add money", { topup = true }, highlighted = true)
                if (provider) ActionButton(Icons.Outlined.NorthEast, "Withdraw", { withdraw = true })
                ActionButton(Icons.Outlined.EventNote, if (provider) "Jobs" else "Bookings", { vm.tab(Tab.Bookings) })
            }
            if (provider && (w?.balance ?: 0) < Backend.MIN_BALANCE) Alert("Keep at least ${rs(Backend.MIN_BALANCE)} to get requests.", Tone.Warn, action = "Add money") { topup = true }
            Header("Activity")
            if (s.txns.isEmpty()) Text("Your payments will show up here.", style = T.defaultBody)
            Column {
                s.txns.forEach { t ->
                    ListRow(t.title, subtitle = fmtDate(t.createdAt) + (t.ref?.let { " · $it" } ?: "") + if (t.status == "pending") " · Processing" else "",
                        icon = if (t.incoming) Icons.Outlined.SouthWest else Icons.Outlined.NorthEast,
                        right = { Text((if (t.incoming) "+ " else "") + rs(t.amount), style = T.largeBodyBold.copy(color = if (t.incoming) OC.positive else OC.ink)) })
                }
            }
        }
        if (topup) OverlaySheet({ topup = false }) {
            var amount by remember { mutableStateOf("1000") }
            var tid by remember { mutableStateOf("") }
            Text("Add money", style = T.subsectionTitle)
            Text("Send it to One Click on JazzCash or EasyPaisa (0300 0000000), then enter the transaction ID.", style = T.defaultBody)
            Row(horizontalArrangement = Arrangement.spacedBy(Gap.chips)) { listOf("500", "1000", "2000", "5000").forEach { a -> Chip("Rs $a", amount == a) { amount = a } } }
            Field(amount, { amount = it.filter(Char::isDigit).take(5) }, "Amount", label = "Amount", number = true, prefix = "Rs")
            Field(tid, { tid = it.uppercase().take(20) }, "e.g. JC-58210443", label = "Transaction ID (TID)")
            Text("In this demo it’s added straight away. Normally we match your TID with our statement first.", style = T.defaultBody)
            PillButton("Add ${rs(amount.toLongOrNull() ?: 0)}", { vm.topup(amount.toLongOrNull() ?: 0, tid); topup = false },
                enabled = tid.length >= 6 && (amount.toLongOrNull() ?: 0) >= 100)
        }
        if (withdraw) OverlaySheet({ withdraw = false }) {
            var amount by remember { mutableStateOf("500") }
            Text("Withdraw earnings", style = T.subsectionTitle)
            Text("It should reach your JazzCash within 24 hours.", style = T.defaultBody)
            Field(amount, { amount = it.filter(Char::isDigit).take(6) }, "Amount", label = "Amount", number = true, prefix = "Rs")
            PillButton("Withdraw ${rs(amount.toLongOrNull() ?: 0)}", { vm.withdraw(amount.toLongOrNull() ?: 0); withdraw = false })
        }
    }
}

// ---------------- Profile & settings ----------------
@Composable
fun ProfileTab(s: UiState, vm: AppViewModel) {
    val me = s.me ?: return
    val p = me.provider
    var switcher by remember { mutableStateOf(false) }
    var confirmOut by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Screen {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(me.user.name, 72.dp, ring = p?.verified == true)
                Spacer(Modifier.height(Gap.betweenText))
                Text(me.user.name, style = T.subsectionTitle)
                Text((if (p != null) "Provider" else "Customer") + " · " + formatPhone(me.user.phone), style = T.defaultBody)
                if (p?.verified == true) { Spacer(Modifier.height(Gap.betweenText)); Badge("Verified", Tone.Good) }
            }
            if (p != null) {
                Header("Your work")
                Column {
                    ListRow("Rating", subtitle = "From ${p.ratingCount} customers", icon = Icons.Outlined.StarOutline, right = { Text("${p.rating}", style = T.largeBodyBold) })
                    ListRow("Services", subtitle = p.categories.joinToString { categoryName(it) }, icon = Icons.Outlined.HomeRepairService)
                    ListRow("Service area", subtitle = "Within ${p.radiusKm} km of ${Backend.HOME_ADDRESS.substringBefore(",")}", icon = Icons.Outlined.Place)
                    ListRow("ID check", subtitle = "CNIC and live selfie", icon = Icons.Outlined.VerifiedUser, right = { Badge("Done", Tone.Good) })
                }
            }
            Header("Account")
            Column {
                ListRow("Switch account", subtitle = "${s.accounts.size} on this phone", icon = Icons.Outlined.SwitchAccount, chevron = true, onClick = { switcher = true })
                ListRow("Add account", icon = Icons.Outlined.PersonAdd, chevron = true, onClick = { vm.open(Page.AddAccount) })
            }
            Header("Settings")
            Column {
                ListRow("Notifications", subtitle = "Offers, job updates and requests", icon = Icons.Outlined.Notifications,
                    right = { Switch(s.notifications, { vm.setNotifications(it) }, colors = SwitchDefaults.colors(checkedTrackColor = OC.deep, checkedThumbColor = OC.gold)) })
                ListRow("Safety", subtitle = "SOS and emergency numbers", icon = Icons.Outlined.Shield, chevron = true, onClick = { vm.open(Page.Safety) })
            }
            Header("Help")
            Column {
                ListRow("Help and support", icon = Icons.Outlined.HelpOutline, chevron = true, onClick = { vm.open(Page.Help) })
                ListRow("About One Click", icon = Icons.Outlined.Info, chevron = true, onClick = { vm.open(Page.About) })
            }
            Spacer(Modifier.height(Space.sm))
            PillButton("Sign out", { confirmOut = true }, kind = Btn.SecondaryNegative)
            if (s.local) PillButton("Reset demo data", { vm.reset() }, kind = Btn.Tertiary)
        }
        if (switcher) OverlaySheet({ switcher = false }) {
            Text("Switch account", style = T.subsectionTitle)
            Column {
                s.accounts.forEach { u ->
                    ListRow(u.name, subtitle = (if (u.role == "customer") "Customer" else "Provider") + " · " + formatPhone(u.phone),
                        left = { Avatar(u.name, ring = u.role != "customer") },
                        right = { if (u.id == me.user.id) Icon(Icons.Filled.CheckCircle, "Current account", tint = OC.positive) },
                        onClick = { switcher = false; if (u.id != me.user.id) vm.switchAccount(u.id) })
                }
            }
            PillButton("Add another account", { switcher = false; vm.open(Page.AddAccount) }, kind = Btn.Secondary, icon = Icons.Outlined.PersonAdd)
        }
        if (confirmOut) ConfirmDialog("Sign out of this account?", "You can sign back in with your mobile number.", "Stay signed in", "Sign out",
            { confirmOut = false }) { confirmOut = false; vm.signOut() }
    }
}

// ---------------- Safety / Help / About ----------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SafetyPage(vm: AppViewModel) {
    val ctx = LocalContext.current
    fun dial(n: String) = ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$n")))
    Screen(title = "Safety", subtitle = "Help is one press away.", onBack = { vm.open(null) }, bottomPad = 32.dp) {
        Box(Modifier.fillMaxWidth().padding(vertical = Space.lg), contentAlignment = Alignment.Center) {
            Box(Modifier.size(152.dp).clip(CircleShape).background(OC.negativeSurface), contentAlignment = Alignment.Center) {
                Box(Modifier.size(112.dp).clip(CircleShape).background(OC.negative)
                    .combinedClickable(onClick = { vm.toast("Press and hold to send SOS") }, onLongClick = { vm.sos() }), contentAlignment = Alignment.Center) {
                    Text("SOS", style = T.displaySmall.copy(color = Color.White))
                }
            }
        }
        Text("Press and hold to alert our team and your emergency contacts with your location.", style = T.defaultBody,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Alert("We’ll never ask you to pay a pro outside the app.", Tone.Info)
        Header("Emergency numbers")
        Column {
            ListRow("Police", subtitle = "15", icon = Icons.Outlined.LocalPolice, chevron = true, onClick = { dial("15") })
            ListRow("Rescue", subtitle = "1122", icon = Icons.Outlined.MedicalServices, chevron = true, onClick = { dial("1122") })
        }
        Header("How we keep you safe")
        Column {
            ListRow("Checked pros", subtitle = "CNIC and live selfie for every pro", icon = Icons.Outlined.VerifiedUser)
            ListRow("Live job tracking", subtitle = "Our team can see every active job", icon = Icons.Outlined.ShareLocation)
            ListRow("Protected payment", subtitle = "Money is held until you confirm", icon = Icons.Outlined.Lock)
        }
    }
}

@Composable
fun HelpPage(vm: AppViewModel) {
    var open by remember { mutableStateOf<Int?>(0) }
    val faq = listOf(
        "How does the price work?" to "You offer a price. Pros accept it or counter-offer, and you choose. The price is fixed before the job starts.",
        "When do I pay?" to "When you accept an offer, we hold the amount. The pro only gets it after you confirm the job is done.",
        "What if something goes wrong?" to "Tap Report a problem during the job. We keep the money on hold until our team has checked.",
        "How do pros get paid?" to "Earnings land in their wallet after the customer confirms. Our fee is 15%.",
    )
    Screen(title = "Help", subtitle = "Quick answers to common questions.", onBack = { vm.open(null) }, bottomPad = 32.dp) {
        Column {
            faq.forEachIndexed { i, (q, a) ->
                ListRow(q, right = { Icon(if (open == i) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null) }, onClick = { open = if (open == i) null else i })
                AnimatedVisibility(open == i) { Text(a, style = T.defaultBody, modifier = Modifier.padding(bottom = Space.md)) }
                Hr()
            }
        }
        Header("Still need help?")
        ListRow("Email us", subtitle = "support@oneclick.pk · we usually reply within a day", icon = Icons.Outlined.Email)
    }
}

@Composable
fun AboutPage(vm: AppViewModel) {
    Screen(onBack = { vm.open(null) }, bottomPad = 32.dp) {
        Column(Modifier.fillMaxWidth().padding(top = Space.xl), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Gap.betweenText)) {
            OneClickMark(96.dp)
            Spacer(Modifier.height(Space.sm))
            Text("One Click", style = T.screenTitle)
            Text("Home services in Lahore", style = T.largeBody)
            Badge("Version 0.4", Tone.Neutral)
        }
        Column {
            ListRow("Made in Lahore", subtitle = "Gulberg · DHA · Johar Town", icon = Icons.Outlined.Place)
            ListRow("Map data", subtitle = "© Google", icon = Icons.Outlined.Map)
        }
    }
}

// ---------------- Floating sheets ----------------
/** Open sheets; the tab bar hides while any is shown. */
object Overlays { var open by mutableIntStateOf(0) }

/**
 * Bottom sheet as a floating card: inset 8dp from the sides and bottom, 32dp corners on all sides,
 * handle floating 8dp above. Used for detours so you never lose your place.
 */
@Composable
fun OverlaySheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onDismiss)
    DisposableEffect(Unit) { Overlays.open++; onDispose { Overlays.open-- } }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(shown, enter = fadeIn(tween(300, easing = Motion.standard)), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(OC.scrim).tap(onDismiss))
        }
        AnimatedVisibility(shown, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(450, easing = Motion.enter)) { it }, exit = slideOutVertically(tween(300, easing = Motion.exit)) { it }) {
            Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(32.dp, 4.dp).clip(CircleShape).background(Color.White.copy(alpha = .7f)))
                Spacer(Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth().clip(Radius.sheet).background(OC.bg).tap {}.padding(horizontal = Space.lg, vertical = Space.xl),
                    verticalArrangement = Arrangement.spacedBy(Gap.component), content = content)
            }
        }
    }
}

/** Confirmation in a sheet: safe action is the primary button, destructive one is quiet red. */
@Composable
fun ConfirmDialog(title: String, text: String, keep: String, destructive: String, onKeep: () -> Unit, onDestructive: () -> Unit) {
    OverlaySheet(onKeep) {
        Text(title, style = T.subsectionTitle)
        Text(text, style = T.largeBody)
        Spacer(Modifier.height(Space.sm))
        PillButton(keep, onKeep)
        PillButton(destructive, onDestructive, kind = Btn.SecondaryNegative)
    }
}

private val dateFmt = SimpleDateFormat("d MMM, HH:mm", Locale.US)
fun fmtDate(ms: Long): String = dateFmt.format(Date(ms))
fun formatPhone(e164: String) = e164.removePrefix("+92").let { "0${it.take(3)} ${it.drop(3)}" }
fun categoryName(slug: String) = Backend.CATEGORIES.firstOrNull { it.slug == slug }?.name ?: slug.replaceFirstChar(Char::uppercase)
fun firstName(n: String) = n.substringBefore(" ")
