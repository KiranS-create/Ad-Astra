package org.sih.itantra.core.network

import org.sih.itantra.core.health.OverallHealthStatus

/**
 * Feature 14: Canonical adaptive network modes representing real physical/logical
 * network conditions for operator-facing chat communication.
 *
 * Derived deterministically from existing canonical sources:
 * [org.sih.itantra.core.health.CommunicationHealthState],
 * [org.sih.itantra.core.mesh.MeshTopologySnapshot], and
 * [org.sih.itantra.core.persistence.MessageRecord].
 */
enum class AdaptiveNetworkMode(val label: String) {
    /** All active transports operational, peer reachable, zero queue backpressure. */
    HEALTHY("HEALTHY"),

    /** Single transport active or operating in broadcast mode with reduced direct peers. */
    LIMITED("LIMITED"),

    /** Link quality degraded, high packet loss, or non-congested delays detected. */
    DEGRADED("DEGRADED"),

    /** All radio transports inactive or disabled; packets are strictly queued locally. */
    OFFLINE("OFFLINE"),

    /** QoS transmission queue congested; normal traffic delayed; emergency pre-empts. */
    CONGESTED("CONGESTED"),

    /** Peer recognized but route discovery is pending; message retained (never failed). */
    WAITING_FOR_ROUTE("WAITING_FOR_ROUTE"),

    /** Bundle buffered in Delay-Tolerant Networking store (10m TTL) pending ferry/forwarding. */
    DTN_STORED("DTN_STORED"),

    /** Insufficient telemetry. */
    UNKNOWN("UNKNOWN")
}

/**
 * Tactical configuration for the adaptive chat composer.
 */
data class AdaptiveComposerState(
    val pttButtonLabel: String,
    val pttButtonSubtext: String? = null,
    val actionPillText: String? = null,
    val canTransmitImmediately: Boolean = true,
    val isOffline: Boolean = false,
    val isCongested: Boolean = false,
    val isWaitingRoute: Boolean = false,
    val isDtnStored: Boolean = false,
    val emergencyPriorityNotice: String? = null,
    val deliveryExpectationText: String
)

/**
 * Compact tactical network-context banner shown above the message list.
 */
data class AdaptiveNetworkBannerState(
    val bannerText: String,
    val badgeLabel: String,
    val mode: AdaptiveNetworkMode,
    val isVisible: Boolean = true
)

/**
 * Consolidated immutable state consumed by [org.sih.itantra.presentation.screens.IndividualChatScreen].
 */
data class AdaptiveNetworkUiState(
    val mode: AdaptiveNetworkMode,
    val banner: AdaptiveNetworkBannerState,
    val composer: AdaptiveComposerState,
    val peerId: String,
    val routeSummary: String,
    val overallHealthStatus: OverallHealthStatus
)
