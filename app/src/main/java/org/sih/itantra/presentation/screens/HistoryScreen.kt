package org.sih.itantra.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import org.sih.itantra.presentation.components.TranscriptBubble
import org.sih.itantra.presentation.theme.RadarGreen
import org.sih.itantra.presentation.theme.TacticalBackground
import org.sih.itantra.presentation.theme.TacticalSurface
import org.sih.itantra.presentation.theme.TextPrimary
import org.sih.itantra.presentation.theme.TextSecondary
import org.sih.itantra.presentation.viewmodel.TransceiverViewModel

@Composable
fun HistoryScreen(
    viewModel: TransceiverViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val history by viewModel.messageHistory.collectAsState()

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
                text = "COMMUNICATION HISTORY",
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

        if (history.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
            ) {
                Text(
                    text = "NO TRANSMISSIONS LOGGED YET",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(history) { record ->
                    TranscriptBubble(record = record)
                }
            }
        }
    }
}
