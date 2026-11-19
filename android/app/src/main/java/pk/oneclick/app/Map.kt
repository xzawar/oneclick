package pk.oneclick.app

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline

// ---- small modifiers used across the UI ----
fun Modifier.clickableNoIndication(onClick: () -> Unit) = composed {
    clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
}
fun Modifier.onFocus(block: (Boolean) -> Unit) = onFocusChanged { block(it.isFocused) }
fun Modifier.borderDna(color: Color) = border(2.dp, color, DnaShape.xs)

enum class PinKind { Provider, You, Customer }
data class Pin(val lat: Double, val lng: Double, val kind: PinKind)

private val LIGHT = XYTileSource("CartoLight", 1, 19, 256, ".png", arrayOf(
    "https://a.basemaps.cartocdn.com/rastertiles/voyager/", "https://b.basemaps.cartocdn.com/rastertiles/voyager/"))
private val DARK = XYTileSource("CartoDark", 1, 19, 256, ".png", arrayOf(
    "https://a.basemaps.cartocdn.com/dark_all/", "https://b.basemaps.cartocdn.com/dark_all/"))

private class RadarOverlay : Overlay() {
    var at: GeoPoint? = null
    var phase = 0f
    var color = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(c: Canvas, map: MapView, shadow: Boolean) {
        val p = at ?: return
        if (shadow) return
        val pt = map.projection.toPixels(p, null)
        val maxR = 200 * map.resources.displayMetrics.density // 400dp circle
        for (i in 0..1) {
            val f = (phase + i * .5f) % 1f
            paint.color = color; paint.alpha = ((1 - f) * 45).toInt()
            c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), maxR * f, paint)
        }
    }
}

/**
 * Full-bleed calm map. [focus] re-centres when [focusKey] changes; [offsetY] pushes the focus point up so it
 * sits in the visible area above the sheet. [radarPhase] ≥ 0 draws the pulsing search circle around [radarAt].
 */
@Composable
fun MapCanvas(
    pins: List<Pin>, modifier: Modifier = Modifier, focus: GeoPoint, focusKey: Any = Unit, zoom: Double = 15.5,
    offsetY: Int = 0, route: Pair<GeoPoint, GeoPoint>? = null, radarAt: GeoPoint? = null, radarPhase: Float = -1f,
) {
    val ctx = LocalContext.current
    val c = dna
    val density = ctx.resources.displayMetrics.density
    val radar = remember { RadarOverlay() }
    var lastKey by remember { mutableStateOf<Any?>(null) }
    val map = remember {
        Configuration.getInstance().userAgentValue = ctx.packageName
        MapView(ctx).apply {
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 11.0
            controller.setZoom(zoom)
            controller.setCenter(focus)
        }
    }
    DisposableEffect(Unit) { onDispose { map.onDetach() } }
    AndroidView(factory = { map }, modifier = modifier, update = { mv ->
        mv.setTileSource(if (c.dark) DARK else LIGHT)
        mv.setMapCenterOffset(0, -offsetY)
        if (lastKey != focusKey) { lastKey = focusKey; mv.controller.animateTo(focus, zoom, 350L) }
        mv.overlays.clear()
        radar.at = radarAt; radar.phase = radarPhase; radar.color = c.textPrimary.toArgb()
        if (radarAt != null && radarPhase >= 0) mv.overlays.add(radar)
        route?.let { (a, b) ->
            mv.overlays.add(Polyline(mv).apply {
                setPoints(listOf(a, b))
                outlinePaint.color = c.textPrimary.toArgb(); outlinePaint.strokeWidth = 4 * density
                outlinePaint.strokeCap = Paint.Cap.ROUND
            })
        }
        pins.forEach { p ->
            mv.overlays.add(Marker(mv).apply {
                position = GeoPoint(p.lat, p.lng)
                icon = pinDrawable(p.kind, density, c)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ -> true }
                infoWindow = null
            })
        }
        mv.invalidate()
    })
}

private fun pinDrawable(kind: PinKind, density: Float, c: DnaColors) = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    val size = when (kind) { PinKind.Provider -> 26; PinKind.You -> 22; PinKind.Customer -> 22 }
    when (kind) {
        PinKind.Provider -> { setColor(Color(0xFF181818).toArgb()); setStroke((4 * density).toInt(), android.graphics.Color.WHITE) }
        else -> { setColor(c.brand.toArgb()); setStroke((5 * density).toInt(), Color(0xFF181818).toArgb()) }
    }
    setSize((size * density).toInt(), (size * density).toInt())
}
