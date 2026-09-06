package org.sih.itantra.core.demo

import org.sih.itantra.core.common.IndicLanguage

/**
 * Predefined deterministic scenarios for the SIH Tactical Mission Demonstration.
 */
enum class DemoScenario(
    val id: Int,
    val title: String,
    val subtitle: String,
    val badgeLabel: String
) {
    NORMAL_VOICE(
        id = 1,
        title = "NORMAL VOICE MESSAGE",
        subtitle = "Voice -> STT -> Text Packet -> HMAC -> Mesh TX -> Delivery ACK -> Receiver TTS",
        badgeLabel = "SCENARIO 1"
    ),
    SEMANTIC_COMPRESSION(
        id = 2,
        title = "SEMANTIC EMERGENCY COMPRESSION",
        subtitle = "Emergency Speech -> 6-Byte Binary -> 90% Wire Reduction -> Instant Priority TX",
        badgeLabel = "SCENARIO 2"
    ),
    MULTI_HOP_DISTRESS(
        id = 3,
        title = "MULTI-HOP DISTRESS + LOCATION",
        subtitle = "Node A -> Node B -> Node C (2 Hops) -> GPS Payload -> AODV Route -> Verified Delivery",
        badgeLabel = "SCENARIO 3"
    ),
    CONGESTION_PREEMPTION(
        id = 4,
        title = "CONGESTION & EMERGENCY PRE-EMPTION",
        subtitle = "Outbound Queue Saturation -> Distress Enqueued -> Pre-empts Normal Traffic -> Sent First",
        badgeLabel = "SCENARIO 4"
    ),
    FAILURE_RESILIENCE(
        id = 5,
        title = "LINK FAILURE & ALTERNATE REROUTE",
        subtitle = "Primary Relay (B) Fails -> RERR Broadcast -> Dynamic Reroute via Node D -> Delivered",
        badgeLabel = "SCENARIO 5"
    )
}

/**
 * End-to-end stages of iTantra's tactical messaging pipeline.
 */
enum class PipelineStage(val label: String, val shortLabel: String) {
    VOICE("Voice Audio Capture", "MIC"),
    STT("Offline Neural STT", "STT"),
    SEMANTIC_OR_TEXT("Semantic / Text Encoding", "ENC"),
    AUTH("HMAC-SHA256 Authentication", "AUTH"),
    QOS("Tactical QoS Scheduler", "QOS"),
    ROUTE("AODV MANET Routing", "ROUTE"),
    TX_RELAY("RFCOMM / Wi-Fi Mesh Relay", "TX"),
    ACK("Delivery Receipt Confirmation", "ACK"),
    TTS("Offline Receiver TTS", "TTS")
}

/**
 * Lifecycle state of a pipeline stage in the active demonstration.
 */
enum class StageState {
    IDLE,
    ACTIVE,
    SUCCESS,
    FAILED
}

/**
 * Measured low-bitrate and wire layout metrics for the active demo scenario.
 */
data class LowBitrateMetrics(
    val rawAudioBytes: Long = 64_000L,       // 2 sec 16kHz 16-bit PCM = 64 KB
    val originalTextBytes: Int = 0,          // String byte count
    val payloadBytes: Int = 0,               // Actual transmitted payload
    val semanticBytes: Int? = null,          // 6 bytes if semantic compression active
    val wireBytes: Int = 0,                  // Total on-wire frame
    val payloadSavingsPercent: Double = 0.0, // (raw - payload) / raw or text savings
    val authTagBytes: Int = 8,               // Truncated HMAC-SHA256
    val crcBytes: Int = 4,                   // CRC32
    val headerBytes: Int = 25,               // Packet wire header
    val fragmentCount: Int = 1,              // Number of wire fragments
    val ackSizeBytes: Int = 35               // Standard DeliveryReceipt wire size
)

/**
 * Timing benchmarks captured during the demo step execution.
 */
data class BenchmarkMetrics(
    val sttLatencyMs: Long = 0L,
    val semanticClassificationMicros: Long = 0L,
    val hmacGenMicros: Long = 0L,
    val qosDispatchMicros: Long = 0L,
    val meshHopLatencyMs: Long = 0L,
    val ackRttMs: Long = 0L,
    val ttsLatencyMs: Long = 0L
)

/**
 * Route hop in the tactical network path display.
 */
data class DemoRouteHop(
    val nodeId: Int,
    val label: String,
    val role: String,
    val transport: String,
    val isOnline: Boolean = true,
    val isCurrentHop: Boolean = false
)

/**
 * Immutable state model for the SIH Tactical Mission Dashboard.
 */
data class SihDemoState(
    val isSimulation: Boolean = true,
    val selectedScenario: DemoScenario = DemoScenario.NORMAL_VOICE,
    val currentStepIndex: Int = 0,
    val totalSteps: Int = 8,
    val isAutoRunning: Boolean = false,
    val stageStates: Map<PipelineStage, StageState> = PipelineStage.entries.associateWith { StageState.IDLE },
    val statusSummary: String = "DEMO READY · SELECT SCENARIO OR TAP START",
    val activeLanguage: IndicLanguage = IndicLanguage.ENGLISH,
    val sampleUtterance: String = "Base camp, patrol team status normal. Standing by.",
    val recognizedText: String = "",
    val lowBitrateMetrics: LowBitrateMetrics = LowBitrateMetrics(),
    val benchmarkMetrics: BenchmarkMetrics = BenchmarkMetrics(),
    val securityStatus: String = "HMAC-SHA256 AUTHENTICATED",
    val replayWindowStatus: String = "64-PACKET BITMASK WINDOW OK",
    val networkPath: List<DemoRouteHop> = emptyList(),
    val activeRouteHops: Int = 1,
    val deliveryStatus: String = "STANDBY",
    val queueDepth: Int = 0,
    val maxQueueCapacity: Int = 100,
    val congestionState: String = "NORMAL",
    val distressPreemptions: Long = 0L,
    val judgeScriptNarration: String = "Welcome to iTantra Tactical Radio. Tap START DEMO to trace an offline tactical message through all 9 subsystems."
)
