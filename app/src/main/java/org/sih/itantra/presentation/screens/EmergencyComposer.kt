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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import org.sih.itantra.presentation.theme.TacticalShapeTokens
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.emergency.EmergencyAction
import org.sih.itantra.core.emergency.EmergencyUiState
import org.sih.itantra.core.protocol.EmergencyBypassCode
import org.sih.itantra.presentation.components.EmergencyConfirmationDialog
import org.sih.itantra.presentation.components.EmergencyQuickActionRow
import org.sih.itantra.presentation.theme.LocalRadioColors

/**
 * Tactical Emergency Distress Composer Screen / Modal.
 * Enables fast, structured emergency dispatch with location visibility and deliberate confirmation.
 */
@Composable
fun EmergencyComposer(
    peerDestination: String,
    uiState: EmergencyUiState,
    onActionSelected: (EmergencyAction) -> Unit,
    onCustomNoteChanged: (String) -> Unit,
    onRequestConfirmation: () -> Unit,
    onDismissConfirmation: () -> Unit,
    onConfirmSend: () -> Unit,
    onDismiss: () -> Unit,
    onSend1ByteBypass: ((EmergencyBypassCode) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(radioColors.background.copy(alpha = 0.98f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = radioColors.alert,
                        shape = TacticalShapeTokens.Button
                    )
                    .background(radioColors.alert.copy(alpha = 0.12f), TacticalShapeTokens.Button)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = "Distress Mode",
                        tint = radioColors.alert,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "EMERGENCY MODE",
                            color = radioColors.alert,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "PRIORITY 1 · BROADCAST / DIRECT",
                            color = radioColors.textSecondary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel Emergency",
                        tint = radioColors.textSecondary
                    )
                }
            }

            // 2. Location Telemetry Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TacticalShapeTokens.Card)
                    .background(radioColors.surface)
                    .border(1.dp, radioColors.border, TacticalShapeTokens.Card)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (uiState.gpsFixAvailable) Icons.Default.GpsFixed else Icons.Default.GpsOff,
                            contentDescription = "GPS Status",
                            tint = if (uiState.gpsFixAvailable) radioColors.sage else radioColors.warning,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (uiState.gpsFixAvailable) "GPS FIX AVAILABLE" else "GPS NOT AVAILABLE",
                                color = if (uiState.gpsFixAvailable) radioColors.sage else radioColors.warning,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = uiState.locationCoordinates ?: "Transmitting without coordinates",
                                color = radioColors.textTertiary,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(TacticalShapeTokens.Tag)
                            .background(if (uiState.gpsFixAvailable) radioColors.sage.copy(alpha = 0.15f) else radioColors.capsule)
                            .border(1.dp, if (uiState.gpsFixAvailable) radioColors.sage else radioColors.border, TacticalShapeTokens.Tag)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (uiState.gpsFixAvailable) "ATTACHED" else "NO LOC",
                            color = if (uiState.gpsFixAvailable) radioColors.sage else radioColors.textTertiary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // 2b. 1-Byte Emergency Bypass Telemetry Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TacticalShapeTokens.Card)
                    .background(radioColors.alert.copy(alpha = 0.08f))
                    .border(1.dp, radioColors.alert.copy(alpha = 0.4f), TacticalShapeTokens.Card)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "1-BYTE EMERGENCY BYPASS",
                                color = radioColors.alert,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(radioColors.alert.copy(alpha = 0.2f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "0x%02X".format(uiState.selectedAction.bypassCode.code),
                                    color = radioColors.alert,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Text(
                            text = if (uiState.gpsFixAvailable) "Wire Frame: 73 Bytes (1B Payload + 32B GPS)" else "Wire Frame: 41 Bytes (1B Payload + CRC/Auth)",
                            color = radioColors.textTertiary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(TacticalShapeTokens.Tag)
                            .background(radioColors.alert.copy(alpha = 0.15f))
                            .border(1.dp, radioColors.alert, TacticalShapeTokens.Tag)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "83% SAVINGS",
                            color = radioColors.alert,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // 3. Quick Distress Categories
            Text(
                text = "TACTICAL DISTRESS CATEGORY:",
                color = radioColors.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            EmergencyQuickActionRow(
                selectedAction = uiState.selectedAction,
                onActionSelected = onActionSelected
            )

            // 4. Message Content Preview & Custom Note
            Text(
                text = "MESSAGE PREVIEW / CUSTOM NOTE:",
                color = radioColors.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            OutlinedTextField(
                value = uiState.customNote,
                onValueChange = onCustomNoteChanged,
                placeholder = {
                    Text(
                        text = uiState.selectedAction.defaultText,
                        color = radioColors.textTertiary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp),
                textStyle = TextStyle(
                    color = radioColors.textPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = radioColors.alert,
                    unfocusedBorderColor = radioColors.border,
                    focusedContainerColor = radioColors.surface,
                    unfocusedContainerColor = radioColors.surface
                ),
                shape = TacticalShapeTokens.Input
            )

            Spacer(modifier = Modifier.weight(1f, fill = false))

            // 5. Bottom Action Controls (Cancel & Send Distress)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Cancel Button
                Box(
                    modifier = Modifier
                        .weight(0.9f)
                        .height(48.dp)
                        .clip(TacticalShapeTokens.Button)
                        .background(radioColors.surface)
                        .border(1.dp, radioColors.border, TacticalShapeTokens.Button)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "CANCEL",
                        color = radioColors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // 1-Byte Instant Bypass Action (if provided)
                if (onSend1ByteBypass != null) {
                    Box(
                        modifier = Modifier
                            .weight(1.1f)
                            .height(48.dp)
                            .clip(TacticalShapeTokens.Button)
                            .background(radioColors.alert.copy(alpha = 0.85f))
                            .clickable(enabled = uiState.canSend) {
                                onSend1ByteBypass(uiState.selectedAction.bypassCode)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "1-BYTE SOS ⚡",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Send Distress Action (Triggers Confirmation)
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .height(48.dp)
                        .clip(TacticalShapeTokens.Button)
                        .background(radioColors.alert)
                        .clickable(enabled = uiState.canSend) { onRequestConfirmation() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (uiState.isTransmitting) "SENDING..." else "DISTRESS →",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // 6. Deliberate Confirmation Dialog
        if (uiState.isConfirmationOpen) {
            EmergencyConfirmationDialog(
                destination = peerDestination,
                hasLocation = uiState.gpsFixAvailable,
                messageSummary = uiState.effectiveMessageText,
                onConfirm = onConfirmSend,
                onDismiss = onDismissConfirmation
            )
        }
    }
}
