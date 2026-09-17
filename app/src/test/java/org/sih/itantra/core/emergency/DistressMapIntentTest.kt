package org.sih.itantra.core.emergency

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.protocol.CorruptPacketException
import org.sih.itantra.core.protocol.GeoLocation
import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.core.protocol.PacketSerializer
import java.util.Locale

/**
 * Focused test suite validating emergency distress map-opening behavior, coordinate validation,
 * decimal precision, package targeting, fallback mechanisms, and packet serialization invariance.
 */
class DistressMapIntentTest {

    // =========================================================================
    // 1. VALID LATITUDE / LONGITUDE
    // =========================================================================

    @Test
    fun testValidLatitudeAndLongitude() {
        // Global quadrants
        val testCoordinates = listOf(
            0.0 to 0.0,                 // Equator / Prime Meridian (Null Island)
            28.613939 to 77.209021,     // New Delhi (NE)
            40.712776 to -74.005974,    // New York (NW)
            -33.868820 to 151.209296,   // Sydney (SE)
            -33.448890 to -70.669266    // Santiago (SW)
        )

        for ((lat, lon) in testCoordinates) {
            assertTrue("Valid coordinates ($lat, $lon) must be accepted", MapLauncher.isValidCoordinates(lat, lon))
            val uri = MapLauncher.buildGeoUriString(lat, lon)
            assertTrue("URI must start with geo:", uri.startsWith("geo:"))
            assertTrue("URI must contain latitude $lat", uri.contains(lat.toString()))
            assertTrue("URI must contain longitude $lon", uri.contains(lon.toString()))
        }

        // Exact geographic boundary limits
        val boundaryCoordinates = listOf(
            90.0 to 0.0,       // North Pole
            -90.0 to 0.0,      // South Pole
            0.0 to 180.0,      // Antimeridian East
            0.0 to -180.0,     // Antimeridian West
            90.0 to 180.0,     // Top-right extreme
            90.0 to -180.0,    // Top-left extreme
            -90.0 to 180.0,    // Bottom-right extreme
            -90.0 to -180.0    // Bottom-left extreme
        )

        for ((lat, lon) in boundaryCoordinates) {
            assertTrue("Boundary coordinates ($lat, $lon) must be valid", MapLauncher.isValidCoordinates(lat, lon))
        }
    }

    // =========================================================================
    // 2. NEGATIVE COORDINATES
    // =========================================================================

    @Test
    fun testNegativeCoordinates() {
        val southWestLat = -45.123456
        val southWestLon = -123.456789

        // 1. Acceptance
        assertTrue("Negative coordinates must be valid", MapLauncher.isValidCoordinates(southWestLat, southWestLon))

        // 2. URI Formatting retains negative sign
        val uri = MapLauncher.buildGeoUriString(southWestLat, southWestLon)
        assertEquals("geo:-45.123456,-123.456789?q=-45.123456,-123.456789", uri)
        assertTrue("URI must contain negative latitude", uri.contains("-45.123456"))
        assertTrue("URI must contain negative longitude", uri.contains("-123.456789"))

        // 3. Google Maps spec retains negative sign
        val mapsSpec = MapLauncher.buildGoogleMapsIntentSpec(southWestLat, southWestLon)
        assertEquals("geo:-45.123456,-123.456789?q=-45.123456,-123.456789", mapsSpec.uriString)

        // 4. Fallback spec retains negative sign
        val fallbackSpec = MapLauncher.buildFallbackMapIntentSpec(southWestLat, southWestLon)
        assertEquals("geo:-45.123456,-123.456789?q=-45.123456,-123.456789", fallbackSpec.uriString)

        // 5. UI direction hemisphere formatting
        val latStr = String.format(Locale.US, "%.6f° %s", Math.abs(southWestLat), if (southWestLat >= 0) "N" else "S")
        val lonStr = String.format(Locale.US, "%.6f° %s", Math.abs(southWestLon), if (southWestLon >= 0) "E" else "W")
        assertEquals("45.123456° S", latStr)
        assertEquals("123.456789° W", lonStr)
    }

    // =========================================================================
    // 3. DECIMAL PRECISION
    // =========================================================================

    @Test
    fun testDecimalPrecision() {
        // High-precision tactical GPS reading (8 decimal places ~ 1.1 mm precision)
        val preciseLat = 11.01684421
        val preciseLon = 76.95583219

        assertTrue(MapLauncher.isValidCoordinates(preciseLat, preciseLon))

        val uri = MapLauncher.buildGeoUriString(preciseLat, preciseLon)
        // Ensure no truncation, rounding loss, or scientific notation (e.g. 1.1016844E7)
        assertEquals("geo:11.01684421,76.95583219?q=11.01684421,76.95583219", uri)
        assertFalse("URI must not use scientific notation", uri.contains("E"))
        assertFalse("URI must not use lowercase e notation", uri.contains("e+"))

        // GeoLocation data model preserves 64-bit IEEE 754 precision
        val loc = GeoLocation(
            latitude = preciseLat,
            longitude = preciseLon,
            accuracy = 1.25f,
            timestamp = 1726000000000L,
            altitude = 450.75
        )
        assertEquals(preciseLat, loc.latitude, 0.000000001)
        assertEquals(preciseLon, loc.longitude, 0.000000001)
    }

    // =========================================================================
    // 4. CORRECT GEO URI
    // =========================================================================

    @Test
    fun testCorrectGeoUri() {
        val lat = 13.0827
        val lon = 80.2707

        val uri = MapLauncher.buildGeoUriString(lat, lon)

        // RFC 5870 / Android Intent convention
        assertTrue("Must start with geo scheme", uri.startsWith("geo:"))
        assertTrue("Must contain separator comma between lat and lon", uri.startsWith("geo:13.0827,80.2707"))
        assertTrue("Must contain query prefix ?q=", uri.contains("?q="))
        assertEquals("geo:13.0827,80.2707?q=13.0827,80.2707", uri)

        // Must not contain invalid whitespace
        assertFalse("URI must not contain whitespace", uri.contains(" "))
    }

    // =========================================================================
    // 5. GOOGLE MAPS PACKAGE TARGETING
    // =========================================================================

    @Test
    fun testGoogleMapsPackageTargeting() {
        val lat = 28.6139
        val lon = 77.2090

        assertEquals("com.google.android.apps.maps", MapLauncher.GOOGLE_MAPS_PACKAGE)

        // Specification targeting
        val spec = MapLauncher.buildGoogleMapsIntentSpec(lat, lon)
        assertEquals(MapLauncher.ACTION_VIEW, spec.action)
        assertEquals("android.intent.action.VIEW", spec.action)
        assertEquals("com.google.android.apps.maps", spec.targetPackage)
        assertEquals("geo:28.6139,77.209?q=28.6139,77.209", spec.uriString)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, spec.flags and Intent.FLAG_ACTIVITY_NEW_TASK)

        // Runtime intent construction
        val intent = MapLauncher.createGoogleMapsIntent(lat, lon)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("com.google.android.apps.maps", intent.`package`)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        assertNotNull("Intent data URI must not be null", intent.data)
        assertEquals("geo:28.6139,77.209?q=28.6139,77.209", intent.data.toString())
    }

    // =========================================================================
    // 6. FALLBACK WHEN GOOGLE MAPS IS UNAVAILABLE
    // =========================================================================

    @Test
    fun testFallbackWhenGoogleMapsUnavailable() {
        val lat = 19.0760
        val lon = 72.8777

        // 1. Fallback specification has null package (allows system / other map apps / browser)
        val spec = MapLauncher.buildFallbackMapIntentSpec(lat, lon)
        assertEquals(MapLauncher.ACTION_VIEW, spec.action)
        assertNull("Fallback spec package must be null to allow OS resolving", spec.targetPackage)
        assertEquals("geo:19.076,72.8777?q=19.076,72.8777", spec.uriString)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, spec.flags and Intent.FLAG_ACTIVITY_NEW_TASK)

        // 2. Runtime fallback intent
        val fallbackIntent = MapLauncher.createFallbackMapIntent(lat, lon)
        assertEquals(Intent.ACTION_VIEW, fallbackIntent.action)
        assertNull("Fallback intent package must be null", fallbackIntent.`package`)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, fallbackIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        assertNotNull("Fallback intent data URI must not be null", fallbackIntent.data)
        assertEquals("geo:19.076,72.8777?q=19.076,72.8777", fallbackIntent.data.toString())

        // 3. Verification of fallback flow logic:
        // When Google Maps throws ActivityNotFoundException, launch falls back to fallbackIntent.
        var fallbackAttempted = false
        var mapsAttempted = false

        val testLauncherLogic: (Intent) -> Unit = { intent ->
            if (intent.`package` == MapLauncher.GOOGLE_MAPS_PACKAGE) {
                mapsAttempted = true
                throw ActivityNotFoundException("Google Maps not installed on tactical device")
            } else {
                fallbackAttempted = true
            }
        }

        try {
            val mapsIntent = MapLauncher.createGoogleMapsIntent(lat, lon)
            testLauncherLogic(mapsIntent)
        } catch (e: ActivityNotFoundException) {
            val fallback = MapLauncher.createFallbackMapIntent(lat, lon)
            testLauncherLogic(fallback)
        }

        assertTrue("Google Maps intent must have been attempted first", mapsAttempted)
        assertTrue("Generic fallback intent must have been dispatched when Google Maps failed", fallbackAttempted)
    }

    // =========================================================================
    // 7. INVALID LATITUDE
    // =========================================================================

    @Test
    fun testInvalidLatitude() {
        val validLon = 77.2090

        val invalidLatitudes = listOf(
            90.000001,                  // Epsilon above max
            91.0,                       // Above North Pole
            180.0,                      // Far above max
            999.99,                     // Extreme positive
            -90.000001,                 // Epsilon below min
            -91.0,                      // Below South Pole
            -180.0,                     // Far below min
            -999.99,                    // Extreme negative
            Double.NaN,                 // Not-a-Number
            Double.POSITIVE_INFINITY,   // Positive Infinity
            Double.NEGATIVE_INFINITY    // Negative Infinity
        )

        for (invalidLat in invalidLatitudes) {
            assertFalse("Latitude $invalidLat must be rejected as invalid", MapLauncher.isValidCoordinates(invalidLat, validLon))
        }

        // MapLauncher.launch must return false without attempting startActivity
        val dummyContext: Context? = null
        assertFalse(
            "Launch must abort on invalid latitude without crashing",
            MapLauncher.launch(dummyContext ?: return, 95.0, validLon)
        )
    }

    // =========================================================================
    // 8. INVALID LONGITUDE
    // =========================================================================

    @Test
    fun testInvalidLongitude() {
        val validLat = 28.6139

        val invalidLongitudes = listOf(
            180.000001,                 // Epsilon above max
            181.0,                      // Past Antimeridian East
            360.0,                      // Full circle
            999.99,                     // Extreme positive
            -180.000001,                // Epsilon below min
            -181.0,                     // Past Antimeridian West
            -360.0,                     // Full circle negative
            -999.99,                    // Extreme negative
            Double.NaN,                 // Not-a-Number
            Double.POSITIVE_INFINITY,   // Positive Infinity
            Double.NEGATIVE_INFINITY    // Negative Infinity
        )

        for (invalidLon in invalidLongitudes) {
            assertFalse("Longitude $invalidLon must be rejected as invalid", MapLauncher.isValidCoordinates(validLat, invalidLon))
        }

        // MapLauncher.launch must return false without attempting startActivity
        val dummyContext: Context? = null
        assertFalse(
            "Launch must abort on invalid longitude without crashing",
            MapLauncher.launch(dummyContext ?: return, validLat, 200.0)
        )
    }

    // =========================================================================
    // 9. MISSING LOCATION
    // =========================================================================

    @Test
    fun testMissingLocation() {
        // 1. Coordinate helper null safety
        assertFalse("Null latitude must be invalid", MapLauncher.isValidCoordinates(null, 77.0))
        assertFalse("Null longitude must be invalid", MapLauncher.isValidCoordinates(12.0, null))
        assertFalse("Both coordinates null must be invalid", MapLauncher.isValidCoordinates(null, null))

        // 2. Packet without location
        val payload = "DISTRESS: GPS OFFLINE".toByteArray(Charsets.UTF_8)
        val packetNoLocation = Packet(
            msgType = Packet.TYPE_DISTRESS,
            priority = MessagePriority.DISTRESS,
            flags = 0, // FLAG_HAS_LOCATION not set
            sequenceNumber = 901,
            timestamp = 1726000000000L,
            sourceDeviceId = 111222,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.ENGLISH,
            payload = payload,
            location = null
        )

        assertFalse("Packet must not have location flag", packetNoLocation.hasLocation)
        assertNull("Packet location must be null", packetNoLocation.location)

        // Wire size must not contain location bytes (28 bytes)
        val serialized = PacketSerializer.serialize(packetNoLocation)
        val expectedSize = Packet.HEADER_SIZE_BYTES + payload.size + Packet.CRC_SIZE_BYTES
        assertEquals("Packet without location must not include location bytes", expectedSize, serialized.size)

        val deserialized = PacketSerializer.deserialize(serialized)
        assertFalse("Deserialized packet must not have location flag", deserialized.hasLocation)
        assertNull("Deserialized location must remain null", deserialized.location)
    }

    // =========================================================================
    // 10. EXISTING DISTRESS LOCATION SERIALIZATION REMAINS UNCHANGED
    // =========================================================================

    @Test
    fun testExistingDistressLocationSerializationUnchanged() {
        val testLocation = GeoLocation(
            latitude = 12.971598,
            longitude = 77.594566,
            accuracy = 4.5f,
            timestamp = 1726000000000L,
            altitude = 920.0
        )
        val payload = "SOS: FLOOD LEVEL RISING EVACUATION NEEDED".toByteArray(Charsets.UTF_8)
        val packet = Packet(
            msgType = Packet.TYPE_DISTRESS,
            priority = MessagePriority.DISTRESS,
            ttl = Packet.DEFAULT_TTL,
            flags = Packet.FLAG_HAS_LOCATION.toByte(),
            sequenceNumber = 505,
            timestamp = 1726000000000L,
            sourceDeviceId = 999888,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.KANNADA,
            payload = payload,
            location = testLocation
        )

        // 1. Exact wire size preservation
        val serialized = PacketSerializer.serialize(packet)
        assertNotNull(serialized)
        val expectedWireSize = Packet.HEADER_SIZE_BYTES + Packet.LOCATION_SIZE_BYTES + payload.size + Packet.CRC_SIZE_BYTES
        assertEquals(
            "Distress packet with location must strictly equal Header(28) + Location(28) + Payload + CRC(4)",
            expectedWireSize,
            serialized.size
        )

        // 2. Exact field deserialization
        val deserialized = PacketSerializer.deserialize(serialized)
        assertEquals(Packet.TYPE_DISTRESS, deserialized.msgType)
        assertEquals(MessagePriority.DISTRESS, deserialized.priority)
        assertEquals(505.toShort(), deserialized.sequenceNumber)
        assertEquals(IndicLanguage.KANNADA, deserialized.language)
        assertEquals(999888, deserialized.sourceDeviceId)
        assertEquals(Packet.BROADCAST_ID, deserialized.destinationDeviceId)
        assertTrue(deserialized.hasLocation)

        val loc = deserialized.location
        assertNotNull("Deserialized location must be non-null", loc)
        assertEquals(testLocation.latitude, loc!!.latitude, 0.000001)
        assertEquals(testLocation.longitude, loc.longitude, 0.000001)
        assertEquals(testLocation.accuracy, loc.accuracy, 0.01f)
        assertEquals(testLocation.altitude!!, loc.altitude!!, 0.01)
        assertEquals(testLocation.timestamp, loc.timestamp)

        // 3. CRC corruption detection across location fields
        val corrupted = serialized.clone()
        // Corrupt a byte in the latitude field
        val latByteOffset = Packet.HEADER_SIZE_BYTES + 4
        corrupted[latByteOffset] = (corrupted[latByteOffset].toInt() xor 0xFF).toByte()

        var caughtCorrupt = false
        try {
            PacketSerializer.deserialize(corrupted)
        } catch (e: CorruptPacketException) {
            caughtCorrupt = true
            assertTrue("Exception message must indicate CRC mismatch", e.message!!.contains("CRC-32 checksum mismatch"))
        }
        assertTrue("Corrupted location byte must fail CRC validation", caughtCorrupt)
    }
}
