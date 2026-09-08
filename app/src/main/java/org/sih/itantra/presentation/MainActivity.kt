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
                    var currentTab by remember { mutableStateOf(RadioNavTab.RADIO) }
                    var activeChatPeerId by remember { mutableStateOf<String?>(null) }
                    var showContacts by remember { mutableStateOf(false) }
                    var showNearbyDevices by remember { mutableStateOf(false) }
                    var showGlobalSearch by remember { mutableStateOf(false) }
                    var showModelAudit by remember { mutableStateOf(false) }
                    var showManetDemo by remember { mutableStateOf(false) }
                    var showSihDemo by remember { mutableStateOf(false) }

                    BackHandler(
                        enabled = activeChatPeerId != null || showNearbyDevices || showContacts || showGlobalSearch || showSihDemo || showModelAudit || showManetDemo
                    ) {
                        when {
                            activeChatPeerId != null -> activeChatPeerId = null
                            showNearbyDevices -> showNearbyDevices = false
                            showContacts -> showContacts = false
                            showGlobalSearch -> showGlobalSearch = false
                            showSihDemo -> showSihDemo = false
                            showModelAudit -> showModelAudit = false
                            showManetDemo -> showManetDemo = false
                        }
                    }

                    if (activeChatPeerId != null) {
                        IndividualChatScreen(
                            peerId = activeChatPeerId!!,
                            viewModel = viewModel,
                            onBack = { activeChatPeerId = null },
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .navigationBarsPadding()
                        )
                    } else if (showGlobalSearch) {
                        GlobalSearchScreen(
                            searchRepository = viewModel.searchRepository,
                            onBack = { showGlobalSearch = false },
                            onOpenChat = { peerId ->
                                showGlobalSearch = false
                                activeChatPeerId = peerId
                            },
                            onOpenContact = { nodeId ->
                                showGlobalSearch = false
                                showContacts = true
                            },
                            onInspectMessage = { messageId ->
                                showGlobalSearch = false
                                val record = MessageHistoryStore.getRecords().firstOrNull { it.id == messageId }
                                if (record != null) {
                                    activeChatPeerId = record.peer
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .navigationBarsPadding()
                        )
                    } else if (showNearbyDevices) {
                        NearbyDevicesScreen(
                            repository = viewModel.nearbyDeviceRepository,
                            onBack = { showNearbyDevices = false },
                            onAddContact = { device ->
                                viewModel.addContactFromNearby(device)
                            },
                            onOpenChat = { nodeId ->
                                showNearbyDevices = false
                                activeChatPeerId = "Node #$nodeId"
                            },
                            onTestConnection = { nodeId ->
                                viewModel.sendTestPacketTo(nodeId)
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .navigationBarsPadding()
                        )
                    } else if (showContacts) {
                        ContactsScreen(
                            contactRepository = viewModel.contactRepository,
                            onOpenChat = { nodeId ->
                                showContacts = false
                                activeChatPeerId = "Node #$nodeId"
                            },
                            onBack = { showContacts = false },
                            onOpenNearby = { showNearbyDevices = true },
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .navigationBarsPadding()
                        )
                    } else if (showSihDemo) {
                        org.sih.itantra.presentation.screens.SihDemoScreen(
                            viewModel = viewModel,
                            onBack = { showSihDemo = false },
                            onOpenDiagnostics = {
                                showSihDemo = false
                                currentTab = RadioNavTab.DIAGNOSTICS
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .navigationBarsPadding()
                        )
                    } else if (showModelAudit) {
                        ModelStatusScreen(
                            onBack = { showModelAudit = false },
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .navigationBarsPadding()
                        )
                    } else if (showManetDemo) {
                        org.sih.itantra.presentation.screens.ManetDemoScreen(
                            viewModel = viewModel,
                            onBack = { showManetDemo = false },
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .navigationBarsPadding()
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(radioColors.background)
                                .statusBarsPadding()
                                .navigationBarsPadding()
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                when (currentTab) {
                                    RadioNavTab.RADIO -> MainTransceiverScreen(
                                        viewModel = viewModel,
                                        onNavigateToSettings = { currentTab = RadioNavTab.SETTINGS },
                                        onNavigateToModelAudit = { showModelAudit = true }
                                    )
                                    RadioNavTab.CHATS -> ChatsHomeScreen(
                                        viewModel = viewModel,
                                        onOpenRadio = { currentTab = RadioNavTab.RADIO },
                                        onOpenChat = { peerId -> activeChatPeerId = peerId },
                                        onOpenContacts = { showContacts = true },
                                        onOpenGlobalSearch = { showGlobalSearch = true },
                                        onOpenNearby = { showNearbyDevices = true }
                                    )
                                    RadioNavTab.TRANSCRIPT -> HistoryScreen(
                                        viewModel = viewModel
                                    )
                                    RadioNavTab.DIAGNOSTICS -> DiagnosticsScreen(
                                        viewModel = viewModel,
                                        onOpenModelAudit = { showModelAudit = true },
                                        onOpenManetDemo = { showManetDemo = true },
                                        onOpenSihDemo = { showSihDemo = true }
                                    )
                                    RadioNavTab.SETTINGS -> SettingsScreen(
                                        viewModel = viewModel,
                                        onOpenModelAudit = { showModelAudit = true },
                                        onOpenManetDemo = { showManetDemo = true },
                                        onOpenSihDemo = { showSihDemo = true },
                                        onOpenContacts = { showContacts = true },
                                        onOpenNearby = { showNearbyDevices = true },
                                        onOpenGlobalSearch = { showGlobalSearch = true }
                                    )
                                }
                            }

                            // 4-Tab Bottom Navigation Bar (Radio leftmost, Chats second)
                            BottomNavBar(
                                currentTab = currentTab,
                                onTabSelected = {
                                    currentTab = it
                                    activeChatPeerId = null
                                    showContacts = false
                                    showNearbyDevices = false
                                    showGlobalSearch = false
                                }
                            )
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

