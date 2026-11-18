package pk.oneclick.app

import android.graphics.Color as AColor
import android.graphics.drawable.GradientDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

data class Pin(val id: String, val lat: Double, val lng: Double, val title: String, val color: String, val sizeDp: Int = 22)

private fun dot(color: String, px: Int) = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(AColor.parseColor(color))
    setStroke(px / 6, AColor.WHITE)
    setSize(px, px)
}

/** OpenStreetMap view (demo — production uses Google Maps SDK). */
@Composable
fun OsmMap(pins: List<Pin>, modifier: Modifier = Modifier, center: GeoPoint = GeoPoint(DEMO_LAT, DEMO_LNG), onPinClick: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    val density = ctx.resources.displayMetrics.density
    val map = remember {
        Configuration.getInstance().userAgentValue = ctx.packageName
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            controller.setCenter(center)
        }
    }
    AndroidView(factory = { map }, modifier = modifier, update = { mv ->
        mv.overlays.clear()
        pins.forEach { p ->
            mv.overlays.add(Marker(mv).apply {
                position = GeoPoint(p.lat, p.lng)
                title = p.title
                icon = dot(p.color, (p.sizeDp * density).toInt())
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ -> onPinClick(p.id); true }
            })
        }
        mv.invalidate()
    })
}
