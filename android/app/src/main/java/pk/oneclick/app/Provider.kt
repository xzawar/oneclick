package pk.oneclick.app

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.io.File

// ======================= Provider home: status, numbers, requests =======================
private val WAIT_LINES = listOf("Customers nearby can see you", "Looking for requests…", "New requests show up here")

@Composable
fun ProviderHome(s: UiState, vm: AppViewModel) {
    val me = s.me ?: return
    val p = me.provider ?: return
    val job = s.job
    Column(Modifier.fillMaxSize().background(OC.bg).verticalScroll(rememberScrollState()).statusBarsPadding()
        .padding(horizontal = Gap.gutter).padding(bottom = Space.xxl), verticalArrangement = Arrangement.spacedBy(Gap.component)) {
        HomeTop(me.user.name, vm, ring = true)
        val online = p.isOnline || p.isOnJob
        ForestCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (online) OC.gold else OC.forestMuted))
                Spacer(Modifier.width(Space.sm))
                Text(when { p.isOnJob -> "You’re on a job"; p.isOnline -> "You’re online"; else -> "You’re offline" },
                    style = T.bodyTitle.copy(color = OC.forestText))
            }
            Text(when { p.isOnJob -> "Finish this job to get new requests."; online -> "Customers near you can see you on their map."; else -> "Go online to get requests near you." },
                style = T.defaultBody.copy(color = OC.forestMuted))
            Spacer(Modifier.height(Space.sm))
            if (!p.isOnJob) {
                if (p.isOnline) PillButton("Go offline", { vm.setOnline(false) }, kind = Btn.Secondary, loading = s.busy)
                else PillButton("Go online", { vm.setOnline(true) }, icon = Icons.Outlined.PowerSettingsNew, loading = s.busy,
                    enabled = me.wallet.balance >= Backend.MIN_BALANCE)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.cards)) {
            Stat("Balance", rs(me.wallet.balance), Modifier.weight(1f))
            Stat("Rating", "★ ${p.rating}", Modifier.weight(1f))
            Stat("Jobs done", "${p.totalJobs}", Modifier.weight(1f))
        }
        if (me.wallet.balance < Backend.MIN_BALANCE) Alert("Keep at least ${rs(Backend.MIN_BALANCE)} in your wallet to get requests.", Tone.Warn,
            action = "Add money") { vm.tab(Tab.Wallet) }
        job?.let { ActiveBookingCard(it) { vm.open(Page.Track) } }

        if (job == null || job.status !in ON_JOB) {
            Header(if (s.requests.isEmpty()) "Requests near you" else "Requests near you · ${s.requests.size}", Modifier.padding(top = Space.sm))
            when {
                !p.isOnline -> Empty("You’re offline", "Go online to see requests near you.", Icons.Outlined.PowerSettingsNew)
                s.requests.isEmpty() -> {
                    var tick by remember { mutableIntStateOf(0) }
                    LaunchedEffect(Unit) { while (true) { delay(3000); tick++ } }
                    AnimatedContent(WAIT_LINES[tick % WAIT_LINES.size], label = "wait",
                        transitionSpec = { fadeIn(tween(300, easing = Motion.standard)) togetherWith fadeOut(tween(150)) }) { Text(it, style = T.largeBody) }
                    repeat(2) {
                        OutlineCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(48.dp).clip(CircleShape).background(OC.card))
                                Spacer(Modifier.width(Space.md))
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { TextSkeleton(Modifier.width(140.dp)); TextSkeleton(Modifier.width(90.dp)) }
                            }
                            TextSkeleton(Modifier.fillMaxWidth(.7f))
                        }
                    }
                }
                else -> s.requests.forEachIndexed { i, r -> key(r.id) { RequestCard(r, best = i == 0, busy = s.busy) { vm.makeOffer(r.id, it) } } }
            }
        }
    }
}

/** A request: who, what, where, the price as the hero, then accept or offer more. */
@Composable
private fun RequestCard(r: RequestView, best: Boolean, busy: Boolean, onOffer: (Long) -> Unit) {
    OutlineCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(r.customerName, 48.dp)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(r.customerName, style = T.largeBodyBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, null, Modifier.size(16.dp), tint = OC.gold)
                    Text(" ${r.customerRating} · ${if (r.postedSecondsAgo < 5) "just now" else "${r.postedSecondsAgo}s ago"}", style = T.defaultBody)
                }
            }
            Text(rs(r.fare), style = T.bodyTitle)
        }
        ListRow("${categoryName(r.category)} · ${r.description}", subtitle = "${Backend.HOME_ADDRESS} · ${r.distanceKm} km away", icon = categoryIcon(r.category))
        when (r.myOfferStatus) {
            "open" -> {
                Alert("You offered ${rs(r.myOffer ?: 0)}. Waiting for ${firstName(r.customerName)} to choose.", Tone.Info)
                ProgressLine(r.myOfferSecondsLeft / 30f)
            }
            else -> {
                if (r.myOfferStatus == "declined") Alert("${firstName(r.customerName)} chose another offer this time.", Tone.Info)
                if (r.myOfferStatus == "expired") Alert("Your offer ran out of time.", Tone.Info)
                PillButton("Accept for ${rs(r.fare)}", { onOffer(r.fare) }, kind = if (best) Btn.Primary else Btn.Secondary,
                    enabled = !busy, progress = 1f - r.postedSecondsAgo / 60f)
                Text("Or offer more", style = T.groupTitle)
                Row(horizontalArrangement = Arrangement.spacedBy(Gap.chips)) {
                    listOf(100L, 200L, 300L).forEach { add -> Chip(rs(r.fare + add)) { if (!busy) onOffer(r.fare + add) } }
                }
            }
        }
    }
}

// ======================= Provider job: map + floating sheet =======================
@Composable
fun ProviderJobPage(s: UiState, vm: AppViewModel) {
    val job = s.job
    val p = s.me?.provider
    var seen by remember { mutableStateOf(false) }
    if (job != null) seen = true
    LaunchedEffect(job == null, seen) { if (job == null && seen) { delay(400); vm.open(null) } }
    if (job == null || p == null) {
        Box(Modifier.fillMaxSize().background(OC.bg), contentAlignment = Alignment.Center) { TextSkeleton(Modifier.width(200.dp), height = 22.dp) }
        if (job == null && !seen) LaunchedEffect(Unit) { delay(5000); vm.open(null) }
        return
    }
    if (job.needsRating) { SuccessScreen(job, vm); return }
    val me = LatLng(job.providerLat, job.providerLng)
    val customer = LatLng(job.lat, job.lng)
    val pins = listOf(Pin("me", me.latitude, me.longitude, PinKind.Me, p.categories.first()), Pin("c", customer.latitude, customer.longitude, PinKind.You))
    val route = if (job.status in listOf("accepted", "en_route")) me to customer else null
    MapWithSheet(pins, customer, job.status, route, radar = false, onBack = { vm.open(null) }, zoom = 14.6f) { ProviderJob(job, s, vm) }
}

@Composable
private fun ProviderJob(job: JobView, s: UiState, vm: AppViewModel) {
    val ctx = LocalContext.current
    val name = firstName(job.otherName)
    var confirmCancel by remember { mutableStateOf(false) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp: Bitmap? ->
        if (bmp == null) vm.toast("Take a photo of the finished job to continue") else {
            val f = File(ctx.filesDir, "proof_${job.id}.jpg")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            vm.complete(f.absolutePath)
        }
    }
    val title = when {
        job.status == "accepted" -> "Head to $name"
        job.status == "en_route" -> "You’re on your way"
        job.status == "arrived" -> "You’ve arrived"
        job.awaitingConfirmation -> "Waiting for $name to confirm"
        job.status == "in_progress" -> "Job in progress"
        else -> "$name reported a problem"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusPill(job.status)
        Spacer(Modifier.weight(1f))
        if (job.etaMin != null && job.status == "en_route") Text("About ${job.etaMin} min to go", style = T.defaultBody)
    }
    Text(title, style = T.subsectionTitle)
    ListRow(job.otherName, subtitle = "★ ${job.otherRating} · ${Backend.HOME_ADDRESS} · ${job.distanceKm} km", left = { Avatar(job.otherName, 48.dp) },
        right = { IconCircle(Icons.Outlined.Call, "Call $name", { vm.toast("In-app calls come with the Agora setup") }) })
    Card {
        ReceiptRow("Job", categoryName(job.category) + if (job.description.isNotBlank()) " · ${job.description}" else "")
        ReceiptRow("Price", rs(job.fare))
        ReceiptRow("Our fee (15%)", "− ${rs(job.fare - job.net)}")
        Hr()
        ReceiptRow("You get", rs(job.net), strong = true, valueColor = OC.positive)
    }
    when {
        job.status == "accepted" -> PillButton("I’m on my way", { vm.advance("en_route") }, icon = Icons.Outlined.DirectionsCar, loading = s.busy)
        job.status == "en_route" -> PillButton("I’ve arrived", { vm.advance("arrived") }, icon = Icons.Outlined.Place, loading = s.busy)
        job.status == "arrived" -> PillButton("Start the job", { vm.advance("in_progress") }, icon = Icons.Outlined.PlayArrow, loading = s.busy)
        job.status == "in_progress" && !job.awaitingConfirmation -> {
            Text("When you’re done, take a photo of the work. $name sees it before paying.", style = T.defaultBody)
            PillButton("Take photo and finish", {
                try { camera.launch(null) } catch (e: Exception) { vm.toast("No camera app found") }
            }, icon = Icons.Outlined.PhotoCamera, loading = s.busy)
        }
        job.awaitingConfirmation -> {
            ProofPhoto(job.photo)
            ProgressLine(null)
            Text("You’ll be paid as soon as $name confirms.", style = T.defaultBody)
        }
        job.status == "disputed" -> Alert("Payment is on hold while our team checks. We’ll call you within 24 hours.", Tone.Warn)
    }
    if (job.status in listOf("accepted", "en_route")) PillButton("Cancel job", { confirmCancel = true }, kind = Btn.SecondaryNegative)
    if (confirmCancel) ConfirmDialog("Cancel this job?", "$name is waiting for you. Cancelling can lower your rating.", "Keep the job", "Cancel job",
        { confirmCancel = false }) { confirmCancel = false; vm.cancel() }
}
