package org.sih.itantra.core.protocol

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

data class CompressionResult(
    val bytes: ByteArray,
    val isCompressed: Boolean,
    val originalSize: Int,
    val finalSize: Int,
    val ratio: Float
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CompressionResult
        return bytes.contentEquals(other.bytes) && isCompressed == other.isCompressed
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + isCompressed.hashCode()
        return result
    }
}

/**
 * Adaptive text compressor.
 * Compares raw UTF-8 payload size vs compressed size.
 * If compression expands the payload (common for short Indic texts < 35 bytes),
 * retains the raw UTF-8 bytes to prevent wasteful radio transmission overhead.
 */
object AdaptiveCompressor {

    fun compress(rawBytes: ByteArray): CompressionResult {
        if (rawBytes.isEmpty()) {
            return CompressionResult(rawBytes, false, 0, 0, 1.0f)
        }

        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(rawBytes)
        deflater.finish()

        val outputStream = ByteArrayOutputStream(rawBytes.size)
        val buffer = ByteArray(256)

        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            outputStream.write(buffer, 0, count)
        }
        deflater.end()

        val compressed = outputStream.toByteArray()

        // Only use compression if it yields a genuine size reduction
        return if (compressed.size < rawBytes.size) {
            val ratio = compressed.size.toFloat() / rawBytes.size.toFloat()
            CompressionResult(compressed, true, rawBytes.size, compressed.size, ratio)
        } else {
            CompressionResult(rawBytes, false, rawBytes.size, rawBytes.size, 1.0f)
        }
    }

    fun decompress(bytes: ByteArray, isCompressed: Boolean): ByteArray {
        if (!isCompressed || bytes.isEmpty()) return bytes

        val inflater = Inflater()
        inflater.setInput(bytes)

        val outputStream = ByteArrayOutputStream(bytes.size * 2)
        val buffer = ByteArray(256)

        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            outputStream.write(buffer, 0, count)
        }
        inflater.end()

        return outputStream.toByteArray()
    }
}
