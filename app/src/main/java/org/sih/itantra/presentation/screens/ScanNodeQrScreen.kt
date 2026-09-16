package org.sih.itantra.presentation.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.sih.itantra.core.contact.ContactAuthStatus
import org.sih.itantra.core.contact.ContactRepository
import org.sih.itantra.core.pairing.QrIdentityPayload
import org.sih.itantra.core.pairing.QrPairingValidator
import org.sih.itantra.core.pairing.ValidationResult
import org.sih.itantra.presentation.components.ManualQrInputDialog
import org.sih.itantra.presentation.components.QrPairingConfirmation
import org.sih.itantra.presentation.components.QrScannerView
import org.sih.itantra.presentation.theme.LocalRadioColors

/**
 * Screen for scanning peer node QR codes to import tactical contacts offline.
 *
 * Implements strict security validations:
 * - Runtime CAMERA permission verification, automatic request, and clear denial recovery.
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val radioColors = LocalRadioColors.current

    var candidatePayload by remember { mutableStateOf<QrIdentityPayload?>(null) }
    var rejectionDialogMessage by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showManualInputFromDeniedScreen by remember { mutableStateOf(false) }

    // Check camera permission state
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDeniedCount by remember { mutableStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            permissionDeniedCount++
        }
    }

    // Automatically request permission on initial display if not yet granted
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Re-verify permission on app resume (e.g., returning from system Settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val currentGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (currentGranted != hasCameraPermission) {
                    hasCameraPermission = currentGranted
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
        if (hasCameraPermission) {
            // Scanner View with live CameraX video preview, HUD reticle & manual paste fallback
            QrScannerView(
                onQrCodeDetected = { raw ->
                    handleScannedRaw(raw)
                },
                onClose = onBack,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Tactical Permission Denied / Rationale View
            CameraPermissionDeniedScreen(
                onGrantPermission = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                },
                onOpenSettings = {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                    context.startActivity(intent)
                },
                onManualInputFallback = {
                    showManualInputFromDeniedScreen = true
                },
                isRepeatedDenial = permissionDeniedCount > 1
            )
        }

        // Top bar overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.75f))
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
                        text = if (hasCameraPermission) "ALIGN QR IN VIEWFINDER OR ENTER PAYLOAD" else "CAMERA PERMISSION REQUIRED",
                        color = if (hasCameraPermission) radioColors.sage else Color(0xFFFFA726),
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

        // Manual Input Dialog opened from Permission Denied screen
        if (showManualInputFromDeniedScreen) {
            ManualQrInputDialog(
                onConfirm = { rawText ->
                    showManualInputFromDeniedScreen = false
                    handleScannedRaw(rawText)
                },
                onDismiss = { showManualInputFromDeniedScreen = false }
            )
        }
    }
}

/**
 * Tactical in-app permission denied content shown when camera permission is unavailable.
 */
@Composable
private fun CameraPermissionDeniedScreen(
    onGrantPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onManualInputFallback: () -> Unit,
    isRepeatedDenial: Boolean
) {
    val radioColors = LocalRadioColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111413))
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1C221F))
                .border(1.dp, Color(0xFF333D37), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.VideocamOff,
                contentDescription = null,
                tint = Color(0xFFFFA726),
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "CAMERA PERMISSION REQUIRED",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Tactical QR scanner requires optical camera access to scan peer node identities. QR decoding operates 100% offline on this handset without internet or server access.",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp,
                fontFamily = FontFamily.SansSerif
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Action: Grant Camera Permission
            Button(
                onClick = onGrantPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E7D32),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "GRANT CAMERA PERMISSION",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Secondary Action: Settings (shown especially on repeated denial)
            OutlinedButton(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4A5550)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = radioColors.sage
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "OPEN APP SETTINGS",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Fallback Action: Manual Entry
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2A332E))
                    .clickable { onManualInputFallback() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = null,
                    tint = Color(0xFF81C784),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "MANUAL CODE / PASTE FALLBACK",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
