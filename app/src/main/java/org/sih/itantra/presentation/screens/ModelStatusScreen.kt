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
import org.sih.itantra.presentation.theme.LocalRadioColors

@Composable
fun ModelStatusScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current
    val capabilities = LanguageModelRegistry.getCapabilities()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(radioColors.background)
            .padding(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "10-LANGUAGE AUDIT",
                color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = radioColors.surface)
            ) {
                Text("CLOSE", color = radioColors.textPrimary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "VERIFICATION AUDIT: ZERO-FABRICATION STATUS",
            color = radioColors.warning,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(capabilities) { cap ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(radioColors.surface, RoundedCornerShape(10.dp))
                        .border(1.dp, radioColors.border.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${cap.language.displayName.uppercase()} [${cap.language.isoCode}]",
                            color = radioColors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        val isVerified = cap.verificationNotes.contains("VERIFIED")
                        Text(
                            text = if (isVerified) "VERIFIED NEURAL" else if (cap.isOfflineReady) "SYSTEM READY" else "FALLBACK-ONLY",
                            color = if (isVerified) radioColors.success else if (cap.isOfflineReady) radioColors.sage else radioColors.warning,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "STT: ${cap.sttEngine}",
                        color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "TTS: ${cap.ttsEngine}",
                        color = radioColors.textSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Status: ${cap.verificationNotes}",
                        color = if (cap.isOfflineReady) radioColors.textPrimary.copy(alpha = 0.8f) else radioColors.warning,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
