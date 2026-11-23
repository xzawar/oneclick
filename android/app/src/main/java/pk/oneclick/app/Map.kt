package pk.oneclick.app

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*
import kotlin.math.cos

enum class PinKind { Provider, You, Me }
data class Pin(val id: String, val lat: Double, val lng: Double, val kind: PinKind, val category: String? = null)

/** JVM screenshot tests have no Google Play services; they get a drawn stand-in map. */
val isJvmTest: Boolean = Build.FINGERPRINT == "robolectric"

private val LAHORE = LatLngBounds(LatLng(31.35, 74.15), LatLng(31.65, 74.50))

fun categoryIcon(slug: String?): ImageVector = when (slug) {
    "plumber" -> Icons.Outlined.Plumbing; "electrician" -> Icons.Outlined.ElectricalServices
    "mechanic" -> Icons.Outlined.CarRepair; "carpenter" -> Icons.Outlined.Carpenter
    "painter" -> Icons.Outlined.FormatPaint; "welder" -> Icons.Outlined.Construction
    else -> Icons.Outlined.HomeRepairService
}

@Composable
private fun PinContent(p: Pin) {
    when (p.kind) {
        PinKind.You -> Box(Modifier.size(26.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(OC.gold).border(4.dp, OC.deep, CircleShape))
        }
        else -> Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(OC.deep), contentAlignment = Alignment.Center) {
                Icon(categoryIcon(p.category), null, Modifier.size(20.dp), tint = OC.gold)
            }
        }
    }
}

/**
 * Real Google Map of Lahore in the calm One Click style. [focusKey] changes re-centre on [focus];
 * [bottomPadding] keeps the focus above the sheet. [radar] pulses a search circle around [radarAt].
 */
@Composable
fun LahoreMap(
    pins: List<Pin>, modifier: Modifier = Modifier, focus: LatLng, focusKey: Any = Unit, zoom: Float = 15f,
    bottomPadding: Dp = 0.dp, route: Pair<LatLng, LatLng>? = null, radarAt: LatLng? = null, radar: Boolean = false,
    onPinClick: (String) -> Unit = {},
) {
    val phase = if (radar) rememberInfiniteTransition(label = "radar")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "p").value else 0f
    if (isJvmTest) { DrawnMap(pins, modifier, focus, bottomPadding, route, if (radar) phase else -1f); return }
    val ctx = LocalContext.current
    val style = remember { MapStyleOptions.loadRawResourceStyle(ctx, R.raw.map_style) }
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(focus, zoom) }
    LaunchedEffect(focusKey) { camera.animate(CameraUpdateFactory.newLatLngZoom(focus, zoom), 600) }
    GoogleMap(
        modifier = modifier, cameraPositionState = camera,
        properties = MapProperties(mapStyleOptions = style, minZoomPreference = 11f, maxZoomPreference = 19f, latLngBoundsForCameraTarget = LAHORE),
        uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false, myLocationButtonEnabled = false,
            compassEnabled = false, tiltGesturesEnabled = false, indoorLevelPickerEnabled = false),
        contentPadding = PaddingValues(top = 72.dp, bottom = bottomPadding),
    ) {
        if (radar && radarAt != null) for (i in 0..1) {
            val f = (phase + i * .5f) % 1f
            Circle(center = radarAt, radius = 60.0 + 600.0 * f, fillColor = OC.deep.copy(alpha = .16f * (1 - f)),
                strokeColor = OC.deep.copy(alpha = .25f * (1 - f)), strokeWidth = 2f)
        }
        route?.let { (a, b) -> Polyline(points = listOf(a, b), color = OC.deep, width = 12f, jointType = 2) }
        pins.forEach { p ->
            key(p.id) {
                val state = remember { MarkerState(LatLng(p.lat, p.lng)) }
                state.position = LatLng(p.lat, p.lng)
                MarkerComposable(p.kind, p.category ?: "", state = state, anchor = Offset(.5f, .5f), zIndex = if (p.kind == PinKind.You) 2f else 1f,
                    onClick = { onPinClick(p.id); true }) { PinContent(p) }
            }
        }
    }
}

/** Stand-in for tests: a quiet street grid with pins placed by a simple local projection (zoom ~15). */
@Composable
private fun DrawnMap(pins: List<Pin>, modifier: Modifier, focus: LatLng, bottomPadding: Dp, route: Pair<LatLng, LatLng>?, radar: Float) {
    BoxWithConstraints(modifier.background(Color(0xFFF3F5F3))) {
        val cx = maxWidth / 2; val cy = (maxHeight - bottomPadding + 72.dp) / 2
        fun pos(lat: Double, lng: Double): Pair<Dp, Dp> {
            val mPerDp = 2.4 // ≈ zoom 15 at Lahore
            val dx = (lng - focus.longitude) * 111_320 * cos(Math.toRadians(focus.latitude)) / mPerDp
            val dy = (focus.latitude - lat) * 110_540 / mPerDp
            return (cx + dx.dp) to (cy + dy.dp)
        }
        Canvas(Modifier.fillMaxSize()) {
            for (i in 0..12) {
                drawLine(Color.White, Offset(0f, i * size.height / 12), Offset(size.width, i * size.height / 12 + 40), strokeWidth = if (i % 4 == 0) 22f else 10f)
                drawLine(Color.White, Offset(i * size.width / 6, 0f), Offset(i * size.width / 6 - 60, size.height), strokeWidth = if (i % 3 == 0) 22f else 10f)
            }
            drawCircle(Color(0xFFDCEBE2), radius = size.width * .18f, center = Offset(size.width * .82f, size.height * .2f))
            if (radar >= 0) {
                val (x, y) = pos(focus.latitude, focus.longitude)
                for (i in 0..1) { val f = (radar + i * .5f) % 1f
                    drawCircle(OC.deep.copy(alpha = .16f * (1 - f)), radius = (30 + 240 * f).dp.toPx(), center = Offset(x.toPx(), y.toPx())) }
            }
            route?.let { (a, b) ->
                val (ax, ay) = pos(a.latitude, a.longitude); val (bx, by) = pos(b.latitude, b.longitude)
                drawLine(OC.deep, Offset(ax.toPx(), ay.toPx()), Offset(bx.toPx(), by.toPx()), strokeWidth = 5.dp.toPx())
            }
        }
        pins.sortedBy { it.kind == PinKind.You }.forEach { p ->
            val (x, y) = pos(p.lat, p.lng)
            Box(Modifier.offset(x - 21.dp, y - 21.dp).size(42.dp), contentAlignment = Alignment.Center) { PinContent(p) }
        }
    }
}
