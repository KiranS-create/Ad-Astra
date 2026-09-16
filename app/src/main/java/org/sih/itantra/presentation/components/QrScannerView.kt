package org.sih.itantra.presentation.components

import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.sih.itantra.presentation.theme.LocalRadioColors
import java.util.concurrent.Executors

/**
 * Tactical QR Scanner View.
 *
 * Implements:
 * - Live CameraX video preview feed.
 * - 100% offline real-time QR decoding with ZXing frame analyzer.
 * - Hardware camera torch toggle affordance.
 * - HUD target reticle with corner brackets and animated laser scan line.
 * - Manual text / paste fallback drawer for development, testing, and dark environments.
 * - Defensive lifecycle binding and executor cleanup preventing memory/camera leaks.
 */
@Composable
fun QrScannerView(
    onQrCodeDetected: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isTorchOn by remember { mutableStateOf(false) }
    var showManualInputDialog by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var cameraErrorMessage by remember { mutableStateOf<String?>(null) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var previewViewInstance by remember { mutableStateOf<PreviewView?>(null) }

    // Laser scanning animation
    val infiniteTransition = rememberInfiniteTransition(label = "laser")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_progress"
    )

    // CameraX setup and lifecycle binding
    LaunchedEffect(previewViewInstance, lifecycleOwner) {
        val pv = previewViewInstance ?: return@LaunchedEffect
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(pv.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                var lastScannedTimestamp = 0L
                val multiFormatReader = MultiFormatReader().apply {
                    val hints = mapOf(
                        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                        DecodeHintType.CHARACTER_SET to "UTF-8"
                    )
                    setHints(hints)
                }

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    try {
                        val now = System.currentTimeMillis()
                        // 1.2s debounce to prevent duplicate firing on the same code
                        if (now - lastScannedTimestamp > 1200L) {
                            val luminanceSource = extractLuminanceSource(imageProxy)
                            if (luminanceSource != null) {
                                val binaryBitmap = BinaryBitmap(HybridBinarizer(luminanceSource))
                                try {
                                    val result = multiFormatReader.decodeWithState(binaryBitmap)
                                    val text = result.text
                                    if (!text.isNullOrBlank()) {
                                        lastScannedTimestamp = now
                                        Handler(Looper.getMainLooper()).post {
                                            onQrCodeDetected(text)
                                        }
                                    }
                                } catch (_: NotFoundException) {
                                    // Normal condition when no QR code is in viewfinder
                                } finally {
                                    multiFormatReader.reset()
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Ignore transient frame analysis errors
                    } finally {
                        imageProxy.close()
                    }
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                cameraErrorMessage = null
            } catch (e: Exception) {
                cameraErrorMessage = "Camera initialization error: ${e.message ?: "hardware unavailable"}"
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Hardware torch control synchronization
    LaunchedEffect(isTorchOn, camera) {
        try {
            val cam = camera
            if (cam != null && cam.cameraInfo.hasFlashUnit()) {
                cam.cameraControl.enableTorch(isTorchOn)
            }
        } catch (_: Exception) {
            // Flash not supported or permission issue
        }
    }

    // Clean up camera use-cases and executor on dispose
    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                if (cameraProviderFuture.isDone) {
                    cameraProviderFuture.get().unbindAll()
                }
            } catch (_: Exception) {
                // Ignore cleanup errors
            }
            cameraExecutor.shutdown()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Live Camera Preview View (fills background)
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    this.scaleType = PreviewView.ScaleType.FILL_CENTER
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    previewViewInstance = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Camera Error Notice (if camera fails to initialize)
        cameraErrorMessage?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "CAMERA HARDWARE UNAVAILABLE",
                        color = Color(0xFFEF5350),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.SansSerif
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Use the manual code fallback button below to enter or paste node identity.",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // 3. Tactical Viewfinder HUD Overlay (Drawn on top of preview)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val boxSize = 250.dp.toPx()
            val left = (canvasWidth - boxSize) / 2f
            val top = (canvasHeight - boxSize) / 2f - 40.dp.toPx()
            val right = left + boxSize
            val bottom = top + boxSize

            // Dark semi-transparent scrim around reticle
            drawRect(
                color = Color.Black.copy(alpha = 0.55f),
                size = size
            )

            // Transparent cut-out viewport for target reticle
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = Size(boxSize, boxSize),
                cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                blendMode = BlendMode.Clear
            )

            // Viewfinder Border
            drawRoundRect(
                color = Color.White.copy(alpha = 0.35f),
                topLeft = Offset(left, top),
                size = Size(boxSize, boxSize),
                cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Tactical Green HUD Brackets at corners
            val bracketLen = 28.dp.toPx()
            val bracketColor = Color(0xFF2E7D32)
            val strokeW = 4.dp.toPx()

            // Top-Left
            drawLine(bracketColor, Offset(left, top), Offset(left + bracketLen, top), strokeW)
            drawLine(bracketColor, Offset(left, top), Offset(left, top + bracketLen), strokeW)

            // Top-Right
            drawLine(bracketColor, Offset(right, top), Offset(right - bracketLen, top), strokeW)
            drawLine(bracketColor, Offset(right, top), Offset(right, top + bracketLen), strokeW)

            // Bottom-Left
            drawLine(bracketColor, Offset(left, bottom), Offset(left + bracketLen, bottom), strokeW)
            drawLine(bracketColor, Offset(left, bottom), Offset(left, bottom - bracketLen), strokeW)

            // Bottom-Right
            drawLine(bracketColor, Offset(right, bottom), Offset(right - bracketLen, bottom), strokeW)
            drawLine(bracketColor, Offset(right, bottom), Offset(right, bottom + bracketLen), strokeW)

            // Sweeping Laser Scan Line
            val laserY = top + (boxSize * laserProgress)
            drawLine(
                color = Color(0xFFD84315).copy(alpha = 0.90f),
                start = Offset(left + 8.dp.toPx(), laserY),
                end = Offset(right - 8.dp.toPx(), laserY),
                strokeWidth = 2.5.dp.toPx()
            )
        }

        // 4. Top Toolbar: Close & Torch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Scanner",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isTorchOn) Color(0xFFFFB300) else Color.Black.copy(alpha = 0.6f))
                    .clickable { isTorchOn = !isTorchOn },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Toggle Torch",
                    tint = if (isTorchOn) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 5. Bottom Controls: Instruction and Manual Fallback Action
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "ALIGN QR CODE WITHIN TARGET RETICLE",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Hold handset steady. Scanning operates 100% offline without remote servers.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.SansSerif
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Manual Code Input Button (Vital Offline Fallback)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF242A27))
                    .border(1.dp, Color(0xFF333B37), RoundedCornerShape(10.dp))
                    .clickable { showManualInputDialog = true }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "MANUAL CODE / PASTE FALLBACK",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Manual Text Entry Fallback Modal
        if (showManualInputDialog) {
            ManualQrInputDialog(
                onConfirm = { rawText ->
                    showManualInputDialog = false
                    onQrCodeDetected(rawText)
                },
                onDismiss = { showManualInputDialog = false }
            )
        }
    }
}

/**
 * Extracts Y-plane luminance from an [ImageProxy] and rotates it according to sensor orientation.
 */
private fun extractLuminanceSource(imageProxy: ImageProxy): LuminanceSource? {
    val plane = imageProxy.planes.getOrNull(0) ?: return null
    val buffer = plane.buffer
    val width = imageProxy.width
    val height = imageProxy.height
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride

    val ySize = width * height
    val yData = ByteArray(ySize)

    val remaining = buffer.remaining()
    if (rowStride == width && pixelStride == 1 && remaining >= ySize) {
        val originalPos = buffer.position()
        buffer.get(yData, 0, ySize)
        buffer.position(originalPos)
    } else {
        val originalPos = buffer.position()
        val rowBuffer = ByteArray(rowStride)
        var outOffset = 0
        for (row in 0 until height) {
            val length = minOf(rowStride, buffer.remaining())
            buffer.get(rowBuffer, 0, length)
            for (col in 0 until width) {
                val idx = col * pixelStride
                if (idx < length) {
                    yData[outOffset + col] = rowBuffer[idx]
                }
            }
            outOffset += width
        }
        buffer.position(originalPos)
    }

    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
    val (rotatedData, rotatedDims) = rotateYData(yData, width, height, rotationDegrees)
    return PlanarYUVLuminanceSource(
        rotatedData,
        rotatedDims.first,
        rotatedDims.second,
        0,
        0,
        rotatedDims.first,
        rotatedDims.second,
        false
    )
}

/**
 * Rotates a 1-byte-per-pixel grayscale byte array by the specified rotation angle.
 */
private fun rotateYData(
    data: ByteArray,
    width: Int,
    height: Int,
    rotationDegrees: Int
): Pair<ByteArray, Pair<Int, Int>> {
    return when (rotationDegrees) {
        90 -> {
            val rotated = ByteArray(data.size)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    rotated[x * height + (height - y - 1)] = data[y * width + x]
                }
            }
            rotated to Pair(height, width)
        }
        180 -> {
            val rotated = ByteArray(data.size)
            for (i in data.indices) {
                rotated[data.size - 1 - i] = data[i]
            }
            rotated to Pair(width, height)
        }
        270 -> {
            val rotated = ByteArray(data.size)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    rotated[(width - x - 1) * height + y] = data[y * width + x]
                }
            }
            rotated to Pair(height, width)
        }
        else -> data to Pair(width, height)
    }
}

@Composable
internal fun ManualQrInputDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val radioColors = LocalRadioColors.current
    val clipboardManager = LocalClipboardManager.current
    var textInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "MANUAL QR CODE INPUT",
                color = radioColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Paste or enter the received iTantra QR payload string:",
                    color = radioColors.textSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif
                )

                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = {
                        Text(
                            text = "ITANTRA:1|477124|NODE BRAVO|...",
                            color = radioColors.textTertiary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (radioColors.isDark) radioColors.sage else radioColors.forest,
                        unfocusedBorderColor = radioColors.border.copy(alpha = 0.6f),
                        focusedContainerColor = radioColors.surface,
                        unfocusedContainerColor = radioColors.surface,
                        focusedTextColor = radioColors.textPrimary,
                        unfocusedTextColor = radioColors.textPrimary
                    )
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(radioColors.capsule)
                        .clickable {
                            val clip = clipboardManager.getText()?.text
                            if (!clip.isNullOrBlank()) {
                                textInput = clip
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Paste from clipboard",
                        tint = radioColors.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PASTE FROM CLIPBOARD",
                        color = radioColors.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (radioColors.isDark) radioColors.sage else radioColors.forest)
                    .clickable {
                        if (textInput.isNotBlank()) {
                            onConfirm(textInput.trim())
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "DECODE & CONFIRM",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "CANCEL", color = radioColors.textSecondary)
            }
        },
        containerColor = radioColors.surface
    )
}
