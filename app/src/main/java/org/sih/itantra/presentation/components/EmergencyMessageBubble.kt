package org.sih.itantra.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.emergency.EmergencyUiMapper
import org.sih.itantra.core.message.MessageTechnicalInspectorMapper
import org.sih.itantra.core.message.RadioMessageStateMapper
import org.sih.itantra.core.persistence.MessageDirection
import org.sih.itantra.core.persistence.MessageRecord
import org.sih.itantra.core.tts.MessagePlaybackState
import org.sih.itantra.core.tts.TtsLanguage
import org.sih.itantra.core.tts.TtsVoiceRegistry
import org.sih.itantra.presentation.theme.LocalRadioColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated, visually distinct message bubble for emergency distress transmissions.
 * Maintains full compatibility with Feature 6 (Radio Delivery States), Feature 7 (Technical Inspector),
 * Feature 9 (Message Journey), and Feature 11 (Multilingual TTS Playback).
 */
@Composable
fun EmergencyMessageBubble(
    record: MessageRecord,
    isExpanded: Boolean,
    playbackState: MessagePlaybackState,
    ttsRegistry: TtsVoiceRegistry,
    onToggleExpand: () -> Unit,
    onPlayVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onOpenJourney: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current
    val isOutgoing = record.direction == MessageDirection.SENT
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(record.timestamp))

    val isActiveDistress = remember(record.timestamp) {
        EmergencyUiMapper.isMessageActiveDistress(record)
    }

    val bubbleBg = if (isActiveDistress) {
        radioColors.alert.copy(alpha = 0.16f)
    } else {
        radioColors.warning.copy(alpha = 0.08f)
    }

    val bubbleBorderColor = if (isActiveDistress) {
        radioColors.alert
    } else {
        radioColors.warning.copy(alpha = 0.5f)
    }

    val alignment = if (isOutgoing) Alignment.End else Alignment.Start

    val isCurrentActive = playbackState.messageId == record.id && playbackState.isActive
    val isCurrentPlaying = playbackState.messageId == record.id && playbackState.isPlaying

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(14.dp))
                .background(bubbleBg)
                .border(1.5.dp, bubbleBorderColor, RoundedCornerShape(14.dp))
                .clickable { onToggleExpand() }
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column {
                // 1. Emergency Priority Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = "Distress Indicator",
                            tint = if (isActiveDistress) radioColors.alert else radioColors.warning,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isActiveDistress) radioColors.alert.copy(alpha = 0.25f) else radioColors.warning.copy(alpha = 0.15f))
                                .border(1.dp, if (isActiveDistress) radioColors.alert else radioColors.warning, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isActiveDistress) "⚠ DISTRESS · PRIORITY 1" else "⚠ HISTORICAL DISTRESS",
                                color = if (isActiveDistress) radioColors.alert else radioColors.warning,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                    Text(
                        text = formattedTime,
                        color = radioColors.textTertiary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Audio Playback Control & Mini Waveform
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (isCurrentActive) radioColors.alert
                                else if (radioColors.isDark) radioColors.sage else radioColors.forest
                            )
                            .clickable {
                                if (isCurrentActive) onStopVoice() else onPlayVoice()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isCurrentActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isCurrentActive) "Stop Voice Playback" else "Play Emergency Voice",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        val barHeights = listOf(6, 14, 18, 10, 16, 12, 8, 14, 10, 16, 8)
                        barHeights.forEach { h ->
                            val dynamicHeight = if (isCurrentPlaying) {
                                ((h * 1.2).toInt()).coerceIn(4, 20)
                            } else h
                            val barColor = if (isCurrentPlaying) {
                                radioColors.alert
                            } else if (isCurrentActive) {
                                radioColors.warning
                            } else {
                                radioColors.alert.copy(alpha = 0.6f)
                            }
                            Box(
                                modifier = Modifier
                                    .width(2.5.dp)
                                    .height(dynamicHeight.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(barColor)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = if (isCurrentPlaying) "PLAY" else "${(record.measuredLatencyMs / 1000.0).coerceAtLeast(0.1).toString().take(3)}s",
                        color = if (isCurrentPlaying) radioColors.alert else radioColors.textTertiary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 3. Dominant Message Text
                Text(
                    text = record.text,
                    color = radioColors.textPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif
                )

                // 4. Attached Location Status & Coordinates
                if (record.location != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(radioColors.warning.copy(alpha = 0.15f))
                            .border(0.5.dp, radioColors.warning, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "📍 LOCATION ATTACHED (${String.format(Locale.US, "%.4f, %.4f", record.location.latitude, record.location.longitude)})",
                            color = radioColors.warning,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 5. Metadata Footer: Language Badge · Wire Size
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val ttsProfile = remember(record.language) {
                        ttsRegistry.getProfile(TtsLanguage.fromIndicLanguage(record.language))
                    }
                    MessageLanguageBadge(
                        language = ttsProfile.language,
                        isTtsReady = ttsProfile.isAvailable,
                        statusLabel = ttsProfile.statusLabel
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(radioColors.capsule)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "${record.packetSizeBytes}B",
                            color = radioColors.textTertiary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // 6. Feature 11 Active Playback Indicator
                if (playbackState.messageId == record.id && (playbackState.isActive || playbackState.isFailed)) {
                    TtsPlaybackIndicator(playbackState = playbackState)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 7. Feature 6 Radio-Aware Delivery State
                val radioTelemetry = remember(record.id, record.deliveryStatus, record.isRelayed) {
                    RadioMessageStateMapper.map(record)
                }
                MessageRadioStateIndicator(
                    telemetry = radioTelemetry,
                    formattedTime = formattedTime,
                    modifier = Modifier.fillMaxWidth()
                )

                // 8. Progressive Disclosure: Technical Packet Inspector (Feature 7) & Journey (Feature 9)
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    val inspector = remember(record.id, record.deliveryStatus, record.isRelayed, record.authStatus) {
                        MessageTechnicalInspectorMapper.map(record, radioTelemetry)
                    }
                    MessageTechnicalInspectorCard(
                        inspector = inspector,
                        onOpenJourney = onOpenJourney,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            }
        }
    }
}
