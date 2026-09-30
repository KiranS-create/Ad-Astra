package org.sih.itantra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.ml.tts.TtsModelMemoryManager

class TtsModelMemoryManagerTest {

    private val releasedModels = mutableListOf<String>()
    private lateinit var manager: TtsModelMemoryManager<String>

    @Before
    fun setUp() {
        releasedModels.clear()
        manager = TtsModelMemoryManager(maxSlots = 2) { model ->
            releasedModels.add(model)
        }
    }

    @Test
    fun testTwoSlotCapacity() {
        manager.getOrPut(IndicLanguage.HINDI) { "HINDI_MODEL" }
        manager.getOrPut(IndicLanguage.TAMIL) { "TAMIL_MODEL" }

        assertTrue(manager.isCached(IndicLanguage.HINDI))
        assertTrue(manager.isCached(IndicLanguage.TAMIL))
        assertEquals(2, manager.getResidentLanguages().size)
        assertTrue(releasedModels.isEmpty())
    }

    @Test
    fun testLruEvictionWhenThirdLanguageAdded() {
        manager.getOrPut(IndicLanguage.HINDI) { "HINDI_MODEL" }
        manager.getOrPut(IndicLanguage.TAMIL) { "TAMIL_MODEL" }

        // Adding 3rd language should evict Hindi (least recently used)
        manager.getOrPut(IndicLanguage.ENGLISH) { "ENGLISH_MODEL" }

        assertFalse("Hindi should have been evicted", manager.isCached(IndicLanguage.HINDI))
        assertTrue(manager.isCached(IndicLanguage.TAMIL))
        assertTrue(manager.isCached(IndicLanguage.ENGLISH))
        assertEquals(listOf("HINDI_MODEL"), releasedModels)
    }

    @Test
    fun testAccessOrderUpdatesLruRecency() {
        manager.getOrPut(IndicLanguage.HINDI) { "HINDI_MODEL" }
        manager.getOrPut(IndicLanguage.TAMIL) { "TAMIL_MODEL" }

        // Access Hindi again so Tamil becomes the least recently used
        val retrieved = manager.getOrPut(IndicLanguage.HINDI) { "NEW_HINDI" }
        assertEquals("HINDI_MODEL", retrieved)

        // Add Bengali -> Tamil should be evicted, Hindi kept!
        manager.getOrPut(IndicLanguage.BENGALI) { "BENGALI_MODEL" }

        assertTrue("Hindi was recently accessed and must be kept", manager.isCached(IndicLanguage.HINDI))
        assertFalse("Tamil was least recently used and must be evicted", manager.isCached(IndicLanguage.TAMIL))
        assertTrue(manager.isCached(IndicLanguage.BENGALI))
        assertEquals(listOf("TAMIL_MODEL"), releasedModels)
    }

    @Test
    fun testPreWarmStandbyVoice() {
        manager.getOrPut(IndicLanguage.HINDI) { "HINDI_MODEL" }
        manager.preWarm(IndicLanguage.ENGLISH) { "ENGLISH_STANDBY" }

        assertTrue(manager.isCached(IndicLanguage.HINDI))
        assertTrue(manager.isCached(IndicLanguage.ENGLISH))
    }

    @Test
    fun testReleaseAllCleansAllModels() {
        manager.getOrPut(IndicLanguage.HINDI) { "HINDI_MODEL" }
        manager.getOrPut(IndicLanguage.TAMIL) { "TAMIL_MODEL" }

        manager.releaseAll()

        assertFalse(manager.isCached(IndicLanguage.HINDI))
        assertFalse(manager.isCached(IndicLanguage.TAMIL))
        assertEquals(0, manager.getResidentLanguages().size)
        assertEquals(2, releasedModels.size)
        assertTrue(releasedModels.contains("HINDI_MODEL"))
        assertTrue(releasedModels.contains("TAMIL_MODEL"))
    }
}
