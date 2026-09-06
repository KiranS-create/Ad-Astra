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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import org.sih.itantra.core.transport.TransportType
import org.sih.itantra.presentation.screens.BenchmarkScreen
import org.sih.itantra.presentation.screens.DiagnosticsScreen
import org.sih.itantra.presentation.screens.HistoryScreen
import org.sih.itantra.presentation.screens.MainTransceiverScreen
import org.sih.itantra.presentation.screens.ModelStatusScreen
import org.sih.itantra.presentation.theme.ITantraTheme
import org.sih.itantra.presentation.theme.TacticalBackground
import org.sih.itantra.presentation.viewmodel.TransceiverViewModel

enum class Screen {
    MAIN,
    DIAGNOSTICS,
    MODELS,
    BENCHMARK,
    HISTORY
}

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
            ITantraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = TacticalBackground
                ) {
                    var currentScreen by remember { mutableStateOf(Screen.MAIN) }

                    when (currentScreen) {
                        Screen.MAIN -> MainTransceiverScreen(
                            viewModel = viewModel,
                            onNavigateToDiagnostics = { currentScreen = Screen.DIAGNOSTICS },
                            onNavigateToModelStatus = { currentScreen = Screen.MODELS },
                            onNavigateToBenchmark = { currentScreen = Screen.BENCHMARK },
                            onNavigateToHistory = { currentScreen = Screen.HISTORY }
                        )
                        Screen.DIAGNOSTICS -> DiagnosticsScreen(
                            viewModel = viewModel,
                            onBack = { currentScreen = Screen.MAIN }
                        )
                        Screen.MODELS -> ModelStatusScreen(
                            onBack = { currentScreen = Screen.MAIN }
                        )
                        Screen.BENCHMARK -> BenchmarkScreen(
                            viewModel = viewModel,
                            onBack = { currentScreen = Screen.MAIN }
                        )
                        Screen.HISTORY -> HistoryScreen(
                            viewModel = viewModel,
                            onBack = { currentScreen = Screen.MAIN }
                        )
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

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CHANGE_WIFI_MULTICAST_STATE
        )

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
