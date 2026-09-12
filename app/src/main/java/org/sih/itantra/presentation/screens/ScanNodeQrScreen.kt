package org.sih.itantra.presentation.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sih.itantra.core.contact.ContactAuthStatus
import org.sih.itantra.core.contact.ContactRepository
import org.sih.itantra.core.pairing.QrIdentityPayload
import org.sih.itantra.core.pairing.QrPairingValidator
import org.sih.itantra.core.pairing.ValidationResult
import org.sih.itantra.presentation.components.QrPairingConfirmation
import org.sih.itantra.presentation.components.QrScannerView
import org.sih.itantra.presentation.theme.LocalRadioColors

/**
 * Screen for scanning peer node QR codes to import tactical contacts offline.
 *
 * Implements strict security validations:
 * - Self-node scan detection and rejection.
 * - Duplicate contact detection and notification.
 * - Format, version, and length validation.
 * - Enforces UNVERIFIED status on successful import.
 */
@Composable
fun ScanNodeQrScreen(
    localNodeId: Int,
    contactRepository: ContactRepository,
    onContactAdded: (Int) -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val radioColors = LocalRadioColors.current

    var candidatePayload by remember { mutableStateOf<QrIdentityPayload?>(null) }
    var rejectionDialogMessage by remember { mutableStateOf<Pair<String, String>?>(null) }

    val handleScannedRaw = { raw: String ->
        val validationResult = QrPairingValidator.validateScannedQr(
            rawPayload = raw,
            localNodeId = localNodeId,
            contactRepository = contactRepository
        )

        when (validationResult) {
            is ValidationResult.Success -> {
                candidatePayload = validationResult.payload
            }
            is ValidationResult.SelfNode -> {
                rejectionDialogMessage = "SELF-NODE DETECTED" to
                        "This QR code belongs to this device (#${validationResult.nodeId}). You cannot pair your own node."
            }
            is ValidationResult.Duplicate -> {
                val contact = validationResult.existingContact
                rejectionDialogMessage = "CONTACT ALREADY EXISTS" to
                        "Node #${contact.nodeId} (${contact.callsign}) is already in your tactical contacts directory."
            }
            is ValidationResult.Invalid -> {
                rejectionDialogMessage = "INVALID QR CODE" to
                        "QR code does not contain a valid iTantra tactical identity: ${validationResult.reason}"
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Scanner View with HUD reticle & manual paste fallback
        QrScannerView(
            onQrCodeDetected = { raw ->
                handleScannedRaw(raw)
            },
            onClose = onBack,
            modifier = Modifier.fillMaxSize()
        )

        // Top bar overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(0.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = "SCAN NODE QR CODE",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "ALIGN QR IN VIEWFINDER OR ENTER PAYLOAD",
                        color = radioColors.sage,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Confirmation Dialog for Valid Scans
        candidatePayload?.let { payload ->
            QrPairingConfirmation(
                payload = payload,
                onConfirm = { confirmedPayload ->
                    val identity = confirmedPayload.toContactIdentity(
                        authStatus = ContactAuthStatus.UNVERIFIED
                    )
                    val added = contactRepository.addContact(identity)
                    candidatePayload = null

                    if (added) {
                        Toast.makeText(
                            context,
                            "Tactical Contact #${identity.nodeId} added (UNVERIFIED)",
                            Toast.LENGTH_LONG
                        ).show()
                        onContactAdded(identity.nodeId)
                        onBack()
                    } else {
                        Toast.makeText(
                            context,
                            "Failed to add contact: already exists",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onDismiss = {
                    candidatePayload = null
                }
            )
        }

        // Rejection / Error Alert Dialog
        rejectionDialogMessage?.let { (title, message) ->
            AlertDialog(
                onDismissRequest = { rejectionDialogMessage = null },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (title.contains("SELF")) Icons.Default.WarningAmber else Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = if (title.contains("SELF")) radioColors.warning else radioColors.alert,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = title,
                            color = radioColors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                text = {
                    Text(
                        text = message,
                        color = radioColors.textSecondary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = { rejectionDialogMessage = null }) {
                        Text(
                            text = "ACKNOWLEDGE",
                            color = radioColors.sage,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                containerColor = radioColors.surface,
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}
