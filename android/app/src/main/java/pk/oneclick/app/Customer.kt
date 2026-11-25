package pk.oneclick.app

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

val HOME = LatLng(Backend.HOME_LAT, Backend.HOME_LNG)
val ON_JOB = listOf("accepted", "en_route", "arrived", "in_progress", "disputed")

fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
fun recommended(slug: String?) = Backend.CATEGORIES.firstOrNull { it.slug == slug }?.recommendedFare ?: 800L

/** Top of every home feed: your avatar (opens Profile), greeting, safety shortcut. */
@Composable
fun HomeTop(name: String, vm: AppViewModel, ring: Boolean = false) {
    Row(Modifier.fillMaxWidth().height(72.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clip(CircleShape).tap { vm.tab(Tab.Profile) }) { Avatar(name, 40.dp, ring = ring) }
        Spacer(Modifier.weight(1f))
        IconCircle(Icons.Outlined.Shield, "Safety", { vm.open(Page.Safety) })
    }
    Text("${greeting()},\n${firstName(name)}", style = T.screenTitle)
}

/** Brand-moment card in the forest theme (emerald surface, gold numbers). */
@Composable
fun ForestCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(Radius.xl).background(OC.forest).padding(Space.xl),
        verticalArrangement = Arrangement.spacedBy(Space.sm), content = content)
}

// ======================= Customer home: a calm, dismissible feed =======================
@Composable
fun CustomerHome(s: UiState, vm: AppViewModel) {
    val me = s.me ?: return
    val job = s.job
    Column(Modifier.fillMaxSize().background(OC.bg).verticalScroll(rememberScrollState()).statusBarsPadding()
        .padding(horizontal = Gap.gutter).padding(bottom = Space.xxl), verticalArrangement = Arrangement.spacedBy(Gap.component)) {
        HomeTop(me.user.name, vm)
        ForestCard {
            Text("Wallet balance", style = T.groupTitle.copy(color = OC.forestMuted))
            Text(rs(me.wallet.balance), style = T.number.copy(color = OC.gold))
            if (me.wallet.escrow > 0) Text("${rs(me.wallet.escrow)} held for your booking", style = T.defaultBody.copy(color = OC.forestText))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ActionButton(Icons.Outlined.HomeRepairService, "Book a pro", { vm.startBooking(null) }, highlighted = true)
            ActionButton(Icons.Outlined.Add, "Add money", { vm.tab(Tab.Wallet) })
            ActionButton(Icons.Outlined.EventNote, "Bookings", { vm.tab(Tab.Bookings) })
            ActionButton(Icons.Outlined.HelpOutline, "Help", { vm.open(Page.Help) })
        }
        job?.let { ActiveBookingCard(it) { vm.open(Page.Track) } }

        Header("What needs fixing?", Modifier.padding(top = Space.sm))
        Backend.CATEGORIES.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Gap.cards)) {
                row.forEach { c ->
                    Surface(onClick = { vm.startBooking(c.slug) }, shape = Radius.md, color = OC.card, modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                            Avatar("", 40.dp, icon = categoryIcon(c.slug), tint = OC.bg)
                            Column {
                                Text(c.name, style = T.bodyBold, maxLines = 1)
                                Text("From ${rs(c.recommendedFare)}", style = T.defaultBody.copy(fontSize = 13.sp), maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        // Activation checklist: shows what's left, dismissible, disappears when done.
        val tasks = listOf(
            "Add money to your wallet" to (me.wallet.balance > 0),
            "Book your first pro" to s.history.any { it.isCustomer && it.status == "completed" },
            "See how we keep you safe" to ("task-safety" in s.dismissed),
        )
        val done = tasks.count { it.second }
        if ("setup" !in s.dismissed && done < tasks.size) Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Get set up", style = T.bodyTitle)
                    Text("$done of ${tasks.size} done", style = T.defaultBody)
                }
                Icon(Icons.Outlined.Close, "Dismiss", Modifier.size(20.dp).tap { vm.dismiss("setup") }, tint = OC.muted)
            }
            ProgressLine(done / tasks.size.toFloat())
            tasks.forEachIndexed { i, (label, ok) ->
                Row(Modifier.fillMaxWidth().clip(Radius.sm).tap {
                    when (i) { 0 -> vm.tab(Tab.Wallet); 1 -> vm.startBooking(null); else -> vm.open(Page.Safety) }
                }.padding(vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                    if (ok) Icon(Icons.Filled.CheckCircle, "Done", Modifier.size(24.dp), tint = OC.positive)
                    else Box(Modifier.size(24.dp).padding(2.dp).clip(CircleShape).background(OC.bg))
                    Spacer(Modifier.width(Space.md))
                    Text(label, style = if (ok) T.largeBody.copy(color = OC.muted) else T.largeBodyBold, modifier = Modifier.weight(1f))
                    if (!ok) Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp))
                }
            }
        }
        if ("nudge-verified" !in s.dismissed) Alert("Every pro is checked with their CNIC and a live selfie.", Tone.Info,
            action = "How we keep you safe", onAction = { vm.open(Page.Safety) }, onDismiss = { vm.dismiss("nudge-verified") })

        val recent = s.history.filter { it.status == "completed" }.take(3)
        if (recent.isNotEmpty()) {
            Header("Recent", action = "See all") { vm.tab(Tab.Bookings) }
            Column {
                recent.forEach { b ->
                    ListRow("${categoryName(b.category)} · ${b.otherName}", subtitle = b.description.ifBlank { "No details" },
                        icon = categoryIcon(b.category), right = { Text(rs(b.fare), style = T.largeBodyBold) })
                }
            }
        }
    }
}

/** Home card for the booking in progress; tap to open the tracker. */
@Composable
fun ActiveBookingCard(job: JobView, onOpen: () -> Unit) {
    val name = firstName(job.otherName)
    val (title, line) = if (!job.isCustomer) when {
        job.needsRating -> "You’ve been paid" to "${rs(job.net)} is in your wallet"
        job.awaitingConfirmation -> "Waiting for $name to confirm" to "You’ll be paid as soon as they do"
        job.status == "accepted" -> "Head to $name" to "Tap to start your trip"
        job.status == "en_route" -> "Driving to $name" to "About ${job.etaMin ?: 1} min to go"
        job.status == "arrived" -> "You’ve arrived" to "Start the job when you’re ready"
        job.status == "in_progress" -> "Job in progress" to "Take a photo when you finish"
        else -> "$name reported a problem" to "Payment is on hold for now"
    } else when {
        job.status == "pending" -> "Finding you a ${categoryName(job.category).lowercase()}" to
            if (job.offers.isEmpty()) "Pros nearby can see your request" else "${job.offers.size} offer${if (job.offers.size > 1) "s" else ""} waiting for you"
        job.status == "cancelled" -> "Request cancelled" to if (job.cancelReason == "no_offers") "No one answered. Try a higher price." else "Your money is back in your wallet."
        job.needsRating -> (if (job.isCustomer) "Job done" else "You’ve been paid") to "Tell us how it went"
        job.awaitingConfirmation -> "$name has finished" to "Check the work and pay"
        job.status == "en_route" -> "$name is on the way" to "Should arrive in about ${job.etaMin ?: 1} min"
        job.status == "accepted" -> "$name is getting ready" to "Should arrive in about ${job.etaMin ?: 5} min"
        job.status == "arrived" -> "$name has arrived" to "Meet them at the door"
        job.status == "in_progress" -> "Job in progress" to "${categoryName(job.category)} · ${rs(job.fare)} held safely"
        else -> "We’re checking your report" to "Your money stays on hold for now"
    }
    Surface(onClick = onOpen, shape = Radius.md, color = OC.goldSoft) {
        Row(Modifier.padding(Space.lg), verticalAlignment = Alignment.CenterVertically) {
            if (job.otherName.isNotBlank()) Avatar(job.otherName, 48.dp, ring = true, tint = OC.bg) else Avatar("", 48.dp, icon = categoryIcon(job.category), tint = OC.bg)
            Spacer(Modifier.width(Space.lg))
            Column(Modifier.weight(1f)) {
                Text(title, style = T.largeBodyBold)
                Text(line, style = T.defaultBody)
            }
            Icon(Icons.Outlined.ChevronRight, "Open", Modifier.size(24.dp))
        }
    }
}

// ======================= Booking: one question per screen =======================
private val SUGGESTIONS = mapOf(
    "plumber" to listOf("Leaking tap", "Geyser not heating", "Blocked drain", "Motor not starting"),
    "electrician" to listOf("No power in a room", "Fan not working", "Socket sparking", "UPS wiring"),
    "mechanic" to listOf("Car won’t start", "Flat tyre", "Battery dead"),
    "carpenter" to listOf("Door won’t close", "Fix a cupboard", "New shelf"),
    "painter" to listOf("Paint one room", "Touch-ups", "Damp patch"),
    "welder" to listOf("Gate repair", "Window grill", "Broken railing"),
)

/** Flow page: back · progress · close in the app bar, one question as the title, sticky primary action. */
@Composable
private fun FlowScreen(
    step: Int, total: Int, title: String, subtitle: String?, onBack: () -> Unit, onClose: () -> Unit,
    footer: (@Composable ColumnScope.() -> Unit)?, content: @Composable ColumnScope.() -> Unit,
) {
    BackHandlerCompat(onBack)
    Screen(title = title, subtitle = subtitle, onBack = onBack, footer = footer, right = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressLine((step + 1f) / total, Modifier.width(140.dp))
            Spacer(Modifier.width(Space.md))
            IconCircle(Icons.Outlined.Close, "Close", onClose)
        }
    }, content = content)
}

@Composable fun BackHandlerCompat(onBack: () -> Unit) = androidx.activity.compose.BackHandler(onBack = onBack)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookFlow(s: UiState, vm: AppViewModel) {
    val first = if (s.bookCategory != null) 1 else 0
    var step by rememberSaveable { mutableIntStateOf(first) }
    var category by rememberSaveable { mutableStateOf(s.bookCategory) }
    var desc by rememberSaveable { mutableStateOf("") }
    var fare by rememberSaveable(category) { mutableLongStateOf(recommended(category)) }
    val close: () -> Unit = { vm.open(null) }
    val back: () -> Unit = { if (step > first) step -= 1 else close() }
    val balance = s.me?.wallet?.balance ?: 0
    val cat = category
    // Where: the phone's GPS fix when allowed and inside Lahore, else the saved home address.
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var place by remember { mutableStateOf(Place.HOME) }
    var locating by remember { mutableStateOf(false) }
    var gpsNote by remember { mutableStateOf<String?>(null) }
    val locate: () -> Unit = {
        locating = true; gpsNote = null
        scope.launch {
            val p = currentPlace(ctx)
            locating = false
            when {
                p == null -> gpsNote = "We couldn’t find you. Check that location is on, or use your saved address."
                !p.inLahore -> { place = Place.HOME; gpsNote = "One Click works in Lahore for now. We’ll use your saved address." }
                else -> place = p
            }
        }
    }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) locate() else gpsNote = "Location is off for One Click, so we’ll use your saved address."
    }
    val useGps: () -> Unit = { if (hasLocationPermission(ctx)) locate() else if (!isJvmTest) askLocation.launch(LOCATION_PERMISSIONS) }
    LaunchedEffect(Unit) { if (hasLocationPermission(ctx)) locate() }

    AnimatedContent(step, label = "book", transitionSpec = {
        (slideInHorizontally(tween(450, easing = Motion.enter)) { if (targetState > initialState) it else -it } + fadeIn(tween(300))) togetherWith
            (slideOutHorizontally(tween(300, easing = Motion.exit)) { if (targetState > initialState) -it / 3 else it / 3 } + fadeOut(tween(200)))
    }) { st ->
        when {
            st == 0 || cat == null -> FlowScreen(0, 4, "What needs fixing?", "Choose the kind of pro you need.", back, close, null) {
                Column {
                    Backend.CATEGORIES.forEach { c ->
                        ListRow(c.name, subtitle = "From ${rs(c.recommendedFare)}", icon = categoryIcon(c.slug), chevron = true,
                            onClick = { category = c.slug; fare = c.recommendedFare; step = 1 })
                    }
                }
            }
            st == 1 -> FlowScreen(1, 4, "Tell us about the job", "Optional — it helps pros give you a fair price.", back, close, footer = {
                PillButton("Continue", { step = 2 })
                PillButton("Skip", { desc = ""; step = 2 }, kind = Btn.Tertiary)
            }) {
                Field(desc, { desc = it.take(200) }, "For example: the kitchen tap is leaking", label = "What’s wrong?", singleLine = false, minHeight = 120.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Gap.chips), verticalArrangement = Arrangement.spacedBy(Gap.chips)) {
                    SUGGESTIONS[cat].orEmpty().forEach { t -> Chip(t, desc == t) { desc = t } }
                }
            }
            st == 2 -> FlowScreen(2, 4, "Where do you need them?", "Pros will come to this address.", back, close, footer = {
                PillButton("Confirm address", { step = 3 }, enabled = !locating)
            }) {
                val nearby = s.mapProviders.filter { it.category == cat && Backend.km(it.lat, it.lng, place.lat, place.lng) <= 8 }
                val here = LatLng(place.lat, place.lng)
                val gps = place != Place.HOME
                Box(Modifier.fillMaxWidth().height(260.dp).clip(Radius.xl)) {
                    LahoreMap(listOf(Pin("you", here.latitude, here.longitude, PinKind.You)) +
                        nearby.map { Pin(it.id, it.lat, it.lng, PinKind.Provider, it.category) }, Modifier.fillMaxSize(), here, place, 14.8f)
                }
                Column {
                    ListRow("Use my current location", subtitle = when { locating -> "Finding you…"; gps -> place.address; else -> "Pros come to where you are now" },
                        icon = Icons.Outlined.MyLocation, onClick = useGps,
                        right = { if (gps && !locating) Icon(Icons.Outlined.CheckCircle, "Selected", tint = OC.positive) })
                    ListRow(Backend.HOME_ADDRESS, subtitle = "Saved home", icon = Icons.Outlined.Home, onClick = { place = Place.HOME; gpsNote = null },
                        right = { if (!gps) Icon(Icons.Outlined.CheckCircle, "Selected", tint = OC.positive) })
                }
                gpsNote?.let { Alert(it, Tone.Info) }
                Alert(if (nearby.isEmpty()) "No ${categoryName(cat).lowercase()}s online near you right now. You can still send a request."
                    else "${nearby.size} ${categoryName(cat).lowercase()}${if (nearby.size > 1) "s are" else " is"} online near you.",
                    if (nearby.isEmpty()) Tone.Warn else Tone.Good)
            }
            else -> {
                val rec = recommended(cat)
                FlowScreen(3, 4, "What do you want to pay?", "Pros can accept your price or send a counter-offer.", back, close, footer = {
                    PillButton("Find pros", { vm.createRequest(cat, desc, fare, place) }, loading = s.busy, enabled = fare <= balance)
                }) {
                    Row(Modifier.fillMaxWidth().padding(vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                        IconCircle(Icons.Outlined.Remove, "Lower price", { fare = (fare - 50).coerceAtLeast(Backend.MIN_FARE) }, 48.dp)
                        AnimatedContent(fare, Modifier.weight(1f), label = "fare", transitionSpec = {
                            (slideInVertically(tween(200, easing = Motion.standard)) { if (targetState > initialState) it / 3 else -it / 3 } + fadeIn(tween(200))) togetherWith fadeOut(tween(100))
                        }) { Text(rs(it), style = T.number, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
                        IconCircle(Icons.Outlined.Add, "Raise price", { fare = (fare + 50).coerceAtMost(Backend.MAX_FARE) }, 48.dp)
                    }
                    when {
                        fare <= Backend.MIN_FARE -> Alert("This is the lowest price you can offer.", Tone.Warn)
                        fare < rec -> Alert("Below the usual price — fewer pros may answer.", Tone.Warn)
                        fare == rec -> Alert("This is the usual price for this job.", Tone.Good)
                        else -> Alert("Above the usual price — pros usually answer faster.", Tone.Good)
                    }
                    Card {
                        ReceiptRow("Usual price", rs(rec))
                        ReceiptRow("Our fee", "Free for you")
                        Hr()
                        ReceiptRow("You pay", rs(fare), strong = true)
                    }
                    Text("We hold your money safely and only pay the pro after you confirm the job is done.", style = T.defaultBody)
                    if (fare > balance) Alert("Your balance is ${rs(balance)}. Add money to offer this price.", Tone.Bad, action = "Add money", onAction = { vm.open(null); vm.tab(Tab.Wallet) })
                }
            }
        }
    }
}

// ======================= Tracker: map + floating sheet =======================
/** Full-screen map with a round back button and a floating card sheet (inset 8dp, 32dp corners). */
@Composable
fun MapWithSheet(
    pins: List<Pin>, focus: LatLng, focusKey: Any, route: Pair<LatLng, LatLng>?, radar: Boolean, onBack: () -> Unit,
    zoom: Float = 15f, sheet: @Composable ColumnScope.() -> Unit,
) {
    var sheetPx by remember { mutableIntStateOf(0) }
    val sheetDp = with(LocalDensity.current) { sheetPx.toDp() }
    Box(Modifier.fillMaxSize()) {
        LahoreMap(pins, Modifier.fillMaxSize(), focus, focusKey, zoom, bottomPadding = sheetDp, route = route, radarAt = focus, radar = radar)
        Surface(onClick = onBack, shape = CircleShape, color = OC.bg, shadowElevation = 4.dp,
            modifier = Modifier.statusBarsPadding().padding(Gap.gutter).size(48.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.ArrowBack, "Back", Modifier.size(24.dp)) }
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(8.dp).onSizeChanged { sheetPx = it.height }) {
            Surface(shape = Radius.sheet, color = OC.bg, shadowElevation = 8.dp) {
                Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.lg, vertical = Space.xl).animateContentSize(tween(300, easing = Motion.standard)),
                    verticalArrangement = Arrangement.spacedBy(Gap.component), content = sheet)
            }
        }
    }
}

private val SEARCH_LINES = listOf("Pros nearby can see your request", "Asking more pros", "Every pro is checked", "You choose who comes")

@Composable
fun TrackPage(s: UiState, vm: AppViewModel) {
    val job = s.job
    var seen by remember { mutableStateOf(false) }
    if (job != null) seen = true
    LaunchedEffect(job == null, seen) { if (job == null && seen) { delay(400); vm.open(null) } }
    if (job == null) {
        Box(Modifier.fillMaxSize().background(OC.bg), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.md)) {
                TextSkeleton(Modifier.width(220.dp), height = 22.dp); TextSkeleton(Modifier.width(160.dp))
            }
        }
        return
    }
    if (job.needsRating) { SuccessScreen(job, vm); return }
    val searching = job.status == "pending"
    val onJob = job.status in ON_JOB
    val you = LatLng(job.lat, job.lng)
    val pins = buildList {
        add(Pin("you", you.latitude, you.longitude, PinKind.You))
        if (onJob) add(Pin("p", job.providerLat, job.providerLng, PinKind.Provider, job.category))
        else s.mapProviders.filter { it.category == job.category }.forEach { add(Pin(it.id, it.lat, it.lng, PinKind.Provider, it.category)) }
    }
    val route = if (job.status in listOf("accepted", "en_route")) LatLng(job.providerLat, job.providerLng) to you else null
    MapWithSheet(pins, you, job.status, route, searching, onBack = { vm.open(null) }) {
        when {
            searching -> Searching(job, s, vm)
            job.status == "cancelled" -> {
                Text("Request cancelled", style = T.subsectionTitle)
                Text(if (job.cancelReason == "no_offers") "No one answered in time. Try again with a higher price." else "Your money is back in your wallet.", style = T.largeBody)
                PillButton("Book again", { vm.startBooking(job.category) })
            }
            else -> CustomerJob(job, s, vm)
        }
    }
}

@Composable
private fun Searching(job: JobView, s: UiState, vm: AppViewModel) {
    var confirmCancel by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Gap.betweenText)) {
        AnimatedContent(SEARCH_LINES[(job.searchSeconds / 4) % SEARCH_LINES.size], label = "line",
            transitionSpec = { fadeIn(tween(300, easing = Motion.standard)) togetherWith fadeOut(tween(150)) }) { Text(it, style = T.subsectionTitle) }
        Text("${categoryName(job.category)} · you offered ${rs(job.fare)}", style = T.largeBody)
    }
    ProgressLine(null)
    if (job.offers.isEmpty() && job.searchSeconds > 20) Alert("Try raising your price — more pros will answer.", Tone.Warn)
    if (job.offers.isNotEmpty()) {
        Header(if (job.offers.size == 1) "1 offer" else "${job.offers.size} offers")
        job.offers.forEachIndexed { i, o ->
            key(o.id) {
                var shown by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { shown = true }
                AnimatedVisibility(shown, enter = slideInVertically(tween(450, easing = Motion.enter)) { it / 2 } + fadeIn(tween(300))) {
                    OfferCard(o, best = i == 0, busy = s.busy, onAccept = { vm.acceptOffer(o.id) }, onDecline = { vm.declineOffer(o.id) })
                }
            }
        }
    }
    PillButton("Raise to ${rs(job.fare + 50)}", { vm.raiseFare(job.fare + 50) }, kind = Btn.Secondary)
    PillButton("Cancel request", { confirmCancel = true }, kind = Btn.SecondaryNegative)
    if (confirmCancel) ConfirmDialog("Cancel your request?", "Pros nearby are looking at it right now.", "Keep looking", "Cancel request",
        { confirmCancel = false }) { confirmCancel = false; vm.cancel() }
}

/** An offer: who, rating, how far, the price (the hero), and one clear action. */
@Composable
private fun OfferCard(o: OfferView, best: Boolean, busy: Boolean, onAccept: () -> Unit, onDecline: () -> Unit) {
    OutlineCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(o.providerName, 48.dp, ring = true)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(o.providerName, style = T.largeBodyBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, null, Modifier.size(16.dp), tint = OC.gold)
                    Text(" ${o.rating} · ${o.etaMin} min away", style = T.defaultBody)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(rs(o.amount), style = T.bodyTitle)
                if (o.isCounter) Badge("Counter-offer", Tone.Warn) else Badge("Your price", Tone.Good)
            }
        }
        ProgressLine(o.secondsLeft / 30f)
        PillButton("Accept ${rs(o.amount)}", onAccept, kind = if (best) Btn.Primary else Btn.Secondary, loading = busy)
        PillButton("Decline", onDecline, kind = Btn.Tertiary, enabled = !busy)
    }
}

@Composable
private fun CustomerJob(job: JobView, s: UiState, vm: AppViewModel) {
    val name = firstName(job.otherName)
    var confirmCancel by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf(false) }
    val title = when {
        job.status == "accepted" -> "$name is getting ready"
        job.status == "en_route" -> "$name is on the way"
        job.status == "arrived" -> "$name has arrived"
        job.awaitingConfirmation -> "Check the work, then pay"
        job.status == "in_progress" -> "Job in progress"
        else -> "We’re checking your report"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusPill(job.status)
        Spacer(Modifier.weight(1f))
        if (job.etaMin != null) Text("Should arrive in about ${job.etaMin} min", style = T.defaultBody)
    }
    Text(title, style = T.subsectionTitle)
    ListRow(job.otherName, subtitle = "★ ${job.otherRating} · Checked pro", left = { Avatar(job.otherName, 48.dp, ring = true) },
        right = {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                IconCircle(Icons.Outlined.Call, "Call $name", { vm.toast("In-app calls come with the Agora setup") })
                IconCircle(Icons.Outlined.Shield, "Safety", { vm.open(Page.Safety) })
            }
        })
    Card {
        ReceiptRow("Job", categoryName(job.category) + if (job.description.isNotBlank()) " · ${job.description}" else "")
        if (job.address.isNotBlank()) ReceiptRow("Where", job.address)
        ReceiptRow("Price", rs(job.fare))
        ReceiptRow("Payment", if (job.status == "disputed") "On hold" else "Held safely", valueColor = OC.positive)
    }
    if (job.awaitingConfirmation) {
        ProofPhoto(job.photo)
        PillButton("Confirm and pay ${rs(job.fare)}", { vm.confirm() }, loading = s.busy)
        PillButton("Report a problem", { report = true }, kind = Btn.Tertiary)
    } else when (job.status) {
        "accepted", "en_route" -> PillButton("Cancel booking", { confirmCancel = true }, kind = Btn.SecondaryNegative)
        "arrived", "in_progress" -> PillButton("Report a problem", { report = true }, kind = Btn.Tertiary)
        "disputed" -> Alert("Your money stays on hold. Our team will call you within 24 hours.", Tone.Warn)
    }
    if (confirmCancel) ConfirmDialog("Cancel this booking?", "$name is already getting ready. You’ll get your ${rs(job.fare)} back.", "Keep booking", "Cancel booking",
        { confirmCancel = false }) { confirmCancel = false; vm.cancel() }
    if (report) ReportSheet(vm) { report = false }
}

@Composable
fun ProofPhoto(path: String?) {
    // Local file (tests) or short-lived signed link from private storage (live).
    val bmp by produceState<android.graphics.Bitmap?>(null, path) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                when {
                    path == null -> null
                    path.startsWith("https://") -> java.net.URL(path).openStream().use { BitmapFactory.decodeStream(it) }
                    File(path).exists() -> BitmapFactory.decodeFile(path)
                    else -> null
                }
            }.getOrNull()
        }
    }
    val b = bmp
    if (b != null) Image(b.asImageBitmap(), "Photo of the finished job", Modifier.fillMaxWidth().height(180.dp).clip(Radius.md), contentScale = ContentScale.Crop)
    else ListRow("Photo of the finished job", subtitle = "Taken by the pro when they finished", icon = Icons.Outlined.PhotoCamera)
}

@Composable
private fun ReportSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf("I was overcharged") }
    OverlaySheet(onDismiss) {
        Text("What went wrong?", style = T.subsectionTitle)
        Text("We’ll keep your money on hold until our team has checked.", style = T.defaultBody)
        Column {
            listOf("I was overcharged", "The work wasn’t done well", "The pro didn’t show up").forEach { r ->
                ListRow(r, onClick = { reason = r }, right = { RadioButton(reason == r, { reason = r }, colors = RadioButtonDefaults.colors(selectedColor = OC.deep)) })
            }
        }
        PillButton("Send report", { vm.dispute(reason); onDismiss() })
    }
}

// ======================= Money moment: success, then rating =======================
@Composable
fun SuccessScreen(job: JobView, vm: AppViewModel) {
    var rate by remember { mutableStateOf(false) }
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, tween(700, easing = Motion.overshoot)) }
    val name = firstName(job.otherName)
    Column(Modifier.fillMaxSize().background(OC.forest).statusBarsPadding().navigationBarsPadding().padding(Gap.gutter)) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(96.dp).scale(pop.value).clip(CircleShape).background(OC.gold), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Check, null, Modifier.size(48.dp), tint = OC.deep)
        }
        Spacer(Modifier.height(Gap.sections))
        Text(if (job.isCustomer) "JOB DONE" else "YOU’VE BEEN PAID", style = T.displayMedium.copy(color = OC.gold))
        Spacer(Modifier.height(Gap.textToComponent))
        Text(if (job.isCustomer) "You paid ${rs(job.fare)} to ${job.otherName}. Thanks for using One Click."
             else "${rs(job.net)} is in your wallet — ${rs(job.fare)} minus our 15% fee.", style = T.largeBody.copy(color = OC.forestText))
        Spacer(Modifier.weight(1f))
        PillButton("Rate $name", { rate = true })
        PillButton("Done", { vm.skipRating() }, kind = Btn.Tertiary, onDark = true)
    }
    if (rate) RatingSheet(job, vm) { rate = false }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RatingSheet(job: JobView, vm: AppViewModel, onDismiss: () -> Unit = { vm.skipRating() }) {
    var stars by remember { mutableIntStateOf(0) }
    val tags = remember { mutableStateListOf<String>() }
    val options = if (job.isCustomer) listOf("On time", "Polite", "Good work", "Fair price") else listOf("Friendly", "Clear request", "Easy to find")
    OverlaySheet(onDismiss) {
        Text("How was ${firstName(job.otherName)}?", style = T.subsectionTitle)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            (1..5).forEach { n ->
                val on = n <= stars
                val scale by animateFloatAsState(if (on) 1f else .85f, tween(450, easing = Motion.overshoot), label = "star")
                Icon(if (on) Icons.Filled.Star else Icons.Outlined.StarOutline, "$n stars",
                    Modifier.size(52.dp).scale(scale).tap { stars = n }.padding(4.dp), tint = if (on) OC.gold else OC.cardStrong)
            }
        }
        AnimatedVisibility(stars > 0) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Gap.chips), verticalArrangement = Arrangement.spacedBy(Gap.chips)) {
                options.forEach { t -> Chip(t, t in tags) { if (t in tags) tags.remove(t) else tags.add(t) } }
            }
        }
        PillButton("Submit", { vm.rate(stars, tags.toList()) }, enabled = stars > 0)
        PillButton("Skip", { vm.skipRating() }, kind = Btn.Tertiary)
    }
}

@Composable
fun RoundSoft(icon: ImageVector, label: String, size: Dp = 48.dp, onCard: Boolean = false, onClick: () -> Unit) =
    IconCircle(icon, label, onClick, size)
