package pk.oneclick.app

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun ProviderHome(s: UiState, vm: AppViewModel) {
    val me = s.me ?: return
    val p = me.provider ?: return
    val job = s.job
    val onJob = job != null && job.status in ON_JOB
    val myPos = if (onJob) LatLng(job!!.providerLat, job.providerLng) else LatLng(p.lat, p.lng)
    val pins = buildList {
        add(Pin("me", myPos.latitude, myPos.longitude, PinKind.Me, p.categories.first()))
        if (onJob) add(Pin("c", job!!.lat, job.lng, PinKind.You))
    }
    val route = if (job != null && job.status in listOf("accepted", "en_route")) myPos to LatLng(job.lat, job.lng) else null
    val focus = if (onJob) LatLng(job!!.lat, job.lng) else LatLng(p.lat, p.lng)
    Box(Modifier.fillMaxSize()) {
        MapPage(pins, focus, job?.status ?: "idle", route, radar = p.isOnline && !onJob && s.requests.isEmpty(), zoom = 14.5f,
            topLeft = { OnlineChip(p.isOnline && !p.isOnJob, onJob) }) {
            AnimatedContent(if (onJob) "job" else "feed", label = "psheet",
                transitionSpec = { fadeIn(tween(220, easing = Motion.emphasized)) togetherWith fadeOut(tween(120)) }) { mode ->
                Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                    if (mode == "job") s.job?.takeIf { it.status in ON_JOB }?.let { ProviderJob(it, s, vm) } else Feed(s, vm, me, p)
                }
            }
        }
        if (job != null && job.needsRating) RatingSheet(job, vm)
    }
}

@Composable
private fun OnlineChip(online: Boolean, onJob: Boolean) {
    Surface(shape = CircleShape, color = OC.bg, shadowElevation = 4.dp) {
        Row(Modifier.height(48.dp).padding(horizontal = Space.lg), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (online || onJob) OC.green else OC.faint))
            Spacer(Modifier.width(Space.sm))
            Text(when { onJob -> "On a job"; online -> "Online"; else -> "Offline" }, style = T.label)
        }
    }
}

private val WAIT_LINES = listOf("Customers nearby can see you", "Looking for requests", "New requests appear here")

@Composable
private fun Feed(s: UiState, vm: AppViewModel, me: Me, p: DbProvider) {
    Column {
        Text("${greeting()}, ${firstName(me.user.name)}", style = T.small)
        Text(if (p.isOnline) "Requests near you" else "You’re offline", style = T.h1.copy(fontSize = 28.sp, lineHeight = 32.sp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        Stat("Balance", rs(me.wallet.balance), Modifier.weight(1f))
        Stat("Rating", "★ ${p.rating}", Modifier.weight(1f))
        Stat("Jobs", "${p.totalJobs}", Modifier.weight(1f))
    }
    if (!p.isOnline) {
        if (me.wallet.balance < Backend.MIN_BALANCE) Note("Top up to get requests — ${rs(Backend.MIN_BALANCE)} minimum", gold = true, action = "Top up") { vm.tab(Tab.Wallet) }
        else Text("Go online and customers nearby will see you on their map.", style = T.small)
        PillButton("Go online", { vm.setOnline(true) }, Modifier.fillMaxWidth(), icon = Icons.Outlined.PowerSettingsNew, loading = s.busy)
        return
    }
    if (s.requests.isEmpty()) {
        var tick by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) { while (true) { delay(3000); tick++ } }
        AnimatedContent(WAIT_LINES[tick % WAIT_LINES.size], label = "wait", transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }) {
            Text(it, style = T.body)
        }
        LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = OC.deep, trackColor = OC.card)
    } else s.requests.forEach { r -> key(r.id) { RequestCard(r, s.busy) { vm.makeOffer(r.id, it) } } }
    PillButton("Go offline", { vm.setOnline(false) }, Modifier.fillMaxWidth(), Btn.Soft, height = 48.dp)
}

/** Order card: customer, place + distance, problem, price, timed accept and quick counter-offers. */
@Composable
private fun RequestCard(r: RequestView, busy: Boolean, onOffer: (Long) -> Unit) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(r.customerName, 44.dp)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(r.customerName, style = T.bodyStrong)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, null, Modifier.size(14.dp), tint = OC.gold)
                    Text(" ${r.customerRating} · ${if (r.postedSecondsAgo < 5) "just now" else "${r.postedSecondsAgo}s ago"}", style = T.small)
                }
            }
            Text(rs(r.fare), style = T.number)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(categoryIcon(r.category), null, Modifier.size(18.dp), tint = OC.deep)
            Spacer(Modifier.width(Space.sm))
            Text("${categoryName(r.category)} · ${r.description}", style = T.body.copy(color = OC.ink))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Place, null, Modifier.size(18.dp), tint = OC.deep)
            Spacer(Modifier.width(Space.sm))
            Text("${Backend.HOME_ADDRESS} · ${r.distanceKm} km", style = T.small)
        }
        when (r.myOfferStatus) {
            "open" -> {
                Text("Offer ${rs(r.myOffer ?: 0)} sent · waiting for ${firstName(r.customerName)}", style = T.small.copy(color = OC.deep))
                CountdownBar(r.myOfferSecondsLeft / 30f)
            }
            else -> {
                if (r.myOfferStatus == "declined") Note("Your fare wasn’t selected")
                if (r.myOfferStatus == "expired") Note("Offer expired")
                PillButton("Accept for ${rs(r.fare)}", { onOffer(r.fare) }, Modifier.fillMaxWidth(), enabled = !busy, height = 48.dp,
                    progress = 1f - r.postedSecondsAgo / 60f)
                Text("Or offer your fare", style = T.tiny)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    listOf(100L, 200L, 300L).forEach { add ->
                        Surface(onClick = { onOffer(r.fare + add) }, enabled = !busy, shape = CircleShape, color = OC.bg, modifier = Modifier.weight(1f).height(40.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text(rs(r.fare + add), style = T.label) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderJob(job: JobView, s: UiState, vm: AppViewModel) {
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
        job.status == "accepted" -> "Head to $name"
        job.status == "en_route" -> "Driving to $name"
        job.status == "arrived" -> "You’ve arrived"
        job.awaitingConfirmation -> "Waiting for $name"
        job.status == "in_progress" -> "Job in progress"
        else -> "$name reported a problem"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            StatusPill(job.status)
            Spacer(Modifier.height(Space.sm))
            Text(title, style = T.title)
        }
        if (job.etaMin != null && job.status == "en_route") Column(horizontalAlignment = Alignment.End) {
            Text("${job.etaMin}", style = T.display.copy(fontSize = 44.sp))
            Text("min away", style = T.tiny)
        }
    }
    Card {
        ListRow(job.otherName, subtitle = "★ ${job.otherRating} (${job.otherRatingCount}) · Customer", left = { Avatar(job.otherName, 48.dp) },
            right = { RoundSoft(Icons.Outlined.Call, "Call", 44.dp, onCard = true) { vm.toast("In-app calls arrive with the Agora setup") } })
        Hr()
        ListRow(Backend.HOME_ADDRESS, subtitle = "${job.distanceKm} km away", icon = Icons.Outlined.Place)
        Hr()
        ListRow("${categoryName(job.category)} · ${job.description.ifBlank { "No details" }}", icon = categoryIcon(job.category))
    }
    Row(Modifier.fillMaxWidth().clip(Radius.lg).background(OC.goldSoft).padding(Space.lg), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("You get", style = T.tiny)
            Text(rs(job.net), style = T.number)
        }
        Text("${rs(job.fare)} − 15% fee", style = T.small)
    }
    when {
        job.status == "accepted" -> PillButton("I’m on my way", { vm.advance("en_route") }, Modifier.fillMaxWidth(), icon = Icons.Outlined.DirectionsCar, loading = s.busy)
        job.status == "en_route" -> PillButton("I’ve arrived", { vm.advance("arrived") }, Modifier.fillMaxWidth(), icon = Icons.Outlined.Place, loading = s.busy)
        job.status == "arrived" -> PillButton("Start job", { vm.advance("in_progress") }, Modifier.fillMaxWidth(), icon = Icons.Outlined.PlayArrow, loading = s.busy)
        job.status == "in_progress" && !job.awaitingConfirmation -> PillButton("Take photo and finish", {
            try { camera.launch(null) } catch (e: Exception) { vm.toast("No camera app found") }
        }, Modifier.fillMaxWidth(), icon = Icons.Outlined.PhotoCamera, loading = s.busy)
        job.awaitingConfirmation -> {
            ProofPhoto(job.photo)
            LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = OC.deep, trackColor = OC.card)
            Text("Payment is released when $name confirms", style = T.small)
        }
        job.status == "disputed" -> Note("Payment is on hold while our team reviews", gold = true)
    }
    if (job.status in listOf("accepted", "en_route")) PillButton("Cancel job", { confirmCancel = true }, Modifier.fillMaxWidth(), Btn.Danger, height = 48.dp)
    if (confirmCancel) ConfirmDialog("Cancel this job?", "$name is waiting. Cancelling can lower your rating.", "Keep job", "Cancel job",
        { confirmCancel = false }) { confirmCancel = false; vm.cancel() }
}
