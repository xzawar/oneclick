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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            (fadeIn(tween(300, easing = Motion.emphasized)) + slideInHorizontally(tween(350, easing = Motion.emphasized)) { it / 8 }) togetherWith
                fadeOut(tween(200))
        }) { stage ->
            when (stage) {
                Stage.Onboarding -> Onboarding(vm)
                Stage.SignIn -> SignIn(vm, onBack = null)
                Stage.App -> Shell(s, vm)
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp, start = 16.dp, end = 16.dp)) {
            Snackbar(it, containerColor = OC.ink, contentColor = Color.White, shape = Radius.md)
        }
        if (s.launching) LaunchCurtain { vm.launchDone() }
    }
}

// ---------------- Onboarding ----------------
private data class Slide(val icon: ImageVector, val title: String, val body: String)
private val SLIDES = listOf(
    Slide(Icons.Outlined.VerifiedUser, "Verified pros near you", "Plumbers, electricians, mechanics and more across Lahore. Every pro is checked with CNIC and a live selfie."),
    Slide(Icons.Outlined.Sell, "You name the price", "Offer your fare. Pros accept it or send a counter-offer, and you choose who comes."),
    Slide(Icons.Outlined.Lock, "Pay when it’s done", "Your money is held safely and released only after you confirm the job is finished."),
)

@Composable
fun Onboarding(vm: AppViewModel) {
    val pager = rememberPagerState { SLIDES.size }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(OC.bg).statusBarsPadding().navigationBarsPadding().padding(Space.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Wordmark(32.dp)
            Spacer(Modifier.weight(1f))
            if (pager.currentPage < SLIDES.lastIndex) Text("Skip", style = T.label.copy(color = OC.muted), modifier = Modifier.tap { vm.finishOnboarding() }.padding(8.dp))
        }
        HorizontalPager(pager, Modifier.weight(1f)) { i ->
            val sl = SLIDES[i]
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Box(Modifier.fillMaxWidth().aspectRatio(1.15f).clip(Radius.xl).background(OC.card), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(168.dp).clip(CircleShape).background(OC.bg), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(112.dp).clip(CircleShape).background(OC.deep), contentAlignment = Alignment.Center) {
                            Icon(sl.icon, null, Modifier.size(52.dp), tint = OC.gold)
                        }
                    }
                }
                Spacer(Modifier.height(Space.xxl))
                Text(sl.title, style = T.h1)
                Spacer(Modifier.height(Space.md))
                Text(sl.body, style = T.body)
            }
        }
        Row(Modifier.padding(vertical = Space.xl), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(SLIDES.size) { i ->
                val w by animateDpAsState(if (i == pager.currentPage) 24.dp else 8.dp, tween(250, easing = Motion.emphasized), label = "dot")
                Box(Modifier.height(8.dp).width(w).clip(CircleShape).background(if (i == pager.currentPage) OC.deep else OC.cardStrong))
            }
        }
        val last = pager.currentPage == SLIDES.lastIndex
        PillButton(if (last) "Get started" else "Next", {
            if (last) vm.finishOnboarding() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
        }, Modifier.fillMaxWidth())
    }
}

// ---------------- Sign in ----------------
@Composable
fun SignIn(vm: AppViewModel, onBack: (() -> Unit)?) {
    val s by vm.state.collectAsState()
    var phone by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val known = remember(phone) { vm.lookup(phone) }
    val valid = Backend.normalizePhone(phone) != null
    if (onBack != null) BackHandler(onBack = onBack)
    Screen(title = if (onBack == null) "Sign in" else "Add account",
        subtitle = "Use your mobile number. We’ll keep you signed in on this phone.", onBack = onBack, bottomPad = 32.dp,
        right = { OneClickMark(56.dp) }) {
        Field(phone, { phone = it.filter { c -> c.isDigit() || c == ' ' }.take(14) }, "0300 1234567", label = "Mobile number", phone = true,
            error = if (phone.length >= 11 && !valid) "Enter a valid Pakistani mobile number" else null)
        AnimatedVisibility(valid) {
            if (known != null) Note("Welcome back, ${known.name}", gold = true)
            else Field(name, { name = it.take(40) }, "Full name", label = "Your name")
        }
        PillButton("Continue", { vm.signIn(phone, name) }, Modifier.fillMaxWidth(),
            enabled = valid && (known != null || name.trim().length >= 2), loading = s.busy)
        Card(title = "Demo accounts") {
            Text("Tap one to fill the number.", style = T.small)
            ListRow("Hamza Ali", subtitle = "Customer · 0300 1110001", left = { Avatar("Hamza Ali", 40.dp) }, onClick = { phone = "0300 1110001" })
            ListRow("Ahmad Khan", subtitle = "Provider · 0300 1110012", left = { Avatar("Ahmad Khan", 40.dp, ring = true) }, onClick = { phone = "0300 1110012" })
        }
        Text("By continuing you agree to the Terms and Privacy Policy. New accounts get Rs 1,000 welcome credit.",
            style = T.tiny, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

// ---------------- App shell: icon-only bottom tabs ----------------
@Composable
fun Shell(s: UiState, vm: AppViewModel) {
    val ctx = LocalContext.current
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= 33 && !isJvmTest) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS) }
    BackHandler(s.page != null) { vm.open(null) }
    BackHandler(s.page == null && s.tab != Tab.Home) { vm.tab(Tab.Home) }
    val provider = s.mode == ActiveMode.PROVIDER
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(s.tab, label = "tab", transitionSpec = {
            // Tabs drift a little and fade in on the emphasized easing; never a full slide.
            (fadeIn(tween(220, easing = Motion.emphasized)) + slideInVertically(tween(220, easing = Motion.emphasized)) { 16 }) togetherWith fadeOut(tween(120))
        }) { tab ->
            when (tab) {
                Tab.Home -> if (provider) ProviderHome(s, vm) else CustomerHome(s, vm)
                Tab.Bookings -> BookingsTab(s)
                Tab.Wallet -> WalletTab(s, vm)
                Tab.Profile -> ProfileTab(s, vm)
            }
        }
        AnimatedVisibility(Overlays.open == 0, Modifier.align(Alignment.BottomCenter), enter = fadeIn(tween(150)), exit = fadeOut(tween(100))) {
            TabBar(s.tab, provider, Modifier) { vm.tab(it) }
        }
        AnimatedVisibility(s.page != null, enter = slideInHorizontally(tween(350, easing = Motion.emphasized)) { it } + fadeIn(),
            exit = slideOutHorizontally(tween(300, easing = Motion.emphasized)) { it } + fadeOut()) {
            Box(Modifier.fillMaxSize().background(OC.bg)) {
                when (s.page) {
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
fun TabBar(current: Tab, provider: Boolean, modifier: Modifier, onTab: (Tab) -> Unit) {
    val items = listOf(
        Tab.Home to (if (provider) Icons.Outlined.Radar to "Requests" else Icons.Outlined.Map to "Home"),
        Tab.Bookings to (Icons.Outlined.EventNote to if (provider) "Jobs" else "Bookings"),
        Tab.Wallet to (Icons.Outlined.AccountBalanceWallet to if (provider) "Earnings" else "Wallet"),
        Tab.Profile to (Icons.Outlined.Person to "Profile"),
    )
    Column(modifier.fillMaxWidth().background(OC.bg)) {
        Hr()
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            items.forEach { (tab, info) ->
                val (icon, label) = info
                val on = tab == current
                Column(Modifier.size(64.dp).clip(CircleShape).tap { onTab(tab) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(icon, label, Modifier.size(26.dp), tint = if (on) OC.ink else OC.muted)
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.size(5.dp).clip(CircleShape).background(if (on) OC.gold else Color.Transparent))
                }
            }
        }
    }
}

// ---------------- Bookings / Jobs ----------------
@Composable
fun BookingsTab(s: UiState) {
    val provider = s.mode == ActiveMode.PROVIDER
    var active by remember { mutableStateOf(false) }
    val list = s.history.filter { if (active) it.status in Backend.ACTIVE else it.status !in Backend.ACTIVE }
    Screen(title = if (provider) "Jobs" else "Bookings", subtitle = if (provider) "Every job you’ve taken" else "Your home service bookings") {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Chip("Past", !active) { active = false }
            Chip("Active", active) { active = true }
        }
        val done = s.history.filter { it.status == "completed" }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            Stat("Completed", "${done.size}", Modifier.weight(1f), Icons.Outlined.TaskAlt)
            Stat(if (provider) "Earned" else "Spent", rs(done.sumOf { if (provider) it.net else it.fare }), Modifier.weight(1f), Icons.Outlined.Payments)
        }
        if (list.isEmpty()) Empty(if (active) "Nothing active" else "No bookings yet", if (provider) "Go online to get requests" else "Book a pro from the Home tab", Icons.Outlined.EventNote)
        else Card {
            list.forEachIndexed { i, b ->
                if (i > 0) Hr()
                ListRow("${categoryName(b.category)} · ${rs(b.fare)}", subtitle = listOf(b.otherName.ifBlank { "Searching…" }, b.description.ifBlank { null }).filterNotNull().joinToString(" · "),
                    icon = categoryIcon(b.category), right = { StatusPill(b.status) })
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
        Screen(title = if (provider) "Earnings" else "Wallet", subtitle = "JazzCash and EasyPaisa top-ups") {
            // Brand moment: the balance card is the one deep surface.
            Column(Modifier.fillMaxWidth().clip(Radius.xl).background(OC.deep).padding(Space.xl), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text("Available balance", style = T.small.copy(color = Color.White.copy(alpha = .7f)))
                Text(rs(w?.balance ?: 0), style = T.display.copy(color = Color.White, fontSize = 36.sp))
                if ((w?.escrow ?: 0) > 0) Text("On hold for your job: ${rs(w!!.escrow)}", style = T.small.copy(color = OC.gold))
                Spacer(Modifier.height(Space.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    PillButton("Top up", { topup = true }, Modifier.weight(1f), icon = Icons.Outlined.Add)
                    if (provider) PillButton("Withdraw", { withdraw = true }, Modifier.weight(1f), Btn.Soft, icon = Icons.Outlined.NorthEast)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                Stat(if (provider) "Total earned" else "On hold", rs(if (provider) w?.earned ?: 0 else w?.escrow ?: 0), Modifier.weight(1f))
                Stat("Transactions", "${s.txns.size}", Modifier.weight(1f))
            }
            if (provider && (w?.balance ?: 0) < Backend.MIN_BALANCE) Note("Top up to get requests — ${rs(Backend.MIN_BALANCE)} minimum", gold = true)
            Card(title = "History") {
                if (s.txns.isEmpty()) Text("Your payments will appear here", style = T.small)
                s.txns.forEachIndexed { i, t ->
                    if (i > 0) Hr()
                    ListRow(t.title, subtitle = fmtDate(t.createdAt) + (t.ref?.let { " · TID $it" } ?: "") + if (t.status == "pending") " · Processing" else "",
                        icon = if (t.incoming) Icons.Outlined.SouthWest else Icons.Outlined.NorthEast,
                        right = { Text((if (t.incoming) "+" else "−") + rs(t.amount), style = T.bodyStrong.copy(color = if (t.incoming) OC.green else OC.ink)) })
                }
            }
        }
        if (topup) OverlaySheet({ topup = false }) {
            var amount by remember { mutableStateOf("1000") }
            var tid by remember { mutableStateOf("") }
            Text("Top up wallet", style = T.title)
            Text("Send money to One Click on JazzCash or EasyPaisa (0300 0000000), then enter the transaction ID.", style = T.small)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) { listOf("500", "1000", "2000", "5000").forEach { a -> Chip("Rs $a", amount == a) { amount = a } } }
            Field(amount, { amount = it.filter(Char::isDigit).take(5) }, "Amount", label = "Amount", number = true, prefix = "Rs")
            Field(tid, { tid = it.uppercase().take(20) }, "e.g. JC-58210443", label = "Transaction ID (TID)")
            Text("Demo approves instantly. In the live app our team matches the TID with the statement.", style = T.tiny)
            PillButton("Add ${rs(amount.toLongOrNull() ?: 0)}", { vm.topup(amount.toLongOrNull() ?: 0, tid); topup = false },
                Modifier.fillMaxWidth(), enabled = tid.length >= 6 && (amount.toLongOrNull() ?: 0) >= 100)
        }
        if (withdraw) OverlaySheet({ withdraw = false }) {
            var amount by remember { mutableStateOf("500") }
            Text("Withdraw earnings", style = T.title)
            Text("Paid to your JazzCash within 24 hours.", style = T.small)
            Field(amount, { amount = it.filter(Char::isDigit).take(6) }, "Amount", label = "Amount", number = true, prefix = "Rs")
            PillButton("Withdraw ${rs(amount.toLongOrNull() ?: 0)}", { vm.withdraw(amount.toLongOrNull() ?: 0); withdraw = false }, Modifier.fillMaxWidth())
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
        Screen(title = me.user.name, subtitle = (if (p != null) "Provider" else "Customer") + " · " + formatPhone(me.user.phone),
            right = { Avatar(me.user.name, 64.dp, ring = p?.verified == true) }) {
            if (p != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    Stat("Rating", "★ ${p.rating}", Modifier.weight(1f))
                    Stat("Jobs", "${p.totalJobs}", Modifier.weight(1f))
                    Stat("Tier", p.tier.replaceFirstChar(Char::uppercase), Modifier.weight(1f))
                }
                Card(title = "Verification") {
                    ListRow("CNIC and live selfie", subtitle = "Checked by our team", icon = Icons.Outlined.VerifiedUser, right = { Badge("Verified", Tone.Good) })
                    Hr()
                    ListRow("Services", subtitle = p.categories.joinToString { categoryName(it) }, icon = Icons.Outlined.HomeRepairService)
                    Hr()
                    ListRow("Service area", subtitle = "${p.radiusKm} km around ${Backend.HOME_ADDRESS.substringBefore(",")}", icon = Icons.Outlined.Place)
                }
            }
            Card(title = "Account") {
                ListRow("Switch account", subtitle = "${s.accounts.size} on this phone", icon = Icons.Outlined.SwitchAccount, chevron = true, onClick = { switcher = true })
                Hr()
                ListRow("Add account", icon = Icons.Outlined.PersonAdd, chevron = true, onClick = { vm.open(Page.AddAccount) })
            }
            Card(title = "Settings") {
                ListRow("Notifications", subtitle = "Offers, job updates and requests", icon = Icons.Outlined.Notifications,
                    right = { Switch(s.notifications, { vm.setNotifications(it) }, colors = SwitchDefaults.colors(checkedTrackColor = OC.deep, checkedThumbColor = OC.gold)) })
                Hr()
                ListRow("Safety", subtitle = "SOS and emergency numbers", icon = Icons.Outlined.Shield, chevron = true, onClick = { vm.open(Page.Safety) })
                Hr()
                ListRow("Help and support", icon = Icons.Outlined.HelpOutline, chevron = true, onClick = { vm.open(Page.Help) })
                Hr()
                ListRow("About One Click", icon = Icons.Outlined.Info, chevron = true, onClick = { vm.open(Page.About) })
            }
            Card {
                ListRow("Sign out", icon = Icons.Outlined.Logout, titleColor = OC.red, onClick = { confirmOut = true })
                Hr()
                ListRow("Reset demo data", subtitle = "Clears bookings and wallets on this phone", icon = Icons.Outlined.RestartAlt, onClick = { vm.reset() })
            }
        }
        if (switcher) OverlaySheet({ switcher = false }) {
            Text("Switch account", style = T.title)
            Card {
                s.accounts.forEachIndexed { i, u ->
                    if (i > 0) Hr()
                    ListRow(u.name, subtitle = (if (u.role == "customer") "Customer" else "Provider") + " · " + formatPhone(u.phone),
                        left = { Avatar(u.name, 40.dp, ring = u.role != "customer") },
                        right = { if (u.id == me.user.id) Icon(Icons.Filled.CheckCircle, "Current", tint = OC.deep) },
                        onClick = { switcher = false; if (u.id != me.user.id) vm.switchAccount(u.id) })
                }
            }
            PillButton("Add account", { switcher = false; vm.open(Page.AddAccount) }, Modifier.fillMaxWidth(), Btn.Soft, icon = Icons.Outlined.PersonAdd)
        }
        if (confirmOut) ConfirmDialog("Sign out?", "You can sign back in with your mobile number.", "Stay signed in", "Sign out",
            { confirmOut = false }) { confirmOut = false; vm.signOut() }
    }
}

// ---------------- Safety / Help / About ----------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SafetyPage(vm: AppViewModel) {
    val ctx = LocalContext.current
    fun dial(n: String) = ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$n")))
    Screen(title = "Safety", subtitle = "Help is one press away", onBack = { vm.open(null) }, bottomPad = 32.dp) {
        Box(Modifier.fillMaxWidth().padding(vertical = Space.lg), contentAlignment = Alignment.Center) {
            Box(Modifier.size(148.dp).clip(CircleShape).background(OC.red.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(112.dp).clip(CircleShape).background(OC.red)
                    .combinedClickable(onClick = { vm.toast("Hold to send SOS") }, onLongClick = { vm.sos() }), contentAlignment = Alignment.Center) {
                    Text("SOS", style = T.h1.copy(color = Color.White))
                }
            }
        }
        Text("Hold the button to alert our team and your emergency contacts with your live location.", style = T.small, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Card {
            ListRow("Live location during jobs", subtitle = "Our team sees every active job on the map", icon = Icons.Outlined.ShareLocation)
            Hr()
            ListRow("Verified providers", subtitle = "CNIC and live selfie checked", icon = Icons.Outlined.VerifiedUser)
        }
        Card(title = "Emergency numbers") {
            ListRow("Police", subtitle = "15", icon = Icons.Outlined.LocalPolice, chevron = true, onClick = { dial("15") })
            Hr()
            ListRow("Rescue", subtitle = "1122", icon = Icons.Outlined.MedicalServices, chevron = true, onClick = { dial("1122") })
        }
    }
}

@Composable
fun HelpPage(vm: AppViewModel) {
    Screen(title = "Help", subtitle = "Answers to common questions", onBack = { vm.open(null) }, bottomPad = 32.dp) {
        listOf(
            "How does pricing work?" to "You offer a fare. Pros accept it or counter-offer, and you choose. The price is fixed before the job starts.",
            "When is my money taken?" to "When you accept an offer the amount is held. It goes to the pro only after you confirm the job is done.",
            "What if something goes wrong?" to "Tap Report a problem during the job. The payment stays on hold until our team reviews it.",
            "How do pros get paid?" to "Earnings arrive in the wallet after the customer confirms, minus a 15% service fee.",
        ).forEach { (q, a) -> Card { Text(q, style = T.bodyStrong); Text(a, style = T.small) } }
        Card(title = "Contact us") { ListRow("support@oneclick.pk", subtitle = "We reply within a day", icon = Icons.Outlined.Email) }
    }
}

@Composable
fun AboutPage(vm: AppViewModel) {
    Screen(onBack = { vm.open(null) }, bottomPad = 32.dp) {
        Column(Modifier.fillMaxWidth().padding(top = Space.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.md)) {
            OneClickMark(96.dp)
            Text("One Click", style = T.h1)
            Text("Home services in Lahore", style = T.body)
            Badge("Version 0.2", Tone.Gold)
        }
        Card {
            ListRow("Made in Lahore", subtitle = "Gulberg · DHA · Johar Town", icon = Icons.Outlined.Place)
            Hr()
            ListRow("Map data", subtitle = "© Google", icon = Icons.Outlined.Map)
        }
    }
}

// ---------------- Shared ----------------
/** Open sheets; the tab bar hides while any is shown so sheets sit on top of everything. */
object Overlays { var open by mutableIntStateOf(0) }

@Composable
fun OverlaySheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onDismiss)
    DisposableEffect(Unit) { Overlays.open++; onDispose { Overlays.open-- } }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(shown, enter = fadeIn(tween(200)), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(OC.scrim).tap(onDismiss))
        }
        AnimatedVisibility(shown, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(350, easing = Motion.emphasized)) { it }, exit = slideOutVertically { it }) {
            Column(Modifier.fillMaxWidth().clip(Radius.sheet).background(OC.bg).tap {}.imePadding().navigationBarsPadding()
                .padding(horizontal = Space.lg).padding(bottom = Space.lg), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                Box(Modifier.fillMaxWidth().padding(top = Space.md), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(36.dp, 4.dp).clip(CircleShape).background(OC.cardStrong))
                }
                content()
            }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, keep: String, destructive: String, onKeep: () -> Unit, onDestructive: () -> Unit) {
    AlertDialog(onDismissRequest = onKeep, containerColor = OC.bg, shape = Radius.xl,
        title = { Text(title, style = T.title) }, text = { Text(text, style = T.body) },
        confirmButton = { PillButton(keep, onKeep, height = 44.dp) },
        dismissButton = { PillButton(destructive, onDestructive, kind = Btn.Danger, height = 44.dp) })
}

private val dateFmt = SimpleDateFormat("MMM d, HH:mm", Locale.US)
fun fmtDate(ms: Long): String = dateFmt.format(Date(ms))
fun formatPhone(e164: String) = e164.removePrefix("+92").let { "0${it.take(3)} ${it.drop(3)}" }
fun categoryName(slug: String) = Backend.CATEGORIES.firstOrNull { it.slug == slug }?.name ?: slug.replaceFirstChar(Char::uppercase)
fun firstName(n: String) = n.substringBefore(" ")
