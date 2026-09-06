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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.presentation.theme.LocalRadioColors
import org.sih.itantra.presentation.viewmodel.TransceiverViewModel
import java.util.Locale

@Composable
fun DiagnosticsScreen(
    viewModel: TransceiverViewModel,
    onOpenModelAudit: () -> Unit = {},
    onOpenManetDemo: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current
    val diag by viewModel.diagnosticsState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(radioColors.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Screen Title
        Text(
            text = "FIELD INSTRUMENT & DIAGNOSTICS",
            color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = 0.5.sp
        )
        Text(
            text = "Real-time acoustic, RF, and neural pipeline metrics",
            color = radioColors.textSecondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.SansSerif
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 1. Bandwidth Reduction Hero Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(radioColors.surface)
                .border(1.5.dp, radioColors.success.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            val hasTransmissions = diag.packetsSent > 0L && diag.overallSavingsPercent != null
            val savingsTitle = if (hasTransmissions) {
                String.format(Locale.US, "%.1f%% SAVINGS", diag.overallSavingsPercent!!)
            } else {
                "--.-% (AWAITING TX)"
            }
            val subtitle = if (hasTransmissions) {
                "Transmitted: ${diag.totalBytesTransmitted} B  |  Raw Voice Saved: ${(diag.totalRawAudioBytesSaved / 1024)} KB"
            } else {
                "Transmitted: 0 B  |  Raw Voice Saved: 0 KB (Awaiting speech transmission)"
            }

            Column {
                Text(
                    text = "BANDWIDTH REDUCTION RATIO",
                    color = radioColors.textSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = savingsTitle,
                    color = if (hasTransmissions) radioColors.success else radioColors.textTertiary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = radioColors.textPrimary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Latency Waterfall Breakdown
        Text(
            text = "LAST MEASURED PIPELINE LATENCY",
            color = radioColors.textSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        val lat = diag.lastLatency
        val hasLatency = lat.audioDurationMs > 0L
        DiagnosticRow(label = "Audio Utterance Duration", value = if (hasLatency) "${lat.audioDurationMs} ms" else "-- ms")
        DiagnosticRow(label = "On-Device STT Inference", value = if (hasLatency) String.format(Locale.US, "%.1f ms", lat.sttLatencyMs) else "-- ms")
        DiagnosticRow(label = "STT Real-Time Factor (RTF)", value = if (hasLatency) String.format(Locale.US, "%.2f", lat.realTimeFactor) else "--")
        DiagnosticRow(label = "Protocol Framing + CRC", value = if (hasLatency) String.format(Locale.US, "%.2f ms", lat.encodingLatencyMs) else "-- ms")
        DiagnosticRow(label = "Wireless Transit Latency", value = if (hasLatency) String.format(Locale.US, "%.1f ms", lat.transportLatencyMs) else "-- ms")
        DiagnosticRow(label = "On-Device TTS Audio Synthesis", value = if (hasLatency) String.format(Locale.US, "%.1f ms", lat.ttsLatencyMs) else "-- ms")

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Empirical Bandwidth Comparison
        Text(
            text = "TRANSMISSION BITRATE COMPARISON",
            color = radioColors.textSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        val bw = diag.lastBandwidth
        val hasMeasuredBw = bw.transmittedPacketBytes > 0L && lat.audioDurationMs > 0L
        val durationSec = lat.audioDurationMs / 1000.0
        val measuredBps = if (hasMeasuredBw && durationSec > 0) {
            (bw.transmittedPacketBytes * 8) / durationSec
        } else 0.0
        val measuredSavings = if (hasMeasuredBw) bw.bandwidthReductionPercent else 0.0
        val barWidth = if (hasMeasuredBw && measuredBps > 0) {
            (measuredBps / 256000.0).toFloat().coerceIn(0.02f, 1f)
        } else 0.02f

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(radioColors.surface)
                .border(1.dp, radioColors.border.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Text(
                text = "TRADITIONAL VOICE (16kHz 16-bit PCM)",
                color = radioColors.alert,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .background(radioColors.alert.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
            )
            Text(
                text = "256,000 bps (32,000 B/s) — 16kHz 16-bit PCM",
                color = radioColors.textTertiary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "iTANTRA NEURAL RADIO ACCESS",
                color = if (hasMeasuredBw) radioColors.success else radioColors.textSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(barWidth)
                    .height(16.dp)
                    .background(if (hasMeasuredBw) radioColors.success else radioColors.textTertiary, RoundedCornerShape(4.dp))
            )
            Text(
                text = if (hasMeasuredBw) {
                    String.format(Locale.US, "%.0f bps — %d Bytes (%.1f%% Savings)", measuredBps, bw.transmittedPacketBytes, measuredSavings)
                } else {
                    "Awaiting transmission to measure bitrate"
                },
                color = if (hasMeasuredBw) radioColors.success else radioColors.textTertiary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Device Hardware & Resource Telemetry
        Text(
            text = "HARDWARE & SYSTEM TELEMETRY",
            color = radioColors.textSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        DiagnosticRow(label = "Target Architecture", value = diag.activeSoC)
        DiagnosticRow(label = "App RAM Usage", value = String.format(Locale.US, "%.1f MB", diag.ramUsageMb))
        DiagnosticRow(label = "Packets Transmitted", value = "${diag.packetsSent}")
        DiagnosticRow(label = "Packets Received", value = "${diag.packetsReceived}")
        DiagnosticRow(label = "Cloud Inference Dependence", value = "0.0% (STRICTLY OFFLINE)")

        Spacer(modifier = Modifier.height(18.dp))

        // 5. Diagnostics Action Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Test Neural Loopback button
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(radioColors.surface)
                    .border(1.dp, radioColors.border.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .clickable { viewModel.testNeuralLoopback() }
                    .padding(vertical = 12.dp, horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "TEST LOOPBACK",
                    color = radioColors.textPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Model Audit button
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(radioColors.surface)
                    .border(1.dp, radioColors.border.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .clickable { onOpenModelAudit() }
                    .padding(vertical = 12.dp, horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = null,
                    tint = radioColors.success,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "10 MODELS AUDIT",
                    color = radioColors.textPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // MANET Topology & Simulation Demo button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(radioColors.surface)
                .border(1.dp, (if (radioColors.isDark) radioColors.sage else radioColors.forest).copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                .clickable { onOpenManetDemo() }
                .padding(vertical = 12.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "MANET TOPOLOGY & ROUTING DEMO",
                color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(radioColors.warning.copy(alpha = 0.2f))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "SIMULATION",
                    color = radioColors.warning,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    val radioColors = LocalRadioColors.current

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(radioColors.surface)
            .border(1.dp, radioColors.border.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp)
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
    Spacer(modifier = Modifier.height(4.dp))
}
