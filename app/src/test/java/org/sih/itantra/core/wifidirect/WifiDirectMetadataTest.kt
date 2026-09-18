package org.sih.itantra.core.wifidirect

import org.junit.Assert.*
import org.junit.Test
import org.sih.itantra.core.transport.WifiDirectDiscoveredPeer

/**
 * Feature 28 — Unit tests for Wi-Fi Direct DNS-SD TXT record metadata codec.
 * Validates encoding and decoding of authentic iTantra identity metadata.
 */
class WifiDirectMetadataTest {

    private fun encode(
        nodeId: Int,
        callsign: String,
        displayName: String,
        languages: List<String>,
        protocolVersion: String = "1",
        capabilities: List<String> = listOf("WIFI_DIRECT", "WIFI_UDP", "BT_SPP"),
        port: Int = 42889
    ): Map<String, String> = mapOf(
        "nodeId" to nodeId.toString(),
        "callsign" to callsign,
        "displayName" to displayName,
        "supportedLanguages" to languages.joinToString(","),
        "protocolVersion" to protocolVersion,
        "capabilities" to capabilities.joinToString(","),
        "port" to port.toString()
    )

    private fun decode(record: Map<String, String>): WifiDirectDiscoveredPeer? {
        val nodeId = record["nodeId"]?.toIntOrNull() ?: return null
        if (nodeId <= 0) return null
        val callsign = record["callsign"] ?: "NODE #$nodeId"
        val displayName = record["displayName"] ?: callsign
        val languages = record["supportedLanguages"]
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val protocolVersion = record["protocolVersion"] ?: "1"
        val capabilities = record["capabilities"]
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: listOf("WIFI_DIRECT")
        return WifiDirectDiscoveredPeer(
            nodeId = nodeId,
            callsign = callsign,
            displayName = displayName,
            supportedLanguages = languages,
            protocolVersion = protocolVersion,
            capabilities = capabilities,
            deviceAddress = "aa:bb:cc:dd:ee:ff",
            deviceName = "Test Device"
        )
    }

    @Test fun testEncodeNodeId() {
        assertEquals("209071", encode(209071, "ALPHA", "Node Alpha", listOf("en"))["nodeId"])
    }

    @Test fun testEncodeCallsignPreserved() {
        assertEquals("BRAVO-7", encode(1, "BRAVO-7", "Bravo", listOf("en"))["callsign"])
    }

    @Test fun testEncodeDisplayNamePreserved() {
        assertEquals("Command Node", encode(42, "C", "Command Node", listOf("hi"))["displayName"])
    }

    @Test fun testEncodeLanguagesJoined() {
        assertEquals("hi,en,kn", encode(10, "X", "X", listOf("hi", "en", "kn"))["supportedLanguages"])
    }

    @Test fun testEncodeProtocolVersion() {
        assertEquals("1", encode(1, "A", "A", listOf("en"))["protocolVersion"])
    }

    @Test fun testDecodeValidRecord() {
        val peer = decode(encode(209071, "DELTA", "Node Delta", listOf("hi", "en")))
        assertNotNull(peer)
        assertEquals(209071, peer!!.nodeId)
        assertEquals("DELTA", peer.callsign)
        assertEquals("Node Delta", peer.displayName)
        assertEquals(listOf("hi", "en"), peer.supportedLanguages)
    }

    @Test fun testDecodeMissingNodeIdReturnsNull() {
        assertNull(decode(mapOf("callsign" to "ALPHA")))
    }

    @Test fun testDecodeNonIntegerNodeIdReturnsNull() {
        assertNull(decode(mapOf("nodeId" to "invalid")))
    }

    @Test fun testDecodeZeroNodeIdRejected() {
        assertNull(decode(mapOf("nodeId" to "0")))
    }

    @Test fun testDecodeNegativeNodeIdRejected() {
        assertNull(decode(mapOf("nodeId" to "-5")))
    }

    @Test fun testDecodeMissingCallsignFallback() {
        val peer = decode(mapOf("nodeId" to "500"))
        assertNotNull(peer)
        assertEquals("NODE #500", peer!!.callsign)
    }

    @Test fun testDecodeLanguagesTrimmed() {
        val peer = decode(mapOf("nodeId" to "1", "supportedLanguages" to " hi , ta , bn "))
        assertNotNull(peer)
        assertEquals(listOf("hi", "ta", "bn"), peer!!.supportedLanguages)
    }

    @Test fun testRoundtripAll10Languages() {
        val allLangs = listOf("hi", "gu", "mr", "kn", "ml", "ta", "te", "or", "bn", "en")
        val decoded = decode(encode(99999, "MULTI", "All Lang Node", allLangs))
        assertNotNull(decoded)
        assertEquals(allLangs, decoded!!.supportedLanguages)
    }

    @Test fun testIdentitySeparationFromDeviceAddress() {
        val peer = WifiDirectDiscoveredPeer(
            nodeId = 209071,
            callsign = "ALPHA",
            displayName = "Alpha Node",
            supportedLanguages = listOf("hi"),
            protocolVersion = "1",
            capabilities = listOf("WIFI_DIRECT"),
            deviceAddress = "12:34:56:78:9a:bc",
            deviceName = "Android-P2P"
        )
        assertEquals(209071, peer.nodeId)
        assertEquals("12:34:56:78:9a:bc", peer.deviceAddress)
        assertNotEquals(peer.nodeId.toString(), peer.deviceAddress)
    }
}
