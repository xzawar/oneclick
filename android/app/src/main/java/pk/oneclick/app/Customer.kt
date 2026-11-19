package pk.oneclick.app

import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.osmdroid.util.GeoPoint
import java.io.File

private val HOME = GeoPoint(Backend.HOME_LAT, Backend.HOME_LNG)
private val ON_JOB = listOf("accepted", "en_route", "arrived", "in_progress", "disputed")

@Composable
fun radarPhase(active: Boolean): Float {
    if (!active) return -1f
    val t = rememberInfiniteTransition(label = "radar")
    return t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "phase").value
}

@Composable
fun CustomerHome(s: UiState, vm: AppViewModel, openMenu: () -> Unit) {
    val job = s.job
    val searching = job?.status == "pending"
    val onJob = job != null && job.status in ON_JOB
    var sheetPx by remember { mutableIntStateOf(0) }
    var recenter by remember { mutableIntStateOf(0) }
    var category by rememberSaveable { mutableStateOf("plumber") }
    var description by rememberSaveable { mutableStateOf("") }
    val rec = Backend.CATEGORIES.first { it.slug == category }.recommendedFare
    var fare by rememberSaveable(category) { mutableLongStateOf(rec) }
    var fareSheet by remember { mutableStateOf(false) }

    val pins = buildList {
        add(Pin(Backend.HOME_LAT, Backend.HOME_LNG, PinKind.You))
        if (onJob) add(Pin(job!!.providerLat, job.providerLng, PinKind.Provider))
        else s.mapProviders.forEach { add(Pin(it.lat, it.lng, PinKind.Provider)) }
    }
    val route = if (job != null && job.status in listOf("accepted", "en_route"))
        GeoPoint(job.providerLat, job.providerLng) to HOME else null

    Box(Modifier.fillMaxSize()) {
        MapCanvas(pins, Modifier.fillMaxSize(), focus = HOME, focusKey = "${job?.status}-$recenter",
            offsetY = sheetPx / 2, route = route, radarAt = HOME, radarPhase = radarPhase(searching))
        RoundButton(Icons.Outlined.Menu, "Menu", openMenu, Modifier.statusBarsPadding().padding(8.dp))
        Text("© OpenStreetMap © CARTO", style = DnaType.caption, color = dna.textSecondary,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp))

        Column(Modifier.align(Alignment.BottomCenter).onSizeChanged { sheetPx = it.height }) {
            if (searching) job!!.offers.forEach { o ->
                key(o.id) {
                    var shown by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { shown = true }
                    AnimatedVisibility(shown, enter = slideInVertically(tween(DnaMotion.popup, easing = DnaMotion.standard)) { it / 2 } + fadeIn(tween(DnaMotion.fade))) {
                        OfferCard(o, { vm.acceptOffer(o.id) }, { vm.declineOffer(o.id) }, s.busy)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                RoundButton(Icons.Outlined.MyLocation, "My location", { recenter++ })
            }
            Sheet {
                AnimatedContent(when { searching -> "search"; onJob -> "job"; else -> "form" }, label = "sheet",
                    transitionSpec = { fadeIn(tween(DnaMotion.fade)) togetherWith fadeOut(tween(DnaMotion.fade)) }) { mode ->
                    Column {
                        when (mode) {
                            "search" -> s.job?.takeIf { it.status == "pending" }?.let { SearchingSheet(it, s, vm) }
                            "job" -> s.job?.takeIf { it.status in ON_JOB }?.let { CustomerJobSheet(it, s, vm) }
                            else -> OrderForm(s, vm, category, { category = it }, description, { description = it.take(200) }, fare) { fareSheet = true }
                        }
                    }
                }
            }
        }
        if (fareSheet) FareSheet(fare, rec, { fare = it }) { fareSheet = false }
        if (job != null && job.needsRating) RatingSheet(job, vm)
    }
}

@Composable
private fun OrderForm(
    s: UiState, vm: AppViewModel, category: String, onCategory: (String) -> Unit, description: String,
    onDescription: (String) -> Unit, fare: Long, openFare: () -> Unit,
) {
    val c = dna
    val cancelled = s.job?.takeIf { it.status == "cancelled" }
    if (cancelled != null) {
        InlineAlert(when (cancelled.cancelReason) {
            "no_offers" -> "No offers yet. Try a higher fare"
            "customer_cancelled" -> "Request cancelled"
            else -> "Booking cancelled. Payment refunded"
        }, AlertKind.Warning)
        Spacer(Modifier.height(12.dp))
    }
    SectionTitle("Book a pro nearby")
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Backend.CATEGORIES.forEach { cat ->
            ChoiceChip(cat.name, cat.slug == category, { onCategory(cat.slug) }, categoryIcon(cat.slug))
        }
    }
    Spacer(Modifier.height(8.dp))
    Cell(Backend.HOME_ADDRESS, subtitle = "Your location", lead = {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(c.brand))
        }
    })
    DnaField(description, onDescription, "What needs fixing?")
    Spacer(Modifier.height(4.dp))
    Cell(rs(fare), subtitle = "Your fare · Wallet ${rs(s.me?.wallet?.balance ?: 0)}", icon = Icons.Outlined.Payments,
        titleStyle = DnaType.heading4, onClick = openFare,
        end = { Icon(Icons.Outlined.ChevronRight, null, tint = c.textSecondary) })
    Spacer(Modifier.height(12.dp))
    DnaButton("Find providers", { vm.createRequest(category, description, fare) }, Modifier.fillMaxWidth(), loading = s.busy)
}

@Composable
private fun FareSheet(fare: Long, rec: Long, onFare: (Long) -> Unit, onDone: () -> Unit) {
    val c = dna
    val step = 50L
    OverlaySheet(onDone) {
        SectionTitle("Offer your fare")
        Helper("Recommended: ${rs(rec)}")
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(Icons.Outlined.Remove, "Decrease fare", { onFare((fare - step).coerceAtLeast(Backend.MIN_FARE)) }, size = 48.dp, floating = false)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Rs", style = DnaType.heading4, color = c.textSecondary)
                AnimatedContent(fare, label = "fare", transitionSpec = { fadeIn(tween(DnaMotion.follow)) togetherWith fadeOut(tween(DnaMotion.follow)) }) {
                    Text("%,d".format(it), style = DnaType.largeNumbers, color = c.textPrimary, maxLines = 1)
                }
            }
            RoundButton(Icons.Outlined.Add, "Increase fare", { onFare((fare + step).coerceAtMost(Backend.MAX_FARE)) }, size = 48.dp, floating = false)
        }
        Spacer(Modifier.height(16.dp))
        // The UI coaches instead of blocking.
        when {
            fare <= Backend.MIN_FARE -> InlineAlert("Minimum fare reached", AlertKind.Warning)
            fare < rec -> InlineAlert("Fewer offers & longer wait", AlertKind.Warning)
            fare == rec -> InlineAlert("Recommended fare", AlertKind.Brand)
            else -> InlineAlert("More offers · faster response", AlertKind.Success)
        }
        Spacer(Modifier.height(16.dp))
        DnaButton("Done", onDone, Modifier.fillMaxWidth())
    }
}

private val SEARCH_LINES = listOf("Providers see your request", "Reaching more providers", "All providers are verified", "You choose your provider")

@Composable
private fun SearchingSheet(job: JobView, s: UiState, vm: AppViewModel) {
    val c = dna
    var confirmCancel by remember { mutableStateOf(false) }
    val line = SEARCH_LINES[(job.searchSeconds / 4) % SEARCH_LINES.size]
    AnimatedContent(line, label = "line", transitionSpec = { fadeIn(tween(DnaMotion.fade)) togetherWith fadeOut(tween(DnaMotion.fade)) }) {
        SectionTitle(it)
    }
    Helper("${categoryName(job.category)} · ${job.description.ifBlank { "No details" }}")
    Spacer(Modifier.height(12.dp))
    LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape), color = c.textPrimary, trackColor = c.bgSecondary)
    Spacer(Modifier.height(16.dp))
    Text("Your fare", style = DnaType.compactBody, color = c.textSecondary)
    Text(rs(job.fare), style = DnaType.heading2, color = c.textPrimary)
    if (job.offers.isEmpty() && job.searchSeconds > 20) {
        Spacer(Modifier.height(12.dp))
        InlineAlert("Try raising the fare", AlertKind.Warning)
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DnaButton("Raise to ${rs(job.fare + 50)}", { vm.raiseFare(job.fare + 50) }, Modifier.weight(1f), BtnKind.Secondary, BtnSize.M)
        DnaButton("Cancel request", { confirmCancel = true }, Modifier.weight(1f), BtnKind.ErrorLow, BtnSize.M)
    }
    if (confirmCancel) ConfirmDialog("Cancel request?", "Providers are looking at your request right now.",
        "Keep searching", "Cancel request", { confirmCancel = false }) { confirmCancel = false; vm.cancel() }
}

/** Offer card: countdown bar, avatar, price as hero, ETA + distance, rating, Decline · Accept (equal widths). */
@Composable
private fun OfferCard(o: OfferView, onAccept: () -> Unit, onDecline: () -> Unit, busy: Boolean) {
    val c = dna
    Surface(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), shape = DnaShape.s, color = c.surface, shadowElevation = 6.dp) {
        Column {
            if (!o.isCounter) Text("Accepts your fare", style = DnaType.compactBody, color = Color.White,
                modifier = Modifier.fillMaxWidth().background(c.accent).padding(horizontal = 12.dp, vertical = 4.dp))
            CountdownBar(o.secondsLeft / 30f)
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(o.providerName, 42.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(o.providerName, style = DnaType.mainBody, color = c.textPrimary)
                    RatingLine(o.rating, o.ratingCount, "${o.jobs} jobs")
                    Text("${o.etaMin} min · ${o.distanceKm} km", style = DnaType.compactBody, color = c.textSecondary, modifier = Modifier.padding(top = 2.dp))
                }
                Text(rs(o.amount), style = DnaType.heading3, color = c.textPrimary)
            }
            Row(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DnaButton("Decline", onDecline, Modifier.weight(1f), BtnKind.ErrorLow, BtnSize.S, enabled = !busy)
                DnaButton("Accept", onAccept, Modifier.weight(1f), BtnKind.Primary, BtnSize.S, loading = busy)
            }
        }
    }
}

@Composable
private fun CustomerJobSheet(job: JobView, s: UiState, vm: AppViewModel) {
    val c = dna
    val name = firstName(job.otherName)
    var confirmCancel by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf(false) }
    val title = when {
        job.status == "accepted" -> "$name is getting ready"
        job.status == "en_route" -> "$name is on the way"
        job.status == "arrived" -> "$name has arrived"
        job.awaitingConfirmation -> "Check the work and confirm"
        job.status == "in_progress" -> "Job in progress"
        else -> "We’re reviewing your report"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            SectionTitle(title)
            Helper(if (job.status == "disputed") "Payment stays on hold" else "${rs(job.fare)} held safely until you confirm")
        }
        if (job.etaMin != null) Column(horizontalAlignment = Alignment.End) {
            Text("${job.etaMin}", style = DnaType.heading1, color = c.textPrimary)
            Text("min", style = DnaType.caption, color = c.textSecondary)
        }
    }
    Spacer(Modifier.height(8.dp))
    Cell(job.otherName, lead = { Avatar(job.otherName, 48.dp) }, titleStyle = DnaType.heading4,
        subtitle = "★ ${job.otherRating} (${job.otherRatingCount}) · Verified",
        end = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundButton(Icons.Outlined.Call, "Call", { vm.toast("In-app calls need an Agora App ID") }, size = 48.dp, floating = false)
                RoundButton(Icons.Outlined.Shield, "Safety", { vm.open(Page.Safety) }, size = 48.dp, floating = false)
            }
        })
    Cell("${categoryName(job.category)} · ${job.description.ifBlank { "No details" }}", icon = categoryIcon(job.category))
    if (job.awaitingConfirmation) {
        ProofPhoto(job.photo)
        Spacer(Modifier.height(12.dp))
        DnaButton("Confirm and pay ${rs(job.fare)}", { vm.confirm() }, Modifier.fillMaxWidth(), loading = s.busy)
        Spacer(Modifier.height(8.dp))
        DnaButton("Report a problem", { report = true }, Modifier.fillMaxWidth(), BtnKind.ErrorLow, BtnSize.M)
    } else when (job.status) {
        "accepted", "en_route" -> DnaButton("Cancel booking", { confirmCancel = true }, Modifier.fillMaxWidth(), BtnKind.ErrorLow, BtnSize.M)
        "arrived", "in_progress" -> DnaButton("Report a problem", { report = true }, Modifier.fillMaxWidth(), BtnKind.ErrorLow, BtnSize.M)
        "disputed" -> InlineAlert("Our team will contact you within 24 hours", AlertKind.Warning)
    }
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.VerifiedUser, null, Modifier.size(16.dp), tint = c.success)
        Spacer(Modifier.width(6.dp))
        Helper("$name passed CNIC and selfie checks")
    }
    if (confirmCancel) ConfirmDialog("Cancel booking?", "$name is already preparing. Your ${rs(job.fare)} will be refunded.",
        "Keep booking", "Cancel booking", { confirmCancel = false }) { confirmCancel = false; vm.cancel() }
    if (report) ReportSheet(vm) { report = false }
}

@Composable
fun ProofPhoto(path: String?) {
    val bmp = remember(path) { path?.takeIf { File(it).exists() }?.let { BitmapFactory.decodeFile(it) } }
    if (bmp != null) Image(bmp.asImageBitmap(), "Photo proof", Modifier.fillMaxWidth().height(160.dp).clip(DnaShape.s), contentScale = ContentScale.Crop)
    else Cell("Photo proof attached", subtitle = "Taken by the provider when the job was finished", icon = Icons.Outlined.PhotoCamera)
}

@Composable
private fun ReportSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf("Overcharged") }
    OverlaySheet(onDismiss) {
        SectionTitle("Report a problem")
        Helper("Payment stays on hold until our team reviews")
        Spacer(Modifier.height(8.dp))
        listOf("Overcharged", "Poor workmanship", "Provider didn’t show up").forEach { r ->
            Cell(r, onClick = { reason = r }, end = { RadioButton(reason == r, { reason = r }, colors = RadioButtonDefaults.colors(selectedColor = dna.textPrimary)) })
        }
        Spacer(Modifier.height(12.dp))
        DnaButton("Send report", { vm.dispute(reason); onDismiss() }, Modifier.fillMaxWidth(), BtnKind.ErrorHigh)
    }
}

/** Review: big check, star pop (200ms in + bounce), mood chips, one primary. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RatingSheet(job: JobView, vm: AppViewModel) {
    val c = dna
    var stars by remember { mutableIntStateOf(0) }
    val tags = remember { mutableStateListOf<String>() }
    val options = if (job.isCustomer) listOf("On time", "Polite", "Good quality", "Fair price") else listOf("Friendly", "Clear request", "Easy to find")
    OverlaySheet({ vm.skipRating() }) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(c.brand), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Check, null, Modifier.size(32.dp), tint = c.onBrand)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(if (job.isCustomer) "Job done" else "${rs(job.net)} added to your wallet", style = DnaType.heading2,
            color = c.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Helper(if (job.isCustomer) "${rs(job.fare)} paid to ${job.otherName}" else "${rs(job.fare)} minus 15% service fee",
            Modifier.fillMaxWidth().padding(top = 4.dp).wrapContentWidth())
        Spacer(Modifier.height(24.dp))
        Text("How was ${firstName(job.otherName)}?", style = DnaType.heading4, color = c.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            (1..5).forEach { n ->
                val on = n <= stars
                val scale by animateFloatAsState(if (on) 1f else .85f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "star")
                Icon(if (on) Icons.Outlined.Star else Icons.Outlined.StarOutline, "$n stars",
                    Modifier.size(44.dp).scale(scale).clickableNoIndication { stars = n }.padding(2.dp),
                    tint = if (on) c.textPrimary else c.bgTertiary)
            }
        }
        if (stars > 0) {
            Spacer(Modifier.height(16.dp))
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { t -> ChoiceChip(t, t in tags, { if (t in tags) tags.remove(t) else tags.add(t) }) }
            }
        }
        Spacer(Modifier.height(24.dp))
        DnaButton("Done", { vm.rate(stars, tags.toList()) }, Modifier.fillMaxWidth(), enabled = stars > 0)
        Text("Skip", style = DnaType.mainBody.copy(fontWeight = FontWeight.SemiBold), color = c.textSecondary, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).clickableNoIndication { vm.skipRating() })
    }
}
