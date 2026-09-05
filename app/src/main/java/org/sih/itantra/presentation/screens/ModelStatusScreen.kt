package org.sih.itantra.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.stt.LanguageModelRegistry
import org.sih.itantra.presentation.theme.RadarGreen
import org.sih.itantra.presentation.theme.SignalBlue
import org.sih.itantra.presentation.theme.TacticalBackground
import org.sih.itantra.presentation.theme.TacticalBorder
import org.sih.itantra.presentation.theme.TacticalSurface
import org.sih.itantra.presentation.theme.TextPrimary
import org.sih.itantra.presentation.theme.TextSecondary

@Composable
fun ModelStatusScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val capabilities = LanguageModelRegistry.getCapabilities()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TacticalBackground)
            .padding(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "10-LANGUAGE STATUS",
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

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "AI4BHARAT / MMS / ANDROID OFFLINE CAPABILITY MATRIX",
            color = TextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(capabilities) { cap ->
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
                        Text(
                            text = "${cap.language.displayName.uppercase()} [${cap.language.isoCode}]",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (cap.isOfflineReady) "100% OFFLINE" else "UNVERIFIED",
                            color = RadarGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "STT: ${cap.sttEngine}",
                        color = SignalBlue,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "TTS: ${cap.ttsEngine}",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Sample: ${cap.language.scriptSample}",
                        color = TextPrimary.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
