package pk.oneclick.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { OneClickTheme { App(vm) } }
    }
}

private val navEasing = DnaMotion.standard

@Composable
fun App(vm: AppViewModel) {
    val s by vm.state.collectAsState()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(s.message) { s.message?.let { vm.clearMessage(); snack.showSnackbar(it) } }
    Box(Modifier.fillMaxSize().background(dna.bgPrimary)) {
        if (!s.signedIn) Welcome(vm) else {
            val drawer = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            BackHandler(drawer.isOpen) { scope.launch { drawer.close() } }
            BackHandler(!drawer.isOpen && s.page != Page.Home) { vm.open(Page.Home) }
            ModalNavigationDrawer(drawerState = drawer, gesturesEnabled = drawer.isOpen, drawerContent = {
                Menu(s, vm) { scope.launch { drawer.close() } }
            }) {
                AnimatedContent(s.page, label = "page", transitionSpec = {
                    // Push 100% → 0 while the old screen recedes −25% and fades (350ms).
                    if (targetState != Page.Home)
                        slideInHorizontally(tween(DnaMotion.nav, easing = navEasing)) { it } togetherWith
                            (slideOutHorizontally(tween(DnaMotion.nav, easing = navEasing)) { -it / 4 } + fadeOut(tween(DnaMotion.nav)))
                    else
                        (slideInHorizontally(tween(DnaMotion.nav, easing = navEasing)) { -it / 4 } + fadeIn(tween(DnaMotion.nav))) togetherWith
                            slideOutHorizontally(tween(DnaMotion.nav, easing = navEasing)) { it }
                }) { page ->
                    when (page) {
                        Page.Home -> if (s.mode == ActiveMode.PROVIDER) ProviderHome(s, vm) { scope.launch { drawer.open() } }
                                     else CustomerHome(s, vm) { scope.launch { drawer.open() } }
                        Page.Wallet -> WalletPage(s, vm)
                        Page.History -> HistoryPage(s, vm)
                        Page.Safety -> SafetyPage(vm)
                    }
                }
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 72.dp, start = 16.dp, end = 16.dp)) {
            Snackbar(it, containerColor = dna.control, contentColor = dna.onControl, shape = DnaShape.s)
        }
    }
}

// ---------------- Welcome ----------------
@Composable
fun Welcome(vm: AppViewModel) {
    val c = dna
    Column(Modifier.fillMaxSize().background(c.bgPrimary).statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
        Spacer(Modifier.height(48.dp))
        Box(Modifier.size(64.dp).clip(CircleShape).background(c.brand), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Bolt, null, Modifier.size(32.dp), tint = c.onBrand)
        }
        Spacer(Modifier.height(24.dp))
        Text("One Click", style = DnaType.heading1, color = c.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text("Trusted home pros in Lahore", style = DnaType.bodyParagraph, color = c.textSecondary)
        Spacer(Modifier.weight(1f))
        SectionTitle("Choose an account")
        Spacer(Modifier.height(8.dp))
        vm.accounts.forEach { u ->
            Cell(u.name, subtitle = if (u.role == "customer") "Customer · Book home services" else "Provider · Plumber, Electrician",
                lead = { Avatar(u.name, 48.dp) }, titleStyle = DnaType.heading4,
                end = { Icon(Icons.Outlined.ChevronRight, null, tint = c.textSecondary) }, onClick = { vm.signIn(u) })
        }
        Spacer(Modifier.height(8.dp))
        Helper("Demo accounts. Data stays on this phone.")
    }
}

// ---------------- Menu ----------------
@Composable
fun Menu(s: UiState, vm: AppViewModel, close: () -> Unit) {
    val c = dna
    val me = s.me ?: return
    val provider = s.mode == ActiveMode.PROVIDER
    ModalDrawerSheet(drawerContainerColor = c.surface, drawerShape = DnaShape.l.copy(topStart = androidx.compose.foundation.shape.CornerSize(0.dp), bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp))) {
        Column(Modifier.fillMaxHeight().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
            Avatar(me.user.name, 56.dp)
            Spacer(Modifier.height(12.dp))
            Text(me.user.name, style = DnaType.heading3, color = c.textPrimary)
            Helper(if (provider) "★ ${me.provider?.rating} · ${me.provider?.totalJobs} jobs · Verified" else "Customer · ${Backend.HOME_ADDRESS}")
            Spacer(Modifier.height(16.dp))
            fun go(p: Page) { vm.open(p); close() }
            Cell(if (provider) "Requests" else "Book a pro", icon = Icons.Outlined.Map, onClick = { go(Page.Home) })
            Cell("Wallet", subtitle = rs(me.wallet.balance), icon = Icons.Outlined.AccountBalanceWallet, onClick = { go(Page.Wallet) })
            Cell(if (provider) "My jobs" else "My bookings", icon = Icons.Outlined.History, onClick = { go(Page.History) })
            Cell("Safety", icon = Icons.Outlined.Shield, onClick = { go(Page.Safety) })
            Spacer(Modifier.weight(1f))
            Cell("Reset demo data", icon = Icons.Outlined.RestartAlt, onClick = { close(); vm.reset() })
            Cell("Sign out", icon = Icons.Outlined.Logout, onClick = { close(); vm.signOut() })
        }
    }
}

@Composable
fun PageHeader(title: String, vm: AppViewModel) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundButton(Icons.Outlined.ArrowBack, "Back", { vm.open(Page.Home) }, size = 48.dp, floating = false)
        Spacer(Modifier.width(12.dp))
        Text(title, style = DnaType.heading2, color = dna.textPrimary)
    }
}

private val dateFmt = SimpleDateFormat("MMM d, HH:mm", Locale.US)
fun fmtDate(ms: Long): String = dateFmt.format(Date(ms))

// ---------------- Wallet ----------------
@Composable
fun WalletPage(s: UiState, vm: AppViewModel) {
    val c = dna
    var topup by remember { mutableStateOf(false) }
    var withdraw by remember { mutableStateOf(false) }
    val w = s.wallet ?: s.me?.wallet
    val provider = s.mode == ActiveMode.PROVIDER
    Box(Modifier.fillMaxSize().background(c.bgPrimary)) {
        Column {
            PageHeader("Wallet", vm)
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    Text("Balance", style = DnaType.compactBody, color = c.textSecondary)
                    Spacer(Modifier.height(4.dp))
                    Text(rs(w?.balance ?: 0), style = DnaType.heading1, color = c.textPrimary)
                    if ((w?.escrow ?: 0) > 0) Helper("On hold for your job: ${rs(w!!.escrow)}", Modifier.padding(top = 4.dp))
                    if (provider) Helper("Earned with One Click: ${rs(w?.earned ?: 0)}", Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(16.dp))
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DnaButton("Top up", { topup = true }, Modifier.weight(1f), size = BtnSize.M)
                        if (provider) DnaButton("Withdraw", { withdraw = true }, Modifier.weight(1f), BtnKind.Secondary, BtnSize.M)
                    }
                    Spacer(Modifier.height(12.dp))
                }
                if (provider && (w?.balance ?: 0) < Backend.MIN_BALANCE) item {
                    InlineAlert("Top up to get requests — ${rs(Backend.MIN_BALANCE)} minimum", AlertKind.Warning); Spacer(Modifier.height(8.dp))
                }
                item { Text("History", style = DnaType.heading4, color = c.textPrimary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) }
                if (s.txns.isEmpty()) item { Helper("Your payments will appear here") }
                items(s.txns) { t ->
                    Cell(t.title, subtitle = fmtDate(t.createdAt) + (t.ref?.let { " · TID $it" } ?: "") + if (t.status == "pending") " · Processing" else "",
                        icon = if (t.incoming) Icons.Outlined.SouthWest else Icons.Outlined.NorthEast,
                        end = { Text((if (t.incoming) "+" else "−") + rs(t.amount), style = DnaType.heading4, color = if (t.incoming) c.success else c.textPrimary) })
                }
            }
        }
        if (topup) OverlaySheet({ topup = false }) {
            var amount by remember { mutableStateOf("1000") }
            var tid by remember { mutableStateOf("") }
            SectionTitle("Top up wallet")
            Helper("Send money to One Click on JazzCash or EasyPaisa: 0300 0000000. Then enter the TID.")
            Spacer(Modifier.height(16.dp))
            DnaField(amount, { amount = it.filter(Char::isDigit).take(5) }, "Amount", label = "Amount, Rs", number = true)
            Spacer(Modifier.height(12.dp))
            DnaField(tid, { tid = it.uppercase().take(20) }, "e.g. JC-58210443", label = "Transaction ID (TID)")
            Spacer(Modifier.height(8.dp))
            Helper("Demo approves instantly. In the live app our team matches the TID with the statement.")
            Spacer(Modifier.height(16.dp))
            DnaButton("Add ${rs(amount.toLongOrNull() ?: 0)}", { vm.topup(amount.toLongOrNull() ?: 0, tid); topup = false },
                Modifier.fillMaxWidth(), enabled = tid.length >= 6 && (amount.toLongOrNull() ?: 0) >= 100)
        }
        if (withdraw) OverlaySheet({ withdraw = false }) {
            var amount by remember { mutableStateOf("500") }
            SectionTitle("Withdraw earnings")
            Helper("Paid to your JazzCash within 24 hours")
            Spacer(Modifier.height(16.dp))
            DnaField(amount, { amount = it.filter(Char::isDigit).take(6) }, "Amount", label = "Amount, Rs", number = true)
            Spacer(Modifier.height(16.dp))
            DnaButton("Withdraw ${rs(amount.toLongOrNull() ?: 0)}", { vm.withdraw(amount.toLongOrNull() ?: 0); withdraw = false }, Modifier.fillMaxWidth())
        }
    }
}

// ---------------- History ----------------
@Composable
fun HistoryPage(s: UiState, vm: AppViewModel) {
    val c = dna
    Column(Modifier.fillMaxSize().background(c.bgPrimary)) {
        PageHeader(if (s.mode == ActiveMode.PROVIDER) "My jobs" else "My bookings", vm)
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            if (s.history.isEmpty()) item { Helper("Your bookings will appear here") }
            items(s.history) { b ->
                Cell("${categoryName(b.category)} · ${rs(b.fare)}",
                    subtitle = listOfNotNull(b.otherName.ifBlank { null }, b.description.ifBlank { "No details" }).joinToString(" · "),
                    icon = categoryIcon(b.category), end = { StatusTag(b.status) })
            }
        }
    }
}

@Composable
fun StatusTag(status: String) {
    val c = dna
    val (text, color) = when (status) {
        "completed" -> "Done" to c.success; "cancelled" -> "Cancelled" to c.textSecondary
        "disputed" -> "Reported" to c.error; "pending" -> "Searching" to c.accent
        else -> "Active" to c.accent
    }
    Tag(text, color)
}

// ---------------- Safety ----------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SafetyPage(vm: AppViewModel) {
    val c = dna
    val ctx = LocalContext.current
    fun dial(n: String) = ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$n")))
    Column(Modifier.fillMaxSize().background(c.bgPrimary)) {
        PageHeader("Safety", vm)
        Column(Modifier.padding(16.dp)) {
            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(120.dp).clip(CircleShape).background(c.error)
                    .combinedClickable(onClick = { vm.toast("Hold to send SOS") }, onLongClick = { vm.sos() }),
                    contentAlignment = Alignment.Center) {
                    Text("SOS", style = DnaType.heading2, color = Color.White)
                }
            }
            Text("Hold the button to alert our team and your emergency contacts", style = DnaType.compactBody,
                color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
            Cell("Live location during jobs", subtitle = "Our team sees every active job on the map", icon = Icons.Outlined.ShareLocation)
            Cell("Verified providers", subtitle = "CNIC and live selfie checked by our team", icon = Icons.Outlined.VerifiedUser)
            Cell("Call police — 15", icon = Icons.Outlined.LocalPolice, onClick = { dial("15") },
                end = { Icon(Icons.Outlined.ChevronRight, null, tint = c.textSecondary) })
            Cell("Call Rescue — 1122", icon = Icons.Outlined.MedicalServices, onClick = { dial("1122") },
                end = { Icon(Icons.Outlined.ChevronRight, null, tint = c.textSecondary) })
        }
    }
}

// ---------------- Shared overlays ----------------
@Composable
fun OverlaySheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onDismiss)
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(shown, enter = fadeIn(tween(DnaMotion.fade)), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .5f)).clickableNoIndication(onDismiss))
        }
        AnimatedVisibility(shown, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(DnaMotion.popup, easing = DnaMotion.standard)) { it }, exit = slideOutVertically { it }) {
            Sheet(Modifier.imePadding(), content = content)
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, keep: String, destructive: String, onKeep: () -> Unit, onDestructive: () -> Unit) {
    AlertDialog(onDismissRequest = onKeep, containerColor = dna.surface, shape = DnaShape.l,
        title = { Text(title, style = DnaType.heading3, color = dna.textPrimary) },
        text = { Text(text, style = DnaType.mainBody, color = dna.textSecondary) },
        confirmButton = { DnaButton(keep, onKeep, size = BtnSize.M) },
        dismissButton = { DnaButton(destructive, onDestructive, kind = BtnKind.ErrorLow, size = BtnSize.M) })
}

fun categoryName(slug: String) = Backend.CATEGORIES.firstOrNull { it.slug == slug }?.name ?: slug.replaceFirstChar(Char::uppercase)
fun categoryIcon(slug: String) = if (slug == "electrician") Icons.Outlined.ElectricalServices else Icons.Outlined.Plumbing
fun firstName(n: String) = n.substringBefore(" ")

@Composable
fun RatingLine(rating: Double, count: Int, extra: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.StarRate, null, Modifier.size(16.dp), tint = dna.textPrimary)
        Spacer(Modifier.width(2.dp))
        Text("$rating", style = DnaType.compactBody.copy(fontWeight = FontWeight.SemiBold), color = dna.textPrimary)
        Text(" ($count)" + (extra?.let { " · $it" } ?: ""), style = DnaType.compactBody, color = dna.textSecondary)
    }
}
