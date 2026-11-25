package pk.oneclick.app

import android.annotation.SuppressLint
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.math.cos

data class LatLng(val latitude: Double, val longitude: Double)
enum class PinKind { Provider, You, Me }
data class Pin(val id: String, val lat: Double, val lng: Double, val kind: PinKind, val category: String? = null)

/** JVM screenshot tests have no network; they get a drawn stand-in map. */
val isJvmTest: Boolean = Build.FINGERPRINT == "robolectric"

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

/** Holds the latest map state for the page; the page pulls it once Google Maps has loaded. */
private class MapBridge(var onPin: (String) -> Unit) {
    @Volatile var state: String = ""
    @JavascriptInterface fun state(): String = state
    @JavascriptInterface fun pin(id: String) { android.os.Handler(android.os.Looper.getMainLooper()).post { onPin(id) } }
}

/**
 * Real Google Map of Lahore (Google Maps JavaScript API in a WebView), in the calm One Click style.
 * [focusKey] changes re-centre on [focus] (or fit both ends of [route]); [bottomPadding] keeps the focus above the sheet;
 * [radar] pulses a search circle around [radarAt]. Pins glide to new positions.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LahoreMap(
    pins: List<Pin>, modifier: Modifier = Modifier, focus: LatLng, focusKey: Any = Unit, zoom: Float = 15f,
    bottomPadding: Dp = 0.dp, route: Pair<LatLng, LatLng>? = null, radarAt: LatLng? = null, radar: Boolean = false,
    onPinClick: (String) -> Unit = {},
) {
    if (isJvmTest) {
        val phase = if (radar) rememberInfiniteTransition(label = "radar")
            .animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "p").value else -1f
        DrawnMap(pins, modifier, focus, bottomPadding, route, phase); return
    }
    val ctx = LocalContext.current
    val bridge = remember { MapBridge(onPinClick) }
    bridge.onPin = onPinClick
    val web = remember {
        WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            setBackgroundColor(0xFFF3F5F3.toInt())
            addJavascriptInterface(bridge, "Android")
            webViewClient = WebViewClient()
            loadDataWithBaseURL("https://oneclick.pk/", mapHtml(BuildConfig.MAPS_KEY), "text/html", "utf-8", null)
        }
    }
    DisposableEffect(Unit) { onDispose { web.destroy() } }
    fun pt(l: LatLng) = buildJsonObject { put("lat", l.latitude); put("lng", l.longitude) }
    val state = buildJsonObject {
        putJsonArray("pins") { pins.forEach { p -> add(buildJsonObject { put("id", p.id); put("lat", p.lat); put("lng", p.lng); put("kind", p.kind.name) }) } }
        if (route != null) put("route", JsonArray(listOf(pt(route.first), pt(route.second))))
        put("radar", radar)
        radarAt?.let { put("radarAt", pt(it)) }
        put("focus", pt(focus)); put("zoom", zoom); put("key", JsonPrimitive(focusKey.toString())); put("bottom", bottomPadding.value)
    }.toString()
    bridge.state = state
    AndroidView({ web }, modifier, update = { it.evaluateJavascript("window.update && update($state)", null) })
}

private fun mapHtml(key: String) = """<!doctype html><html><head>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<style>html,body,#m{margin:0;padding:0;height:100%;background:#F3F5F3}</style></head><body><div id="m"></div><script>
const STYLE=[{elementType:'geometry',stylers:[{color:'#f3f5f3'}]},{elementType:'labels.icon',stylers:[{visibility:'off'}]},
{elementType:'labels.text.fill',stylers:[{color:'#6a7570'}]},{elementType:'labels.text.stroke',stylers:[{color:'#ffffff'}]},
{featureType:'administrative.land_parcel',stylers:[{visibility:'off'}]},{featureType:'poi',elementType:'geometry',stylers:[{color:'#e9eeeb'}]},
{featureType:'poi.business',stylers:[{visibility:'off'}]},{featureType:'poi.park',elementType:'geometry',stylers:[{color:'#dcebe2'}]},
{featureType:'poi.park',elementType:'labels.text.fill',stylers:[{color:'#4f8a6c'}]},{featureType:'road',elementType:'geometry',stylers:[{color:'#ffffff'}]},
{featureType:'road.arterial',elementType:'labels.text.fill',stylers:[{color:'#8a948f'}]},{featureType:'road.highway',elementType:'geometry',stylers:[{color:'#f6e6bd'}]},
{featureType:'road.highway',elementType:'geometry.stroke',stylers:[{color:'#ecd59a'}]},{featureType:'road.local',elementType:'labels.text.fill',stylers:[{color:'#a9b2ad'}]},
{featureType:'transit',stylers:[{visibility:'off'}]},{featureType:'water',elementType:'geometry',stylers:[{color:'#cfe3da'}]},
{featureType:'water',elementType:'labels.text.fill',stylers:[{color:'#4f8a6c'}]}];
const ICON={You:'<svg xmlns="http://www.w3.org/2000/svg" width="30" height="30"><circle cx="15" cy="15" r="14" fill="#fff"/><circle cx="15" cy="15" r="8" fill="#E8B84A" stroke="#0B3D2E" stroke-width="4"/></svg>',
 Pro:'<svg xmlns="http://www.w3.org/2000/svg" width="44" height="44"><circle cx="22" cy="22" r="21" fill="#fff"/><circle cx="22" cy="22" r="17" fill="#0B3D2E"/><circle cx="22" cy="22" r="6" fill="#E8B84A"/></svg>'};
let map,markers={},route=null,radar=[],radarOn=false,radarAt=null,lastKey=null,lastBottom=null;
function icon(k){const you=k==='You',s=ICON[you?'You':'Pro'],n=you?30:44;return{url:'data:image/svg+xml;charset=UTF-8,'+encodeURIComponent(s),anchor:new google.maps.Point(n/2,n/2),scaledSize:new google.maps.Size(n,n)}}
function glide(mk,to){const a=mk.getPosition(),t0=performance.now();function f(t){const k=Math.min(1,(t-t0)/900);mk.setPosition({lat:a.lat()+(to.lat-a.lat())*k,lng:a.lng()+(to.lng-a.lng())*k});if(k<1)requestAnimationFrame(f)}requestAnimationFrame(f)}
function frame(t){radar.forEach((c,i)=>{if(!radarOn||!radarAt){c.setVisible(false);return}const f=((t/2200)+i*.5)%1;
 c.setOptions({visible:true,center:radarAt,radius:60+600*f,fillColor:'#0B3D2E',fillOpacity:.16*(1-f),strokeColor:'#0B3D2E',strokeOpacity:.25*(1-f),strokeWeight:2})});requestAnimationFrame(frame)}
window.update=function(s){if(!map)return;const seen={};
 s.pins.forEach(p=>{seen[p.id]=1;const pos={lat:p.lat,lng:p.lng};let mk=markers[p.id];
  if(!mk){mk=markers[p.id]=new google.maps.Marker({map,position:pos,icon:icon(p.kind),zIndex:p.kind==='You'?2:1});mk.addListener('click',()=>Android.pin(p.id))}
  else glide(mk,pos)});
 for(const id in markers)if(!seen[id]){markers[id].setMap(null);delete markers[id]}
 if(s.route){if(!route)route=new google.maps.Polyline({map,strokeColor:'#0B3D2E',strokeWeight:5,strokeOpacity:.9});route.setPath(s.route)}
 else if(route){route.setMap(null);route=null}
 radarOn=s.radar;radarAt=s.radarAt||null;
 if(s.key!==lastKey||s.bottom!==lastBottom){lastKey=s.key;lastBottom=s.bottom;
 if(s.route){const b=new google.maps.LatLngBounds();b.extend(s.route[0]);b.extend(s.route[1]);
  map.fitBounds(b,{top:96,left:56,right:56,bottom:s.bottom+32});google.maps.event.addListenerOnce(map,'idle',()=>{if(map.getZoom()>16)map.setZoom(16)});return}
 map.setZoom(s.zoom);map.setCenter(s.focus);const off=(s.bottom-72)/2;
 if(off){if(map.getProjection())map.panBy(0,off);else google.maps.event.addListenerOnce(map,'idle',()=>map.panBy(0,off))}}};
function init(){map=new google.maps.Map(document.getElementById('m'),{center:{lat:31.5204,lng:74.3587},zoom:15,disableDefaultUI:true,
 clickableIcons:false,gestureHandling:'greedy',styles:STYLE,minZoom:11,
 restriction:{latLngBounds:{north:31.65,south:31.35,west:74.15,east:74.5},strictBounds:false}});
 for(let i=0;i<2;i++)radar.push(new google.maps.Circle({map,clickable:false,visible:false}));
 requestAnimationFrame(frame);const st=Android.state();if(st)update(JSON.parse(st))}
</script><script async src="https://maps.googleapis.com/maps/api/js?key=$key&callback=init&loading=async"></script></body></html>"""

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
