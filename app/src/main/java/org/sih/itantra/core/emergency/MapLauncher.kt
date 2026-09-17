package org.sih.itantra.core.emergency

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Specification for map view intents, decoupling intent definition from Android runtime
 * resolution so that intent parameters and URI formatting can be verified deterministically.
 */
data class MapIntentSpec(
    val action: String,
    val uriString: String,
    val targetPackage: String?,
    val flags: Int
)

/**
 * Reusable helper responsible for validating distress coordinates and dispatching
 * map intents to Google Maps with graceful fallback to standard system geo-viewers.
 */
object MapLauncher {

    const val GOOGLE_MAPS_PACKAGE: String = "com.google.android.apps.maps"
    const val ACTION_VIEW: String = "android.intent.action.VIEW"

    /**
     * Validates whether latitude and longitude are finite numbers within the standard geographic boundaries:
     * Latitude: [-90.0, 90.0]
     * Longitude: [-180.0, 180.0]
     */
    fun isValidCoordinates(latitude: Double?, longitude: Double?): Boolean {
        if (latitude == null || longitude == null) return false
        if (latitude.isNaN() || longitude.isNaN()) return false
        if (latitude.isInfinite() || longitude.isInfinite()) return false
        return latitude in -90.0..90.0 && longitude in -180.0..180.0
    }

    /**
     * Builds standard geo URI string preserving exact floating-point precision and sign:
     * "geo:<lat>,<lon>?q=<lat>,<lon>"
     */
    fun buildGeoUriString(latitude: Double, longitude: Double): String {
        return "geo:$latitude,$longitude?q=$latitude,$longitude"
    }

    /**
     * Creates an intent specification targeting the official Google Maps application.
     */
    fun buildGoogleMapsIntentSpec(latitude: Double, longitude: Double): MapIntentSpec {
        return MapIntentSpec(
            action = ACTION_VIEW,
            uriString = buildGeoUriString(latitude, longitude),
            targetPackage = GOOGLE_MAPS_PACKAGE,
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        )
    }

    /**
     * Creates a fallback intent specification with no package restriction, allowing any installed
     * geo viewer (browser, OSM, etc.) to handle the location.
     */
    fun buildFallbackMapIntentSpec(latitude: Double, longitude: Double): MapIntentSpec {
        return MapIntentSpec(
            action = ACTION_VIEW,
            uriString = buildGeoUriString(latitude, longitude),
            targetPackage = null,
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        )
    }

    /**
     * Instantiates an Android Intent configured for Google Maps.
     */
    fun createGoogleMapsIntent(latitude: Double, longitude: Double): Intent {
        val spec = buildGoogleMapsIntentSpec(latitude, longitude)
        return Intent(Intent.ACTION_VIEW, Uri.parse(spec.uriString)).apply {
            setPackage(spec.targetPackage)
            addFlags(spec.flags)
        }
    }

    /**
     * Instantiates a generic fallback Android Intent for geo viewing.
     */
    fun createFallbackMapIntent(latitude: Double, longitude: Double): Intent {
        val spec = buildFallbackMapIntentSpec(latitude, longitude)
        return Intent(Intent.ACTION_VIEW, Uri.parse(spec.uriString)).apply {
            addFlags(spec.flags)
        }
    }

    /**
     * Attempts to launch Google Maps first, falling back to any available map viewer.
     * Returns true if an activity was successfully started, false otherwise.
     */
    fun launch(context: Context, latitude: Double, longitude: Double): Boolean {
        if (!isValidCoordinates(latitude, longitude)) {
            return false
        }

        return try {
            val mapsIntent = createGoogleMapsIntent(latitude, longitude)
            context.startActivity(mapsIntent)
            true
        } catch (e: ActivityNotFoundException) {
            try {
                val fallbackIntent = createFallbackMapIntent(latitude, longitude)
                context.startActivity(fallbackIntent)
                true
            } catch (ignored: Exception) {
                false
            }
        } catch (e: Exception) {
            try {
                val fallbackIntent = createFallbackMapIntent(latitude, longitude)
                context.startActivity(fallbackIntent)
                true
            } catch (ignored: Exception) {
                false
            }
        }
    }
}
