package org.sih.itantra.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.demo.DemoScenario
import org.sih.itantra.core.demo.PipelineStage
import org.sih.itantra.core.demo.StageState
import org.sih.itantra.presentation.theme.LocalRadioColors
import org.sih.itantra.presentation.viewmodel.TransceiverViewModel
import java.util.Locale

/**
 * SIH Tactical Demo Mode — Unified Mission Dashboard & One-Flow Demonstration.
 *
 * Provides evaluators and judges with a single, highly readable screen that exposes
 * the entire offline tactical capability stack:
 * Voice -> STT -> Semantic Compression -> HMAC-SHA256 -> QoS Priority -> MANET Route -> Relay -> ACK -> TTS.
 */
@Composable
fun SihDemoScreen(
    viewModel: TransceiverViewModel,
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current
    val demoState by viewModel.sihDemoState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(radioColors.background)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .verticalScroll(scrollState)
    ) {
        // =====================================================================
        // 1. Top Header: Brand + Simulation Warning + Back
        // =====================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = radioColors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "TACTICAL MISSION DEMO",
                        color = radioColors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "OFFLINE • LOCAL • MESH RADIO",
                        color = radioColors.textTertiary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // High-visibility SIMULATION badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(radioColors.alert.copy(alpha = 0.2f))
                    .border(1.dp, radioColors.alert, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "SIMULATION",
                    color = radioColors.alert,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // =====================================================================
        // 2. 5-Second Judge Executive Summary Banner
        // =====================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(radioColors.surfaceHighlight)
                .border(1.dp, (if (radioColors.isDark) radioColors.sage else radioColors.forest).copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "iTANTRA TACTICAL ARCHITECTURE",
                        color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "ZERO CLOUD",
                        color = radioColors.textTertiary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "VOICE → STT → COMPRESS → SECURE → MESH → ACK",
                    color = radioColors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                if (demoState.selectedScenario == DemoScenario.SEMANTIC_COMPRESSION) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(radioColors.success.copy(alpha = 0.2f))
                            .border(1.dp, radioColors.success, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "⚡ 90.3% PAYLOAD SAVED · 62 BYTES -> 6 BYTES",
                            color = radioColors.success,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // =====================================================================
        // 3. Scenario Selector Carousel / Chips
        // =====================================================================
        Text(
            text = "DEMONSTRATION SCENARIO",
            color = radioColors.textSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DemoScenario.entries.forEach { scenario ->
                val isSelected = demoState.selectedScenario == scenario
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) (if (radioColors.isDark) radioColors.sage else radioColors.forest).copy(alpha = 0.35f)
                            else radioColors.surface
                        )
                        .border(
                            1.dp,
                            if (isSelected) (if (radioColors.isDark) radioColors.sage else radioColors.forest)
                            else radioColors.border.copy(alpha = 0.4f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { viewModel.selectDemoScenario(scenario) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "S-${scenario.id}",
                        color = if (isSelected) radioColors.textPrimary else radioColors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "${demoState.selectedScenario.badgeLabel}: ${demoState.selectedScenario.title}",
            color = radioColors.textPrimary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = demoState.selectedScenario.subtitle,
            color = radioColors.textTertiary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(12.dp))

        // =====================================================================
        // 4. Live Message Pipeline Flow (9-Stage Flowchart)
        // =====================================================================
        Text(
            text = "LIVE MESSAGE PIPELINE",
            color = radioColors.textSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(radioColors.surface)
                .border(1.dp, radioColors.border.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                .padding(10.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "STEP ${demoState.currentStepIndex} / ${demoState.totalSteps}",
                        color = radioColors.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = demoState.deliveryStatus,
                        color = if (demoState.deliveryStatus.contains("DELIVERED")) radioColors.success else (if (radioColors.isDark) radioColors.sage else radioColors.forest),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Pipeline Stages Chips (2 rows of 5 and 4)
                val allStages = PipelineStage.entries
                val row1 = allStages.take(5)
                val row2 = allStages.drop(5)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    row1.forEach { stage ->
                        val state = demoState.stageStates[stage] ?: StageState.IDLE
                        StagePill(stage = stage, state = state, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    row2.forEach { stage ->
                        val state = demoState.stageStates[stage] ?: StageState.IDLE
                        StagePill(stage = stage, state = state, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // =====================================================================
        // 5. Operator Actions & Controls (START, NEXT, FULL RUN, RESET)
        // =====================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // NEXT / START button
            Button(
                onClick = { viewModel.nextDemoStep() },
                modifier = Modifier.weight(1.3f).height(38.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (radioColors.isDark) radioColors.sage else radioColors.forest
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = if (demoState.currentStepIndex == 0) Icons.Default.PlayArrow else Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (demoState.currentStepIndex == 0) "START" else "NEXT (${demoState.currentStepIndex}/${demoState.totalSteps})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White
                )
            }

            // RUN FULL DEMO button
            Button(
                onClick = {
                    if (demoState.isAutoRunning) viewModel.stopAutoRun()
                    else viewModel.runFullDemo()
                },
                modifier = Modifier.weight(1.3f).height(38.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (demoState.isAutoRunning) radioColors.alert else radioColors.surfaceHighlight
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (demoState.isAutoRunning) Color.White else radioColors.textPrimary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (demoState.isAutoRunning) "STOP" else "FULL AUTO",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (demoState.isAutoRunning) Color.White else radioColors.textPrimary
                )
            }

            // RESET button
            Button(
                onClick = { viewModel.resetDemo() },
                modifier = Modifier.weight(1f).height(38.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = radioColors.surface
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, radioColors.border.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = radioColors.textSecondary
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "RESET",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = radioColors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // =====================================================================
        // 6. Judge Narration Script Card
        // =====================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(radioColors.surfaceHighlight)
                .border(1.dp, radioColors.border.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "JUDGE SCRIPT NARRATION",
                        color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = demoState.judgeScriptNarration,
                    color = radioColors.textPrimary,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
                if (demoState.recognizedText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = radioColors.border.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "UTTERANCE: \"${demoState.recognizedText}\"",
                        color = radioColors.textSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // =====================================================================
        // 7. Low-Bitrate & Wire Breakdown Card
        // =====================================================================
        val metrics = demoState.lowBitrateMetrics
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(radioColors.surface)
                .border(1.dp, radioColors.border.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column {
                Text(
                    text = "LOW-BITRATE & WIRE LAYOUT",
                    color = radioColors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))

                DemoMetricRow(label = "Raw Audio (16kHz PCM)", value = "${metrics.rawAudioBytes} B")
                DemoMetricRow(label = "Original Transcript", value = "${metrics.originalTextBytes} B")
                DemoMetricRow(
                    label = "Transmitted Payload",
                    value = if (metrics.semanticBytes != null) "${metrics.payloadBytes} B (SEMANTIC)" else "${metrics.payloadBytes} B"
                )
                if (metrics.semanticBytes != null) {
                    DemoMetricRow(label = "Semantic Compression", value = "6 Bytes (Structured INT8)")
                }
                DemoMetricRow(
                    label = "Wire Frame Layout",
                    value = "${metrics.wireBytes} B (Hdr: ${metrics.headerBytes}B + Data: ${metrics.payloadBytes}B + Auth: ${metrics.authTagBytes}B + CRC: ${metrics.crcBytes}B)"
                )
                DemoMetricRow(
                    label = "Bandwidth / Data Savings",
                    value = String.format(Locale.US, "%.1f%% SAVINGS", metrics.payloadSavingsPercent)
                )
                DemoMetricRow(label = "Delivery ACK Packet", value = "${metrics.ackSizeBytes} B (Receipt Wire Frame)")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // =====================================================================
        // 8. Tactical Network Path & QoS Telemetry Card
        // =====================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(radioColors.surface)
                .border(1.dp, radioColors.border.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "NETWORK PATH & TACTICAL QOS",
                        color = radioColors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${demoState.activeRouteHops} HOPS",
                        color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Network Route Nodes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    demoState.networkPath.forEachIndexed { index, hop ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(
                                        if (!hop.isOnline) radioColors.alert.copy(alpha = 0.2f)
                                        else if (hop.isCurrentHop) (if (radioColors.isDark) radioColors.sage else radioColors.forest).copy(alpha = 0.3f)
                                        else radioColors.capsule,
                                        CircleShape
                                    )
                                    .border(
                                        1.dp,
                                        if (!hop.isOnline) radioColors.alert
                                        else if (hop.isCurrentHop) (if (radioColors.isDark) radioColors.sage else radioColors.forest)
                                        else radioColors.border.copy(alpha = 0.5f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${hop.nodeId % 100}",
                                    color = if (!hop.isOnline) radioColors.alert else radioColors.textPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = hop.label,
                                color = if (!hop.isOnline) radioColors.alert else radioColors.textSecondary,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (index < demoState.networkPath.size - 1) {
                            Text(
                                text = "➔",
                                color = radioColors.textTertiary,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = radioColors.border.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(6.dp))

                DemoMetricRow(label = "Outbound Queue Depth", value = "${demoState.queueDepth} / ${demoState.maxQueueCapacity}")
                DemoMetricRow(label = "QoS Congestion State", value = demoState.congestionState)
                DemoMetricRow(label = "Emergency Pre-emptions", value = "${demoState.distressPreemptions}")
                DemoMetricRow(label = "Security Protocol", value = demoState.securityStatus)
                DemoMetricRow(label = "Anti-Replay Window", value = demoState.replayWindowStatus)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // =====================================================================
        // 9. Benchmark Summary Card
        // =====================================================================
        val bench = demoState.benchmarkMetrics
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(radioColors.surface)
                .border(1.dp, radioColors.border.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ON-DEVICE BENCHMARK TIMINGS",
                        color = radioColors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "OFFLINE NATIVE",
                        color = radioColors.success,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                DemoMetricRow(label = "Offline STT Latency", value = "${bench.sttLatencyMs} ms")
                DemoMetricRow(
                    label = "Semantic Classification",
                    value = if (bench.semanticClassificationMicros > 0) "${bench.semanticClassificationMicros} µs" else "N/A"
                )
                DemoMetricRow(label = "HMAC-SHA256 Generation", value = "${bench.hmacGenMicros} µs")
                DemoMetricRow(label = "Tactical QoS Dispatch", value = "${bench.qosDispatchMicros} µs")
                DemoMetricRow(label = "Mesh Transit (Per Hop)", value = "${bench.meshHopLatencyMs} ms")
                DemoMetricRow(label = "Delivery ACK RTT", value = "${bench.ackRttMs} ms")
                DemoMetricRow(label = "Receiver Neural TTS", value = "${bench.ttsLatencyMs} ms")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // =====================================================================
        // 10. Navigation to Detailed Diagnostics
        // =====================================================================
        Button(
            onClick = onOpenDiagnostics,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            colors = ButtonDefaults.buttonColors(containerColor = radioColors.surfaceHighlight),
            border = androidx.compose.foundation.BorderStroke(1.dp, (if (radioColors.isDark) radioColors.sage else radioColors.forest).copy(alpha = 0.6f)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = "OPEN FULL TECHNICAL DIAGNOSTICS",
                color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Individual pipeline stage chip with reactive color transitions.
 */
@Composable
private fun StagePill(
    stage: PipelineStage,
    state: StageState,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current

    val bgColor = when (state) {
        StageState.SUCCESS -> radioColors.success.copy(alpha = 0.2f)
        StageState.ACTIVE -> (if (radioColors.isDark) radioColors.sage else radioColors.forest).copy(alpha = 0.35f)
        StageState.FAILED -> radioColors.alert.copy(alpha = 0.25f)
        StageState.IDLE -> radioColors.surfaceHighlight
    }

    val borderColor = when (state) {
        StageState.SUCCESS -> radioColors.success
        StageState.ACTIVE -> (if (radioColors.isDark) radioColors.sage else radioColors.forest)
        StageState.FAILED -> radioColors.alert
        StageState.IDLE -> radioColors.border.copy(alpha = 0.3f)
    }

    val textColor = when (state) {
        StageState.SUCCESS -> radioColors.success
        StageState.ACTIVE -> radioColors.textPrimary
        StageState.FAILED -> radioColors.alert
        StageState.IDLE -> radioColors.textTertiary
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(vertical = 6.dp, horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stage.shortLabel,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = if (state == StageState.ACTIVE || state == StageState.SUCCESS) FontWeight.Black else FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * Key-Value metric row styled with tactical monospace font.
 */
@Composable
private fun DemoMetricRow(label: String, value: String) {
    val radioColors = LocalRadioColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = radioColors.textSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = radioColors.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
