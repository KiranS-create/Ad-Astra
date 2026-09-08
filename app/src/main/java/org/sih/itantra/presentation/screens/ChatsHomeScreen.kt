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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.chat.ChatDeliveryStatus
import org.sih.itantra.core.chat.ChatRouteState
import org.sih.itantra.core.chat.ConversationSummary
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.presentation.theme.LocalRadioColors
import org.sih.itantra.presentation.viewmodel.TransceiverViewModel
import org.sih.itantra.presentation.viewmodel.VoiceEngineStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * iTantra Chats Home Screen.
 *
 * Blends familiar WhatsApp-style messaging ergonomics with tactical offline
 * network intelligence (MANET routing, DTN store-and-forward, Indic multilingual
 * badges, and hardware delivery receipts).
 */
@Composable
fun ChatsHomeScreen(
    viewModel: TransceiverViewModel,
    onOpenRadio: () -> Unit,
    onOpenChat: (peerId: String) -> Unit = {},
    onOpenContacts: () -> Unit = {},
    onOpenGlobalSearch: () -> Unit = {},
    onOpenNearby: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current
    val conversations by viewModel.conversationSummaries.collectAsState()
    val searchQuery by viewModel.chatSearchQuery.collectAsState()
    val topologySnapshot by viewModel.meshTopologySnapshot.collectAsState()
    val voiceStatus by viewModel.voiceEngineStatus.collectAsState()

    var showNewChatDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(radioColors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 1. Top Header: Branding + Network Telemetry Pill + Voice Status
            ChatsHeader(
                activeNodeCount = topologySnapshot.nodes.size,
                voiceStatus = voiceStatus,
                onOpenRadio = onOpenRadio,
                onOpenContacts = onOpenContacts,
                onOpenGlobalSearch = onOpenGlobalSearch
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Search Affordance (Offline Instant Filter)
            ChatsSearchBar(
                query = searchQuery,
                onQueryChanged = { viewModel.setChatSearchQuery(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Conversation List or Tactical Empty State
            if (conversations.isEmpty() && searchQuery.isBlank()) {
                TacticalEmptyState(
                    localNodeId = viewModel.coordinator.manetRouter.localNodeId,
                    onOpenRadio = onOpenRadio,
                    onSendTest = {
                        viewModel.testNeuralLoopback()
                    }
                )
            } else if (conversations.isEmpty() && searchQuery.isNotBlank()) {
                SearchEmptyState(
                    query = searchQuery,
                    onClearSearch = { viewModel.setChatSearchQuery("") }
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(conversations, key = { it.id }) { conv ->
                        ConversationCard(
                            conversation = conv,
                            onClick = {
                                viewModel.markConversationAsRead(conv.id)
                                onOpenChat(conv.id)
                            }
                        )
                    }
                }
            }
        }

        // 4. Floating Action Button (New Conversation)
        FloatingActionButton(
            onClick = { showNewChatDialog = true },
            containerColor = if (radioColors.isDark) radioColors.sage else radioColors.forest,
            contentColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddComment,
                    contentDescription = "New Conversation",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "NEW CHAT",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }

    // New Conversation Tactical Picker Dialog
    if (showNewChatDialog) {
        NewConversationDialog(
            nodes = topologySnapshot.nodes.filter { !it.isLocal },
            onDismiss = { showNewChatDialog = false },
            onSelectBroadcast = {
                showNewChatDialog = false
                onOpenChat("Broadcast")
            },
            onSelectNode = { nodeId ->
                showNewChatDialog = false
                viewModel.markConversationAsRead("Node #$nodeId")
                onOpenChat("Node #$nodeId")
            },
            onSelectContacts = {
                showNewChatDialog = false
                onOpenContacts()
            },
            onSelectNearby = {
                showNewChatDialog = false
                onOpenNearby()
            }
        )
    }
}

/**
 * Tactical Header Bar with Offline Mesh Pill.
 */
@Composable
private fun ChatsHeader(
    activeNodeCount: Int,
    voiceStatus: VoiceEngineStatus,
    onOpenRadio: () -> Unit,
    onOpenContacts: () -> Unit = {},
    onOpenGlobalSearch: () -> Unit = {}
) {
    val radioColors = LocalRadioColors.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "iTANTRA",
                    color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Mesh Telemetry Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (activeNodeCount > 0)
                                if (radioColors.isDark) radioColors.forest.copy(alpha = 0.4f) else radioColors.sage.copy(alpha = 0.15f)
                            else radioColors.capsule
                        )
                        .border(
                            1.dp,
                            if (activeNodeCount > 0) radioColors.sage.copy(alpha = 0.6f) else radioColors.border,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (activeNodeCount > 0) "● MESH ACTIVE ($activeNodeCount)" else "● OFFLINE READY",
                        color = if (activeNodeCount > 0) radioColors.sage else radioColors.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Text(
                text = "TACTICAL MESH & RADIO CONVERSATIONS",
                color = radioColors.textSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
        }

        // Action icons: Search, Contacts, Quick Radio Access
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(radioColors.capsule)
                    .border(1.dp, radioColors.border.copy(alpha = 0.5f), CircleShape)
                    .clickable { onOpenGlobalSearch() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Global Search",
                    tint = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                    modifier = Modifier.size(18.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(radioColors.capsule)
                    .border(1.dp, radioColors.border.copy(alpha = 0.5f), CircleShape)
                    .clickable { onOpenContacts() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Tactical Contacts",
                    tint = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                    modifier = Modifier.size(18.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(radioColors.capsule)
                    .border(1.dp, radioColors.border.copy(alpha = 0.5f), CircleShape)
                    .clickable { onOpenRadio() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Radio,
                    contentDescription = "Quick Radio",
                    tint = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Local offline search affordance.
 */
@Composable
private fun ChatsSearchBar(
    query: String,
    onQueryChanged: (String) -> Unit
) {
    val radioColors = LocalRadioColors.current

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        placeholder = {
            Text(
                text = "Search nodes, callsigns, or messages...",
                color = radioColors.textTertiary,
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = radioColors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChanged("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear Search",
                        tint = radioColors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = if (radioColors.isDark) radioColors.sage else radioColors.forest,
            unfocusedBorderColor = radioColors.border.copy(alpha = 0.6f),
            focusedContainerColor = radioColors.surface,
            unfocusedContainerColor = radioColors.surface,
            focusedTextColor = radioColors.textPrimary,
            unfocusedTextColor = radioColors.textPrimary
        )
    )
}

/**
 * Single Conversation Card with Tactical Progressive Disclosure.
 */
@Composable
private fun ConversationCard(
    conversation: ConversationSummary,
    onClick: () -> Unit
) {
    val radioColors = LocalRadioColors.current
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(conversation.lastTimestamp))

    val dotColor = when (conversation.routeState) {
        ChatRouteState.CONNECTED_DIRECT -> radioColors.sage
        ChatRouteState.CONNECTED_RELAYED -> Color(0xFF0288D1)
        ChatRouteState.RECENTLY_HEARD -> radioColors.warning
        ChatRouteState.DTN_STORED -> Color(0xFFFF9800)
        ChatRouteState.DISCONNECTED -> radioColors.textTertiary
    }

    val cardBorderColor = if (conversation.isEmergency) {
        radioColors.alert
    } else {
        radioColors.border.copy(alpha = 0.6f)
    }

    val cardBg = if (conversation.isEmergency) {
        radioColors.alert.copy(alpha = 0.08f)
    } else {
        radioColors.surface
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(1.dp, cardBorderColor, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Row 1: Avatar + Name + Emergency Badge + Timestamp + Unread Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    // Node Monogram Avatar with Reachability Status Dot
                    Box(modifier = Modifier.size(36.dp)) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (conversation.isEmergency) radioColors.alert.copy(alpha = 0.2f)
                                    else radioColors.capsule
                                )
                                .border(
                                    1.dp,
                                    if (conversation.isEmergency) radioColors.alert else radioColors.border.copy(alpha = 0.5f),
                                    RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = conversation.displayName.take(2).uppercase(),
                                color = if (conversation.isEmergency) radioColors.alert else radioColors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        // Route Status Dot
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                                .border(1.5.dp, radioColors.surface, CircleShape)
                                .align(Alignment.BottomEnd)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = conversation.displayName,
                                color = radioColors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (conversation.isEmergency) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(radioColors.alert)
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "EMERGENCY",
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }

                // Time and Unread Count Pill
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formattedTime,
                        color = radioColors.textSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    if (conversation.unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (conversation.isEmergency) radioColors.alert
                                    else if (radioColors.isDark) radioColors.sage else radioColors.forest
                                )
                                .padding(horizontal = 7.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = conversation.unreadCount.toString(),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Row 2: Last Message Preview Snippet
            Text(
                text = conversation.lastMessageText,
                color = radioColors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 46.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Row 3: Tactical Metadata Pill Footer (Language · Route · Delivery Status · Reachability)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 46.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Language Capsule
                    TacticalCapsule(
                        text = conversation.language.name.lowercase().replaceFirstChar { it.uppercase() },
                        color = radioColors.textSecondary,
                        bg = radioColors.capsule
                    )

                    // Route Capsule (Direct / Hops)
                    val routeLabel = if (conversation.hopCount <= 1) "Direct" else "${conversation.hopCount} hops"
                    TacticalCapsule(
                        text = routeLabel,
                        color = radioColors.textSecondary,
                        bg = radioColors.capsule
                    )

                    // Radio-Aware Delivery Status
                    DeliveryStatusBadge(status = conversation.deliveryStatus)
                }

                // Reachability Label
                Text(
                    text = conversation.routeState.label.uppercase(),
                    color = dotColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/**
 * Compact Tactical Metadata Capsule.
 */
@Composable
private fun TacticalCapsule(
    text: String,
    color: Color,
    bg: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
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

/**
 * Compact Radio Delivery Status Badge.
 */
@Composable
private fun DeliveryStatusBadge(status: ChatDeliveryStatus) {
    val radioColors = LocalRadioColors.current

    val (badgeBg, badgeColor) = when (status) {
        ChatDeliveryStatus.ACK -> {
            val c = if (radioColors.isDark) radioColors.sage else radioColors.forest
            c.copy(alpha = 0.15f) to c
        }
        ChatDeliveryStatus.RECEIVED -> {
            Color(0xFF0288D1).copy(alpha = 0.15f) to Color(0xFF0288D1)
        }
        ChatDeliveryStatus.TRANSMITTING -> {
            radioColors.warning.copy(alpha = 0.15f) to radioColors.warning
        }
        ChatDeliveryStatus.DTN_STORED -> {
            Color(0xFFFF9800).copy(alpha = 0.15f) to Color(0xFFFF9800)
        }
        ChatDeliveryStatus.RELAYED -> {
            Color(0xFF0288D1).copy(alpha = 0.15f) to Color(0xFF0288D1)
        }
        ChatDeliveryStatus.QUEUED -> {
            radioColors.capsule to radioColors.textSecondary
        }
        ChatDeliveryStatus.FAILED -> {
            radioColors.alert.copy(alpha = 0.15f) to radioColors.alert
        }
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(badgeBg)
            .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = status.label,
            color = badgeColor,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Intelligent Tactical Empty State.
 */
@Composable
private fun TacticalEmptyState(
    localNodeId: Int,
    onOpenRadio: () -> Unit,
    onSendTest: () -> Unit
) {
    val radioColors = LocalRadioColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Tactical Radar Icon Container
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (radioColors.isDark) radioColors.forest.copy(alpha = 0.5f)
                        else radioColors.sage.copy(alpha = 0.15f)
                    )
                    .border(
                        1.dp,
                        radioColors.sage.copy(alpha = 0.6f),
                        RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Sensors,
                    contentDescription = "Tactical Mesh Radar",
                    tint = radioColors.sage,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "NO ACTIVE MESH CONVERSATIONS",
                color = radioColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "iTantra operates completely offline. Conversations appear automatically as nearby peer nodes are detected over Wi-Fi broadcast or Bluetooth mesh.",
                color = radioColors.textSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Local Node Telemetry Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(radioColors.surface)
                    .border(1.dp, radioColors.border.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "LOCAL NODE ID:",
                        color = radioColors.textSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "#$localNodeId",
                        color = radioColors.sage,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "RADIO TRANSPORT:",
                        color = radioColors.textSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Wi-Fi + BLE MESH",
                        color = Color(0xFF0288D1),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "UDP CHANNEL:",
                        color = radioColors.textSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "PORT 42888",
                        color = radioColors.textPrimary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons: Send Test Packet & Open Radio
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (radioColors.isDark) radioColors.sage else radioColors.forest)
                        .clickable { onSendTest() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SEND TEST PACKET",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(radioColors.capsule)
                        .border(1.dp, radioColors.border, RoundedCornerShape(10.dp))
                        .clickable { onOpenRadio() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "OPEN RADIO",
                        color = radioColors.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/**
 * Empty state when search query matches no items.
 */
@Composable
private fun SearchEmptyState(
    query: String,
    onClearSearch: () -> Unit
) {
    val radioColors = LocalRadioColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "NO MATCHING CONVERSATIONS",
                color = radioColors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "No results found for \"$query\".",
                color = radioColors.textTertiary,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            TextButton(onClick = onClearSearch) {
                Text(
                    text = "CLEAR SEARCH",
                    color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/**
 * Tactical New Conversation Dialog for picking peers or broadcasting.
 */
@Composable
private fun NewConversationDialog(
    nodes: List<org.sih.itantra.core.mesh.TopologyNode>,
    onDismiss: () -> Unit,
    onSelectBroadcast: () -> Unit,
    onSelectNode: (Int) -> Unit,
    onSelectContacts: () -> Unit = {},
    onSelectNearby: () -> Unit = {}
) {
    val radioColors = LocalRadioColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "START CONVERSATION",
                color = radioColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Select an active tactical channel or nearby mesh node:",
                    color = radioColors.textSecondary,
                    fontSize = 11.sp
                )

                // Tactical Contacts Option
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(radioColors.capsule)
                        .border(1.dp, radioColors.border, RoundedCornerShape(8.dp))
                        .clickable { onSelectContacts() }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Contacts",
                            tint = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "CHOOSE FROM CONTACTS",
                                color = radioColors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Browse offline verified peer directory",
                                color = radioColors.textSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // Scan Nearby Option
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(radioColors.capsule)
                        .border(1.dp, radioColors.border, RoundedCornerShape(8.dp))
                        .clickable { onSelectNearby() }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = "Nearby Devices",
                            tint = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "SCAN NEARBY DEVICES",
                                color = radioColors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Discover off-grid BLE & mesh neighbors",
                                color = radioColors.textSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // Option 1: Tactical Broadcast
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(radioColors.capsule)
                        .border(1.dp, radioColors.border, RoundedCornerShape(8.dp))
                        .clickable { onSelectBroadcast() }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Podcasts,
                            contentDescription = "Broadcast",
                            tint = radioColors.sage,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "TACTICAL BROADCAST",
                                color = radioColors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "All local peers within radio range",
                                color = radioColors.textSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // Option 2..N: Discovered Mesh Nodes
                if (nodes.isNotEmpty()) {
                    nodes.forEach { node ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(radioColors.surface)
                                .border(1.dp, radioColors.border, RoundedCornerShape(8.dp))
                                .clickable { onSelectNode(node.nodeId) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "NODE #${node.nodeId}",
                                        color = radioColors.textPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "${node.transport} · ${node.hopCount} hop(s)",
                                        color = radioColors.textSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (node.isReachable) radioColors.sage else radioColors.textTertiary)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "CLOSE",
                    color = radioColors.textSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        containerColor = radioColors.surface,
        shape = RoundedCornerShape(16.dp)
    )
}
