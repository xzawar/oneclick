package pk.oneclick.app

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.osmdroid.util.GeoPoint
import java.io.File

private val ACTIVE_JOB = listOf("accepted", "en_route", "arrived", "in_progress", "disputed")

@Composable
fun ProviderHome(s: UiState, vm: AppViewModel, openMenu: () -> Unit) {
    val me = s.me ?: return
    val p = me.provider ?: return
    val job = s.job
    val onJob = job != null && job.status in ACTIVE_JOB
    var sheetPx by remember { mutableIntStateOf(0) }
    var recenter by remember { mutableIntStateOf(0) }
    val myPos = if (onJob) GeoPoint(job!!.providerLat, job.providerLng) else GeoPoint(p.lat, p.lng)
    val pins = buildList {
        add(Pin(myPos.latitude, myPos.longitude, PinKind.Provider))
        if (onJob) add(Pin(job!!.lat, job.lng, PinKind.Customer))
    }
    val route = if (job != null && job.status in listOf("accepted", "en_route")) myPos to GeoPoint(job.lat, job.lng) else null
    val focus = if (onJob) GeoPoint(job!!.lat, job.lng) else GeoPoint(p.lat, p.lng)

    Box(Modifier.fillMaxSize()) {
        MapCanvas(pins, Modifier.fillMaxSize(), focus = focus, focusKey = "${job?.status}-$recenter", zoom = 15.0,
            offsetY = sheetPx / 2, route = route, radarAt = GeoPoint(p.lat, p.lng),
            radarPhase = radarPhase(p.isOnline && !onJob && s.requests.isEmpty()))
        RoundButton(Icons.Outlined.Menu, "Menu", openMenu, Modifier.statusBarsPadding().padding(8.dp))
        Text("© OpenStreetMap © CARTO", style = DnaType.caption, color = dna.textSecondary,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp))
        Column(Modifier.align(Alignment.BottomCenter).onSizeChanged { sheetPx = it.height }) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                RoundButton(Icons.Outlined.MyLocation, "My location", { recenter++ })
            }
            Sheet {
                AnimatedContent(if (onJob) "job" else "feed", label = "psheet",
                    transitionSpec = { fadeIn(tween(DnaMotion.fade)) togetherWith fadeOut(tween(DnaMotion.fade)) }) { mode ->
                    Column {
                        if (mode == "job") s.job?.takeIf { it.status in ACTIVE_JOB }?.let { ProviderJobSheet(it, s, vm) }
                        else Feed(s, vm, me, p)
                    }
                }
            }
        }
        if (job != null && job.needsRating) RatingSheet(job, vm)
    }
}

@Composable
private fun Widget(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(DnaShape.s).background(dna.bgSecondary).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(label, style = DnaType.caption, color = dna.textSecondary)
        Text(value, style = DnaType.heading4, color = dna.textPrimary, maxLines = 1)
    }
}

private val WAIT_LINES = listOf("Customers can see you", "Looking for requests nearby", "New requests appear here")

@Composable
private fun Feed(s: UiState, vm: AppViewModel, me: Me, p: DbProvider) {
    val c = dna
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Widget("Balance", rs(me.wallet.balance), Modifier.weight(1f))
        Widget("Rating", "★ ${p.rating}", Modifier.weight(1f))
        Widget("Jobs", "${p.totalJobs}", Modifier.weight(1f))
    }
    Spacer(Modifier.height(16.dp))
    if (!p.isOnline) {
        SectionTitle("You’re offline")
        Helper("Go online to get requests nearby")
        Spacer(Modifier.height(12.dp))
        if (me.wallet.balance < Backend.MIN_BALANCE) {
            InlineAlert("Top up to get requests — ${rs(Backend.MIN_BALANCE)} minimum", AlertKind.Warning, action = "Top up") { vm.open(Page.Wallet) }
            Spacer(Modifier.height(12.dp))
        }
        DnaButton("Go online", { vm.setOnline(true) }, Modifier.fillMaxWidth(), BtnKind.Inverse, loading = s.busy)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(c.success))
        Spacer(Modifier.width(8.dp))
        Text("You’re online", style = DnaType.heading3, color = c.textPrimary, modifier = Modifier.weight(1f))
        DnaButton("Go offline", { vm.setOnline(false) }, kind = BtnKind.Secondary, size = BtnSize.S)
    }
    Spacer(Modifier.height(12.dp))
    if (s.requests.isEmpty()) {
        var tick by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(3000); tick++ } }
        AnimatedContent(WAIT_LINES[tick % WAIT_LINES.size], label = "wait",
            transitionSpec = { fadeIn(tween(DnaMotion.fade)) togetherWith fadeOut(tween(DnaMotion.fade)) }) { Helper(it) }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape), color = c.textPrimary, trackColor = c.bgSecondary)
    } else {
        LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(s.requests, key = { it.id }) { r -> RequestCard(r, s.busy) { vm.makeOffer(r.id, it) } }
        }
    }
}

/** Driver-style order card: customer, place + distance, problem, price, timed accept + quick bids. */
@Composable
private fun RequestCard(r: RequestView, busy: Boolean, onOffer: (Long) -> Unit) {
    val c = dna
    Column(Modifier.fillMaxWidth().clip(DnaShape.s).border(1.dp, c.bgTertiary, DnaShape.s).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(r.customerName, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.customerName, style = DnaType.mainBody, color = c.textPrimary)
                Text("★ ${r.customerRating}", style = DnaType.compactBody, color = c.textSecondary)
            }
            Text(if (r.postedSecondsAgo < 5) "now" else "${r.postedSecondsAgo} s ago", style = DnaType.caption, color = c.textSecondary)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(c.brand).border(2.dp, c.control, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text("${Backend.HOME_ADDRESS} · ${r.distanceKm} km", style = DnaType.compactBody, color = c.textPrimary)
        }
        Spacer(Modifier.height(6.dp))
        Text("${categoryName(r.category)} · ${r.description}", style = DnaType.mainBody, color = c.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(rs(r.fare), style = DnaType.heading3, color = c.textPrimary)
        Spacer(Modifier.height(10.dp))
        when (r.myOfferStatus) {
            "open" -> {
                Helper("Offer ${rs(r.myOffer ?: 0)} sent · waiting for ${firstName(r.customerName)}")
                Spacer(Modifier.height(8.dp))
                CountdownBar(r.myOfferSecondsLeft / 30f)
            }
            else -> {
                if (r.myOfferStatus == "declined") { InlineAlert("Your fare wasn’t selected", AlertKind.Info); Spacer(Modifier.height(8.dp)) }
                if (r.myOfferStatus == "expired") { InlineAlert("Offer expired", AlertKind.Info); Spacer(Modifier.height(8.dp)) }
                DnaButton("Accept for ${rs(r.fare)}", { onOffer(r.fare) }, Modifier.fillMaxWidth(), size = BtnSize.M,
                    enabled = !busy, progress = 1f - r.postedSecondsAgo / 60f)
                Spacer(Modifier.height(8.dp))
                Text("Or offer your fare", style = DnaType.caption, color = c.textSecondary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(100L, 200L, 300L).forEach { add ->
                        DnaButton(rs(r.fare + add), { onOffer(r.fare + add) }, Modifier.weight(1f), BtnKind.Secondary, BtnSize.S, enabled = !busy)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderJobSheet(job: JobView, s: UiState, vm: AppViewModel) {
    val c = dna
    val ctx = LocalContext.current
    val name = firstName(job.otherName)
    var confirmCancel by remember { mutableStateOf(false) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp: Bitmap? ->
        if (bmp == null) vm.toast("Photo proof is required to finish") else {
            val f = File(ctx.filesDir, "proof_${job.id}.jpg")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            vm.complete(f.absolutePath)
        }
    }
    val title = when {
        job.status == "accepted" -> "Go to $name"
        job.status == "en_route" -> "Driving to $name"
        job.status == "arrived" -> "You’ve arrived"
        job.awaitingConfirmation -> "Waiting for $name to confirm"
        job.status == "in_progress" -> "Job in progress"
        else -> "$name reported a problem"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionTitle(title, Modifier.weight(1f))
        if (job.etaMin != null && job.status == "en_route") Column(horizontalAlignment = Alignment.End) {
            Text("${job.etaMin}", style = DnaType.heading1, color = c.textPrimary)
            Text("min", style = DnaType.caption, color = c.textSecondary)
        }
    }
    Spacer(Modifier.height(4.dp))
    Cell(job.otherName, lead = { Avatar(job.otherName, 48.dp) }, titleStyle = DnaType.heading4,
        subtitle = "★ ${job.otherRating} (${job.otherRatingCount}) · Customer",
        end = { RoundButton(Icons.Outlined.Call, "Call", { vm.toast("In-app calls need an Agora App ID") }, size = 48.dp, floating = false) })
    Cell(Backend.HOME_ADDRESS, subtitle = "${job.distanceKm} km away", icon = Icons.Outlined.Place)
    Cell("${categoryName(job.category)} · ${job.description.ifBlank { "No details" }}", icon = categoryIcon(job.category))
    Spacer(Modifier.height(4.dp))
    Text(rs(job.fare), style = DnaType.heading2, color = c.textPrimary)
    Helper("You get ${rs(job.net)} after 15% service fee")
    Spacer(Modifier.height(16.dp))
    when {
        job.status == "accepted" -> DnaButton("I’m on my way", { vm.advance("en_route") }, Modifier.fillMaxWidth(), loading = s.busy)
        job.status == "en_route" -> DnaButton("I’ve arrived", { vm.advance("arrived") }, Modifier.fillMaxWidth(), loading = s.busy)
        job.status == "arrived" -> DnaButton("Start job", { vm.advance("in_progress") }, Modifier.fillMaxWidth(), loading = s.busy)
        job.status == "in_progress" && !job.awaitingConfirmation ->
            DnaButton("Take photo and finish", {
                try { camera.launch(null) } catch (e: Exception) { vm.toast("No camera app found") }
            }, Modifier.fillMaxWidth(), icon = Icons.Outlined.PhotoCamera, loading = s.busy)
        job.awaitingConfirmation -> {
            ProofPhoto(job.photo)
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape), color = c.textPrimary, trackColor = c.bgSecondary)
            Spacer(Modifier.height(8.dp))
            Helper("Payment is released when $name confirms")
        }
        job.status == "disputed" -> InlineAlert("Payment is on hold while our team reviews", AlertKind.Warning)
    }
    if (job.status in listOf("accepted", "en_route")) {
        Spacer(Modifier.height(8.dp))
        DnaButton("Cancel job", { confirmCancel = true }, Modifier.fillMaxWidth(), BtnKind.ErrorLow, BtnSize.M)
    }
    if (confirmCancel) ConfirmDialog("Cancel this job?", "$name is waiting for you. Cancelling can lower your rating.",
        "Keep job", "Cancel job", { confirmCancel = false }) { confirmCancel = false; vm.cancel() }
}
