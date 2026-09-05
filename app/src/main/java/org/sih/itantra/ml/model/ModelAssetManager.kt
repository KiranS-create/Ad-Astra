package org.sih.itantra.ml.model

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Manages extraction and location of Sherpa-ONNX neural models for offline STT & TTS.
 * Extracts models from APK assets to internal storage (context.filesDir/models)
 * so that native C++ code (including espeak-ng-data filesystem traversal)
 * can access them directly without AssetManager limitations.
 */
class ModelAssetManager(private val context: Context) {

    private val tag = "ModelAssetManager"
    private val modelsBaseDir = File(context.filesDir, "models")

    // Whisper Tiny Quantized STT paths
    val sttDir = File(modelsBaseDir, "stt/whisper-tiny")
    val whisperEncoderFile = File(sttDir, "tiny-encoder.int8.onnx")
    val whisperDecoderFile = File(sttDir, "tiny-decoder.int8.onnx")
    val whisperTokensFile = File(sttDir, "tiny-tokens.txt")

    // VITS Piper Rohan Medium Hindi TTS paths
    val ttsDir = File(modelsBaseDir, "tts/vits-piper-hi")
    val vitsModelFile = File(ttsDir, "hi_IN-rohan-medium.onnx")
    val vitsTokensFile = File(ttsDir, "tokens.txt")
    val vitsDataDir = File(ttsDir, "espeak-ng-data")

    fun isHindiSttReady(): Boolean {
        return whisperEncoderFile.exists() && whisperEncoderFile.length() > 10_000_000L &&
                whisperDecoderFile.exists() && whisperDecoderFile.length() > 50_000_000L &&
                whisperTokensFile.exists() && whisperTokensFile.length() > 100_000L
    }

    fun isHindiTtsReady(): Boolean {
        return vitsModelFile.exists() && vitsModelFile.length() > 50_000_000L &&
                vitsTokensFile.exists() &&
                vitsDataDir.exists() && vitsDataDir.isDirectory
    }

    suspend fun ensureModelsReady(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!modelsBaseDir.exists()) {
                modelsBaseDir.mkdirs()
            }

            // Extract STT if not ready
            if (!isHindiSttReady()) {
                Log.i(tag, "Extracting Whisper STT model assets to ${sttDir.absolutePath}...")
                copyAssetFolder(context.assets, "models/stt/whisper-tiny", sttDir)
                Log.i(tag, "Whisper STT extraction complete. Ready: ${isHindiSttReady()}")
            } else {
                Log.i(tag, "Whisper STT models already ready at ${sttDir.absolutePath}")
            }

            // Extract TTS if not ready
            if (!isHindiTtsReady()) {
                Log.i(tag, "Extracting VITS Piper TTS model assets to ${ttsDir.absolutePath}...")
                copyAssetFolder(context.assets, "models/tts/vits-piper-hi", ttsDir)
                Log.i(tag, "VITS Piper TTS extraction complete. Ready: ${isHindiTtsReady()}")
            } else {
                Log.i(tag, "VITS Piper TTS models already ready at ${ttsDir.absolutePath}")
            }

            return@withContext isHindiSttReady() && isHindiTtsReady()
        } catch (e: Exception) {
            Log.e(tag, "Failed to prepare neural models", e)
            return@withContext false
        }
    }

    private fun copyAssetFolder(assetManager: AssetManager, fromAssetPath: String, toDir: File) {
        if (!toDir.exists()) {
            toDir.mkdirs()
        }

        val assetList = assetManager.list(fromAssetPath) ?: return
        if (assetList.isEmpty()) {
            // It's a file
            copyAssetFile(assetManager, fromAssetPath, toDir)
        } else {
            // It's a directory
            for (item in assetList) {
                val subAssetPath = "$fromAssetPath/$item"
                val subDest = File(toDir, item)
                val subList = assetManager.list(subAssetPath)
                if (subList != null && subList.isNotEmpty()) {
                    copyAssetFolder(assetManager, subAssetPath, subDest)
                } else {
                    copyAssetFile(assetManager, subAssetPath, subDest)
                }
            }
        }
    }

    private fun copyAssetFile(assetManager: AssetManager, assetPath: String, destFile: File) {
        // If file already exists with same or larger size, skip
        if (destFile.exists() && destFile.length() > 0) {
            return
        }

        destFile.parentFile?.mkdirs()
        var input: InputStream? = null
        var output: FileOutputStream? = null
        try {
            input = assetManager.open(assetPath)
            output = FileOutputStream(destFile)
            val buffer = ByteArray(64 * 1024)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
            }
            output.flush()
        } catch (e: Exception) {
            Log.w(tag, "Failed to copy asset $assetPath: ${e.message}")
        } finally {
            try { input?.close() } catch (_: Exception) {}
            try { output?.close() } catch (_: Exception) {}
        }
    }
}
