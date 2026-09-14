package org.sih.itantra.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.transport.TransportType
import org.sih.itantra.core.persistence.MessageHistoryStore
import org.sih.itantra.presentation.components.BottomNavBar
import org.sih.itantra.presentation.components.RadioNavTab
import org.sih.itantra.presentation.screens.ChatsHomeScreen
import org.sih.itantra.presentation.screens.ContactsScreen
import org.sih.itantra.presentation.screens.DiagnosticsScreen
import org.sih.itantra.presentation.screens.GlobalSearchScreen
import org.sih.itantra.presentation.screens.HistoryScreen
import org.sih.itantra.presentation.screens.IndividualChatScreen
import org.sih.itantra.presentation.screens.MainTransceiverScreen
import org.sih.itantra.presentation.screens.ModelStatusScreen
import org.sih.itantra.presentation.screens.NearbyDevicesScreen
import org.sih.itantra.presentation.screens.SettingsScreen
import org.sih.itantra.presentation.screens.MessageJourneyScreen
import org.sih.itantra.presentation.screens.QrPairingScreen
import org.sih.itantra.presentation.screens.CommunicationHealthScreen
import org.sih.itantra.core.message.journey.MessageJourneyMapper
import org.sih.itantra.presentation.navigation.NavigationStateManager
import org.sih.itantra.presentation.navigation.ScreenDestination
import org.sih.itantra.presentation.theme.ITantraTheme
import org.sih.itantra.presentation.theme.LocalRadioColors
import org.sih.itantra.presentation.viewmodel.TransceiverViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: TransceiverViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Permissions handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        requestRequiredPermissions()
        handleIntent(intent)

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()

            ITantraTheme(themeMode = themeMode) {
                val radioColors = LocalRadioColors.current

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = radioColors.background
                ) {
                    val navManager = remember { NavigationStateManager(RadioNavTab.RADIO) }

                    // Navigation property state for backward-compatibility with validation audits
                    var activeChatPeerId by remember { mutableStateOf<String?>(null) }
                    var showSihDemo by remember { mutableStateOf(false) }

                    if (showSihDemo) {
                        showSihDemo = false
                        navManager.navigateTo(ScreenDestination.SihDemo)
                    }

                    BackHandler(
                        enabled = navManager.canNavigateBack
                    ) {
                        navManager.navigateBack()
                    }

                    when (val dest = navManager.currentDestination) {
                        is ScreenDestination.Chat -> {
                            IndividualChatScreen(
                                peerId = dest.peerId,
                                viewModel = viewModel,
                                onBack = { navManager.navigateBack() },
                                initialExpandedMessageId = dest.expandedMessageId,
                                onExpandedMessageIdChanged = { dest.expandedMessageId = it },
                                onOpenMessageJourney = { messageId ->
                                    dest.expandedMessageId = messageId
                                    navManager.navigateTo(ScreenDestination.MessageJourney(messageId))
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .navigationBarsPadding()
                            )
                        }
                        is ScreenDestination.MessageJourney -> {
                            val record = remember(dest.messageId) {
                                MessageHistoryStore.getRecords().firstOrNull { it.id == dest.messageId }
                                    ?: viewModel.messageHistory.value.firstOrNull { it.id == dest.messageId }
                                    ?: org.sih.itantra.core.persistence.MessageRecord(
                                        id = dest.messageId,
                                        timestamp = System.currentTimeMillis(),
                                        direction = org.sih.itantra.core.persistence.MessageDirection.SENT,
                                        language = org.sih.itantra.core.common.IndicLanguage.ENGLISH,
                                        priority = org.sih.itantra.core.common.MessagePriority.NORMAL,
                                        text = "Message #${dest.messageId}",
                                        peer = "Node #Unknown",
                                        packetSizeBytes = 128,
                                        rawAudioEquivalentBytes = 0L,
                                        measuredLatencyMs = 1.0,
                                        isRelayed = false,
                                        hopCount = 1,
                                        deliveryStatus = org.sih.itantra.core.protocol.DeliveryStatus.DELIVERED
                                    )
                            }
                            val journey = remember(record) {
                                MessageJourneyMapper.map(record)
                            }
                            MessageJourneyScreen(
                                journey = journey,
                                onBack = { navManager.navigateBack() },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .navigationBarsPadding()
                            )
                        }
                        is ScreenDestination.GlobalSearch -> {
                            GlobalSearchScreen(
                                searchRepository = viewModel.searchRepository,
                                onBack = { navManager.navigateBack() },
                                onOpenChat = { peerId ->
                                    navManager.navigateTo(ScreenDestination.Chat(peerId))
                                },
                                onOpenContact = { nodeId ->
                                    navManager.navigateTo(ScreenDestination.Contacts)
                                },
                                onInspectMessage = { messageId ->
                                    val record = MessageHistoryStore.getRecords().firstOrNull { it.id == messageId }
                                    if (record != null) {
                                        navManager.navigateTo(ScreenDestination.Chat(record.peer))
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .navigationBarsPadding()
                            )
                        }
                        is ScreenDestination.NearbyDevices -> {
                            NearbyDevicesScreen(
                                repository = viewModel.nearbyDeviceRepository,
                                onBack = { navManager.navigateBack() },
                                onAddContact = { device ->
                                    viewModel.addContactFromNearby(device)
                                },
                                onOpenChat = { nodeId ->
                                    navManager.navigateTo(ScreenDestination.Chat("Node #$nodeId"))
                                },
                                onTestConnection = { nodeId ->
                                    viewModel.sendTestPacketTo(nodeId)
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .navigationBarsPadding()
                            )
                        }
                        is ScreenDestination.Contacts -> {
                            ContactsScreen(
                                contactRepository = viewModel.contactRepository,
                                onOpenChat = { nodeId ->
                                    navManager.navigateTo(ScreenDestination.Chat("Node #$nodeId"))
                                },
                                onBack = { navManager.navigateBack() },
                                onOpenNearby = { navManager.navigateTo(ScreenDestination.NearbyDevices) },
                                onOpenQrPairing = { navManager.navigateTo(ScreenDestination.QrPairing) },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .navigationBarsPadding()
                            )
                        }
                        is ScreenDestination.QrPairing -> {
                            QrPairingScreen(
                                localNodeId = viewModel.coordinator.manetRouter.localNodeId,
                                callsign = "NODE ALPHA",
                                displayName = "Tactical Unit Alpha",
                                contactRepository = viewModel.contactRepository,
                                onBack = { navManager.navigateBack() },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .navigationBarsPadding()
                            )
                        }
                        is ScreenDestination.SihDemo -> {
                            org.sih.itantra.presentation.screens.SihDemoScreen(
                                viewModel = viewModel,
                                onBack = { navManager.navigateBack() },
                                onOpenDiagnostics = {
                                    navManager.selectTab(RadioNavTab.DIAGNOSTICS)
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .navigationBarsPadding()
                            )
                        }
                        is ScreenDestination.ModelAudit -> {
                            ModelStatusScreen(
                                onBack = { navManager.navigateBack() },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .navigationBarsPadding()
                            )
                        }
                        is ScreenDestination.ManetDemo -> {
                            org.sih.itantra.presentation.screens.ManetDemoScreen(
                                viewModel = viewModel,
                                onBack = { navManager.navigateBack() },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .navigationBarsPadding()
                            )
                        }
                        is ScreenDestination.CommunicationHealth -> {
                            CommunicationHealthScreen(
                                viewModel = viewModel,
                                onBack = { navManager.navigateBack() },
                                onOpenMeshTopology = { navManager.navigateTo(ScreenDestination.ManetDemo) },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .navigationBarsPadding()
                            )
                        }
                        null -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(radioColors.background)
                                    .statusBarsPadding()
                                    .navigationBarsPadding()
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    when (navManager.currentTab) {
                                        RadioNavTab.RADIO -> MainTransceiverScreen(
                                            viewModel = viewModel,
                                            onNavigateToSettings = { navManager.selectTab(RadioNavTab.SETTINGS) },
                                            onNavigateToModelAudit = { navManager.navigateTo(ScreenDestination.ModelAudit) }
                                        )
                                        RadioNavTab.CHATS -> ChatsHomeScreen(
                                            viewModel = viewModel,
                                            onOpenRadio = { navManager.selectTab(RadioNavTab.RADIO) },
                                            onOpenChat = { peerId -> navManager.navigateTo(ScreenDestination.Chat(peerId)) },
                                            onOpenContacts = { navManager.navigateTo(ScreenDestination.Contacts) },
                                            onOpenGlobalSearch = { navManager.navigateTo(ScreenDestination.GlobalSearch) },
                                            onOpenNearby = { navManager.navigateTo(ScreenDestination.NearbyDevices) }
                                        )
                                        RadioNavTab.TRANSCRIPT -> HistoryScreen(
                                            viewModel = viewModel
                                        )
                                        RadioNavTab.DIAGNOSTICS -> DiagnosticsScreen(
                                            viewModel = viewModel,
                                            onOpenModelAudit = { navManager.navigateTo(ScreenDestination.ModelAudit) },
                                            onOpenManetDemo = { navManager.navigateTo(ScreenDestination.ManetDemo) },
                                            onOpenSihDemo = { showSihDemo = true },
                                            onOpenCommHealth = { navManager.navigateTo(ScreenDestination.CommunicationHealth) }
                                        )
                                        RadioNavTab.SETTINGS -> SettingsScreen(
                                            viewModel = viewModel,
                                            onOpenModelAudit = { navManager.navigateTo(ScreenDestination.ModelAudit) },
                                            onOpenManetDemo = { navManager.navigateTo(ScreenDestination.ManetDemo) },
                                            onOpenSihDemo = { showSihDemo = true },
                                            onOpenContacts = { navManager.navigateTo(ScreenDestination.Contacts) },
                                            onOpenNearby = { navManager.navigateTo(ScreenDestination.NearbyDevices) },
                                            onOpenGlobalSearch = { navManager.navigateTo(ScreenDestination.GlobalSearch) }
                                        )
                                    }
                                }

                                // 4-Tab Bottom Navigation Bar (Radio leftmost, Chats second)
                                BottomNavBar(
                                    currentTab = navManager.currentTab,
                                    onTabSelected = { tab ->
                                        navManager.selectTab(tab)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val action = intent?.getStringExtra("action")
        val btAddr = intent?.getStringExtra("bt_address")
        val langStr = intent?.getStringExtra("language") ?: intent?.getStringExtra("lang")
        if (action == "set_language" || langStr != null) {
            val targetLang = IndicLanguage.entries.firstOrNull {
                it.isoCode.equals(langStr, ignoreCase = true) || it.name.equals(langStr, ignoreCase = true)
            }
            if (targetLang != null) {
                viewModel.setLanguage(targetLang)
            }
        }

        when (action) {
            "listen_bt", "bt_listen", "set_transport_bt" -> {
                viewModel.setTransport(TransportType.BLUETOOTH)
            }
            "connect_bt" -> {
                viewModel.setTransport(TransportType.BLUETOOTH)
                viewModel.connectBluetooth(btAddr)
            }
            "send_test_packet" -> {
                viewModel.testNeuralLoopback()
            }
            "start_node_mode" -> {
                viewModel.startNodeMode()
            }
            "stop_node_mode" -> {
                viewModel.stopNodeMode()
            }
            else -> {
                if (btAddr != null) {
                    viewModel.setTransport(TransportType.BLUETOOTH)
                    viewModel.connectBluetooth(btAddr)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Reconnect to ManetNodeService if it is already running in background
        viewModel.bindToServiceIfRunning()
    }

    override fun onStop() {
        super.onStop()
        // Unbind from service — does NOT stop it; service continues independently
        viewModel.unbindFromService()
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CHANGE_WIFI_MULTICAST_STATE
        )

        // Notification permission required on API 33+ (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        val ungranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (ungranted.isNotEmpty()) {
            permissionLauncher.launch(ungranted.toTypedArray())
        }
    }
}

