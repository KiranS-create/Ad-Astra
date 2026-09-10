package org.sih.itantra.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.chat.ChatDeliveryStatus
import org.sih.itantra.core.chat.ChatRouteState
import org.sih.itantra.core.chat.IndividualChatHeaderState
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.message.MessageTechnicalInspectorMapper
import org.sih.itantra.core.message.RadioMessageStateMapper
import org.sih.itantra.core.persistence.MessageDirection
import org.sih.itantra.core.persistence.MessageRecord
import org.sih.itantra.core.protocol.DeliveryStatus
import org.sih.itantra.core.session.PttState
import org.sih.itantra.presentation.components.MessageRadioStateIndicator
import org.sih.itantra.presentation.components.MessageTechnicalInspectorCard
import org.sih.itantra.presentation.theme.LocalRadioColors
import org.sih.itantra.presentation.viewmodel.TransceiverViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * iTantra Tactical Individual Chat / Thread Screen.
 *
 * Provides a familiar, modern 1-to-1 conversation view with iTantra-specific
 * tactical radio intelligence (peer route telemetry, progressive disclosure
 * packet inspector, offline voice synthesis playback, and PTT field composer).
 */
@Composable
fun IndividualChatScreen(
    peerId: String,
    viewModel: TransceiverViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val radioColors = LocalRadioColors.current
    val pttState by viewModel.pttState.collectAsState()
    val allMessages by viewModel.messageHistory.collectAsState()
    val topologySnapshot by viewModel.meshTopologySnapshot.collectAsState()
    val localNodeId = viewModel.coordinator.manetRouter.localNodeId

    // Mark as read immediately on opening
    LaunchedEffect(peerId) {
        viewModel.markConversationAsRead(peerId)
    }

    // Dynamic thread messages filtered and sorted chronologically (oldest to newest)
    val threadMessages = remember(allMessages, peerId) {
        viewModel.getThreadMessages(peerId)
    }

    // Dynamic header state derived from topology and thread
    val headerState = remember(allMessages, topologySnapshot, peerId) {
        viewModel.getChatHeaderState(peerId)
    }

    var showRouteInspector by remember { mutableStateOf(false) }
    var expandedMessageId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // Auto-scroll to latest message on update
    LaunchedEffect(threadMessages.size) {
        if (threadMessages.isNotEmpty()) {
            listState.animateScrollToItem(threadMessages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(radioColors.background)
    ) {
        // 1. Peer Header with Tactical Telemetry & Route Inspector Toggle
        IndividualChatHeader(
            headerState = headerState,
            localNodeId = localNodeId,
            showRouteInspector = showRouteInspector,
            onToggleRouteInspector = { showRouteInspector = !showRouteInspector },
            onBack = onBack
        )

        // Expandable Route Inspector Card (Progressive Disclosure)
        AnimatedVisibility(
            visible = showRouteInspector,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            RouteInspectorPanel(
                headerState = headerState,
                localNodeId = localNodeId
            )
        }

        // Emergency Distress Banner if thread has active emergency
        if (headerState.isEmergency) {
            EmergencyThreadBanner()
        }

        // 2. Chronological Message Timeline (LazyColumn)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            if (threadMessages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "NO PRIOR COMMUNICATIONS WITH THIS NODE",
                        color = radioColors.textTertiary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 8.dp)
                ) {
                    items(threadMessages, key = { it.id }) { msg ->
                        ChatMessageBubble(
                            record = msg,
                            isExpanded = expandedMessageId == msg.id,
                            onToggleExpand = {
                                expandedMessageId = if (expandedMessageId == msg.id) null else msg.id
                            },
                            onPlayVoice = {
                                viewModel.playVoiceMessage(msg.text, msg.language)
                            }
                        )
                    }
                }
            }
        }

        // 3. Bottom Composer: Tactical PTT Button + Quick Action Send
        ChatComposerBar(
            peerId = peerId,
            pttState = pttState,
            onStartPtt = { viewModel.startPtt() },
            onStopPtt = { viewModel.stopPtt() },
            onSendQuickMessage = {
                viewModel.testNeuralLoopback()
            }
        )
    }
}

/**
 * Top App Bar displaying Peer Callsign, Node ID, Reachability Status, and Route Badge.
 */
@Composable
private fun IndividualChatHeader(
    headerState: IndividualChatHeaderState,
    localNodeId: Int,
    showRouteInspector: Boolean,
    onToggleRouteInspector: () -> Unit,
    onBack: () -> Unit
) {
    val radioColors = LocalRadioColors.current

    val dotColor = when (headerState.routeState) {
        ChatRouteState.CONNECTED_DIRECT -> radioColors.sage
        ChatRouteState.CONNECTED_RELAYED -> Color(0xFF0288D1)
        ChatRouteState.RECENTLY_HEARD -> radioColors.warning
        ChatRouteState.DTN_STORED -> Color(0xFFFF9800)
        ChatRouteState.DISCONNECTED -> radioColors.textTertiary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(radioColors.surface)
            .border(1.dp, radioColors.border.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = if (radioColors.isDark) radioColors.sage else radioColors.forest
            )
        }

        // Peer Monogram Avatar with Route Dot
        Box(modifier = Modifier.size(38.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (headerState.isEmergency) radioColors.alert.copy(alpha = 0.2f)
                        else radioColors.capsule
                    )
                    .border(
                        1.dp,
                        if (headerState.isEmergency) radioColors.alert else radioColors.border.copy(alpha = 0.6f),
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = headerState.displayName.take(2).uppercase(),
                    color = if (headerState.isEmergency) radioColors.alert else radioColors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            // Reachability dot
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

        // Peer Title & Reachability Summary (Clickable to inspect route)
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { onToggleRouteInspector() }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = headerState.displayName,
                    color = radioColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (headerState.peerNodeId != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "#${headerState.peerNodeId}",
                        color = radioColors.textTertiary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${headerState.routeState.label.uppercase()} · ${if (headerState.hopCount <= 1) "1 HOP" else "${headerState.hopCount} HOPS"}",
                    color = dotColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (showRouteInspector) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Inspect Route",
                    tint = radioColors.textTertiary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * Expandable Tactical Route Inspector Panel.
 */
@Composable
private fun RouteInspectorPanel(
    headerState: IndividualChatHeaderState,
    localNodeId: Int
) {
    val radioColors = LocalRadioColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(radioColors.forest.copy(alpha = 0.25f))
            .border(1.dp, radioColors.border)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "TACTICAL ROUTE & TOPOLOGY INSPECTOR",
            color = if (radioColors.isDark) radioColors.sage else radioColors.forest,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "SOURCE:",
                color = radioColors.textSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Node #$localNodeId (Local)",
                color = radioColors.textPrimary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "RELAY PATH:",
                color = radioColors.textSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = if (headerState.relayNodeId != null) "Relayed via Node #${headerState.relayNodeId}" else "Direct Line-of-Sight",
                color = if (headerState.relayNodeId != null) Color(0xFF0288D1) else radioColors.sage,
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
                text = "DESTINATION:",
                color = radioColors.textSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = headerState.displayName,
                color = radioColors.textPrimary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "TRANSPORT:",
                color = radioColors.textSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "${headerState.transport} (Port 42888)",
                color = radioColors.textPrimary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * Emergency High Priority Channel Banner.
 */
@Composable
private fun EmergencyThreadBanner() {
    val radioColors = LocalRadioColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(radioColors.alert.copy(alpha = 0.15f))
            .border(1.dp, radioColors.alert)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = "Emergency Alert",
                tint = radioColors.alert,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "EMERGENCY CHANNEL ACTIVE",
                color = radioColors.alert,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Text(
            text = "PRIORITY 1",
            color = radioColors.alert,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * Chronological Tactical Message Bubble with Progressive Disclosure.
 */
@Composable
private fun ChatMessageBubble(
    record: MessageRecord,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onPlayVoice: () -> Unit
) {
    val radioColors = LocalRadioColors.current
    val isOutgoing = record.direction == MessageDirection.SENT
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(record.timestamp))

    val isDistress = record.priority == MessagePriority.DISTRESS || record.priority == MessagePriority.ALERT

    val bubbleBg = when {
        isDistress -> radioColors.alert.copy(alpha = 0.12f)
        isOutgoing -> Color(0xFF1F3028)
        else -> Color(0xFF142820)
    }

    val bubbleBorderColor = when {
        isDistress -> radioColors.alert
        isOutgoing -> radioColors.sage.copy(alpha = 0.5f)
        else -> radioColors.border.copy(alpha = 0.6f)
    }

    val alignment = if (isOutgoing) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = alignment
    ) {
        // Main Bubble Container
        Box(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .clip(RoundedCornerShape(
                    topStart = 14.dp,
                    topEnd = 14.dp,
                    bottomStart = if (isOutgoing) 14.dp else 2.dp,
                    bottomEnd = if (isOutgoing) 2.dp else 14.dp
                ))
                .background(bubbleBg)
                .border(
                    1.dp,
                    bubbleBorderColor,
                    RoundedCornerShape(
                        topStart = 14.dp,
                        topEnd = 14.dp,
                        bottomStart = if (isOutgoing) 14.dp else 2.dp,
                        bottomEnd = if (isOutgoing) 2.dp else 14.dp
                    )
                )
                .clickable { onToggleExpand() }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column {
                // Voice Message Playback Affordance
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (radioColors.isDark) radioColors.sage else radioColors.forest)
                            .clickable { onPlayVoice() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Message Voice",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Mini Waveform Visualizer Bars
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        val barHeights = listOf(6, 12, 16, 8, 14, 10, 5, 12, 8, 15, 6)
                        barHeights.forEach { h ->
                            Box(
                                modifier = Modifier
                                    .width(2.5.dp)
                                    .height(h.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(radioColors.sage.copy(alpha = 0.8f))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = "${(record.measuredLatencyMs / 1000.0).coerceAtLeast(0.1).toString().take(3)}s",
                        color = radioColors.textTertiary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Transcribed / Message Text
                Text(
                    text = record.text,
                    color = radioColors.textPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontFamily = FontFamily.SansSerif
                )

                // Attached Geo-Location if present
                if (record.location != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "📍 ${String.format(Locale.US, "%.4f, %.4f", record.location.latitude, record.location.longitude)}",
                        color = radioColors.warning,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Footer: Language Badge · Wire size
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Language Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(radioColors.capsule)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = record.language.displayName.take(5),
                            color = radioColors.textSecondary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Wire size
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

                Spacer(modifier = Modifier.height(4.dp))

                // Feature 6: Radio-Aware Message State Indicator
                // Projects real delivery/network state from MessageRecord — no fabrication.
                val radioTelemetry = remember(record.id, record.deliveryStatus, record.isRelayed) {
                    RadioMessageStateMapper.map(record)
                }
                MessageRadioStateIndicator(
                    telemetry = radioTelemetry,
                    formattedTime = formattedTime,
                    modifier = Modifier.fillMaxWidth()
                )

                // 4. Progressive Disclosure: Expanded Technical Packet Inspector (Feature 7)
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            }
        }
    }
}


/**
 * Bottom Tactical Composer Bar with Press-to-Talk and Quick Loopback send controls.
 */
@Composable
private fun ChatComposerBar(
    peerId: String,
    pttState: PttState,
    onStartPtt: () -> Unit,
    onStopPtt: () -> Unit,
    onSendQuickMessage: () -> Unit
) {
    val radioColors = LocalRadioColors.current

    val pttLabel = when (pttState) {
        PttState.RECORDING, PttState.SPEECH_DETECTED -> "RECORDING... RELEASE TO SEND"
        PttState.STT_PROCESSING -> "STT PROCESSING..."
        PttState.TRANSMITTING -> "TRANSMITTING..."
        else -> "HOLD TO TALK"
    }

    val pttBg = when (pttState) {
        PttState.RECORDING, PttState.SPEECH_DETECTED -> radioColors.alert
        PttState.STT_PROCESSING, PttState.TRANSMITTING -> radioColors.warning
        else -> if (radioColors.isDark) radioColors.sage else radioColors.forest
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(radioColors.surface)
            .border(1.dp, radioColors.border.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Tactical PTT Button (Press and Hold)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(pttBg)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            onStartPtt()
                            tryAwaitRelease()
                            onStopPtt()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "PTT Microphone",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = pttLabel,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Quick Send Loopback Button
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(radioColors.capsule)
                .border(1.dp, radioColors.border.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .clickable { onSendQuickMessage() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "Quick Send Test Packet",
                tint = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
