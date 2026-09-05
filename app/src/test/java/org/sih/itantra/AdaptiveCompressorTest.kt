package org.sih.itantra

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sih.itantra.core.protocol.AdaptiveCompressor

class AdaptiveCompressorTest {

    @Test
    fun testShortStringAvoidsInefficientCompression() {
        // Very short Indic string where deflate would produce overhead
        val shortText = "हाँ"
        val raw = shortText.toByteArray(Charsets.UTF_8)

        val result = AdaptiveCompressor.compress(raw)
        // Should detect expansion and retain raw UTF-8
        if (result.finalSize >= result.originalSize) {
            assertFalse(result.isCompressed)
            assertArrayEquals(raw, result.bytes)
        }
    }

    @Test
    fun testLongTextCompressionAndDecompression() {
        val longText = "यह एक बहुत लंबा परीक्षण संदेश है जिसे बार-बार दोहराया गया है ताकि संपीड़न अनुपात का सही मूल्यांकन किया जा सके। ".repeat(10)
        val raw = longText.toByteArray(Charsets.UTF_8)

        val compressionResult = AdaptiveCompressor.compress(raw)
        assertTrue(compressionResult.isCompressed)
        assertTrue(compressionResult.finalSize < compressionResult.originalSize)

        val decompressed = AdaptiveCompressor.decompress(compressionResult.bytes, compressionResult.isCompressed)
        assertArrayEquals(raw, decompressed)
        assertEquals(longText, String(decompressed, Charsets.UTF_8))
    }
}
