package org.sih.itantra.presentation.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.session.PttState
import org.sih.itantra.presentation.theme.DistressRed
import org.sih.itantra.presentation.theme.RadarGreen
import org.sih.itantra.presentation.theme.RadarGreenDim
import org.sih.itantra.presentation.theme.SignalBlue
import org.sih.itantra.presentation.theme.TacticalBackground
import org.sih.itantra.presentation.theme.TacticalSurfaceHighlight

@Composable
fun TacticalPttButton(
    pttState: PttState,
    isContinuousMode: Boolean,
    onPressStart: () -> Unit,
    onPressRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val isActive = pttState != PttState.IDLE

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val buttonColor = when {
        isContinuousMode -> SignalBlue
        pttState == PttState.RECORDING || pttState == PttState.SPEECH_DETECTED -> RadarGreen
        pttState == PttState.TRANSMITTING -> AlertAmberColor
        pttState == PttState.ERROR -> DistressRed
        else -> TacticalSurfaceHighlight
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(170.dp)
    ) {
        // Outer pulsing ring when active
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(165.dp)
                    .scale(pulseScale)
                    .border(2.dp, buttonColor.copy(alpha = 0.4f), CircleShape)
            )
        }

        // Inner solid push button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(140.dp)
                .background(TacticalBackground, CircleShape)
                .border(3.dp, buttonColor, CircleShape)
                .pointerInput(isContinuousMode) {
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (isContinuousMode) Icons.Default.Radio else Icons.Default.Mic,
                    contentDescription = "PTT Icon",
                    tint = buttonColor,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isContinuousMode) "CONTINUOUS" else if (isActive) pttState.displayName.uppercase() else "HOLD TO TALK",
                    color = buttonColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

private val AlertAmberColor = Color(0xFFFFB300)
