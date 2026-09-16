package org.sih.itantra.presentation.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.session.PttState
import org.sih.itantra.presentation.theme.LocalRadioColors
import kotlin.math.sin

/**
 * Compact rectangular state-driven PTT button with rounded corners.
 *
 * Horizontally oriented and substantially more compact in screen height (~58 dp)
 * than legacy circular controls, leaving ample vertical space for Live Radio Traffic.
 *
 * Preserves all functionality:
 * - Press-and-hold to talk / record, release to send / process
 * - Haptic feedback (Long press on engage, tap on release)
 * - Transceiver state animations (Dynamic audio waveform, pulsing STT dots, linear TX progress)
 * - Full color hierarchy across IDLE, RECORDING, PROCESSING, TRANSMITTING, RECEIVING, DISTRESS, ERROR
 */
@Composable
fun RadioPttControl(
    pttState: PttState,
    isContinuousMode: Boolean = false,
    isDistressActive: Boolean = false,
    onPressStart: () -> Unit,
    onPressRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current
    val view = LocalView.current

    // Infinite transitions for smooth animations
    val infiniteTransition = rememberInfiniteTransition(label = "pttTransitions")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    val progressPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progressPhase"
    )

    // Determine state flags
    val isListening = pttState == PttState.RECORDING || pttState == PttState.SPEECH_DETECTED || pttState == PttState.PTT_PRESSED
    val isProcessing = pttState == PttState.STT_PROCESSING || pttState == PttState.MESSAGE_ENCODED
    val isTransmitting = pttState == PttState.TRANSMITTING
    val isReceiving = pttState == PttState.RECEIVED || pttState == PttState.TTS_PROCESSING || pttState == PttState.PLAYING
    val isError = pttState == PttState.ERROR
    val isActive = pttState != PttState.IDLE || isDistressActive

    val accentColor: Color = when {
        isDistressActive -> radioColors.alert
        isError -> radioColors.alert
        isListening -> radioColors.alert
        isProcessing -> radioColors.sage
        isTransmitting -> radioColors.sage
        isReceiving -> Color(0xFF0288D1) // Signal blue
        isContinuousMode -> radioColors.sage
        else -> if (radioColors.isDark) radioColors.sage else radioColors.forest
    }

    val buttonCornerRadius = 12.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .scale(if (isActive) pulseScale else 1.0f)
            .clip(RoundedCornerShape(buttonCornerRadius))
            .background(
                if (isActive) accentColor.copy(alpha = 0.12f) else radioColors.surface
            )
            .border(
                width = if (isActive) 2.dp else 1.5.dp,
                color = if (isActive) accentColor else radioColors.border.copy(alpha = 0.7f),
                shape = RoundedCornerShape(buttonCornerRadius)
            )
            .pointerInput(isContinuousMode, isDistressActive) {
                if (!isContinuousMode) {
                    detectTapGestures(
                        onPress = {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onPressStart()
                            tryAwaitRelease()
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onPressRelease()
                        }
                    )
                }
            }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: State Icon inside rounded badge
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = if (isActive) 0.22f else 0.12f))
                    .border(1.dp, accentColor.copy(alpha = if (isActive) 0.5f else 0.25f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isDistressActive -> {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = "Distress",
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    isError -> {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    isTransmitting -> {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Transmitting",
                            tint = accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    isReceiving -> {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Receiving",
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    isProcessing -> {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Processing",
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    isContinuousMode -> {
                        Icon(
                            imageVector = Icons.Default.Radio,
                            contentDescription = "Continuous Mode",
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    else -> {
                        // IDLE or LISTENING
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Microphone",
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Center: Primary Title & Dynamic Subtitle / Waveform
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // Primary state headline
                Text(
                    text = when {
                        isDistressActive -> "EMERGENCY DISTRESS"
                        isError -> "TRANSCEIVER ERROR"
                        isTransmitting -> "TRANSMITTING TO MESH"
                        isReceiving -> "RECEIVING AUDIO"
                        isProcessing -> "STT PROCESSING"
                        isListening -> "RECORDING AUDIO (VAD)"
                        isContinuousMode -> "CONTINUOUS RADIO MONITOR"
                        else -> "PRESS TO TALK"
                    },
                    color = if (isActive) accentColor else radioColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Subtitle row: dynamic waveform, progress or instructions
                when {
                    isDistressActive -> {
                        Text(
                            text = "HOLD TO BROADCAST DISTRESS BEACON",
                            color = radioColors.alert,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    isListening -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Dynamic 5-bar waveform
                            Canvas(modifier = Modifier.size(width = 38.dp, height = 10.dp)) {
                                val barWidth = 3.5.dp.toPx()
                                val spacing = (size.width - 5 * barWidth) / 4
                                for (i in 0 until 5) {
                                    val phaseOffset = i * 0.9f
                                    val normalizedHeight = (0.3f + 0.65f * kotlin.math.abs(sin(wavePhase + phaseOffset))).coerceIn(0.2f, 1f)
                                    val barHeight = size.height * normalizedHeight
                                    val left = i * (barWidth + spacing)
                                    val top = (size.height - barHeight) / 2f
                                    drawRoundRect(
                                        color = accentColor,
                                        topLeft = Offset(left, top),
                                        size = Size(barWidth, barHeight),
                                        cornerRadius = CornerRadius(1.5.dp.toPx())
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "RELEASE TO SEND",
                                color = accentColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    isProcessing -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Pulsing 3 dots
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.width(28.dp).height(8.dp)
                            ) {
                                for (i in 0 until 3) {
                                    val alpha = 0.3f + 0.7f * kotlin.math.abs(sin(wavePhase + i * 1.0f))
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .background(accentColor.copy(alpha = alpha), CircleShape)
                                    )
                                    if (i < 2) Spacer(modifier = Modifier.width(4.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "CONVERTING SPEECH...",
                                color = accentColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    isTransmitting -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Sweeping progress indicator
                            Canvas(modifier = Modifier.size(width = 40.dp, height = 4.dp)) {
                                drawRoundRect(
                                    color = accentColor.copy(alpha = 0.25f),
                                    size = size,
                                    cornerRadius = CornerRadius(2.dp.toPx())
                                )
                                val activeWidth = size.width * 0.4f
                                val activeLeft = (size.width + activeWidth) * progressPhase - activeWidth
                                drawRoundRect(
                                    color = accentColor,
                                    topLeft = Offset(activeLeft.coerceIn(0f, size.width - activeWidth), 0f),
                                    size = Size(activeWidth, size.height),
                                    cornerRadius = CornerRadius(2.dp.toPx())
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "RELAYING TO MESH...",
                                color = accentColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    isReceiving -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Incoming audio wave
                            Canvas(modifier = Modifier.size(width = 38.dp, height = 10.dp)) {
                                val barWidth = 3.5.dp.toPx()
                                val spacing = (size.width - 5 * barWidth) / 4
                                for (i in 0 until 5) {
                                    val phaseOffset = (4 - i) * 0.8f
                                    val normalizedHeight = (0.25f + 0.70f * kotlin.math.abs(sin(wavePhase + phaseOffset))).coerceIn(0.2f, 1f)
                                    val barHeight = size.height * normalizedHeight
                                    val left = i * (barWidth + spacing)
                                    val top = (size.height - barHeight) / 2f
                                    drawRoundRect(
                                        color = accentColor,
                                        topLeft = Offset(left, top),
                                        size = Size(barWidth, barHeight),
                                        cornerRadius = CornerRadius(1.5.dp.toPx())
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "PLAYING AUDIO...",
                                color = accentColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    isError -> {
                        Text(
                            text = "TRANSMISSION FAILED • TAP TO RETRY",
                            color = accentColor,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    else -> {
                        // IDLE state
                        Text(
                            text = "HOLD TO TRANSMIT • RELEASE TO SEND",
                            color = radioColors.textTertiary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right: Tactical State Tag / Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(accentColor.copy(alpha = if (isActive) 0.20f else 0.10f))
                    .border(1.dp, accentColor.copy(alpha = if (isActive) 0.6f else 0.3f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        isDistressActive -> "SOS"
                        isError -> "ERR"
                        isTransmitting -> "TX"
                        isReceiving -> "RX"
                        isProcessing -> "STT"
                        isListening -> "REC"
                        isContinuousMode -> "MON"
                        else -> "PTT"
                    },
                    color = accentColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
