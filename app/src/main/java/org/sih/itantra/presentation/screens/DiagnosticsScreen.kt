package org.sih.itantra.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.presentation.theme.RadarGreen
import org.sih.itantra.presentation.theme.SignalBlue
import org.sih.itantra.presentation.theme.TacticalBackground
import org.sih.itantra.presentation.theme.TacticalBorder
import org.sih.itantra.presentation.theme.TacticalSurface
import org.sih.itantra.presentation.theme.TextPrimary
import org.sih.itantra.presentation.theme.TextSecondary
import org.sih.itantra.presentation.viewmodel.TransceiverViewModel

@Composable
fun DiagnosticsScreen(
    viewModel: TransceiverViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val diag by viewModel.diagnosticsState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TacticalBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "TELEMETRY & DIAGNOSTICS",
                color = RadarGreen,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = TacticalSurface)
            ) {
                Text("CLOSE", color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bandwidth Reduction Highlight Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(TacticalSurface, RoundedCornerShape(8.dp))
                .border(2.dp, RadarGreen, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "BANDWIDTH SAVINGS (TEXT VS RAW AUDIO)",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = String.format("%.1f%% REDUCTION", diag.overallSavingsPercent),
                    color = RadarGreen,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Transmitted: ${diag.totalBytesTransmitted} B | Raw Voice Saved: ${(diag.totalRawAudioBytesSaved / 1024)} KB",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Latency Waterfall Breakdown
        Text(
            text = "LAST MEASURED PIPELINE LATENCY",
            color = SignalBlue,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        val lat = diag.lastLatency
        DiagnosticRow(label = "Audio Utterance Duration", value = "${lat.audioDurationMs} ms")
        DiagnosticRow(label = "On-Device STT Inference", value = String.format("%.1f ms", lat.sttLatencyMs))
        DiagnosticRow(label = "STT Real-Time Factor (RTF)", value = String.format("%.2f", lat.realTimeFactor))
        DiagnosticRow(label = "Protocol Framing + Compression", value = String.format("%.2f ms", lat.encodingLatencyMs))
        DiagnosticRow(label = "Wireless Transit Latency", value = String.format("%.1f ms", lat.transportLatencyMs))
        DiagnosticRow(label = "On-Device TTS Audio Synthesis", value = String.format("%.1f ms", lat.ttsLatencyMs))

        Spacer(modifier = Modifier.height(14.dp))

        // Device Resource Telemetry
        Text(
            text = "DEVICE TELEMETRY",
            color = SignalBlue,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        DiagnosticRow(label = "Target Architecture", value = diag.activeSoC)
        DiagnosticRow(label = "App RAM Footprint", value = String.format("%.1f MB", diag.ramUsageMb))
        DiagnosticRow(label = "Total Packets Transmitted", value = "${diag.packetsSent}")
        DiagnosticRow(label = "Total Packets Received", value = "${diag.packetsReceived}")
        DiagnosticRow(label = "Cloud Inference Dependence", value = "0.0% (STRICTLY OFFLINE)")
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .background(TacticalSurface, RoundedCornerShape(4.dp))
            .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text = label, color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(text = value, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
    Spacer(modifier = Modifier.height(4.dp))
}
