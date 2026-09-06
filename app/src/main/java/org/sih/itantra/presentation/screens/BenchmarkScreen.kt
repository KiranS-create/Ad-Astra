package org.sih.itantra.presentation.screens

import java.util.Locale
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
import org.sih.itantra.presentation.theme.DistressRed
import org.sih.itantra.presentation.theme.RadarGreen
import org.sih.itantra.presentation.theme.SignalBlue
import org.sih.itantra.presentation.theme.TacticalBackground
import org.sih.itantra.presentation.theme.TacticalBorder
import org.sih.itantra.presentation.theme.TacticalSurface
import org.sih.itantra.presentation.theme.TextPrimary
import org.sih.itantra.presentation.theme.TextSecondary
import org.sih.itantra.presentation.viewmodel.TransceiverViewModel

@Composable
fun BenchmarkScreen(
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
                text = "EMPIRICAL BENCHMARKS",
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

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "BANDWIDTH COMPARISON (MEASURED)",
            color = SignalBlue,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Raw Audio vs iTantra text transmission comparison card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TacticalSurface, RoundedCornerShape(8.dp))
                .border(1.dp, TacticalBorder, RoundedCornerShape(8.dp))
                .padding(14.dp)
        ) {
            Text(
                text = "TRADITIONAL VOICE TRANSMISSION (16kHz 16-bit PCM)",
                color = DistressRed,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .background(DistressRed.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
            )
            Text(
                text = "Rate: 256,000 bps (32,000 B/s) | 3s Utterance = 96,000 Bytes",
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "iTANTRA TEXT TRANSMISSION (NEURAL TRANSCEIVER)",
                color = RadarGreen,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            val bw = diag.lastBandwidth
            val lat = diag.lastLatency
            val hasMeasured = bw.transmittedPacketBytes > 0L && lat.audioDurationMs > 0L
            val durationSec = lat.audioDurationMs / 1000.0
            val measuredBps = if (hasMeasured && durationSec > 0) (bw.transmittedPacketBytes * 8) / durationSec else 0.0
            val barWidth = if (hasMeasured && measuredBps > 0) (measuredBps / 256000.0).toFloat().coerceIn(0.02f, 1f) else 0.02f

            Box(
                modifier = Modifier
                    .fillMaxWidth(barWidth)
                    .height(18.dp)
                    .background(RadarGreen, RoundedCornerShape(4.dp))
            )
            Text(
                text = if (hasMeasured) {
                    String.format(Locale.US, "Rate: %.0f bps | Measured: %d B (%.1f%% Reduction)", measuredBps, bw.transmittedPacketBytes, bw.bandwidthReductionPercent)
                } else {
                    "Awaiting live transmission for measured rate"
                },
                color = RadarGreen,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "END-TO-END LATENCY BREAKDOWN",
            color = SignalBlue,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        val lat = diag.lastLatency
        BenchmarkCard(
            title = "STT Inference Latency",
            metric = String.format("%.1f ms", lat.sttLatencyMs),
            subtitle = "Measured using monotonic System.nanoTime()"
        )
        BenchmarkCard(
            title = "Protocol Encoding + Deflate",
            metric = String.format("%.2f ms", lat.encodingLatencyMs),
            subtitle = "31-byte compact radio header framing"
        )
        BenchmarkCard(
            title = "Local Transit Delay",
            metric = String.format("%.1f ms", lat.transportLatencyMs),
            subtitle = "Wi-Fi broadcast / Bluetooth SPP transit"
        )
        BenchmarkCard(
            title = "TTS Audio Synthesis",
            metric = String.format("%.1f ms", lat.ttsLatencyMs),
            subtitle = "On-device voice generation from received text"
        )
    }
}

@Composable
private fun BenchmarkCard(title: String, metric: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TacticalSurface, RoundedCornerShape(6.dp))
            .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(text = metric, color = RadarGreen, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        }
        Text(text = subtitle, color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
    Spacer(modifier = Modifier.height(6.dp))
}
