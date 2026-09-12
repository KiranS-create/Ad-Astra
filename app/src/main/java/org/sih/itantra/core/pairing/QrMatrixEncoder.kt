package org.sih.itantra.core.pairing

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max

/**
 * Self-contained, pure Kotlin QR Code Matrix Generator.
 *
 * Implements ISO/IEC 18004 specification for Model 2 QR codes:
 * - 8-bit Byte encoding mode
 * - Reed-Solomon Error Correction (GF(256))
 * - Dynamic Version selection (Versions 1 through 10)
 * - Error Correction Level L and M
 * - Standard finder, alignment, and timing patterns
 * - Quiet zone padding
 * - Android Bitmap rendering
 *
 * Zero external dependencies; 100% offline.
 */
object QrMatrixEncoder {

    enum class EccLevel(val bits: Int, val formatBits: Int) {
        L(1, 0b01),
        M(0, 0b00),
        Q(3, 0b11),
        H(2, 0b10)
    }

    data class QrMatrix(
        val size: Int,
        val modules: Array<BooleanArray>
    ) {
        operator fun get(row: Int, col: Int): Boolean = modules[row][col]

        /**
         * Renders the QR matrix to an Android Bitmap.
         */
        fun toBitmap(
            modulePixelSize: Int = 8,
            quietZone: Int = 4,
            darkColor: Int = Color.BLACK,
            lightColor: Int = Color.WHITE
        ): Bitmap {
            val totalModules = size + (quietZone * 2)
            val imageSize = totalModules * modulePixelSize
            val bitmap = Bitmap.createBitmap(imageSize, imageSize, Bitmap.Config.ARGB_8888)

            val pixels = IntArray(imageSize * imageSize)

            for (y in 0 until imageSize) {
                val modY = (y / modulePixelSize) - quietZone
                for (x in 0 until imageSize) {
                    val modX = (x / modulePixelSize) - quietZone
                    val isDark = if (modY in 0 until size && modX in 0 until size) {
                        modules[modY][modX]
                    } else {
                        false
                    }
                    pixels[y * imageSize + x] = if (isDark) darkColor else lightColor
                }
            }

            bitmap.setPixels(pixels, 0, imageSize, 0, 0, imageSize, imageSize)
            return bitmap
        }
    }

    /**
     * Encodes a string into a QR code boolean matrix.
     */
    fun encode(text: String, ecc: EccLevel = EccLevel.M): QrMatrix {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val version = selectVersion(bytes.size, ecc)
        val dataCodewords = encodeData(bytes, version, ecc)
        val finalCodewords = addErrorCorrection(dataCodewords, version, ecc)
        return constructMatrix(finalCodewords, version, ecc)
    }

    // Capacity table for Byte mode (Versions 1 to 10)
    // [Version - 1][EccLevel.ordinal] = max data bytes
    private val CAPACITY_TABLE = arrayOf(
        // V1: 21x21
        intArrayOf(19, 16, 13, 9),
        // V2: 25x25
        intArrayOf(34, 28, 22, 16),
        // V3: 29x29
        intArrayOf(55, 44, 34, 26),
        // V4: 33x33
        intArrayOf(80, 64, 48, 36),
        // V5: 37x37
        intArrayOf(108, 86, 62, 46),
        // V6: 41x41
        intArrayOf(136, 108, 76, 60),
        // V7: 45x45
        intArrayOf(156, 124, 88, 66),
        // V8: 49x49
        intArrayOf(194, 154, 110, 86),
        // V9: 53x53
        intArrayOf(232, 182, 132, 100),
        // V10: 57x57
        intArrayOf(274, 216, 154, 122)
    )

    // Total data + EC codewords per version
    private val TOTAL_CODEWORDS = intArrayOf(26, 44, 70, 100, 134, 172, 196, 242, 292, 346)

    // EC codewords per block and block count
    // Triple: (totalBlocks, ecCodewordsPerBlock, totalDataCodewords)
    private fun getBlockInfo(version: Int, ecc: EccLevel): BlockInfo {
        return when (version) {
            1 -> when (ecc) {
                EccLevel.L -> BlockInfo(1, 7, 19)
                EccLevel.M -> BlockInfo(1, 10, 16)
                EccLevel.Q -> BlockInfo(1, 13, 13)
                EccLevel.H -> BlockInfo(1, 17, 9)
            }
            2 -> when (ecc) {
                EccLevel.L -> BlockInfo(1, 10, 34)
                EccLevel.M -> BlockInfo(1, 16, 28)
                EccLevel.Q -> BlockInfo(1, 22, 22)
                EccLevel.H -> BlockInfo(1, 28, 16)
            }
            3 -> when (ecc) {
                EccLevel.L -> BlockInfo(1, 15, 55)
                EccLevel.M -> BlockInfo(1, 26, 44)
                EccLevel.Q -> BlockInfo(2, 18, 34)
                EccLevel.H -> BlockInfo(2, 22, 26)
            }
            4 -> when (ecc) {
                EccLevel.L -> BlockInfo(1, 20, 80)
                EccLevel.M -> BlockInfo(2, 18, 64)
                EccLevel.Q -> BlockInfo(2, 26, 48)
                EccLevel.H -> BlockInfo(4, 16, 36)
            }
            5 -> when (ecc) {
                EccLevel.L -> BlockInfo(1, 26, 108)
                EccLevel.M -> BlockInfo(2, 24, 86)
                EccLevel.Q -> BlockInfo(4, 18, 62)
                EccLevel.H -> BlockInfo(4, 22, 46)
            }
            6 -> when (ecc) {
                EccLevel.L -> BlockInfo(2, 18, 136)
                EccLevel.M -> BlockInfo(4, 16, 108)
                EccLevel.Q -> BlockInfo(4, 24, 76)
                EccLevel.H -> BlockInfo(4, 28, 60)
            }
            7 -> when (ecc) {
                EccLevel.L -> BlockInfo(2, 20, 156)
                EccLevel.M -> BlockInfo(4, 18, 124)
                EccLevel.Q -> BlockInfo(6, 18, 88)
                EccLevel.H -> BlockInfo(5, 26, 66)
            }
            8 -> when (ecc) {
                EccLevel.L -> BlockInfo(2, 24, 194)
                EccLevel.M -> BlockInfo(4, 22, 154)
                EccLevel.Q -> BlockInfo(6, 22, 110)
                EccLevel.H -> BlockInfo(6, 26, 86)
            }
            9 -> when (ecc) {
                EccLevel.L -> BlockInfo(2, 30, 232)
                EccLevel.M -> BlockInfo(5, 22, 182)
                EccLevel.Q -> BlockInfo(8, 20, 132)
                EccLevel.H -> BlockInfo(8, 24, 100)
            }
            10 -> when (ecc) {
                EccLevel.L -> BlockInfo(4, 18, 274)
                EccLevel.M -> BlockInfo(5, 26, 216)
                EccLevel.Q -> BlockInfo(8, 24, 154)
                EccLevel.H -> BlockInfo(8, 28, 122)
            }
            else -> BlockInfo(1, 10, 19)
        }
    }

    private data class BlockInfo(
        val totalBlocks: Int,
        val ecCodewordsPerBlock: Int,
        val totalDataCodewords: Int
    )

    private fun selectVersion(dataLength: Int, ecc: EccLevel): Int {
        for (v in 1..10) {
            val cap = CAPACITY_TABLE[v - 1][ecc.ordinal]
            if (dataLength <= cap) {
                return v
            }
        }
        throw IllegalArgumentException("Payload size ($dataLength bytes) exceeds max capacity for supported QR versions (V1-10)")
    }

    private fun encodeData(data: ByteArray, version: Int, ecc: EccLevel): IntArray {
        val blockInfo = getBlockInfo(version, ecc)
        val bitBuffer = BitBuffer()

        // 1. Mode indicator: Byte mode = 0100
        bitBuffer.appendBits(0b0100, 4)

        // 2. Character count indicator: 8 bits for V1..9, 16 bits for V10+
        val countBits = if (version < 10) 8 else 16
        bitBuffer.appendBits(data.size, countBits)

        // 3. Data bits
        for (b in data) {
            bitBuffer.appendBits(b.toInt() and 0xFF, 8)
        }

        // 4. Terminator: up to 4 zero bits
        val totalDataBits = blockInfo.totalDataCodewords * 8
        val remaining = totalDataBits - bitBuffer.bitLength
        val termLen = remaining.coerceAtMost(4).coerceAtLeast(0)
        bitBuffer.appendBits(0, termLen)

        // 5. Pad to whole byte
        val padBits = (8 - (bitBuffer.bitLength % 8)) % 8
        bitBuffer.appendBits(0, padBits)

        // 6. Pad bytes 0xEC and 0x11
        var padToggle = true
        while (bitBuffer.bitLength < totalDataBits) {
            bitBuffer.appendBits(if (padToggle) 0xEC else 0x11, 8)
            padToggle = !padToggle
        }

        return bitBuffer.toCodewords()
    }

    private fun addErrorCorrection(dataCodewords: IntArray, version: Int, ecc: EccLevel): IntArray {
        val blockInfo = getBlockInfo(version, ecc)
        val totalBlocks = blockInfo.totalBlocks
        val ecCount = blockInfo.ecCodewordsPerBlock
        val totalData = blockInfo.totalDataCodewords

        val shortBlockLen = totalData / totalBlocks
        val longBlocks = totalData % totalBlocks
        val shortBlocks = totalBlocks - longBlocks

        val dataBlocks = Array(totalBlocks) { IntArray(0) }
        val ecBlocks = Array(totalBlocks) { IntArray(ecCount) }

        var offset = 0
        for (i in 0 until totalBlocks) {
            val len = if (i < shortBlocks) shortBlockLen else shortBlockLen + 1
            val block = dataCodewords.copyOfRange(offset, offset + len)
            dataBlocks[i] = block
            ecBlocks[i] = calculateReedSolomon(block, ecCount)
            offset += len
        }

        // Interleave data codewords
        val totalCodewords = TOTAL_CODEWORDS[version - 1]
        val result = IntArray(totalCodewords)
        var resultIdx = 0

        val maxDataBlockLen = shortBlockLen + if (longBlocks > 0) 1 else 0
        for (j in 0 until maxDataBlockLen) {
            for (i in 0 until totalBlocks) {
                if (j < dataBlocks[i].size) {
                    result[resultIdx++] = dataBlocks[i][j]
                }
            }
        }

        // Interleave EC codewords
        for (j in 0 until ecCount) {
            for (i in 0 until totalBlocks) {
                result[resultIdx++] = ecBlocks[i][j]
            }
        }

        return result
    }

    // Reed-Solomon math in Galois Field GF(2^8) with generator poly 0x11D (285)
    private val EXP = IntArray(512)
    private val LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            EXP[i] = x
            EXP[i + 255] = x
            LOG[x] = i
            x = (x shl 1)
            if (x >= 256) {
                x = x xor 0x11D
            }
        }
        LOG[0] = 0
    }

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return EXP[LOG[a] + LOG[b]]
    }

    private fun calculateReedSolomon(data: IntArray, ecCount: Int): IntArray {
        // Generator polynomial: (x - a^0)(x - a^1)...(x - a^(ecCount-1))
        val gen = IntArray(ecCount + 1)
        gen[0] = 1
        for (i in 0 until ecCount) {
            val root = EXP[i]
            for (j in i downTo 0) {
                gen[j + 1] = gen[j + 1] xor gfMul(gen[j], root)
            }
        }

        val remainder = IntArray(ecCount)
        for (coeff in data) {
            val factor = coeff xor remainder[0]
            for (j in 0 until ecCount - 1) {
                remainder[j] = remainder[j + 1] xor gfMul(gen[j + 1], factor)
            }
            remainder[ecCount - 1] = gfMul(gen[ecCount], factor)
        }

        return remainder
    }

    private fun constructMatrix(codewords: IntArray, version: Int, ecc: EccLevel): QrMatrix {
        val size = 21 + (version - 1) * 4
        val matrix = Array(size) { BooleanArray(size) }
        val isFunction = Array(size) { BooleanArray(size) }

        // 1. Finder patterns
        placeFinderPattern(matrix, isFunction, 0, 0)
        placeFinderPattern(matrix, isFunction, size - 7, 0)
        placeFinderPattern(matrix, isFunction, 0, size - 7)

        // 2. Alignment patterns
        val alignCoords = getAlignmentCoordinates(version)
        for (r in alignCoords) {
            for (c in alignCoords) {
                if (isFunction[r][c]) continue
                placeAlignmentPattern(matrix, isFunction, r - 2, c - 2)
            }
        }

        // 3. Timing patterns
        for (i in 8 until size - 8) {
            val dark = (i % 2 == 0)
            if (!isFunction[6][i]) {
                matrix[6][i] = dark
                isFunction[6][i] = true
            }
            if (!isFunction[i][6]) {
                matrix[i][6] = dark
                isFunction[i][6] = true
            }
        }

        // 4. Dark module
        matrix[4 * version + 9][8] = true
        isFunction[4 * version + 9][8] = true

        // 5. Reserve format info areas
        reserveFormatInfo(isFunction, size)

        // 6. Data placement (zigzag 2-column pattern)
        var bitIdx = 0
        val totalBits = codewords.size * 8
        var goingUp = true
        var c = size - 1

        while (c > 0) {
            if (c == 6) c-- // Skip vertical timing line
            val rows = if (goingUp) (size - 1 downTo 0) else (0 until size)
            for (r in rows) {
                for (colOffset in 0..1) {
                    val col = c - colOffset
                    if (!isFunction[r][col]) {
                        var bit = false
                        if (bitIdx < totalBits) {
                            val byteIdx = bitIdx / 8
                            val bitInByte = 7 - (bitIdx % 8)
                            bit = ((codewords[byteIdx] shr bitInByte) and 1) == 1
                            bitIdx++
                        }
                        // Apply Mask Pattern 0: (row + col) % 2 == 0
                        val mask = (r + col) % 2 == 0
                        matrix[r][col] = bit xor mask
                    }
                }
            }
            goingUp = !goingUp
            c -= 2
        }

        // 7. Format bits (Mask Pattern 0 + ECC level)
        placeFormatInfo(matrix, ecc, maskPattern = 0, size = size)

        return QrMatrix(size, matrix)
    }

    private fun placeFinderPattern(matrix: Array<BooleanArray>, isFunc: Array<BooleanArray>, r: Int, c: Int) {
        for (dr in -1..7) {
            for (dc in -1..7) {
                val cr = r + dr
                val cc = c + dc
                if (cr in matrix.indices && cc in matrix.indices) {
                    val isDark = when {
                        dr in 0..6 && (dc == 0 || dc == 6) -> true
                        dc in 0..6 && (dr == 0 || dr == 6) -> true
                        dr in 2..4 && dc in 2..4 -> true
                        else -> false
                    }
                    matrix[cr][cc] = isDark
                    isFunc[cr][cc] = true
                }
            }
        }
    }

    private fun placeAlignmentPattern(matrix: Array<BooleanArray>, isFunc: Array<BooleanArray>, r: Int, c: Int) {
        for (dr in 0..4) {
            for (dc in 0..4) {
                val cr = r + dr
                val cc = c + dc
                val isDark = (dr == 0 || dr == 4 || dc == 0 || dc == 4 || (dr == 2 && dc == 2))
                matrix[cr][cc] = isDark
                isFunc[cr][cc] = true
            }
        }
    }

    private fun getAlignmentCoordinates(version: Int): IntArray {
        return when (version) {
            1 -> intArrayOf()
            2 -> intArrayOf(6, 18)
            3 -> intArrayOf(6, 22)
            4 -> intArrayOf(6, 26)
            5 -> intArrayOf(6, 30)
            6 -> intArrayOf(6, 34)
            7 -> intArrayOf(6, 22, 38)
            8 -> intArrayOf(6, 24, 42)
            9 -> intArrayOf(6, 26, 46)
            10 -> intArrayOf(6, 28, 50)
            else -> intArrayOf()
        }
    }

    private fun reserveFormatInfo(isFunc: Array<BooleanArray>, size: Int) {
        for (i in 0..8) {
            isFunc[8][i] = true
            isFunc[i][8] = true
            isFunc[8][size - 1 - i] = true
            isFunc[size - 1 - i][8] = true
        }
    }

    private fun placeFormatInfo(matrix: Array<BooleanArray>, ecc: EccLevel, maskPattern: Int, size: Int) {
        // 5-bit format: (ecc_format_bits shl 3) or maskPattern
        val rawData = (ecc.formatBits shl 3) or maskPattern
        var bch = rawData shl 10
        val poly = 0x537 // BCH(15,5) generator
        for (i in 14 downTo 10) {
            if (((bch shr i) and 1) != 0) {
                bch = bch xor (poly shl (i - 10))
            }
        }
        val formatBits = ((rawData shl 10) or bch) xor 0x5412 // standard mask

        // Place around top-left finder
        var bit = 0
        for (i in 0..5) {
            matrix[8][i] = ((formatBits shr bit) and 1) == 1
            bit++
        }
        matrix[8][7] = ((formatBits shr bit) and 1) == 1
        bit++
        matrix[8][8] = ((formatBits shr bit) and 1) == 1
        bit++
        matrix[7][8] = ((formatBits shr bit) and 1) == 1
        bit++
        for (i in 5 downTo 0) {
            matrix[i][8] = ((formatBits shr bit) and 1) == 1
            bit++
        }

        // Place split between bottom-left and top-right
        bit = 0
        for (i in 0..6) {
            matrix[size - 1 - i][8] = ((formatBits shr bit) and 1) == 1
            bit++
        }
        for (i in 7 downTo 0) {
            matrix[8][size - 8 + i] = ((formatBits shr bit) and 1) == 1
            bit++
        }
    }

    private class BitBuffer {
        private val bits = ArrayList<Boolean>()

        val bitLength: Int get() = bits.size

        fun appendBits(value: Int, length: Int) {
            for (i in length - 1 downTo 0) {
                bits.add(((value shr i) and 1) == 1)
            }
        }

        fun toCodewords(): IntArray {
            val len = bits.size / 8
            val result = IntArray(len)
            for (i in 0 until len) {
                var v = 0
                for (j in 0 until 8) {
                    if (bits[i * 8 + j]) {
                        v = v or (1 shl (7 - j))
                    }
                }
                result[i] = v
            }
            return result
        }
    }
}
