package org.sih.itantra.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.chat.ChatRouteState
import org.sih.itantra.core.contact.ContactNetworkState
import org.sih.itantra.presentation.theme.LocalRadioColors

/**
 * Tactical status indicator pill representing dynamic reachability, hop count,
 * and live radio presence for a contact.
 */
@Composable
fun ContactStatusIndicator(
    networkState: ContactNetworkState,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current

    val (dotColor, badgeBg, badgeBorder, text) = when (networkState.routeState) {
        ChatRouteState.CONNECTED_DIRECT -> {
            val c = radioColors.sage
            Quadruple(c, c.copy(alpha = 0.15f), c.copy(alpha = 0.4f), "DIRECT · 1 HOP")
        }
        ChatRouteState.CONNECTED_RELAYED -> {
            val c = Color(0xFF0288D1)
            val hops = networkState.hopCount.coerceAtLeast(2)
            Quadruple(c, c.copy(alpha = 0.15f), c.copy(alpha = 0.4f), "$hops HOPS · RELAYED")
        }
        ChatRouteState.RECENTLY_HEARD -> {
            val c = radioColors.warning
            Quadruple(c, c.copy(alpha = 0.15f), c.copy(alpha = 0.4f), "RECENTLY HEARD")
        }
        ChatRouteState.DTN_STORED -> {
            val c = Color(0xFFFF9800)
            Quadruple(c, c.copy(alpha = 0.15f), c.copy(alpha = 0.4f), "DTN STORED")
        }
        ChatRouteState.DISCONNECTED -> {
            val c = radioColors.textTertiary
            Quadruple(c, radioColors.capsule, radioColors.border, "OFFLINE")
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(badgeBg)
            .border(1.dp, badgeBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = text,
                color = if (networkState.routeState == ChatRouteState.DISCONNECTED) radioColors.textTertiary else dotColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
