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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import org.sih.itantra.core.contact.ContactAuthStatus
import org.sih.itantra.core.contact.TacticalContact
import org.sih.itantra.presentation.theme.LocalRadioColors

/**
 * Tactical contact card representing an iTantra communication peer.
 * Features clean conversational hierarchy with progressive disclosure for deep RF telemetry.
 */
@Composable
fun ContactCard(
    contact: TacticalContact,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onOpenChat: (nodeId: Int) -> Unit,
    onDeleteContact: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current

    val accentStripe = when (contact.networkState.routeState) {
        ChatRouteState.CONNECTED_DIRECT -> radioColors.sage
        ChatRouteState.CONNECTED_RELAYED -> Color(0xFF0288D1)
        ChatRouteState.RECENTLY_HEARD -> radioColors.warning
        ChatRouteState.DTN_STORED -> Color(0xFFFF9800)
        ChatRouteState.DISCONNECTED -> radioColors.border
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(radioColors.surface)
            .border(
                1.dp,
                if (isExpanded) radioColors.sage.copy(alpha = 0.8f) else radioColors.border,
                RoundedCornerShape(12.dp)
            )
            .clickable { onToggleExpand() }
    ) {
        // Left tactical accent indicator
        Box(
            modifier = Modifier
                .width(4.dp)
                .matchParentSize()
                .background(accentStripe)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
        ) {
            // Row 1: Identity & Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Tactical Avatar Pill
                    val initials = contact.callsign.split(" ")
                        .mapNotNull { it.firstOrNull()?.toString() }
                        .take(2)
                        .joinToString("")
                        .ifEmpty { "N" }

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(radioColors.capsule)
                            .border(1.dp, radioColors.border, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            color = radioColors.sage,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = contact.callsign.uppercase(),
                            color = radioColors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "${contact.displayName} · #${contact.nodeId}",
                            color = radioColors.textSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    ContactStatusIndicator(networkState = contact.networkState)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = radioColors.textTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: Metadata Chips & Last Heard Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Languages Chip
                val langsStr = if (contact.supportedLanguages.isNotEmpty()) {
                    contact.supportedLanguages.take(2).joinToString(" · ") { it.displayName }
                } else {
                    "LANGUAGE UNKNOWN"
                }
                TacticalChip(text = langsStr, textColor = radioColors.textSecondary)

                // Transport Pill
                if (contact.networkState.transport.isNotBlank() && contact.networkState.transport != "None") {
                    TacticalChip(text = contact.networkState.transport, textColor = radioColors.textSecondary)
                }

                // Authentication Badge
                when (contact.authStatus) {
                    ContactAuthStatus.AUTHENTICATED -> {
                        TacticalChip(text = "AUTH ✓", textColor = radioColors.sage, borderColor = radioColors.sage.copy(alpha = 0.4f))
                    }
                    ContactAuthStatus.TRUSTED -> {
                        TacticalChip(text = "TRUSTED", textColor = Color(0xFF4FC3F7), borderColor = Color(0xFF4FC3F7).copy(alpha = 0.4f))
                    }
                    ContactAuthStatus.UNVERIFIED -> {
                        TacticalChip(text = "UNVERIFIED", textColor = radioColors.warning, borderColor = radioColors.warning.copy(alpha = 0.4f))
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Last Heard
                Text(
                    text = contact.networkState.lastHeardFormatted,
                    color = radioColors.textTertiary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Progressive Disclosure: Expanded Telemetry & Action Drawer
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    HorizontalDivider(color = radioColors.border, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "TACTICAL PEER TELEMETRY",
                        color = radioColors.sage,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Telemetry Grid
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(radioColors.capsule)
                            .border(1.dp, radioColors.border, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TelemetryRow(label = "NODE ID:", value = "#${contact.nodeId}")
                            TelemetryRow(
                                label = "ROUTE STATE:",
                                value = "${contact.networkState.routeState.label.uppercase()} (${contact.networkState.hopCount} Hops)",
                                valueColor = accentStripe
                            )
                            if (contact.networkState.relayNodeId != null) {
                                TelemetryRow(label = "RELAY NODE:", value = "Node #${contact.networkState.relayNodeId}")
                            }
                            TelemetryRow(label = "TRANSPORT:", value = contact.networkState.transport)
                            TelemetryRow(
                                label = "LANGUAGES:",
                                value = contact.supportedLanguages.joinToString(", ") { "${it.displayName} (${it.isoCode})" }
                            )
                            TelemetryRow(
                                label = "SECURITY:",
                                value = when (contact.authStatus) {
                                    ContactAuthStatus.AUTHENTICATED -> "HMAC-SHA256 Authenticated ✓"
                                    ContactAuthStatus.TRUSTED -> "Locally Trusted Peer"
                                    ContactAuthStatus.UNVERIFIED -> "Unverified Node Identity"
                                },
                                valueColor = when (contact.authStatus) {
                                    ContactAuthStatus.AUTHENTICATED -> radioColors.sage
                                    ContactAuthStatus.TRUSTED -> Color(0xFF4FC3F7)
                                    ContactAuthStatus.UNVERIFIED -> radioColors.warning
                                }
                            )
                            if (!contact.identity.notes.isNullOrBlank()) {
                                TelemetryRow(label = "TACTICAL NOTES:", value = contact.identity.notes)
                            }
                            TelemetryRow(label = "LAST HEARD:", value = contact.networkState.lastHeardFormatted)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action Row: Open Chat + Optional Delete
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (radioColors.isDark) radioColors.sage else radioColors.forest)
                                .clickable { onOpenChat(contact.nodeId) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Open Chat",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "OPEN CHAT",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        if (onDeleteContact != null) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(radioColors.capsule)
                                    .border(1.dp, radioColors.border, RoundedCornerShape(8.dp))
                                    .clickable { onDeleteContact() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Contact",
                                    tint = radioColors.alert,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TacticalChip(
    text: String,
    textColor: Color,
    borderColor: Color = Color.Transparent
) {
    val radioColors = LocalRadioColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(radioColors.capsule)
            .border(1.dp, if (borderColor != Color.Transparent) borderColor else radioColors.border, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun TelemetryRow(
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified
) {
    val radioColors = LocalRadioColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = radioColors.textTertiary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = if (valueColor != Color.Unspecified) valueColor else radioColors.textPrimary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}
