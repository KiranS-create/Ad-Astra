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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * Hardened evaluation dashboard with reproducible benchmarks, real runtime readiness indicators,
 * manual mic fallback input, live elapsed timer, and system capability scorecard.
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
    var manualTextState by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(radioColors.background)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .verticalScroll(scrollState)
    ) {
        // =====================================================================
        // 1. Top Header: Brand + Timer + Simulation Badge + Back
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

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Live Demo Timer badge
                if (demoState.elapsedSeconds > 0 || demoState.isTimerRunning) {
                    val mins = demoState.elapsedSeconds / 60
                    val secs = demoState.elapsedSeconds % 60
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(radioColors.surfaceHighlight)
                            .border(1.dp, (if (radioColors.isDark) radioColors.sage else radioColors.forest).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = String.format(Locale.ROOT, "⏱ %02d:%02d", mins, secs),
                            color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
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
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Optional Failure Notice Banner (Graceful recovery)
        if (demoState.failureMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(radioColors.alert.copy(alpha = 0.2f))
                    .border(1.dp, radioColors.alert, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = radioColors.alert, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = demoState.failureMessage ?: "",
                            color = radioColors.alert,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = { viewModel.sihDemoCoordinator.clearFailureNotice() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = radioColors.alert, modifier = Modifier.size(14.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // =====================================================================
        // 2. Current Device Readiness Card (Actual Runtime State)
        // =====================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(radioColors.surface)
                .border(1.dp, radioColors.surfaceHighlight, RoundedCornerShape(10.dp))
                .padding(10.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DEVICE READINESS STATUS",
                        color = radioColors.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "REFRESH ⟳",
                        color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable { viewModel.refreshDemoReadiness() }
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val readiness = demoState.deviceReadiness
                    ReadinessBadge("STT", readiness.sttStatus == "READY", radioColors)
                    ReadinessBadge("TTS", readiness.ttsStatus == "READY", radioColors)
                    ReadinessBadge("NET", readiness.networkStatus == "READY", radioColors)
                    ReadinessBadge("SEC", readiness.securityStatus == "READY", radioColors)
                    ReadinessBadge("MANET", readiness.manetServiceStatus == "RUNNING", radioColors)
                    ReadinessBadge("TOPO", readiness.topologyStatus == "AVAILABLE", radioColors)
                    ReadinessBadge("DEMO", true, radioColors)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // =====================================================================
        // 3. 5-Second Judge Executive Summary Banner
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
        // 4. Scenario Selector Chips
        // =====================================================================
        Text(
            text = "DEMO SCENARIOS",
            color = radioColors.textSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DemoScenario.entries.forEach { scenario ->
                val isSelected = scenario == demoState.selectedScenario
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) (if (radioColors.isDark) radioColors.sage else radioColors.forest).copy(alpha = 0.25f) else radioColors.surface)
                        .border(
                            1.dp,
                            if (isSelected) (if (radioColors.isDark) radioColors.sage else radioColors.forest) else radioColors.surfaceHighlight,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { viewModel.selectDemoScenario(scenario) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "S-${scenario.id}",
                        color = if (isSelected) (if (radioColors.isDark) radioColors.sage else radioColors.forest) else radioColors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Active scenario title & subtitle description
        Text(
            text = "${demoState.selectedScenario.badgeLabel}: ${demoState.selectedScenario.title}",
            color = radioColors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = demoState.selectedScenario.subtitle,
            color = radioColors.textSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(10.dp))

        // =====================================================================
        // 5. 9-Stage Interactive Pipeline Matrix
        // =====================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(radioColors.surface)
                .border(1.dp, radioColors.surfaceHighlight, RoundedCornerShape(10.dp))
                .padding(10.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "9-STAGE CAPABILITY PIPELINE",
                        color = radioColors.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = demoState.deliveryStatus,
                        color = if (demoState.currentStepIndex == demoState.totalSteps) radioColors.success else radioColors.warning,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // First row: Stages 1 to 5
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PipelineStage.entries.take(5).forEach { stage ->
                        StagePill(
                            stage = stage,
                            state = demoState.stageStates[stage] ?: StageState.IDLE,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Second row: Stages 6 to 9
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PipelineStage.entries.drop(5).forEach { stage ->
                        StagePill(
                            stage = stage,
                            state = demoState.stageStates[stage] ?: StageState.IDLE,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // =====================================================================
        // 6. Operator Controls: START / NEXT / FULL AUTO / RESET
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
                    imageVector = if (demoState.isAutoRunning) Icons.Default.Stop else Icons.Default.Speed,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = if (demoState.isAutoRunning) Color.White else (if (radioColors.isDark) radioColors.sage else radioColors.forest)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (demoState.isAutoRunning) "STOP" else "FULL AUTO",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (demoState.isAutoRunning) Color.White else (if (radioColors.isDark) radioColors.sage else radioColors.forest)
                )
            }

            // RESET button
            Button(
                onClick = { viewModel.resetDemo() },
                modifier = Modifier.weight(0.9f).height(38.dp),
                colors = ButtonDefaults.buttonColors(containerColor = radioColors.surface),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, radioColors.surfaceHighlight)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = radioColors.textSecondary
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "RESET",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = radioColors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // =====================================================================
        // 7. Manual Fallback Input (Mic-Independent Demo Path)
        // =====================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(radioColors.surface)
                .border(1.dp, radioColors.surfaceHighlight, RoundedCornerShape(10.dp))
                .padding(10.dp)
        ) {
            Column {
                Text(
                    text = "MANUAL FALLBACK (MIC-INDEPENDENT DEMO)",
                    color = radioColors.textSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = manualTextState,
                        onValueChange = { manualTextState = it },
                        placeholder = {
                            Text(
                                text = "Type tactical message...",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = radioColors.textTertiary
                            )
                        },
                        modifier = Modifier.weight(1f).height(46.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = radioColors.textPrimary
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                            unfocusedBorderColor = radioColors.surfaceHighlight
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.processManualDemoInput(manualTextState)
                        },
                        enabled = manualTextState.isNotBlank(),
                        modifier = Modifier.height(46.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (radioColors.isDark) radioColors.sage else radioColors.forest
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "TEST",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Presets row for instant demonstration without typing
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PresetChip(
                        label = "⚡ Medical Emergency",
                        text = "Medical emergency, officer down at Grid 42, heavy bleeding.",
                        onClick = { manualTextState = it; viewModel.processManualDemoInput(it) },
                        radioColors = radioColors,
                        modifier = Modifier.weight(1f)
                    )
                    PresetChip(
                        label = "Tactical Sitrep",
                        text = "Base camp, patrol team alpha status normal. Standing by.",
                        onClick = { manualTextState = it; viewModel.processManualDemoInput(it) },
                        radioColors = radioColors,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // =====================================================================
        // 8. Evaluator Narration Script & Utterance Card
        // =====================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(radioColors.surface)
                .border(1.dp, radioColors.surfaceHighlight, RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "EVALUATOR SPOKEN SCRIPT",
                        color = radioColors.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "STEP ${demoState.currentStepIndex}/${demoState.totalSteps}",
                        color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = demoState.judgeScriptNarration,
                    color = radioColors.textPrimary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = radioColors.surfaceHighlight)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "UTTERANCE: \"${demoState.sampleUtterance}\"",
                    color = radioColors.textTertiary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                if (demoState.recognizedText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "STT TRANSCRIPT: \"${demoState.recognizedText}\"",
                        color = radioColors.success,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // =====================================================================
        // 9. Low-Bitrate Comparison Card (Illustrative Baseline vs Measured Wire)
        // =====================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(radioColors.surface)
                .border(1.dp, radioColors.surfaceHighlight, RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LOW-BITRATE WIRE COMPARISON",
                        color = radioColors.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "MEASURED WIRE",
                        color = radioColors.textTertiary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                MetricRow(
                    label = "Traditional 16kHz PCM (Illustrative)",
                    value = "64,000 B (2s 16-bit)",
                    valueColor = radioColors.textSecondary
                )
                MetricRow(
                    label = "Original UTF-8 Text",
                    value = "${demoState.lowBitrateMetrics.originalTextBytes} Bytes",
                    valueColor = radioColors.textPrimary
                )
                MetricRow(
                    label = "Transmitted Payload",
                    value = "${demoState.lowBitrateMetrics.payloadBytes} Bytes ${if (demoState.lowBitrateMetrics.semanticBytes != null) "(Semantic)" else "(Text)"}",
                    valueColor = radioColors.success
                )
                MetricRow(
                    label = "Total Wire Frame",
                    value = "${demoState.lowBitrateMetrics.wireBytes} Bytes (Header: 25B + Payload + Auth: 8B + CRC: 4B)",
                    valueColor = if (radioColors.isDark) radioColors.sage else radioColors.forest
                )
                MetricRow(
                    label = "Payload Compression Savings",
                    value = "${String.format(Locale.ROOT, "%.1f", demoState.lowBitrateMetrics.payloadSavingsPercent)}% SAVINGS",
                    valueColor = radioColors.success
                )
                MetricRow(
                    label = "Delivery Receipt Wire Frame",
                    value = "35 Bytes (Compact ACK Frame)",
                    valueColor = radioColors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // =====================================================================
        // 10. Network Path & Tactical QoS Telemetry
        // =====================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(radioColors.surface)
                .border(1.dp, radioColors.surfaceHighlight, RoundedCornerShape(10.dp))
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
                        color = radioColors.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${demoState.activeRouteHops} HOPS",
                        color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Network path hop nodes
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
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            !hop.isOnline -> radioColors.alert.copy(alpha = 0.2f)
                                            hop.isCurrentHop -> (if (radioColors.isDark) radioColors.sage else radioColors.forest).copy(alpha = 0.3f)
                                            else -> radioColors.surfaceHighlight
                                        }
                                    )
                                    .border(
                                        1.dp,
                                        when {
                                            !hop.isOnline -> radioColors.alert
                                            hop.isCurrentHop -> if (radioColors.isDark) radioColors.sage else radioColors.forest
                                            else -> radioColors.textTertiary
                                        },
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    color = when {
                                        !hop.isOnline -> radioColors.alert
                                        hop.isCurrentHop -> if (radioColors.isDark) radioColors.sage else radioColors.forest
                                        else -> radioColors.textSecondary
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = hop.label,
                                color = if (hop.isOnline) radioColors.textPrimary else radioColors.alert,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        if (index < demoState.networkPath.size - 1) {
                            Text(
                                text = "➔",
                                color = radioColors.textTertiary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = radioColors.surfaceHighlight)
                Spacer(modifier = Modifier.height(8.dp))

                MetricRow(label = "Outbound Queue Depth", value = "${demoState.queueDepth} / ${demoState.maxQueueCapacity}", valueColor = radioColors.textPrimary)
                MetricRow(label = "QoS Congestion State", value = demoState.congestionState, valueColor = if (demoState.congestionState == "BUSY") radioColors.warning else radioColors.success)
                MetricRow(label = "Emergency Pre-emptions", value = "${demoState.distressPreemptions}", valueColor = if (demoState.distressPreemptions > 0) radioColors.alert else radioColors.textSecondary)
                MetricRow(label = "Security Protocol", value = demoState.securityStatus, valueColor = if (radioColors.isDark) radioColors.sage else radioColors.forest)
                MetricRow(label = "Anti-Replay Window", value = demoState.replayWindowStatus, valueColor = radioColors.textPrimary)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // =====================================================================
        // 11. Reproducible Benchmark Runner & Statistical Samples Card
        // =====================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(radioColors.surface)
                .border(1.dp, radioColors.surfaceHighlight, RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "REPRODUCIBLE BENCHMARKS",
                        color = radioColors.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "N=10 SAMPLES",
                        color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Benchmark trigger button
                Button(
                    onClick = { viewModel.runDemoBenchmarks() },
                    enabled = !demoState.isBenchmarkRunning,
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = (if (radioColors.isDark) radioColors.sage else radioColors.forest).copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (radioColors.isDark) radioColors.sage else radioColors.forest)
                ) {
                    if (demoState.isBenchmarkRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = if (radioColors.isDark) radioColors.sage else radioColors.forest, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("RUNNING N=10 SAMPLES...", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = if (radioColors.isDark) radioColors.sage else radioColors.forest)
                    } else {
                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (radioColors.isDark) radioColors.sage else radioColors.forest)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (demoState.benchmarkSuiteResult != null) "RE-RUN BENCHMARKS (N=10)" else "RUN REPRODUCIBLE BENCHMARK",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (radioColors.isDark) radioColors.sage else radioColors.forest
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val suite = demoState.benchmarkSuiteResult
                if (suite != null) {
                    // Statistical Summary Table
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("SUBSYSTEM (µs)", color = radioColors.textTertiary, fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1.4f))
                        Text("MIN", color = radioColors.textTertiary, fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.7f))
                        Text("MEDIAN", color = radioColors.success, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.9f))
                        Text("MEAN", color = radioColors.textTertiary, fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.7f))
                        Text("MAX", color = radioColors.textTertiary, fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.7f))
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    StatRow("Semantic Classifier", suite.semanticClassificationMicros, radioColors)
                    StatRow("HMAC Generation", suite.hmacGenerationMicros, radioColors)
                    StatRow("HMAC Verification", suite.hmacVerificationMicros, radioColors)
                    StatRow("Tactical QoS Dispatch", suite.qosSchedulingMicros, radioColors)
                    StatRow("Frag & Reassembly", suite.fragmentationReassemblyMicros, radioColors)
                    StatRow("Delivery Receipt Frame", suite.deliveryReceiptMicros, radioColors)
                    StatRow("AODV Hop Resolution", suite.multiHopTransitMicros, radioColors)

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = radioColors.surfaceHighlight)
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "DETERMINISTIC BENCHMARK SCENARIOS",
                        color = radioColors.textSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    suite.scenarios.forEach { sc ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = sc.scenarioName,
                                color = radioColors.textPrimary,
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1.8f)
                            )
                            Text(
                                text = "${sc.wireBytes} B",
                                color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(0.7f)
                            )
                            Text(
                                text = "${sc.executionLatency.median} µs",
                                color = radioColors.success,
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(0.9f)
                            )
                        }
                    }
                } else {
                    // Default timings before running multi-sample benchmark
                    MetricRow(label = "Offline STT Latency", value = "${demoState.benchmarkMetrics.sttLatencyMs} ms", valueColor = radioColors.textPrimary)
                    MetricRow(label = "Semantic Classification", value = "${demoState.benchmarkMetrics.semanticClassificationMicros} µs", valueColor = radioColors.textPrimary)
                    MetricRow(label = "HMAC-SHA256 Generation", value = "${demoState.benchmarkMetrics.hmacGenMicros} µs", valueColor = radioColors.textPrimary)
                    MetricRow(label = "Tactical QoS Dispatch", value = "${demoState.benchmarkMetrics.qosDispatchMicros} µs", valueColor = radioColors.textPrimary)
                    MetricRow(label = "Mesh Transit (Per Hop)", value = "${demoState.benchmarkMetrics.meshHopLatencyMs} ms", valueColor = radioColors.textPrimary)
                    MetricRow(label = "Delivery ACK RTT", value = "${demoState.benchmarkMetrics.ackRttMs} ms", valueColor = radioColors.textPrimary)
                    MetricRow(label = "Receiver Neural TTS", value = "${demoState.benchmarkMetrics.ttsLatencyMs} ms", valueColor = radioColors.textPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // =====================================================================
        // 12. Final System Scorecard (Capabilities Summary)
        // =====================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(radioColors.surface)
                .border(1.dp, radioColors.surfaceHighlight, RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FINAL SYSTEM SCORECARD",
                        color = radioColors.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "CAPABILITY STATUS",
                        color = radioColors.textTertiary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "System capability status · not a formal certification",
                    color = radioColors.textTertiary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))

                ScorecardCategory("OFFLINE SUBSYSTEMS", listOf("Local Neural STT", "Local Neural TTS", "Deterministic Voice Commands"), radioColors)
                Spacer(modifier = Modifier.height(6.dp))
                ScorecardCategory("LOW BANDWIDTH", listOf("Semantic Compression (90.3% Drop)", "Bounded MTU Fragmentation", "DTN Store-and-Forward"), radioColors)
                Spacer(modifier = Modifier.height(6.dp))
                ScorecardCategory("MESH NETWORKING", listOf("AODV MANET Routing", "Multi-Hop Mesh Relay", "Bluetooth/Wi-Fi Auto-Failover"), radioColors)
                Spacer(modifier = Modifier.height(6.dp))
                ScorecardCategory("RELIABILITY & QOS", listOf("Sequence-Ordered Reassembly", "Delivery ACK Receipt", "Tactical QoS Queue Pre-emption"), radioColors)
                Spacer(modifier = Modifier.height(6.dp))
                ScorecardCategory("SECURITY PROTOCOL", listOf("Truncated HMAC-SHA256 Auth", "64-Packet Anti-Replay Bitmask"), radioColors)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // =====================================================================
        // 13. Navigation to Detailed Diagnostics
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
    val (bg, border, textCol) = when (state) {
        StageState.IDLE -> Triple(
            radioColors.surfaceHighlight,
            radioColors.surfaceHighlight,
            radioColors.textTertiary
        )
        StageState.ACTIVE -> Triple(
            radioColors.warning.copy(alpha = 0.25f),
            radioColors.warning,
            radioColors.warning
        )
        StageState.SUCCESS -> Triple(
            radioColors.success.copy(alpha = 0.25f),
            radioColors.success,
            radioColors.success
        )
        StageState.FAILED -> Triple(
            radioColors.alert.copy(alpha = 0.25f),
            radioColors.alert,
            radioColors.alert
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .padding(vertical = 6.dp, horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stage.shortLabel,
            color = textCol,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun MetricRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = LocalRadioColors.current.textSecondary
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = valueColor
        )
    }
}

@Composable
private fun StatRow(
    label: String,
    stat: org.sih.itantra.core.demo.StatisticalMetric,
    radioColors: org.sih.itantra.presentation.theme.RadioColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = radioColors.textPrimary, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1.4f))
        Text("${stat.min}", color = radioColors.textSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.7f))
        Text("${stat.median}", color = radioColors.success, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.9f))
        Text("${stat.mean}", color = radioColors.textSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.7f))
        Text("${stat.max}", color = radioColors.textSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.7f))
    }
}

@Composable
private fun ReadinessBadge(
    label: String,
    isReady: Boolean,
    radioColors: org.sih.itantra.presentation.theme.RadioColors
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isReady) radioColors.success.copy(alpha = 0.15f) else radioColors.alert.copy(alpha = 0.15f))
            .border(1.dp, if (isReady) radioColors.success.copy(alpha = 0.5f) else radioColors.alert.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = "$label ${if (isReady) "✓" else "✗"}",
            color = if (isReady) radioColors.success else radioColors.alert,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun PresetChip(
    label: String,
    text: String,
    onClick: (String) -> Unit,
    radioColors: org.sih.itantra.presentation.theme.RadioColors,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(radioColors.surfaceHighlight)
            .border(1.dp, radioColors.surfaceHighlight, RoundedCornerShape(6.dp))
            .clickable { onClick(text) }
            .padding(vertical = 4.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = radioColors.textSecondary,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ScorecardCategory(
    title: String,
    items: List<String>,
    radioColors: org.sih.itantra.presentation.theme.RadioColors
) {
    Column {
        Text(
            text = title,
            color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = radioColors.success,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = item,
                        color = radioColors.textSecondary,
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }
        }
    }
}
