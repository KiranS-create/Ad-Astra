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

    // VITS Piper Google Medium Marathi TTS paths
    val mrTtsDir = File(modelsBaseDir, "tts/vits-piper-mr")
    val mrVitsModelFile = File(mrTtsDir, "mr_IN-google-medium.onnx")
    val mrVitsTokensFile = File(mrTtsDir, "tokens.txt")

    // VITS MMS Kannada TTS paths
    val knTtsDir = File(modelsBaseDir, "tts/vits-mms-kn")
    val knVitsModelFile = File(knTtsDir, "model.onnx")
    val knVitsTokensFile = File(knTtsDir, "tokens.txt")

    // VITS Piper Arjun Medium Malayalam TTS paths
    val mlTtsDir = File(modelsBaseDir, "tts/vits-piper-ml")
    val mlVitsModelFile = File(mlTtsDir, "ml_IN-arjun-medium.onnx")
    val mlVitsTokensFile = File(mlTtsDir, "tokens.txt")

    // VITS MMS Tamil TTS paths
    val taTtsDir = File(modelsBaseDir, "tts/vits-mms-ta")
    val taVitsModelFile = File(taTtsDir, "model.onnx")
    val taVitsTokensFile = File(taTtsDir, "tokens.txt")

    // VITS Piper Maya Medium Telugu TTS paths
    val teTtsDir = File(modelsBaseDir, "tts/vits-piper-te")
    val teVitsModelFile = File(teTtsDir, "te_IN-maya-medium.onnx")
    val teVitsTokensFile = File(teTtsDir, "tokens.txt")

    // VITS MMS Odia TTS paths
    val orTtsDir = File(modelsBaseDir, "tts/vits-mms-or")
    val orVitsModelFile = File(orTtsDir, "model.onnx")
    val orVitsTokensFile = File(orTtsDir, "tokens.txt")

    fun isWhisperSttReady(): Boolean {
        return whisperEncoderFile.exists() && whisperEncoderFile.length() > 10_000_000L &&
                whisperDecoderFile.exists() && whisperDecoderFile.length() > 50_000_000L &&
                whisperTokensFile.exists() && whisperTokensFile.length() > 100_000L
    }

    fun isHindiSttReady(): Boolean = isWhisperSttReady()
    fun isGujaratiSttReady(): Boolean = isWhisperSttReady()
    fun isMarathiSttReady(): Boolean = isWhisperSttReady()
    fun isKannadaSttReady(): Boolean = isWhisperSttReady()
    fun isMalayalamSttReady(): Boolean = isWhisperSttReady()
    fun isTamilSttReady(): Boolean = isWhisperSttReady()
    fun isTeluguSttReady(): Boolean = isWhisperSttReady()

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

    fun isMarathiTtsReady(): Boolean {
        return mrVitsModelFile.exists() && mrVitsModelFile.length() > 50_000_000L &&
                mrVitsTokensFile.exists() &&
                sharedEspeakDataDir.exists() && sharedEspeakDataDir.isDirectory
    }

    fun isKannadaTtsReady(): Boolean {
        return knVitsModelFile.exists() && knVitsModelFile.length() > 50_000_000L &&
                knVitsTokensFile.exists()
    }

    fun isMalayalamTtsReady(): Boolean {
        return mlVitsModelFile.exists() && mlVitsModelFile.length() > 50_000_000L &&
                mlVitsTokensFile.exists() &&
                sharedEspeakDataDir.exists() && sharedEspeakDataDir.isDirectory
    }

    fun isTamilTtsReady(): Boolean {
        return taVitsModelFile.exists() && taVitsModelFile.length() > 50_000_000L &&
                taVitsTokensFile.exists()
    }

    fun isTeluguTtsReady(): Boolean {
        return teVitsModelFile.exists() && teVitsModelFile.length() > 50_000_000L &&
                teVitsTokensFile.exists() &&
                sharedEspeakDataDir.exists() && sharedEspeakDataDir.isDirectory
    }

    fun isOdiaTtsReady(): Boolean {
        return orVitsModelFile.exists() && orVitsModelFile.length() > 50_000_000L &&
                orVitsTokensFile.exists()
    }

    suspend fun ensureModelsReady(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!modelsBaseDir.exists()) {
                modelsBaseDir.mkdirs()
            }

            // Extract Whisper STT if not ready
            if (!isWhisperSttReady()) {
                Log.i(tag, "Extracting Whisper-Tiny multilingual STT model assets to ${sttDir.absolutePath}...")
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

            // Extract Marathi TTS if not ready
            if (!isMarathiTtsReady()) {
                Log.i(tag, "Extracting VITS Piper Marathi TTS model assets to ${mrTtsDir.absolutePath}...")
                copyAssetFolder(context.assets, "models/tts/vits-piper-mr", mrTtsDir)
                Log.i(tag, "VITS Piper Marathi TTS extraction complete. Ready: ${isMarathiTtsReady()}")
            } else {
                Log.i(tag, "VITS Piper Marathi TTS models already ready at ${mrTtsDir.absolutePath}")
            }

            // Extract Kannada TTS if not ready
            if (!isKannadaTtsReady()) {
                Log.i(tag, "Extracting VITS MMS Kannada TTS model assets to ${knTtsDir.absolutePath}...")
                copyAssetFolder(context.assets, "models/tts/vits-mms-kn", knTtsDir)
                Log.i(tag, "VITS MMS Kannada TTS extraction complete. Ready: ${isKannadaTtsReady()}")
            } else {
                Log.i(tag, "VITS MMS Kannada TTS models already ready at ${knTtsDir.absolutePath}")
            }

            // Extract Malayalam TTS if not ready
            if (!isMalayalamTtsReady()) {
                Log.i(tag, "Extracting VITS Piper Malayalam TTS model assets to ${mlTtsDir.absolutePath}...")
                copyAssetFolder(context.assets, "models/tts/vits-piper-ml", mlTtsDir)
                Log.i(tag, "VITS Piper Malayalam TTS extraction complete. Ready: ${isMalayalamTtsReady()}")
            } else {
                Log.i(tag, "VITS Piper Malayalam TTS models already ready at ${mlTtsDir.absolutePath}")
            }

            // Extract Tamil TTS if not ready
            if (!isTamilTtsReady()) {
                Log.i(tag, "Extracting VITS MMS Tamil TTS model assets to ${taTtsDir.absolutePath}...")
                copyAssetFolder(context.assets, "models/tts/vits-mms-ta", taTtsDir)
                Log.i(tag, "VITS MMS Tamil TTS extraction complete. Ready: ${isTamilTtsReady()}")
            } else {
                Log.i(tag, "VITS MMS Tamil TTS models already ready at ${taTtsDir.absolutePath}")
            }

            // Extract Telugu TTS if not ready
            if (!isTeluguTtsReady()) {
                Log.i(tag, "Extracting VITS Piper Telugu TTS model assets to ${teTtsDir.absolutePath}...")
                copyAssetFolder(context.assets, "models/tts/vits-piper-te", teTtsDir)
                Log.i(tag, "VITS Piper Telugu TTS extraction complete. Ready: ${isTeluguTtsReady()}")
            } else {
                Log.i(tag, "VITS Piper Telugu TTS models already ready at ${teTtsDir.absolutePath}")
            }

            // Extract Odia TTS if not ready
            if (!isOdiaTtsReady()) {
                Log.i(tag, "Extracting VITS MMS Odia TTS model assets to ${orTtsDir.absolutePath}...")
                copyAssetFolder(context.assets, "models/tts/vits-mms-or", orTtsDir)
                Log.i(tag, "VITS MMS Odia TTS extraction complete. Ready: ${isOdiaTtsReady()}")
            } else {
                Log.i(tag, "VITS MMS Odia TTS models already ready at ${orTtsDir.absolutePath}")
            }

            return@withContext (isWhisperSttReady() || true) && (isHindiTtsReady() || isGujaratiTtsReady() || isMarathiTtsReady() || isKannadaTtsReady() || isMalayalamTtsReady() || isTamilTtsReady() || isTeluguTtsReady() || isOdiaTtsReady())
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
