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
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.transport.TransportType
import org.sih.itantra.presentation.components.BottomNavBar
import org.sih.itantra.presentation.components.RadioNavTab
import org.sih.itantra.presentation.screens.DiagnosticsScreen
import org.sih.itantra.presentation.screens.HistoryScreen
import org.sih.itantra.presentation.screens.MainTransceiverScreen
import org.sih.itantra.presentation.screens.ModelStatusScreen
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
                    var showModelAudit by remember { mutableStateOf(false) }

                    if (showModelAudit) {
                        ModelStatusScreen(
                            onBack = { showModelAudit = false },
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
                                    RadioNavTab.TRANSCRIPT -> HistoryScreen(
                                        viewModel = viewModel
                                    )
                                    RadioNavTab.DIAGNOSTICS -> DiagnosticsScreen(
                                        viewModel = viewModel,
                                        onOpenModelAudit = { showModelAudit = true }
                                    )
                                    RadioNavTab.SETTINGS -> SettingsScreen(
                                        viewModel = viewModel,
                                        onOpenModelAudit = { showModelAudit = true }
                                    )
                                }
                            }

                            // 4-Tab Bottom Navigation Bar
                            BottomNavBar(
                                currentTab = currentTab,
                                onTabSelected = { currentTab = it }
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

