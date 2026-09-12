package org.sih.itantra.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.presentation.theme.LocalRadioColors

/**
 * High-contrast tactical confirmation modal displayed immediately before sending
 * an emergency distress packet over the MANET mesh network.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyConfirmationDialog(
    destination: String,
    hasLocation: Boolean,
    messageSummary: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val radioColors = LocalRadioColors.current

    BasicAlertDialog(
        onDismissRequest = onDismiss
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(radioColors.surface)
                .border(2.dp, radioColors.alert, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Title Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = "Emergency Confirmation",
                        tint = radioColors.alert,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "EMERGENCY MESSAGE",
                        color = radioColors.alert,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }

                Text(
                    text = "This will broadcast a high-priority distress signal over the MANET mesh network, preempting standard traffic.",
                    color = radioColors.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                // Tactical Telemetry Fields
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(radioColors.forest.copy(alpha = 0.25f))
                        .border(1.dp, radioColors.border, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ConfirmFieldRow(
                        label = "PRIORITY:",
                        value = "DISTRESS (P1 / QoS 1)",
                        valueColor = radioColors.alert
                    )
                    ConfirmFieldRow(
                        label = "DESTINATION:",
                        value = destination,
                        valueColor = radioColors.textPrimary
                    )
                    ConfirmFieldRow(
                        label = "LOCATION:",
                        value = if (hasLocation) "ATTACHED (GPS FIX ✓)" else "NOT ATTACHED",
                        valueColor = if (hasLocation) radioColors.sage else radioColors.warning
                    )
                    ConfirmFieldRow(
                        label = "MESSAGE:",
                        value = messageSummary.take(45),
                        valueColor = radioColors.textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Cancel
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(radioColors.capsule)
                            .border(1.dp, radioColors.border, RoundedCornerShape(10.dp))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "CANCEL",
                            color = radioColors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Send Distress
                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(radioColors.alert)
                            .clickable { onConfirm() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "SEND DISTRESS",
                            color = androidx.compose.ui.graphics.Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmFieldRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color
) {
    val radioColors = LocalRadioColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = radioColors.textTertiary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
