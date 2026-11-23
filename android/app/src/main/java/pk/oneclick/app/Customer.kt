package pk.oneclick.app

import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.LatLng
import java.io.File
import java.util.Calendar

val HOME = LatLng(Backend.HOME_LAT, Backend.HOME_LNG)
val ON_JOB = listOf("accepted", "en_route", "arrived", "in_progress", "disputed")
val TAB_BAR = 65.dp

/** Map-first page: full-bleed Lahore map, floating chips on top, one sheet above the tab bar. */
@Composable
fun MapPage(
    pins: List<Pin>, focus: LatLng, focusKey: Any, route: Pair<LatLng, LatLng>?, radar: Boolean, zoom: Float = 15f,
    topLeft: @Composable () -> Unit, overSheet: @Composable ColumnScope.() -> Unit = {}, sheet: @Composable ColumnScope.() -> Unit,
) {
    var sheetPx by remember { mutableIntStateOf(0) }
    var recenter by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val sheetDp = with(density) { sheetPx.toDp() }
    Box(Modifier.fillMaxSize()) {
        LahoreMap(pins, Modifier.fillMaxSize(), focus, "$focusKey-$recenter", zoom, bottomPadding = sheetDp + TAB_BAR, route = route, radarAt = focus, radar = radar)
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(Space.lg), verticalAlignment = Alignment.CenterVertically) {
            topLeft()
            Spacer(Modifier.weight(1f))
            Surface(onClick = { recenter++ }, shape = CircleShape, color = OC.bg, shadowElevation = 4.dp, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.MyLocation, "My location", Modifier.size(22.dp), tint = OC.ink) }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = TAB_BAR).onSizeChanged { sheetPx = it.height }) {
            overSheet()
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).clip(Radius.sheet).background(OC.bg)
                .verticalScroll(rememberScrollState()).padding(horizontal = Space.lg).padding(bottom = Space.lg)
                .animateContentSize(tween(250, easing = Motion.emphasized)), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                Box(Modifier.fillMaxWidth().padding(top = Space.md), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(36.dp, 4.dp).clip(CircleShape).background(OC.cardStrong))
                }
                sheet()
            }
        }
    }
}

@Composable
fun LocationChip(text: String) {
    Surface(shape = CircleShape, color = OC.bg, shadowElevation = 4.dp) {
        Row(Modifier.height(48.dp).padding(horizontal = Space.lg), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(OC.gold))
            Spacer(Modifier.width(Space.sm))
            Text(text, style = T.label)
        }
    }
}

fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }

@Composable
fun CustomerHome(s: UiState, vm: AppViewModel) {
    val job = s.job
    val searching = job?.status == "pending"
    val onJob = job != null && job.status in ON_JOB
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var description by rememberSaveable { mutableStateOf("") }
    var fare by rememberSaveable(category) { mutableLongStateOf(Backend.CATEGORIES.firstOrNull { it.slug == category }?.recommendedFare ?: 800) }
    var fareSheet by remember { mutableStateOf(false) }

    val pins = buildList {
        add(Pin("you", HOME.latitude, HOME.longitude, PinKind.You))
        if (onJob) add(Pin("p", job!!.providerLat, job.providerLng, PinKind.Provider, job.category))
        else s.mapProviders.filter { category == null || it.category == category }.forEach { add(Pin(it.id, it.lat, it.lng, PinKind.Provider, it.category)) }
    }
    val route = if (job != null && job.status in listOf("accepted", "en_route")) LatLng(job.providerLat, job.providerLng) to HOME else null

    Box(Modifier.fillMaxSize()) {
        MapPage(pins, HOME, job?.status ?: "idle", route, searching, topLeft = { LocationChip(Backend.HOME_ADDRESS) },
            overSheet = {
                if (searching) job!!.offers.forEach { o ->
                    key(o.id) {
                        var shown by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { shown = true }
                        AnimatedVisibility(shown, enter = slideInVertically(tween(300, easing = Motion.emphasized)) { it / 2 } + fadeIn(tween(200))) {
                            OfferCard(o, s.busy, { vm.acceptOffer(o.id) }) { vm.declineOffer(o.id) }
                        }
                    }
                }
            }) {
            AnimatedContent(when { searching -> "search"; onJob -> "job"; else -> "form" }, label = "sheet",
                transitionSpec = { fadeIn(tween(220, easing = Motion.emphasized)) togetherWith fadeOut(tween(120)) }) { mode ->
                Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                    when (mode) {
                        "search" -> s.job?.takeIf { it.status == "pending" }?.let { Searching(it, vm) }
                        "job" -> s.job?.takeIf { it.status in ON_JOB }?.let { CustomerJob(it, s, vm) }
                        else -> OrderForm(s, vm, category, { category = it }, description, { description = it.take(200) }, fare) { fareSheet = true }
                    }
                }
            }
        }
        if (fareSheet) category?.let { c -> FareSheet(fare, Backend.CATEGORIES.first { it.slug == c }.recommendedFare, { fare = it }) { fareSheet = false } }
        if (job != null && job.needsRating) RatingSheet(job, vm)
    }
}

@Composable
private fun OrderForm(s: UiState, vm: AppViewModel, category: String?, onCategory: (String?) -> Unit, description: String,
                      onDescription: (String) -> Unit, fare: Long, openFare: () -> Unit) {
    s.job?.takeIf { it.status == "cancelled" }?.let {
        Note(when (it.cancelReason) { "no_offers" -> "No offers yet. Try a higher fare"; "customer_cancelled" -> "Request cancelled"; else -> "Booking cancelled. Payment refunded" }, gold = true)
    }
    Column {
        Text("${greeting()}, ${firstName(s.me?.user?.name ?: "")}", style = T.small)
        Text(if (category == null) "What needs fixing?" else "Book a ${categoryName(category).lowercase()}", style = T.h1.copy(fontSize = 28.sp, lineHeight = 32.sp))
    }
    val cats = Backend.CATEGORIES
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        cats.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                row.forEach { c ->
                    val on = c.slug == category
                    Surface(onClick = { onCategory(if (on) null else c.slug) }, shape = Radius.lg, color = if (on) OC.deep else OC.card, modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(if (on) OC.gold else OC.bg), contentAlignment = Alignment.Center) {
                                Icon(categoryIcon(c.slug), null, Modifier.size(20.dp), tint = OC.deep)
                            }
                            Text(c.name, style = T.label.copy(color = if (on) Color.White else OC.ink), maxLines = 1)
                            Text("from ${rs(c.recommendedFare)}", style = T.tiny.copy(color = if (on) Color.White.copy(alpha = .7f) else OC.muted), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
    AnimatedVisibility(category != null, enter = expandVertically(tween(250, easing = Motion.emphasized)) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            Field(description, onDescription, "Describe the problem (optional)")
            Surface(onClick = openFare, shape = Radius.lg, color = OC.card) {
                Row(Modifier.padding(Space.lg), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Your fare", style = T.tiny)
                        Text(rs(fare), style = T.number)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Wallet", style = T.tiny)
                        Text(rs(s.me?.wallet?.balance ?: 0), style = T.label)
                    }
                    Icon(Icons.Outlined.ChevronRight, null, Modifier.padding(start = Space.sm), tint = OC.muted)
                }
            }
            PillButton("Find a ${categoryName(category ?: "").lowercase()}", { category?.let { vm.createRequest(it, description, fare) } },
                Modifier.fillMaxWidth(), loading = s.busy)
        }
    }
}

@Composable
private fun FareSheet(fare: Long, rec: Long, onFare: (Long) -> Unit, onDone: () -> Unit) {
    OverlaySheet(onDone) {
        Text("Offer your fare", style = T.title)
        Text("Recommended for this job: ${rs(rec)}", style = T.small)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundSoft(Icons.Outlined.Remove, "Decrease fare") { onFare((fare - 50).coerceAtLeast(Backend.MIN_FARE)) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Rs", style = T.label.copy(color = OC.muted))
                AnimatedContent(fare, label = "fare", transitionSpec = {
                    (slideInVertically(tween(180)) { if (targetState > initialState) it / 3 else -it / 3 } + fadeIn(tween(180))) togetherWith fadeOut(tween(120))
                }) { Text("%,d".format(it), style = T.display.copy(fontSize = 56.sp, lineHeight = 60.sp), maxLines = 1) }
            }
            RoundSoft(Icons.Outlined.Add, "Increase fare") { onFare((fare + 50).coerceAtMost(Backend.MAX_FARE)) }
        }
        when {
            fare <= Backend.MIN_FARE -> Note("Minimum fare reached")
            fare < rec -> Note("Fewer offers and a longer wait", gold = true)
            fare == rec -> Note("Recommended fare")
            else -> Note("More offers, faster response")
        }
        PillButton("Done", onDone, Modifier.fillMaxWidth())
    }
}

@Composable
fun RoundSoft(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, size: Dp = 52.dp, onCard: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = if (onCard) OC.bg else OC.card, modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(22.dp), tint = OC.ink) }
    }
}

private val SEARCH_LINES = listOf("Pros nearby see your request", "Reaching more pros", "Every pro is verified", "You choose who comes")

@Composable
private fun Searching(job: JobView, vm: AppViewModel) {
    var confirmCancel by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        AnimatedContent(SEARCH_LINES[(job.searchSeconds / 4) % SEARCH_LINES.size], label = "line",
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }) { Text(it, style = T.title) }
        Text("${categoryName(job.category)} · ${rs(job.fare)} · ${Backend.HOME_ADDRESS.substringBefore(",")}", style = T.small)
    }
    LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = OC.deep, trackColor = OC.card)
    if (job.offers.isEmpty() && job.searchSeconds > 20) Note("Try raising the fare", gold = true)
    if (job.offers.isNotEmpty()) Text("${job.offers.size} offer${if (job.offers.size > 1) "s" else ""} above — pick one", style = T.small.copy(color = OC.deep))
    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        PillButton("Raise to ${rs(job.fare + 50)}", { vm.raiseFare(job.fare + 50) }, Modifier.weight(1f), Btn.Soft, height = 48.dp)
        PillButton("Cancel", { confirmCancel = true }, Modifier.weight(1f), Btn.Danger, height = 48.dp)
    }
    if (confirmCancel) ConfirmDialog("Cancel request?", "Pros nearby are looking at it right now.", "Keep searching", "Cancel request",
        { confirmCancel = false }) { confirmCancel = false; vm.cancel() }
}

/** Offer card floating over the map: countdown, pro, rating, ETA, price as the hero, Decline · Accept. */
@Composable
private fun OfferCard(o: OfferView, busy: Boolean, onAccept: () -> Unit, onDecline: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = 4.dp), shape = Radius.xl, color = OC.bg, shadowElevation = 6.dp) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            CountdownBar(o.secondsLeft / 30f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(o.providerName, 48.dp, ring = true)
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text(o.providerName, style = T.bodyStrong)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, null, Modifier.size(14.dp), tint = OC.gold)
                        Text(" ${o.rating} (${o.ratingCount}) · ${o.etaMin} min · ${o.distanceKm} km", style = T.small)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(rs(o.amount), style = T.number)
                    if (!o.isCounter) Badge("Your fare", Tone.Gold) else Badge("Counter-offer")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                PillButton("Decline", onDecline, Modifier.weight(1f), Btn.Danger, height = 44.dp, enabled = !busy)
                PillButton("Accept", onAccept, Modifier.weight(1f), height = 44.dp, loading = busy)
            }
        }
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
        job.awaitingConfirmation -> "Check the work"
        job.status == "in_progress" -> "Job in progress"
        else -> "We’re reviewing your report"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            StatusPill(job.status)
            Spacer(Modifier.height(Space.sm))
            Text(title, style = T.title)
        }
        if (job.etaMin != null) Column(horizontalAlignment = Alignment.End) {
            Text("${job.etaMin}", style = T.display.copy(fontSize = 44.sp))
            Text("min away", style = T.tiny)
        }
    }
    Card {
        ListRow(job.otherName, subtitle = "★ ${job.otherRating} (${job.otherRatingCount}) · Verified", left = { Avatar(job.otherName, 48.dp, ring = true) },
            right = {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    RoundSoft(Icons.Outlined.Call, "Call", 44.dp, onCard = true) { vm.toast("In-app calls arrive with the Agora setup") }
                    RoundSoft(Icons.Outlined.Shield, "Safety", 44.dp, onCard = true) { vm.open(Page.Safety) }
                }
            })
        Hr()
        ListRow("${categoryName(job.category)} · ${rs(job.fare)}", subtitle = job.description.ifBlank { "No details" }, icon = categoryIcon(job.category))
    }
    if (job.awaitingConfirmation) {
        ProofPhoto(job.photo)
        PillButton("Confirm and pay ${rs(job.fare)}", { vm.confirm() }, Modifier.fillMaxWidth(), loading = s.busy)
        PillButton("Report a problem", { report = true }, Modifier.fillMaxWidth(), Btn.Danger, height = 48.dp)
    } else when (job.status) {
        "accepted", "en_route" -> PillButton("Cancel booking", { confirmCancel = true }, Modifier.fillMaxWidth(), Btn.Danger, height = 48.dp)
        "arrived", "in_progress" -> PillButton("Report a problem", { report = true }, Modifier.fillMaxWidth(), Btn.Danger, height = 48.dp)
        "disputed" -> Note("Payment is on hold. Our team will call you within 24 hours.", gold = true)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Lock, null, Modifier.size(16.dp), tint = OC.green)
        Spacer(Modifier.width(6.dp))
        Text("${rs(job.fare)} is held safely until you confirm", style = T.tiny)
    }
    if (confirmCancel) ConfirmDialog("Cancel booking?", "$name is already preparing. Your ${rs(job.fare)} will be refunded.", "Keep booking", "Cancel booking",
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
    if (b != null) Image(b.asImageBitmap(), "Photo proof", Modifier.fillMaxWidth().height(170.dp).clip(Radius.lg), contentScale = ContentScale.Crop)
    else Card { ListRow("Photo proof attached", subtitle = "Taken when the job was finished", icon = Icons.Outlined.PhotoCamera) }
}

@Composable
private fun ReportSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf("Overcharged") }
    OverlaySheet(onDismiss) {
        Text("Report a problem", style = T.title)
        Text("The payment stays on hold until our team reviews it.", style = T.small)
        Card {
            listOf("Overcharged", "Poor workmanship", "Pro didn’t show up").forEachIndexed { i, r ->
                if (i > 0) Hr()
                ListRow(r, onClick = { reason = r }, right = { RadioButton(reason == r, { reason = r }, colors = RadioButtonDefaults.colors(selectedColor = OC.deep)) })
            }
        }
        PillButton("Send report", { vm.dispute(reason); onDismiss() }, Modifier.fillMaxWidth(), Btn.Deep)
    }
}

/** Review: gold check, star pop, mood chips, one primary. Used by both sides. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RatingSheet(job: JobView, vm: AppViewModel) {
    var stars by remember { mutableIntStateOf(0) }
    val tags = remember { mutableStateListOf<String>() }
    val options = if (job.isCustomer) listOf("On time", "Polite", "Good quality", "Fair price") else listOf("Friendly", "Clear request", "Easy to find")
    OverlaySheet({ vm.skipRating() }) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(OC.gold), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Check, null, Modifier.size(36.dp), tint = OC.deep)
            }
            Text(if (job.isCustomer) "Job done" else "${rs(job.net)} earned", style = T.h1, textAlign = TextAlign.Center)
            Text(if (job.isCustomer) "${rs(job.fare)} paid to ${job.otherName}" else "${rs(job.fare)} minus 15% service fee", style = T.small)
            Spacer(Modifier.height(Space.sm))
            Text("How was ${firstName(job.otherName)}?", style = T.h2)
            Row {
                (1..5).forEach { n ->
                    val on = n <= stars
                    val scale by animateFloatAsState(if (on) 1f else .82f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "star")
                    Icon(if (on) Icons.Filled.Star else Icons.Outlined.StarOutline, "$n stars",
                        Modifier.size(46.dp).scale(scale).tap { stars = n }.padding(3.dp), tint = if (on) OC.gold else OC.cardStrong)
                }
            }
            AnimatedVisibility(stars > 0) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    options.forEach { t -> Chip(t, t in tags) { if (t in tags) tags.remove(t) else tags.add(t) } }
                }
            }
        }
        PillButton("Done", { vm.rate(stars, tags.toList()) }, Modifier.fillMaxWidth(), enabled = stars > 0)
        Text("Skip", style = T.label.copy(color = OC.muted), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().tap { vm.skipRating() })
    }
}
