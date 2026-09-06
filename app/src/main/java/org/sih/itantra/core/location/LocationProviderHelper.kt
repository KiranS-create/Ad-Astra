package org.sih.itantra.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.sih.itantra.core.protocol.GeoLocation
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * Completely offline, on-device location helper for emergency distress packets.
 *
 * Requirements:
 * - Absolutely no Google Maps API, Play Services location library, or cloud dependencies.
 * - One-shot capture: never tracks continuously in background.
 * - Fails fast (3s max) so emergency transmission is never delayed.
 */
object LocationProviderHelper {

    private const val TAG = "LocationProviderHelper"
    private const val FRESHNESS_THRESHOLD_MS = 60_000L // 60 seconds
    private const val ONE_SHOT_TIMEOUT_MS = 3_000L     // 3 seconds max timeout

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * Acquire the freshest practical location fix offline.
     * Returns null if permission is denied, location providers are disabled, or fix times out.
     */
    suspend fun getFreshLocation(context: Context): GeoLocation? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission(context)) {
            Log.w(TAG, "Location permission not granted; proceeding without location metadata.")
            return@withContext null
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager == null) {
            Log.w(TAG, "LocationManager unavailable on system.")
            return@withContext null
        }

        // 1. Check last known locations first for instant fresh fix
        val lastKnown = getBestLastKnownLocation(locationManager)
        val now = System.currentTimeMillis()
        if (lastKnown != null && (now - lastKnown.time) < FRESHNESS_THRESHOLD_MS) {
            Log.i(TAG, "Using fresh last-known fix: lat=${lastKnown.latitude}, lon=${lastKnown.longitude}, acc=${lastKnown.accuracy}m (age=${now - lastKnown.time}ms)")
            return@withContext toGeoLocation(lastKnown)
        }

        // 2. Otherwise, attempt a fast one-shot update with timeout
        val freshFix = withTimeoutOrNull(ONE_SHOT_TIMEOUT_MS) {
            requestSingleFix(locationManager)
        }

        if (freshFix != null) {
            Log.i(TAG, "Acquired fresh one-shot fix: lat=${freshFix.latitude}, lon=${freshFix.longitude}, acc=${freshFix.accuracy}m")
            return@withContext toGeoLocation(freshFix)
        }

        // 3. Fallback to best last known (even if older) if available
        if (lastKnown != null) {
            Log.i(TAG, "One-shot fix timed out; falling back to older last-known fix (age=${now - lastKnown.time}ms)")
            return@withContext toGeoLocation(lastKnown)
        }

        Log.w(TAG, "No GPS/network location fix available within timeout.")
        null
    }

    private fun getBestLastKnownLocation(locationManager: LocationManager): Location? {
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        var bestLocation: Location? = null
        for (provider in providers) {
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    val loc = locationManager.getLastKnownLocation(provider) ?: continue
                    if (bestLocation == null || loc.time > bestLocation.time) {
                        bestLocation = loc
                    }
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException querying provider $provider: ${e.message}")
            } catch (e: Exception) {
                Log.d(TAG, "Provider $provider query error: ${e.message}")
            }
        }
        return bestLocation
    }

    private suspend fun requestSingleFix(locationManager: LocationManager): Location? {
        return suspendCancellableCoroutine { continuation ->
            // Android 30+ (API 30+) provides getCurrentLocation with CancellationSignal
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val cancellationSignal = CancellationSignal()
                continuation.invokeOnCancellation { cancellationSignal.cancel() }

                val executor = Executors.newSingleThreadExecutor()
                try {
                    val provider = when {
                        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                        else -> LocationManager.PASSIVE_PROVIDER
                    }
                    locationManager.getCurrentLocation(
                        provider,
                        cancellationSignal,
                        executor
                    ) { loc ->
                        if (continuation.isActive) {
                            continuation.resume(loc)
                        }
                    }
                } catch (e: SecurityException) {
                    Log.w(TAG, "SecurityException in getCurrentLocation: ${e.message}")
                    if (continuation.isActive) continuation.resume(null)
                } catch (e: Exception) {
                    Log.w(TAG, "Exception in getCurrentLocation: ${e.message}")
                    if (continuation.isActive) continuation.resume(null)
                }
            } else {
                // Pre-API 30: register single-shot LocationListener on main looper
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        try {
                            locationManager.removeUpdates(this)
                        } catch (_: Exception) {}
                        if (continuation.isActive) {
                            continuation.resume(location)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }

                continuation.invokeOnCancellation {
                    try {
                        locationManager.removeUpdates(listener)
                    } catch (_: Exception) {}
                }

                try {
                    val provider = when {
                        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                        else -> null
                    }
                    if (provider != null) {
                        locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                    } else {
                        if (continuation.isActive) continuation.resume(null)
                    }
                } catch (e: SecurityException) {
                    if (continuation.isActive) continuation.resume(null)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
    }

    private fun toGeoLocation(loc: Location): GeoLocation {
        return GeoLocation(
            latitude = loc.latitude,
            longitude = loc.longitude,
            accuracy = loc.accuracy,
            timestamp = loc.time,
            altitude = if (loc.hasAltitude()) loc.altitude else null
        )
    }
}
