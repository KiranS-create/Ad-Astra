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

    // Shared espeak-ng-data directory
    val sharedEspeakDataDir = File(modelsBaseDir, "tts/vits-piper-hi/espeak-ng-data")

    // VITS Piper Rohan Medium Hindi TTS paths
    val ttsDir = File(modelsBaseDir, "tts/vits-piper-hi")
    val vitsModelFile = File(ttsDir, "hi_IN-rohan-medium.onnx")
    val vitsTokensFile = File(ttsDir, "tokens.txt")
    val vitsDataDir = sharedEspeakDataDir

    // VITS Mimic3 CMU-Indic Low Gujarati TTS paths
    val guTtsDir = File(modelsBaseDir, "tts/vits-mimic3-gu")
    val guVitsModelFile = File(guTtsDir, "gu_IN-cmu-indic_low.onnx")
    val guVitsTokensFile = File(guTtsDir, "tokens.txt")

    fun isWhisperSttReady(): Boolean {
        return whisperEncoderFile.exists() && whisperEncoderFile.length() > 10_000_000L &&
                whisperDecoderFile.exists() && whisperDecoderFile.length() > 50_000_000L &&
                whisperTokensFile.exists() && whisperTokensFile.length() > 100_000L
    }

    fun isHindiSttReady(): Boolean = isWhisperSttReady()
    fun isGujaratiSttReady(): Boolean = isWhisperSttReady()

    fun isHindiTtsReady(): Boolean {
        return vitsModelFile.exists() && vitsModelFile.length() > 50_000_000L &&
                vitsTokensFile.exists() &&
                sharedEspeakDataDir.exists() && sharedEspeakDataDir.isDirectory
    }

    fun isGujaratiTtsReady(): Boolean {
        return guVitsModelFile.exists() && guVitsModelFile.length() > 50_000_000L &&
                guVitsTokensFile.exists() &&
                sharedEspeakDataDir.exists() && sharedEspeakDataDir.isDirectory
    }

    suspend fun ensureModelsReady(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!modelsBaseDir.exists()) {
                modelsBaseDir.mkdirs()
            }

            // Extract STT if not ready
            if (!isWhisperSttReady()) {
                Log.i(tag, "Extracting Whisper STT model assets to ${sttDir.absolutePath}...")
                copyAssetFolder(context.assets, "models/stt/whisper-tiny", sttDir)
                Log.i(tag, "Whisper STT extraction complete. Ready: ${isWhisperSttReady()}")
            } else {
                Log.i(tag, "Whisper STT models already ready at ${sttDir.absolutePath}")
            }

            // Extract Hindi TTS if not ready
            if (!isHindiTtsReady()) {
                Log.i(tag, "Extracting VITS Piper Hindi TTS model assets to ${ttsDir.absolutePath}...")
                copyAssetFolder(context.assets, "models/tts/vits-piper-hi", ttsDir)
                Log.i(tag, "VITS Piper Hindi TTS extraction complete. Ready: ${isHindiTtsReady()}")
            } else {
                Log.i(tag, "VITS Piper Hindi TTS models already ready at ${ttsDir.absolutePath}")
            }

            // Extract Gujarati TTS if not ready
            if (!isGujaratiTtsReady()) {
                Log.i(tag, "Extracting VITS Mimic3 Gujarati TTS model assets to ${guTtsDir.absolutePath}...")
                copyAssetFolder(context.assets, "models/tts/vits-mimic3-gu", guTtsDir)
                Log.i(tag, "VITS Mimic3 Gujarati TTS extraction complete. Ready: ${isGujaratiTtsReady()}")
            } else {
                Log.i(tag, "VITS Mimic3 Gujarati TTS models already ready at ${guTtsDir.absolutePath}")
            }

            return@withContext isWhisperSttReady() && (isHindiTtsReady() || isGujaratiTtsReady())
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
