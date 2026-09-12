package org.sih.itantra.core.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test suite for pure Kotlin QrMatrixEncoder.
 * Verifies matrix construction, dimensions, finder patterns, and timing patterns according to ISO/IEC 18004.
 */
class QrMatrixEncoderTest {

    // Test Scenario 14: QR matrix generation produces non-empty, valid square grid
    @Test
    fun qrMatrixGeneration_producesValidSquareGrid() {
        val payload = "ITANTRA:1|209070|NODE ALPHA|Alpha Unit|hi,en"
        val matrix = QrMatrixEncoder.encode(payload, QrMatrixEncoder.EccLevel.M)

        assertNotNull("Generated QR matrix must not be null", matrix)
        assertTrue("Matrix size must be at least 21 (Version 1)", matrix.size >= 21)
        assertEquals("Matrix modules row count must match size", matrix.size, matrix.modules.size)

        for (row in 0 until matrix.size) {
            assertEquals("Each row must have length equal to matrix size (square)", matrix.size, matrix.modules[row].size)
        }

        // Verify top-left finder pattern (7x7 with center 3x3 solid)
        // Center is at (3, 3)
        assertTrue("Center of top-left finder must be black", matrix[3, 3])
        assertTrue("Corner (0,0) of top-left finder must be black", matrix[0, 0])
        assertTrue("Corner (0,6) of top-left finder must be black", matrix[0, 6])
        assertTrue("Corner (6,0) of top-left finder must be black", matrix[6, 0])
        assertTrue("Corner (6,6) of top-left finder must be black", matrix[6, 6])

        // Verify top-right finder pattern
        val trCol = matrix.size - 7
        assertTrue("Top-right finder pattern (0, size-7) must be black", matrix[0, trCol])
        assertTrue("Top-right finder pattern center must be black", matrix[3, trCol + 3])

        // Verify bottom-left finder pattern
        val blRow = matrix.size - 7
        assertTrue("Bottom-left finder pattern (size-7, 0) must be black", matrix[blRow, 0])
        assertTrue("Bottom-left finder pattern center must be black", matrix[blRow + 3, 3])
    }

    @Test
    fun qrMatrixGeneration_timingPatterns_alternateProperly() {
        val payload = "ITANTRA:1|123456|TEST|Display|hi"
        val matrix = QrMatrixEncoder.encode(payload, QrMatrixEncoder.EccLevel.L)

        // Timing patterns run at row 6 and col 6 between the finder patterns
        // Row 6 from col 8 to size - 9 should alternate
        var prevHorizontal = matrix[6, 8]
        for (c in 9 until (matrix.size - 8)) {
            val curr = matrix[6, c]
            assertEquals("Horizontal timing pattern must alternate at column $c", !prevHorizontal, curr)
            prevHorizontal = curr
        }

        var prevVertical = matrix[8, 6]
        for (r in 9 until (matrix.size - 8)) {
            val curr = matrix[r, 6]
            assertEquals("Vertical timing pattern must alternate at row $r", !prevVertical, curr)
            prevVertical = curr
        }
    }

    @Test
    fun qrMatrixGeneration_shortVsLongPayload_selectsAppropriateVersion() {
        val shortPayload = "ITANTRA:1|100000|A||hi"
        val shortMatrix = QrMatrixEncoder.encode(shortPayload, QrMatrixEncoder.EccLevel.L)

        val longPayload = "ITANTRA:1|999999|LONG CALLSIGN EXPERIMENTAL|Very Long Operator Deployment Designation|hi,en,mr,ta,te,kn"
        val longMatrix = QrMatrixEncoder.encode(longPayload, QrMatrixEncoder.EccLevel.M)

        assertTrue(
            "Longer payload must select a larger or equal QR version size",
            longMatrix.size >= shortMatrix.size
        )
    }
}
