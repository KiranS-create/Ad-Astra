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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
 * Large circular state-driven PTT button (180 dp) matching the hardware field radio specifications.
 * Implements concentric styling, touch haptics, and distinct visual animations for all transceiver states:
 * - IDLE: Mic icon ("Press to Talk")
 * - LISTENING / RECORDING: Alert orange, Mic icon, active animated waveform bars
 * - PROCESSING / STT: Sage green, GraphicEq icon, pulsing dots
 * - TRANSMITTING: Sage green, Send icon, sweeping progress indicator
 * - RECEIVING / PLAYING: Signal blue, ArrowDownward, incoming waveform
 * - DISTRESS: Alert red/orange, WarningAmber icon, hold guard
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
        targetValue = 1.08f,
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

    // Determine state styling
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

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(190.dp)
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
    ) {
        // Outer Concentric Ring 1 (186 dp)
        Box(
            modifier = Modifier
                .size(186.dp)
                .scale(if (isActive) pulseScale else 1.0f)
                .background(
                    if (isActive) accentColor.copy(alpha = 0.08f) else radioColors.capsule.copy(alpha = 0.4f),
                    CircleShape
                )
                .border(
                    width = 1.dp,
                    color = if (isActive) accentColor.copy(alpha = 0.35f) else radioColors.border.copy(alpha = 0.4f),
                    shape = CircleShape
                )
        )

        // Middle Concentric Ring 2 (162 dp)
        Box(
            modifier = Modifier
                .size(162.dp)
                .background(
                    if (isActive) accentColor.copy(alpha = 0.12f) else radioColors.capsule,
                    CircleShape
                )
                .border(
                    width = 1.5.dp,
                    color = if (isActive) accentColor.copy(alpha = 0.6f) else radioColors.border.copy(alpha = 0.7f),
                    shape = CircleShape
                )
        )

        // Inner Core Interactive Button (138 dp)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(138.dp)
                .background(radioColors.surface, CircleShape)
                .border(
                    width = 2.5.dp,
                    color = accentColor,
                    shape = CircleShape
                )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                // Icon based on state
                when {
                    isDistressActive -> {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = "Distress",
                            tint = accentColor,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    isError -> {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = accentColor,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    isTransmitting -> {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Transmitting",
                            tint = accentColor,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    isReceiving -> {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Receiving",
                            tint = accentColor,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    isProcessing -> {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Processing",
                            tint = accentColor,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    isContinuousMode -> {
                        Icon(
                            imageVector = Icons.Default.Radio,
                            contentDescription = "Continuous Mode",
                            tint = accentColor,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    else -> {
                        // IDLE or LISTENING
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Microphone",
                            tint = accentColor,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Sub-indicator animation or state text
                when {
                    isDistressActive -> {
                        Text(
                            text = "DISTRESS",
                            color = accentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "HOLD TO SEND",
                            color = radioColors.textSecondary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    isListening -> {
                        // Dynamic 5-bar waveform
                        Canvas(modifier = Modifier.size(width = 46.dp, height = 12.dp)) {
                            val barWidth = 4.dp.toPx()
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
                                    cornerRadius = CornerRadius(2.dp.toPx())
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "LISTENING",
                            color = accentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    isProcessing -> {
                        // Pulsing 3 dots
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.width(36.dp).height(10.dp)
                        ) {
                            for (i in 0 until 3) {
                                val alpha = 0.3f + 0.7f * kotlin.math.abs(sin(wavePhase + i * 1.0f))
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .background(accentColor.copy(alpha = alpha), CircleShape)
                                )
                                if (i < 2) Spacer(modifier = Modifier.width(5.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "PROCESSING",
                            color = accentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    isTransmitting -> {
                        // Linear progress indicator
                        Canvas(modifier = Modifier.size(width = 50.dp, height = 4.dp)) {
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
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "TRANSMITTING",
                            color = accentColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    isReceiving -> {
                        // Incoming audio wave
                        Canvas(modifier = Modifier.size(width = 46.dp, height = 10.dp)) {
                            val barWidth = 4.dp.toPx()
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
                                    cornerRadius = CornerRadius(2.dp.toPx())
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "RECEIVING",
                            color = accentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    isError -> {
                        Text(
                            text = "ERROR",
                            color = accentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    else -> {
                        // IDLE state
                        Text(
                            text = "Press to Talk",
                            color = radioColors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }
        }
    }
}
