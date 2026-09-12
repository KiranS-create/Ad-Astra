package org.sih.itantra.core.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.contact.ContactAuthStatus

/**
 * Unit test suite for QrIdentityCodec.
 * Covers encoding, decoding, roundtrips, versioning, determinism, length limits, and security properties.
 */
class QrIdentityCodecTest {

    // Test Scenario 1: Valid payload parses correctly
    @Test
    fun validPayload_parsesCorrectly() {
        val raw = "ITANTRA:1|209070|NODE ALPHA|Alpha Patrol|hi,en"
        val payload = QrIdentityCodec.decode(raw)

        assertNotNull("Payload must not be null", payload)
        assertEquals(1, payload!!.version)
        assertEquals(209070, payload.nodeId)
        assertEquals("NODE ALPHA", payload.callsign)
        assertEquals("Alpha Patrol", payload.displayName)
        assertEquals(listOf(IndicLanguage.HINDI, IndicLanguage.ENGLISH), payload.supportedLanguages)
    }

    // Test Scenario 2: Version mismatch handling (unknown future version)
    @Test
    fun versionMismatch_returnsNullOnUnsupportedFutureVersion() {
        val rawFutureVersion = "ITANTRA:99|209070|NODE ALPHA|Alpha Patrol|hi,en"
        val payload = QrIdentityCodec.decode(rawFutureVersion)
        assertNull("Future/unsupported schema version must be safely rejected", payload)

        val rawZeroVersion = "ITANTRA:0|209070|NODE ALPHA|Alpha Patrol|hi,en"
        assertNull("Schema version 0 must be rejected", QrIdentityCodec.decode(rawZeroVersion))
    }

    // Test Scenario 3: Malformed payload detection (missing fields, bad delimiters)
    @Test
    fun malformedPayload_missingFieldsOrBadDelimiters_returnsNull() {
        // Missing fields (only 3 parts)
        assertNull(QrIdentityCodec.decode("ITANTRA:1|209070|NODE ALPHA"))
        // Empty string
        assertNull(QrIdentityCodec.decode(""))
        // Missing prefix
        assertNull(QrIdentityCodec.decode("1|209070|NODE ALPHA|Lead|hi"))
        // Wrong prefix
        assertNull(QrIdentityCodec.decode("OTHERAPP:1|209070|NODE ALPHA|Lead|hi"))
        // Missing colon in header
        assertNull(QrIdentityCodec.decode("ITANTRA1|209070|NODE ALPHA|Lead|hi"))
    }

    // Test Scenario 6: Successful conversion to ContactIdentity with authStatus = UNVERIFIED
    @Test
    fun conversionToContactIdentity_enforcesUnverifiedAuthStatus() {
        val payload = QrIdentityPayload(
            nodeId = 477124,
            callsign = "BRAVO RECON",
            displayName = "Recon Team Lead",
            supportedLanguages = listOf(IndicLanguage.HINDI, IndicLanguage.MARATHI)
        )

        val identity = payload.toContactIdentity(authStatus = ContactAuthStatus.UNVERIFIED)

        assertEquals(477124, identity.nodeId)
        assertEquals("BRAVO RECON", identity.callsign)
        assertEquals("Recon Team Lead", identity.displayName)
        assertEquals(listOf(IndicLanguage.HINDI, IndicLanguage.MARATHI), identity.supportedLanguages)
        assertEquals(
            "Scanned QR peer identity must ALWAYS be initialized as UNVERIFIED",
            ContactAuthStatus.UNVERIFIED,
            identity.authStatus
        )
    }

    // Test Scenario 7: Encoding roundtrip (encode -> decode -> verify equality)
    @Test
    fun encodingRoundtrip_preservesExactData() {
        val original = QrIdentityPayload(
            nodeId = 312900,
            callsign = "COMMAND BASE",
            displayName = "HQ Tactical Post",
            supportedLanguages = listOf(IndicLanguage.HINDI, IndicLanguage.TAMIL, IndicLanguage.ENGLISH)
        )

        val encoded = QrIdentityCodec.encode(original)
        val decoded = QrIdentityCodec.decode(encoded)

        assertNotNull(decoded)
        assertEquals(original.nodeId, decoded!!.nodeId)
        assertEquals(original.callsign, decoded.callsign)
        assertEquals(original.displayName, decoded.displayName)
        assertEquals(original.supportedLanguages, decoded.supportedLanguages)
        assertEquals(original.version, decoded.version)
        assertEquals(original, decoded)
    }

    // Test Scenario 8: Unsupported / corrupted payload handling
    @Test
    fun corruptedPayload_corruptedBytes_returnsNullSafely() {
        val corruptedStrings = listOf(
            "ITANTRA:1|not_an_int|NODE|Name|hi",
            "ITANTRA:1|-500|NODE|Name|hi",
            "ITANTRA:1||||",
            "???&&&%%%GARBAGE###",
            "ITANTRA:1|209070||Name|hi" // Blank callsign
        )

        for (corrupted in corruptedStrings) {
            val result = QrIdentityCodec.decode(corrupted)
            assertNull("Corrupted string '$corrupted' should decode to null", result)
        }
    }

    // Test Scenario 9: Handling of special characters in callsigns / display names
    @Test
    fun specialCharacters_unicodeAndDelimiters_sanitizedAndPreserved() {
        val payloadWithSpecialChars = QrIdentityPayload(
            nodeId = 123456,
            callsign = "SIG-ALPHA-01",
            displayName = "वार्डन 01 / Unit-X (Lead)",
            supportedLanguages = listOf(IndicLanguage.HINDI)
        )

        val encoded = QrIdentityCodec.encode(payloadWithSpecialChars)
        val decoded = QrIdentityCodec.decode(encoded)

        assertNotNull(decoded)
        assertEquals(123456, decoded!!.nodeId)
        assertEquals("SIG-ALPHA-01", decoded.callsign)
        assertEquals("वार्डन 01 / Unit-X (Lead)", decoded.displayName)
    }

    // Test Scenario 10: Deterministic string output across multiple encodes
    @Test
    fun deterministicOutput_repeatedEncodesProduceIdenticalStrings() {
        val payload = QrIdentityPayload(
            nodeId = 555666,
            callsign = "SCOUT BRAVO",
            displayName = "Outpost Recon",
            supportedLanguages = listOf(IndicLanguage.BENGALI, IndicLanguage.HINDI)
        )

        val run1 = QrIdentityCodec.encode(payload)
        val run2 = QrIdentityCodec.encode(payload)
        val run3 = QrIdentityCodec.encode(payload)

        assertEquals("Encoding must be strictly deterministic", run1, run2)
        assertEquals("Encoding must be strictly deterministic", run2, run3)
    }

    // Test Scenario 11: Empty display name fallback handling
    @Test
    fun emptyDisplayName_decodesSafelyWithFallback() {
        val rawWithEmptyDisplay = "ITANTRA:1|778899|CALLSIGN||hi,en"
        val decoded = QrIdentityCodec.decode(rawWithEmptyDisplay)

        assertNotNull(decoded)
        assertEquals(778899, decoded!!.nodeId)
        assertEquals("CALLSIGN", decoded.callsign)
        assertEquals("", decoded.displayName)

        val identity = decoded.toContactIdentity()
        // displaySubtitle should fall back to "NODE #778899"
        assertEquals("NODE #778899", identity.displaySubtitle)
    }

    // Test Scenario 12: Maximum payload length constraint enforcement (reject payloads > 512 bytes)
    @Test
    fun maximumPayloadLength_rejectsPayloadsExceedingLimit() {
        val longString = "A".repeat(500)
        val oversizedRaw = "ITANTRA:1|209070|NODE|$longString|hi"
        assertTrue(oversizedRaw.length > 512)

        val decoded = QrIdentityCodec.decode(oversizedRaw)
        assertNull("Payload exceeding 512 bytes must be rejected", decoded)
    }

    // Test Scenario 13: Invalid Node ID range / format rejection
    @Test
    fun invalidNodeId_rejectsZeroNegativeAndOutOfRange() {
        assertNull("Zero node ID must be rejected", QrIdentityCodec.decode("ITANTRA:1|0|NODE|Disp|hi"))
        assertNull("Negative node ID must be rejected", QrIdentityCodec.decode("ITANTRA:1|-10|NODE|Disp|hi"))
        assertNull("OutOfRange node ID must be rejected", QrIdentityCodec.decode("ITANTRA:1|10000000|NODE|Disp|hi"))
        assertNull("Non-numeric node ID must be rejected", QrIdentityCodec.decode("ITANTRA:1|ABCDEF|NODE|Disp|hi"))
    }

    // Test Scenario 15: Absence of sensitive key / crypto material in payload
    @Test
    fun payloadContainsZeroSecretsOrKeys() {
        val payload = QrIdentityPayload(
            nodeId = 209070,
            callsign = "ALPHA",
            displayName = "Alpha Lead",
            supportedLanguages = listOf(IndicLanguage.HINDI)
        )
        val encoded = QrIdentityCodec.encode(payload)

        // Verify no cryptographic tokens, keys, hashes, or passwords exist in payload
        assertFalse("Payload must not contain key references", encoded.contains("key", ignoreCase = true))
        assertFalse("Payload must not contain secret references", encoded.contains("secret", ignoreCase = true))
        assertFalse("Payload must not contain token references", encoded.contains("token", ignoreCase = true))
        assertFalse("Payload must not contain private references", encoded.contains("private", ignoreCase = true))
        assertFalse("Payload must not contain hmac references", encoded.contains("hmac", ignoreCase = true))
    }
}
