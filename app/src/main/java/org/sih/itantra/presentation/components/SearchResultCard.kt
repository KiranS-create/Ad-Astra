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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.chat.ChatDeliveryStatus
import org.sih.itantra.core.chat.ChatRouteState
import org.sih.itantra.core.search.SearchResult
import org.sih.itantra.core.search.SearchResultType
import org.sih.itantra.presentation.theme.LocalRadioColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tactical result card rendering individual search hits.
 * Fully distinguishes MESSAGE, NODE, CONTACT, and CONVERSATION entries.
 */
@Composable
fun SearchResultCard(
    result: SearchResult,
    onClick: () -> Unit,
    onOpenChat: (peerId: String) -> Unit = {},
    onOpenContact: (nodeId: Int) -> Unit = {},
    onInspectMessage: (messageId: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current
    val clipboardManager = LocalClipboardManager.current

    val borderColor = if (result.isEmergency) {
        radioColors.alert
    } else {
        radioColors.border.copy(alpha = 0.5f)
    }

    val cardBg = if (result.isEmergency) {
        radioColors.alert.copy(alpha = 0.08f)
    } else {
        radioColors.surface
    }

    val a11yDescription = "${result.resultType.label}: ${result.title}. ${result.snippet ?: ""}"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .semantics { contentDescription = a11yDescription }
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header: Category Icon + Title + Type Badge + Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    // Result Type Icon
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(radioColors.capsule)
                            .border(1.dp, radioColors.border.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        val icon = when (result.resultType) {
                            SearchResultType.MESSAGE -> Icons.Default.Chat
                            SearchResultType.NODE -> Icons.Default.Router
                            SearchResultType.CONTACT -> Icons.Default.Person
                            SearchResultType.CONVERSATION -> Icons.Default.Sensors
                            SearchResultType.NEARBY_DEVICE -> Icons.Default.Sensors
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = result.resultType.label,
                            tint = if (result.isEmergency) radioColors.alert
                            else if (radioColors.isDark) radioColors.sage else radioColors.forest,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = result.title,
                                color = if (result.isEmergency) radioColors.alert else radioColors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (result.isEmergency) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(radioColors.alert)
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = result.priority?.label ?: "ALERT",
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        if (!result.subtitle.isNullOrBlank()) {
                            Text(
                                text = result.subtitle,
                                color = radioColors.textSecondary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Time / Auth / Reachability Pill
                Column(horizontalAlignment = Alignment.End) {
                    if (result.timestamp != null && result.timestamp > 0) {
                        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                        Text(
                            text = timeFormat.format(Date(result.timestamp)),
                            color = radioColors.textSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (result.authStatus != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (radioColors.isDark) radioColors.sage.copy(alpha = 0.2f) else radioColors.forest.copy(alpha = 0.1f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = result.authStatus,
                                color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Body Snippet (if available)
            if (!result.snippet.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = result.snippet,
                    color = radioColors.textPrimary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 36.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer: Badges + Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Metadata Badges (Language, Delivery, Hops)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (result.language != null) {
                        BadgeCapsule(
                            text = result.language.displayName,
                            color = radioColors.textSecondary,
                            bg = radioColors.capsule
                        )
                    }

                    if (result.deliveryStatus != null) {
                        DeliveryBadge(status = result.deliveryStatus)
                    }

                    if (result.routeState != null) {
                        RouteBadge(routeState = result.routeState)
                    }
                }

                // Quick Action Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Copy snippet action
                    if (!result.snippet.isNullOrBlank()) {
                        ActionIconButton(
                            icon = Icons.Default.ContentCopy,
                            contentDescription = "Copy text",
                            onClick = {
                                clipboardManager.setText(AnnotatedString(result.snippet))
                            }
                        )
                    }

                    // Open Chat / Direct Action Button
                    val chatTarget = result.nodeId?.let { "Node #$it" } ?: result.title
                    ActionPillButton(
                        label = "CHAT",
                        onClick = { onOpenChat(chatTarget) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BadgeCapsule(text: String, color: Color, bg: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun RouteBadge(routeState: ChatRouteState) {
    val radioColors = LocalRadioColors.current
    val (dotColor, label) = when (routeState) {
        ChatRouteState.CONNECTED_DIRECT -> radioColors.sage to "DIRECT"
        ChatRouteState.CONNECTED_RELAYED -> Color(0xFF0288D1) to "RELAYED"
        ChatRouteState.RECENTLY_HEARD -> radioColors.warning to "RECENT"
        ChatRouteState.DTN_STORED -> Color(0xFFFF9800) to "DTN"
        ChatRouteState.DISCONNECTED -> radioColors.textTertiary to "OFFLINE"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(radioColors.capsule)
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = radioColors.textSecondary,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DeliveryBadge(status: ChatDeliveryStatus) {
    val radioColors = LocalRadioColors.current
    val (bg, color) = when (status) {
        ChatDeliveryStatus.ACK -> (if (radioColors.isDark) radioColors.sage.copy(alpha = 0.2f) else radioColors.forest.copy(alpha = 0.1f)) to (if (radioColors.isDark) radioColors.sage else radioColors.forest)
        ChatDeliveryStatus.RECEIVED -> Color(0xFF0288D1).copy(alpha = 0.15f) to Color(0xFF0288D1)
        ChatDeliveryStatus.TRANSMITTING -> radioColors.warning.copy(alpha = 0.2f) to radioColors.warning
        ChatDeliveryStatus.DTN_STORED -> Color(0xFFFF9800).copy(alpha = 0.2f) to Color(0xFFFF9800)
        ChatDeliveryStatus.RELAYED -> Color(0xFF0288D1).copy(alpha = 0.15f) to Color(0xFF0288D1)
        ChatDeliveryStatus.QUEUED -> radioColors.capsule to radioColors.textSecondary
        ChatDeliveryStatus.FAILED -> radioColors.alert.copy(alpha = 0.2f) to radioColors.alert
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            text = status.label,
            color = color,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ActionIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    val radioColors = LocalRadioColors.current
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(radioColors.capsule)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = radioColors.textSecondary,
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun ActionPillButton(
    label: String,
    onClick: () -> Unit
) {
    val radioColors = LocalRadioColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (radioColors.isDark) radioColors.sage else radioColors.forest)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp
        )
    }
}
