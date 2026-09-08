package org.sih.itantra.core.discovery

import org.sih.itantra.core.common.IndicLanguage

/**
 * Reachability of a discovered node relative to the MANET mesh protocol.
 */
enum class MeshReachabilityState(val label: String, val badge: String) {
    DIRECT_NEIGHBOR("Direct 1-Hop Neighbor", "1-HOP DIRECT"),
    MULTI_HOP_RELAY("Multi-Hop Mesh Route", "MULTI-HOP"),
    RECENTLY_HEARD("Recently Heard (Inactive)", "RECENTLY HEARD"),
    NOT_IN_MESH("Discovered (Not In Mesh)", "DISCOVERED")
}

/**
 * Explicit cryptographic / operator trust classification.
 * Local radio proximity does NOT imply cryptographic verification.
 */
enum class DeviceTrustState(val label: String, val isVerified: Boolean) {
    UNVERIFIED("UNVERIFIED PROXIMITY", false),
    AUTHENTICATED_HMAC("AUTHENTICATED HMAC", true),
    KNOWN_CONTACT("VERIFIED CONTACT", true),
    LOCAL_DEVICE("LOCAL NODE", true)
}

/**
 * Permanent identity facet of a device.
 */
data class NearbyDeviceIdentity(
    val nodeId: Int,
    val callsign: String,
    val isLocalDevice: Boolean = false
) {
    val formattedNodeId: String
        get() = "#$nodeId"
}

/**
 * Physical discovery facet observed via RF beacons / proximity sensors.
 */
data class NearbyDeviceDiscoveryState(
    val discoverySource: DiscoverySourceType,
    val proximityState: ProximityState,
    val signalStrength: SignalStrengthInfo,
    val rawRssi: Int? = null,
    val firstDiscoveredMs: Long = System.currentTimeMillis(),
    val lastSeenMs: Long = System.currentTimeMillis(),
    val relativeTimeText: String = "Just now"
)

/**
 * Networking and routing topology facet.
 */
data class NearbyDeviceNetworkState(
    val meshReachability: MeshReachabilityState,
    val transportCapabilities: List<String> = emptyList(),
    val supportedLanguages: List<IndicLanguage> = listOf(IndicLanguage.HINDI, IndicLanguage.ENGLISH),
    val hopCount: Int? = null,
    val batteryPct: Int? = null
)

/**
 * Unified model for a discovered iTantra node.
 * Strictly separates IDENTITY, DISCOVERY STATE, and NETWORK STATE.
 */
data class NearbyDevice(
    val identity: NearbyDeviceIdentity,
    val discovery: NearbyDeviceDiscoveryState,
    val network: NearbyDeviceNetworkState,
    val trustState: DeviceTrustState = DeviceTrustState.UNVERIFIED
) {
    // Convenience delegates for UI ergonomics
    val nodeId: Int get() = identity.nodeId
    val callsign: String get() = identity.callsign
    val isLocalDevice: Boolean get() = identity.isLocalDevice
    val formattedNodeId: String get() = identity.formattedNodeId

    val discoverySource: DiscoverySourceType get() = discovery.discoverySource
    val proximityState: ProximityState get() = discovery.proximityState
    val signalStrength: SignalStrengthInfo get() = discovery.signalStrength
    val rawRssi: Int? get() = discovery.rawRssi
    val lastSeenMs: Long get() = discovery.lastSeenMs
    val relativeTimeText: String get() = discovery.relativeTimeText

    val meshReachability: MeshReachabilityState get() = network.meshReachability
    val transportCapabilities: List<String> get() = network.transportCapabilities
    val supportedLanguages: List<IndicLanguage> get() = network.supportedLanguages
    val hopCount: Int? get() = network.hopCount
    val batteryPct: Int? get() = network.batteryPct
}

/**
 * Reactive status of the nearby discovery subsystem.
 */
data class DiscoveryScanningState(
    val isScanning: Boolean = false,
    val statusMessage: String = "IDLE",
    val bleStatus: DiscoverySourceStatus = DiscoverySourceStatus.STANDBY,
    val uwbStatus: DiscoverySourceStatus = DiscoverySourceStatus.UNAVAILABLE,
    val meshStatus: DiscoverySourceStatus = DiscoverySourceStatus.STANDBY,
    val discoveredCount: Int = 0,
    val activeSourceCount: Int = 0
)
