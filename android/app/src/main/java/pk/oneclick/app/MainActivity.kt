package pk.oneclick.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import java.io.File
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.osmdroid.util.GeoPoint

// ---- Design tokens (docs/DESIGN.md) ----
object C {
    val Navy = Color(0xFF1A1A2E); val DarkBlue = Color(0xFF0F3D6B); val Bright = Color(0xFF2196F3)
    val Green = Color(0xFF4CAF50); val Red = Color(0xFFF44336); val Orange = Color(0xFFFF9800)
    val Gray = Color(0xFF9E9E9E); val LtGray = Color(0xFFF5F5F5)
}
fun statusColor(s: String) = when (s) {
    "pending" -> C.Orange; "accepted" -> C.Bright; "en_route" -> C.DarkBlue; "arrived" -> C.DarkBlue
    "in_progress" -> C.Green; "completed" -> C.Navy; "disputed" -> C.Red; else -> C.Gray
}
fun hex(c: String) = Color(android.graphics.Color.parseColor(c))

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = C.DarkBlue, secondary = C.Bright, background = C.LtGray)) {
                val s by vm.state.collectAsState()
                val snack = remember { SnackbarHostState() }
                LaunchedEffect(s.message) { s.message?.let { snack.showSnackbar(it); vm.clearMessage() } }
                Box(Modifier.fillMaxSize().background(C.LtGray)) {
                    if (!s.loggedIn) LoginScreen(s, vm) else HomeScreen(s, vm)
                    SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp))
                }
            }
        }
    }
}

@Composable
fun LoginScreen(s: UiState, vm: AppViewModel) {
    Column(Modifier.fillMaxSize().background(C.Navy).systemBarsPadding().padding(24.dp)) {
        Spacer(Modifier.height(32.dp))
        Text("⚡ One Click", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text("Verified local pros in Lahore", color = Color.White.copy(alpha = .7f), fontSize = 15.sp)
        Spacer(Modifier.height(24.dp))
        Text("DEMO — sign in as:", color = Color.White, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        if (s.demoUsers.isEmpty()) {
            Button(onClick = { vm.loadDemoUsers() }) { Text("Retry loading demo users") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(s.demoUsers) { u ->
                Card(Modifier.fillMaxWidth().clickable(enabled = !s.busy) { vm.login(u) }, shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(u.name)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(u.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text(
                                when (u.userRole) { "customer" -> "Customer"; "both" -> "Customer + Provider"; else -> "Provider" } +
                                    (u.categories?.let { " · " + it.joinToString().replaceFirstChar(Char::uppercase) } ?: ""),
                                fontSize = 13.sp, color = C.Gray,
                            )
                        }
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
        }
    }
}

@Composable
fun Avatar(name: String, size: Int = 44, color: Color = C.DarkBlue) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(name.take(1), color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Pill(text: String, color: Color) {
    Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(color).padding(horizontal = 8.dp, vertical = 3.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(s: UiState, vm: AppViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    val provider = s.mode == ActiveMode.PROVIDER
    val canToggle = s.me?.user?.userRole == "both"
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("One Click", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = if (provider) C.Navy else C.DarkBlue, titleContentColor = Color.White),
                actions = {
                    if (canToggle) ModeToggle(s.mode) { vm.switchMode(it) }
                    else Pill(if (provider) "Provider" else "Customer", C.Bright)
                    Spacer(Modifier.width(12.dp))
                },
            )
        },
        bottomBar = {
            NavigationBar {
                listOf(
                    (if (provider) "Jobs" else "Map") to Icons.Default.Map, "Bookings" to Icons.Default.History,
                    "Wallet" to Icons.Default.AccountBalanceWallet, "Profile" to Icons.Default.Person,
                ).forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icon, label) }, label = { Text(label) })
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> if (provider) ProviderHome(s, vm) else CustomerMap(s, vm)
                1 -> HistoryScreen(s, vm)
                2 -> WalletScreen(s, vm)
                else -> ProfileScreen(s, vm)
            }
        }
    }
}

@Composable
fun ModeToggle(mode: ActiveMode, onChange: (ActiveMode) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(32.dp)).background(Color.White.copy(alpha = .15f)).padding(3.dp)) {
        ActiveMode.entries.forEach { m ->
            val on = m == mode
            Text(m.name.lowercase().replaceFirstChar(Char::uppercase), color = if (on) C.Navy else Color.White, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(32.dp)).background(if (on) Color.White else Color.Transparent)
                    .clickable { onChange(m) }.padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
}

// ---------------- Customer ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerMap(s: UiState, vm: AppViewModel) {
    var selected by remember { mutableStateOf<ProviderDto?>(null) }
    var booking by remember { mutableStateOf<ProviderDto?>(null) }
    val color = { cat: String -> s.categories.firstOrNull { it.slug == cat }?.pinColor ?: "#0F3D6B" }
    val a = s.active
    val pins = buildList {
        add(Pin("me", DEMO_LAT, DEMO_LNG, "You", "#F44336", 16))
        if (a != null && a.status in listOf("accepted", "en_route", "arrived", "in_progress")) {
            add(Pin("p", a.providerLat, a.providerLng, a.otherName, color(a.category), 28))
        } else s.providers.forEach { add(Pin(it.id, it.lat, it.lng, it.name, color(it.categories.first()))) }
    }
    Box(Modifier.fillMaxSize()) {
        OsmMap(pins, Modifier.fillMaxSize()) { id -> selected = s.providers.firstOrNull { it.id == id } }
        Column(Modifier.fillMaxWidth()) {
            LazyRow(Modifier.background(Color.White).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(s.selectedCategory == null, { vm.selectCategory(null) }, { Text("All") }) }
                items(s.categories) { c ->
                    FilterChip(s.selectedCategory == c.slug, { vm.selectCategory(c.slug) }, { Text(c.nameEn) },
                        leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(hex(c.pinColor))) })
                }
            }
            if (s.fallback && a == null) Text("No providers in range — showing nearest", Modifier.fillMaxWidth().background(C.Orange).padding(6.dp), color = Color.White, fontSize = 13.sp)
            if (a == null && s.providers.isEmpty()) Text("No providers online for this category", Modifier.fillMaxWidth().background(C.Gray).padding(6.dp), color = Color.White, fontSize = 13.sp)
        }
        if (a != null) ActiveBookingPanel(a, s, vm, Modifier.align(Alignment.BottomCenter))
    }
    selected?.let { p ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(p.name, 56, hex(color(p.categories.first())))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(p.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.categories.joinToString().replaceFirstChar(Char::uppercase), color = C.Gray)
                            if (p.isVerified) { Spacer(Modifier.width(6.dp)); Pill("VERIFIED", C.DarkBlue) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("⭐ ${p.ratingAvg} (${p.totalJobs} jobs)  ·  📍 ${p.distanceKm} km  ·  ${p.tier.uppercase()}")
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton({ vm.toast("In-app calling needs an Agora App ID (add key to enable)") }, Modifier.weight(1f)) {
                        Icon(Icons.Default.Call, null); Spacer(Modifier.width(6.dp)); Text("Call")
                    }
                    Button({ booking = p; selected = null }, Modifier.weight(1f), enabled = s.active == null) { Text("Book") }
                }
            }
        }
    }
    booking?.let { p -> BookDialog(p, s, onDismiss = { booking = null }) { d, f -> vm.book(p, d, f); booking = null } }
}

@Composable
fun BookDialog(p: ProviderDto, s: UiState, onDismiss: () -> Unit, onSend: (String, Long) -> Unit) {
    var desc by remember { mutableStateOf("") }
    var fare by remember { mutableStateOf("800") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Book ${p.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Service: ${p.categories.first().replaceFirstChar(Char::uppercase)} · Now", color = C.Gray)
                OutlinedTextField(desc, { desc = it }, label = { Text("What needs fixing?") })
                OutlinedTextField(fare, { fare = it.filter(Char::isDigit) }, label = { Text("Your offer (Rs.)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Text("Wallet: ${rs(s.me?.wallet?.balance ?: 0)} · held in escrow when accepted", fontSize = 13.sp, color = C.Gray)
            }
        },
        confirmButton = { Button({ onSend(desc, fare.toLongOrNull() ?: 0) }, enabled = (fare.toLongOrNull() ?: 0) >= 100) { Text("Send Request →") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActiveBookingPanel(a: BookingDto, s: UiState, vm: AppViewModel, modifier: Modifier = Modifier) {
    val customer = a.mySide == "customer"
    var dispute by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp == null) vm.toast("Photo proof is required to complete") else {
            val f = File(ctx.filesDir, "proof_${a.id}.jpg")
            f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
            vm.act({ complete(it, CompleteReq(f.absolutePath)) }, "Photo proof uploaded")
        }
    }
    Card(modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(a.otherName)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.otherName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("${a.otherRole} · ${a.category} · ${rs(a.agreedFare)}", fontSize = 13.sp, color = C.Gray)
                }
                Pill(a.status, statusColor(a.status))
            }
            val info = when {
                a.status == "pending" -> if (customer) "Waiting for provider… ${a.expiresInS ?: 0}s" else "New request"
                a.status == "accepted" -> if (customer) "Accepted! Rs. ${a.agreedFare} locked in escrow." else "Escrow locked. Start driving."
                a.status == "en_route" -> "On the way · ETA ${a.etaMin ?: 0} min 🕐"
                a.status == "arrived" -> "Provider has arrived"
                a.awaitingConfirmation -> if (customer) "Job marked done with photo proof. Confirm to release payment." else "Waiting for customer to confirm"
                a.status == "in_progress" -> "Job in progress (escrow locked)"
                a.status == "disputed" -> "Dispute raised — escrow frozen, admin reviewing"
                a.status == "cancelled" -> "Cancelled: ${a.cancelReason?.replace('_', ' ')}"
                a.needsRating -> "Job completed ✅ Rate ${a.otherName}"
                else -> ""
            }
            Text(info)
            val photo = a.completionPhotoUrl?.takeIf { a.awaitingConfirmation || a.needsRating }
            if (photo != null) {
                val bmp = remember(photo) { if (File(photo).exists()) BitmapFactory.decodeFile(photo) else null }
                if (bmp != null) Image(bmp.asImageBitmap(), "Photo proof", Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                else Text("📷 Photo proof attached by provider", fontSize = 13.sp, color = C.Gray)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (customer) {
                    if (a.status in listOf("pending", "accepted", "en_route")) OutlinedButton({ vm.act({ cancel(it) }, "Booking cancelled") }) { Text("Cancel") }
                    if (a.awaitingConfirmation) Button({ vm.act({ confirm(it) }, "Payment released to provider") }, colors = ButtonDefaults.buttonColors(C.Green)) { Text("Confirm complete") }
                    if (a.status in listOf("arrived", "in_progress")) TextButton({ dispute = true }) { Text("Dispute", color = C.Red) }
                } else {
                    when {
                        a.status == "accepted" -> Button({ vm.act({ status(it, StatusReq("en_route")) }) }) { Text("Start driving") }
                        a.status == "en_route" -> Button({ vm.act({ status(it, StatusReq("arrived")) }) }) { Text("Mark arrived") }
                        a.status == "arrived" -> Button({ vm.act({ status(it, StatusReq("in_progress")) }) }) { Text("Start job") }
                        a.status == "in_progress" && !a.awaitingConfirmation ->
                            Button({
                                try { camera.launch(null) } catch (e: Exception) { vm.toast("No camera app found") }
                            }, colors = ButtonDefaults.buttonColors(C.Green)) {
                                Icon(Icons.Default.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text("Photo proof & complete")
                            }
                    }
                    if (a.status in listOf("accepted", "en_route")) OutlinedButton({ vm.act({ cancel(it) }, "Booking cancelled") }) { Text("Cancel") }
                }
                Spacer(Modifier.weight(1f))
                if (a.status in listOf("accepted", "en_route", "arrived", "in_progress", "disputed")) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(C.Red).combinedClickable(
                        onClick = { vm.toast("Long-press SOS to send alert") }, onLongClick = { vm.sos() }),
                        contentAlignment = Alignment.Center) { Text("SOS", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
            if (a.needsRating) Row {
                (1..5).forEach { n -> IconButton({ vm.act({ rate(it, RateReq(n)) }, "Thanks for rating!") }) { Icon(Icons.Default.Star, "$n stars", tint = C.Orange) } }
            }
        }
    }
    if (dispute) {
        var reason by remember { mutableStateOf("Overcharged") }
        AlertDialog(onDismissRequest = { dispute = false }, title = { Text("Raise a Dispute") },
            text = {
                Column {
                    listOf("Overcharged", "Poor workmanship", "No-show").forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(reason == r, { reason = r }); Text(r) }
                    }
                    Text("⚠ Escrow will freeze until admin reviews", color = C.Orange, fontSize = 13.sp)
                }
            },
            confirmButton = { Button({ vm.act({ dispute(it, DisputeReq(reason)) }, "Dispute submitted"); dispute = false }, colors = ButtonDefaults.buttonColors(C.Red)) { Text("Submit Dispute") } },
            dismissButton = { TextButton({ dispute = false }) { Text("Back") } })
    }
}

// ---------------- Provider ----------------
@Composable
fun ProviderHome(s: UiState, vm: AppViewModel) {
    val p = s.me?.provider
    val a = s.active
    Box(Modifier.fillMaxSize()) {
        val pins = if (a != null && a.status != "pending")
            listOf(Pin("me", a.providerLat, a.providerLng, "You", "#2196F3", 26), Pin("c", a.customerLat, a.customerLng, a.otherName, "#F44336", 18))
        else emptyList()
        OsmMap(pins, Modifier.fillMaxSize())
        Card(Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(if (p?.isOnline == true) C.Green else C.Gray))
                        Spacer(Modifier.width(6.dp))
                        Text(if (p?.isOnJob == true) "ON JOB" else if (p?.isOnline == true) "ONLINE" else "OFFLINE", fontWeight = FontWeight.Bold)
                    }
                    Text("Wallet: ${rs(s.me?.wallet?.balance ?: 0)}", fontSize = 13.sp)
                    if ((s.me?.wallet?.balance ?: 0) < 200) Text("⚠ Wallet low. Top up Rs. 200+ to stay visible.", color = C.Orange, fontSize = 12.sp)
                }
                Switch(checked = p?.isOnline == true, onCheckedChange = { vm.setOnline(it) }, enabled = p?.isOnJob != true)
            }
        }
        if (a != null && a.status != "pending") ActiveBookingPanel(a, s, vm, Modifier.align(Alignment.BottomCenter))
    }
    if (a?.status == "pending") JobRequestPopup(a, vm)
}

@Composable
fun JobRequestPopup(a: BookingDto, vm: AppViewModel) {
    val left = (a.expiresInS ?: 0).coerceIn(0, 30)
    val barColor = if (left > 10) C.Green else if (left > 5) C.Orange else C.Red
    AlertDialog(onDismissRequest = {}, title = { Text("New Job Request") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(progress = { left / 30f }, Modifier.fillMaxWidth(), color = barColor)
                Text("${left}s", color = barColor)
                Text("${a.otherName} · ${a.category.replaceFirstChar(Char::uppercase)}", fontWeight = FontWeight.SemiBold)
                if (a.description.isNotBlank()) Text(a.description)
                Text("Offer: ${rs(a.agreedFare)}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = { Button({ vm.act({ accept(it) }, "Booking accepted! Escrow locked.") }, colors = ButtonDefaults.buttonColors(C.Green)) { Text("Accept") } },
        dismissButton = { OutlinedButton({ vm.act({ reject(it) }) }) { Text("Reject") } })
}

// ---------------- Shared tabs ----------------
@Composable
fun WalletScreen(s: UiState, vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.loadWallet() }
    var topup by remember { mutableStateOf(false) }
    val w = s.wallet
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(C.DarkBlue), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text("My Wallet", color = Color.White.copy(alpha = .8f))
                    Text(rs(w?.wallet?.balance ?: 0), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text("In escrow: ${rs(w?.wallet?.escrowHeld ?: 0)}  ·  Earned: ${rs(w?.wallet?.totalEarned ?: 0)}", color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ topup = true }, colors = ButtonDefaults.buttonColors(C.Bright)) { Text("+ Top Up") }
                        OutlinedButton({ vm.toast("Withdrawals are processed by admin (not in demo)") }) { Text("↑ Withdraw", color = Color.White) }
                    }
                }
            }
        }
        item { Text("Transaction History", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) }
        items(w?.transactions ?: emptyList()) { t ->
            Card(shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.type.replace('_', ' ').replaceFirstChar(Char::uppercase), fontWeight = FontWeight.Medium)
                        Text(t.createdAt.take(16).replace('T', ' ') + (t.externalRef?.let { " · TID $it" } ?: "") + " · ${t.status}", fontSize = 12.sp, color = C.Gray)
                    }
                    Text((if (t.incoming) "+" else "−") + "%,d".format(t.amount), color = if (t.incoming) C.Green else C.Red, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (topup) {
        var amount by remember { mutableStateOf("1000") }
        var tid by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { topup = false }, title = { Text("Top Up Wallet") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. Send money to One Click JazzCash/EasyPaisa 0300-0000000\n2. Enter amount + transaction ID (TID)", fontSize = 13.sp)
                    OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("Amount (Rs.)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(tid, { tid = it.uppercase() }, label = { Text("TID") })
                    Text("Demo: auto-approved. Live: admin matches TID on statement.", fontSize = 12.sp, color = C.Gray)
                }
            },
            confirmButton = { Button({ vm.topup(amount.toLongOrNull() ?: 0, tid); topup = false }, enabled = tid.length >= 6) { Text("Submit") } },
            dismissButton = { TextButton({ topup = false }) { Text("Cancel") } })
    }
}

@Composable
fun HistoryScreen(s: UiState, vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.loadHistory() }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Booking History", fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
        if (s.history.isEmpty()) item { Text("No bookings yet", color = C.Gray) }
        items(s.history) { b ->
            Card(shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${b.otherName} · ${b.category}", fontWeight = FontWeight.Medium)
                        Text(b.createdAt.take(16).replace('T', ' ') + " · " + rs(b.agreedFare), fontSize = 12.sp, color = C.Gray)
                    }
                    Pill(b.status, statusColor(b.status))
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(s: UiState, vm: AppViewModel) {
    val me = s.me ?: return
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Avatar(me.user.name, 72)
        Text(me.user.name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(me.user.phone ?: "", color = C.Gray)
        Text("Role: ${me.user.userRole}")
        me.provider?.let { p ->
            Text("${p.categories.joinToString()} · ${p.tier.uppercase()} · ⭐ ${p.ratingAvg} · ${p.totalJobs} jobs")
            if (p.isVerified) Pill("VERIFIED (CNIC + selfie)", C.DarkBlue)
        }
        Spacer(Modifier.weight(1f))
        Text("Demo build · data stored on this phone · other users simulated", fontSize = 12.sp, color = C.Gray)
        TextButton({ vm.resetDemo() }, Modifier.fillMaxWidth()) { Text("Reset demo data", color = C.Red) }
        OutlinedButton({ vm.logout() }, Modifier.fillMaxWidth()) { Text("Log out / switch demo user") }
    }
}
