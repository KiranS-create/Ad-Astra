package org.sih.itantra.ml.tts

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.sih.itantra.core.common.IndicLanguage
import java.util.LinkedHashMap

/**
 * Universal Multi-Language Voice Memory Pool for On-Device Neural TTS.
 *
 * Implements a thread-safe, 2-slot Least Recently Used (LRU) model cache:
 * - Slot 1: Active primary language (e.g. Hindi, Tamil, Bengali)
 * - Slot 2: Standby emergency / bridge language (e.g. English or Hindi)
 *
 * Benefits:
 * - Switching between local team language and national/disaster coordinator language
 *   is instant (0ms reload latency) instead of stalling the audio pipeline for 150-300ms.
 * - Caps resident RAM usage strictly to two compact quantized INT8 VITS models (~48MB each),
 *   safely below Android's aggressive Low Memory Killer (LMK) threshold.
 * - Automatic background garbage collection after model eviction.
 */
class TtsModelMemoryManager<T : Any>(
    val maxSlots: Int = 2,
    private val releaseFn: (T) -> Unit = {}
) {
    private val tag = "TtsModelMemoryManager"
    private val lock = Any()

    // LinkedHashMap with accessOrder=true for LRU eviction
    private val pool = object : LinkedHashMap<IndicLanguage, T>(maxSlots, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<IndicLanguage, T>?): Boolean {
            if (size > maxSlots && eldest != null) {
                Log.i(tag, "Evicting LRU TTS model (${eldest.key.displayName}) to maintain max $maxSlots resident models")
                try {
                    releaseFn(eldest.value)
                } catch (e: Throwable) {
                    Log.w(tag, "Error releasing evicted TTS model for ${eldest.key.displayName}", e)
                }
                // Asynchronously trigger clean memory reclaim
                CoroutineScope(Dispatchers.IO).launch {
                    System.gc()
                }
                return true
            }
            return false
        }
    }

    /**
     * Retrieves an existing cached model or initializes one using the provided factory.
     */
    fun getOrPut(language: IndicLanguage, factory: (IndicLanguage) -> T?): T? = synchronized(lock) {
        val existing = pool[language]
        if (existing != null) {
            return existing
        }

        val newModel = factory(language) ?: return null
        pool[language] = newModel
        Log.i(tag, "Loaded and cached TTS model for ${language.displayName}. Active pool: ${pool.keys.map { it.displayName }}")
        newModel
    }

    /**
     * Checks if a model for the specified language is already resident in memory.
     */
    fun isCached(language: IndicLanguage): Boolean = synchronized(lock) {
        pool.containsKey(language)
    }

    /**
     * Returns the list of currently resident languages.
     */
    fun getResidentLanguages(): List<IndicLanguage> = synchronized(lock) {
        pool.keys.toList()
    }

    /**
     * Pre-warms a standby language into the secondary slot without interrupting active playback.
     */
    fun preWarm(language: IndicLanguage, factory: (IndicLanguage) -> T?) = synchronized(lock) {
        if (!pool.containsKey(language)) {
            Log.i(tag, "Pre-warming standby TTS voice: ${language.displayName}")
            val model = factory(language)
            if (model != null) {
                pool[language] = model
            }
        }
    }

    /**
     * Releases and evicts all resident models (e.g. on application exit or severe memory trim).
     */
    fun releaseAll() = synchronized(lock) {
        Log.i(tag, "Releasing all resident TTS models in memory pool")
        for ((lang, model) in pool) {
            try {
                releaseFn(model)
            } catch (e: Throwable) {
                Log.w(tag, "Error releasing TTS model for ${lang.displayName}", e)
            }
        }
        pool.clear()
        CoroutineScope(Dispatchers.IO).launch {
            System.gc()
        }
    }
}
