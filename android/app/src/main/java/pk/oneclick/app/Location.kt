package pk.oneclick.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

fun hasLocationPermission(ctx: Context) = !isJvmTest && LOCATION_PERMISSIONS.any {
    ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
}

/** One GPS fix for the booking flow, with a short street address. Null when location is off or slow. */
@SuppressLint("MissingPermission")
suspend fun currentPlace(ctx: Context): Place? {
    if (!hasLocationPermission(ctx)) return null
    val client = LocationServices.getFusedLocationProviderClient(ctx)
    val loc = withTimeoutOrNull(15_000) {
        suspendCancellableCoroutine<android.location.Location?> { cont ->
            val cancel = CancellationTokenSource()
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancel.token)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
            cont.invokeOnCancellation { cancel.cancel() }
        }
    } ?: return null
    return Place(loc.latitude, loc.longitude, streetAddress(ctx, loc.latitude, loc.longitude))
}

/** "Street, Area" from Android's geocoder; "Pinned location" if it has nothing. */
private suspend fun streetAddress(ctx: Context, lat: Double, lng: Double): String = withContext(Dispatchers.IO) {
    if (!Geocoder.isPresent()) return@withContext "Pinned location"
    val g = Geocoder(ctx, Locale.ENGLISH)
    val a: Address? = withTimeoutOrNull(5_000) {
        if (Build.VERSION.SDK_INT >= 33) suspendCancellableCoroutine { cont ->
            g.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                override fun onGeocode(list: MutableList<Address>) { cont.resume(list.firstOrNull()) }
                override fun onError(message: String?) { cont.resume(null) }
            })
        } else @Suppress("DEPRECATION") runCatching { g.getFromLocation(lat, lng, 1)?.firstOrNull() }.getOrNull()
    }
    listOfNotNull(a?.thoroughfare, a?.subLocality, a?.locality).distinct().joinToString(", ").take(80).ifBlank { "Pinned location" }
}

/** What the app wants from the tracking service. The service reads this; the view model writes it. */
object Tracking {
    /** True while on a job: a fix every 5 s. Otherwise every 20 s. */
    val fast = MutableStateFlow(false)
    @Volatile var running = false

    fun sync(ctx: Context, on: Boolean, fast: Boolean) {
        this.fast.value = fast
        val intent = Intent(ctx, LocationService::class.java)
        if (on && !running && hasLocationPermission(ctx)) {
            // Android blocks starting it from the background; the next refresh in the foreground retries.
            runCatching { ContextCompat.startForegroundService(ctx, intent) }
        } else if (!on && running) ctx.stopService(intent)
    }
}

/**
 * Foreground service (type location) that sends the provider's GPS fix to the server while they are
 * online or on a job, so customers see the pro moving towards them. Stops when they go offline.
 */
class LocationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var client: FusedLocationProviderClient
    private lateinit var api: Api
    private val callback = object : LocationCallback() {
        override fun onLocationResult(r: LocationResult) {
            val l = r.lastLocation ?: return
            scope.launch { runCatching { api.updateLocation(l.latitude, l.longitude) } }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Tracking.running = true
        val session = Session(this)
        api = RemoteApi(BuildConfig.API_BASE_URL, { session.token }, { ActiveMode.PROVIDER })
        client = LocationServices.getFusedLocationProviderClient(this)
        scope.launch(Dispatchers.Main) { Tracking.fast.collect { request(it) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(ID, notification())
        } catch (e: Exception) { stopSelf() }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun request(fast: Boolean) {
        if (!hasLocationPermission(this)) { stopSelf(); return }
        val every = if (fast) 5_000L else 20_000L
        val req = LocationRequest.Builder(if (fast) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY, every)
            .setMinUpdateIntervalMillis(every / 2).build()
        client.removeLocationUpdates(callback)
        client.requestLocationUpdates(req, callback, Looper.getMainLooper())
    }

    private fun notification(): android.app.Notification {
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Location sharing", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while customers can see where you are"
            })
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification).setColor(0xFF0B3D2E.toInt())
            .setContentTitle("You’re online").setContentText("Sharing your location with customers")
            .setOngoing(true).setContentIntent(open).setPriority(NotificationCompat.PRIORITY_LOW).build()
    }

    override fun onDestroy() {
        client.removeLocationUpdates(callback)
        scope.cancel()
        Tracking.running = false
        super.onDestroy()
    }

    private companion object { const val ID = 42; const val CHANNEL = "location" }
}
