package org.sih.itantra

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.sih.itantra.core.protocol.PacketFragmenter

class PacketFragmenterTest {

    @Test
    fun testFragmentationAndReassembly() {
        val fragmenter = PacketFragmenter(maxMtuBytes = 64)
        val largeData = ByteArray(200) { (it % 256).toByte() }

        val seq: Short = 100
        val fragments = fragmenter.fragment(largeData, seq)
        assertEquals(4, fragments.size) // 64 + 64 + 64 + 8 = 200 bytes

        // Simulate receiving fragments out of order
        assertNull(fragmenter.addFragment(seq, 2, 4, fragments[2]))
        assertNull(fragmenter.addFragment(seq, 0, 4, fragments[0]))
        assertNull(fragmenter.addFragment(seq, 3, 4, fragments[3]))

        val assembled = fragmenter.addFragment(seq, 1, 4, fragments[1])
        assertNotNull(assembled)
        assertArrayEquals(largeData, assembled)
    }
}
