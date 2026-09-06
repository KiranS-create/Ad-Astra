package org.sih.itantra.ml.model

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sih.itantra.core.common.IndicLanguage
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

    // AI4Bharat IndicConformer NeMo CTC STT paths (Native Indic SOTA)
    val indicConformerDir = File(modelsBaseDir, "stt/indicconformer")
    val indicConformerTokensFile = File(indicConformerDir, "tokens.txt")

    fun getIndicConformerModelFile(language: IndicLanguage): File {
        val langCode = when (language) {
            IndicLanguage.HINDI -> "hi"
            IndicLanguage.GUJARATI -> "gu"
            IndicLanguage.MARATHI -> "mr"
            IndicLanguage.KANNADA -> "kn"
            IndicLanguage.MALAYALAM -> "ml"
            IndicLanguage.TAMIL -> "ta"
            IndicLanguage.TELUGU -> "te"
            IndicLanguage.BENGALI -> "bn"
            else -> ""
        }
        return File(indicConformerDir, "$langCode/model.int8.onnx")
    }

    fun isIndicConformerSttReady(language: IndicLanguage): Boolean {
        val model = getIndicConformerModelFile(language)
        return indicConformerTokensFile.exists() && indicConformerTokensFile.length() > 50_000L &&
                model.exists() && model.length() > 100_000_000L
    }

    // Dolphin Small Multi-Lang Quantized CTC STT paths (Native Indic / Odia)
    val dolphinDir = File(modelsBaseDir, "stt/dolphin")
    val dolphinModelFile = File(dolphinDir, "model.int8.onnx")
    val dolphinTokensFile = File(dolphinDir, "tokens.txt")

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

    // VITS Piper Google Medium Bengali TTS paths
    val bnTtsDir = File(modelsBaseDir, "tts/vits-piper-bn")
    val bnVitsModelFile = File(bnTtsDir, "bn_BD-google-medium.onnx")
    val bnVitsTokensFile = File(bnTtsDir, "tokens.txt")

    // VITS Piper Lessac Medium English TTS paths
    val enTtsDir = File(modelsBaseDir, "tts/vits-piper-en")
    val enVitsModelFile = File(enTtsDir, "en_US-lessac-medium.onnx")
    val enVitsTokensFile = File(enTtsDir, "tokens.txt")

    fun isDolphinSttReady(): Boolean {
        return dolphinModelFile.exists() && dolphinModelFile.length() > 200_000_000L &&
                dolphinTokensFile.exists() && dolphinTokensFile.length() > 400_000L
    }

    fun isWhisperSttReady(): Boolean {
        return whisperEncoderFile.exists() && whisperEncoderFile.length() > 10_000_000L &&
                whisperDecoderFile.exists() && whisperDecoderFile.length() > 50_000_000L &&
                whisperTokensFile.exists() && whisperTokensFile.length() > 100_000L
    }

    fun isHindiSttReady(): Boolean = isIndicConformerSttReady(IndicLanguage.HINDI) || isDolphinSttReady() || isWhisperSttReady()
    fun isGujaratiSttReady(): Boolean = isIndicConformerSttReady(IndicLanguage.GUJARATI) || isDolphinSttReady() || isWhisperSttReady()
    fun isMarathiSttReady(): Boolean = isIndicConformerSttReady(IndicLanguage.MARATHI) || isDolphinSttReady() || isWhisperSttReady()
    fun isKannadaSttReady(): Boolean = isIndicConformerSttReady(IndicLanguage.KANNADA) || isWhisperSttReady()
    fun isMalayalamSttReady(): Boolean = isIndicConformerSttReady(IndicLanguage.MALAYALAM) || isDolphinSttReady() || isWhisperSttReady()
    fun isTamilSttReady(): Boolean = isIndicConformerSttReady(IndicLanguage.TAMIL) || isDolphinSttReady() || isWhisperSttReady()
    fun isTeluguSttReady(): Boolean = isIndicConformerSttReady(IndicLanguage.TELUGU) || isDolphinSttReady() || isWhisperSttReady()
    fun isOdiaSttReady(): Boolean = isDolphinSttReady()
    fun isBengaliSttReady(): Boolean = isIndicConformerSttReady(IndicLanguage.BENGALI) || isDolphinSttReady() || isWhisperSttReady()
    fun isEnglishSttReady(): Boolean = isWhisperSttReady()

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

    fun isBengaliTtsReady(): Boolean {
        return bnVitsModelFile.exists() && bnVitsModelFile.length() > 50_000_000L &&
                bnVitsTokensFile.exists() &&
                sharedEspeakDataDir.exists() && sharedEspeakDataDir.isDirectory
    }

    fun isEnglishTtsReady(): Boolean {
        return enVitsModelFile.exists() && enVitsModelFile.length() > 50_000_000L &&
                enVitsTokensFile.exists() &&
                sharedEspeakDataDir.exists() && sharedEspeakDataDir.isDirectory
    }

    suspend fun ensureModelsReady(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!modelsBaseDir.exists()) {
                modelsBaseDir.mkdirs()
            }

            // Extract IndicConformer STT if present in assets and not ready
            try {
                val indicAssets = context.assets.list("models/stt/indicconformer")
                if (indicAssets != null && indicAssets.isNotEmpty()) {
                    if (!indicConformerTokensFile.exists()) {
                        Log.i(tag, "Extracting IndicConformer STT model assets to ${indicConformerDir.absolutePath}...")
                        copyAssetFolder(context.assets, "models/stt/indicconformer", indicConformerDir)
                        Log.i(tag, "IndicConformer STT extraction complete.")
                    }
                }
            } catch (e: Exception) {
                Log.d(tag, "IndicConformer assets check: ${e.message}")
            }

            // Extract Dolphin STT if not ready
            if (!isDolphinSttReady()) {
                Log.i(tag, "Extracting Dolphin Small Multi-Lang STT model assets to ${dolphinDir.absolutePath}...")
                copyAssetFolder(context.assets, "models/stt/dolphin", dolphinDir)
                Log.i(tag, "Dolphin STT extraction complete. Ready: ${isDolphinSttReady()}")
            } else {
                Log.i(tag, "Dolphin STT models already ready at ${dolphinDir.absolutePath}")
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

            // Extract Bengali TTS if not ready
            if (!isBengaliTtsReady()) {
                Log.i(tag, "Extracting VITS Piper Bengali TTS model assets to ${bnTtsDir.absolutePath}...")
                copyAssetFolder(context.assets, "models/tts/vits-piper-bn", bnTtsDir)
                Log.i(tag, "VITS Piper Bengali TTS extraction complete. Ready: ${isBengaliTtsReady()}")
            } else {
                Log.i(tag, "VITS Piper Bengali TTS models already ready at ${bnTtsDir.absolutePath}")
            }

            // Extract English TTS if not ready
            if (!isEnglishTtsReady()) {
                Log.i(tag, "Extracting VITS Piper English TTS model assets to ${enTtsDir.absolutePath}...")
                copyAssetFolder(context.assets, "models/tts/vits-piper-en", enTtsDir)
                Log.i(tag, "VITS Piper English TTS extraction complete. Ready: ${isEnglishTtsReady()}")
            } else {
                Log.i(tag, "VITS Piper English TTS models already ready at ${enTtsDir.absolutePath}")
            }

            return@withContext (isWhisperSttReady() || true) && (isHindiTtsReady() || isGujaratiTtsReady() || isMarathiTtsReady() || isKannadaTtsReady() || isMalayalamTtsReady() || isTamilTtsReady() || isTeluguTtsReady() || isOdiaTtsReady() || isBengaliTtsReady() || isEnglishTtsReady())
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
