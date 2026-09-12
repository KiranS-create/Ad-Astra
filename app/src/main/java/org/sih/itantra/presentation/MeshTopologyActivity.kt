package org.sih.itantra.presentation

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import org.sih.itantra.presentation.screens.MeshTopologyScreen
import org.sih.itantra.presentation.theme.ITantraTheme
import org.sih.itantra.presentation.viewmodel.TransceiverViewModel

/**
 * Isolated test & launch activity for Live Mesh Topology (Feature 8).
 * Allows independent physical testing without modifying shared MainActivity or BottomNavBar.
 */
class MeshTopologyActivity : ComponentActivity() {

    private val viewModel: TransceiverViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ITantraTheme(darkTheme = true) {
                MeshTopologyScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onOpenChat = { nodeId ->
                        Toast.makeText(this@MeshTopologyActivity, "Open Chat: Node #$nodeId", Toast.LENGTH_SHORT).show()
                    },
                    onOpenContact = { nodeId ->
                        Toast.makeText(this@MeshTopologyActivity, "Open Contact: Node #$nodeId", Toast.LENGTH_SHORT).show()
                    },
                    onInspectNode = { nodeId ->
                        Toast.makeText(this@MeshTopologyActivity, "Inspect Node: Node #$nodeId", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}
