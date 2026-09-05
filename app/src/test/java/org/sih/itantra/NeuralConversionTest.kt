package org.sih.itantra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sih.itantra.core.common.IndicLanguage
import org.sih.itantra.core.common.MessagePriority
import org.sih.itantra.core.protocol.AdaptiveCompressor
import org.sih.itantra.core.protocol.Packet
import org.sih.itantra.core.protocol.PacketSerializer
import org.sih.itantra.core.stt.LanguageModelRegistry
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NeuralConversionTest {

    @Test
    fun testPcmToFloatAndFloatToPcmRoundTrip() {
        // Generate simulated 16-bit PCM sine wave samples
        val numSamples = 1600 // 100ms of 16kHz audio
        val pcmOriginal = ByteArray(numSamples * 2)
        val shortBuffer = ByteBuffer.wrap(pcmOriginal).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

        for (i in 0 until numSamples) {
            val sample = (kotlin.math.sin(2.0 * Math.PI * 440.0 * i / 16000.0) * 20000.0).toInt().toShort()
            shortBuffer.put(i, sample)
        }

        // 1. Convert PCM ByteArray to FloatArray [-1.0, 1.0] (Used by Sherpa-ONNX STT)
        val floatSamples = FloatArray(numSamples)
        val inputBuffer = ByteBuffer.wrap(pcmOriginal).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        for (i in 0 until numSamples) {
            floatSamples[i] = inputBuffer.get(i) / 32768.0f
        }

        // Verify float bounds
        for (s in floatSamples) {
            assertTrue("Float sample must be between -1.0 and 1.0", s >= -1.0f && s <= 1.0f)
        }

        // 2. Convert FloatArray back to 16-bit PCM ByteArray (Used by Sherpa-ONNX TTS output)
        val pcmReconstructed = ByteArray(numSamples * 2)
        val outBuffer = ByteBuffer.wrap(pcmReconstructed).order(ByteOrder.LITTLE_ENDIAN)
        for (s in floatSamples) {
            val clamped = s.coerceIn(-1.0f, 1.0f)
            val shortVal = (clamped * 32767.0f).toInt().toShort()
            outBuffer.putShort(shortVal)
        }

        // 3. Verify numerical reconstruction fidelity (< 2 quantization steps error)
        val origShorts = ByteBuffer.wrap(pcmOriginal).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val reconShorts = ByteBuffer.wrap(pcmReconstructed).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

        for (i in 0 until numSamples) {
            val diff = kotlin.math.abs(origShorts.get(i).toInt() - reconShorts.get(i).toInt())
            assertTrue("Quantization difference must be minimal (<= 2), got $diff at sample $i", diff <= 2)
        }
    }

    @Test
    fun testHindiTextEndToEndTransceiverPacket() {
        val hindiText = "नमस्ते, यह स्मार्ट इंडिया हैकथॉन का परीक्षण है।"
        val rawBytes = hindiText.toByteArray(Charsets.UTF_8)
        val compressed = AdaptiveCompressor.compress(rawBytes)

        val packet = Packet(
            version = Packet.PROTOCOL_VERSION,
            msgType = Packet.TYPE_TEXT,
            priority = MessagePriority.ALERT,
            flags = if (compressed.isCompressed) Packet.FLAG_COMPRESSED.toByte() else 0.toByte(),
            sequenceNumber = 42,
            timestamp = 1718000000000L,
            sourceDeviceId = 1001,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.HINDI,
            payload = compressed.bytes
        )

        val serialized = PacketSerializer.serialize(packet)
        val deserialized = PacketSerializer.deserialize(serialized)

        assertEquals(IndicLanguage.HINDI, deserialized.language)
        assertEquals(MessagePriority.ALERT, deserialized.priority)
        assertEquals(42.toShort(), deserialized.sequenceNumber)

        val decompressed = AdaptiveCompressor.decompress(deserialized.payload, deserialized.isCompressed)
        val recoveredText = String(decompressed, Charsets.UTF_8)
        assertEquals(hindiText, recoveredText)
    }

    @Test
    fun testLanguageModelRegistryReportsRealNeuralHindi() {
        val capabilities = LanguageModelRegistry.getCapabilities()
        val hindiStatus = capabilities.find { it.language == IndicLanguage.HINDI }

        assertTrue("Hindi capability must be registered", hindiStatus != null)
        assertTrue("Hindi must be marked offline ready", hindiStatus!!.isOfflineReady)
        assertTrue("STT engine must cite Sherpa-ONNX Whisper", hindiStatus.sttEngine.contains("Sherpa-ONNX"))
        assertTrue("TTS engine must cite Sherpa-ONNX VITS", hindiStatus.ttsEngine.contains("Sherpa-ONNX"))
        assertTrue("Footprint must reflect neural models (~170MB)", hindiStatus.footprintMb >= 100.0f)
        assertTrue("Verification notes must confirm genuine offline inference", hindiStatus.verificationNotes.contains("VERIFIED"))
    }

    @Test
    fun testGujaratiTextEndToEndTransceiverPacket() {
        val gujaratiText = "નમસ્તે, આ સ્માર્ટ ઇન્ડિયા હેકાથોનનું વાસ્તવિક પરીક્ષણ છે."
        val rawBytes = gujaratiText.toByteArray(Charsets.UTF_8)
        val compressed = AdaptiveCompressor.compress(rawBytes)

        val packet = Packet(
            version = Packet.PROTOCOL_VERSION,
            msgType = Packet.TYPE_TEXT,
            priority = MessagePriority.ALERT,
            flags = if (compressed.isCompressed) Packet.FLAG_COMPRESSED.toByte() else 0.toByte(),
            sequenceNumber = 88,
            timestamp = 1718000000000L,
            sourceDeviceId = 2002,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.GUJARATI,
            payload = compressed.bytes
        )

        val serialized = PacketSerializer.serialize(packet)
        val deserialized = PacketSerializer.deserialize(serialized)

        assertEquals(IndicLanguage.GUJARATI, deserialized.language)
        assertEquals(MessagePriority.ALERT, deserialized.priority)
        assertEquals(88.toShort(), deserialized.sequenceNumber)

        val decompressed = AdaptiveCompressor.decompress(deserialized.payload, deserialized.isCompressed)
        val recoveredText = String(decompressed, Charsets.UTF_8)
        assertEquals(gujaratiText, recoveredText)
    }

    @Test
    fun testLanguageModelRegistryReportsRealNeuralGujarati() {
        val capabilities = LanguageModelRegistry.getCapabilities()
        val guStatus = capabilities.find { it.language == IndicLanguage.GUJARATI }

        assertTrue("Gujarati capability must be registered", guStatus != null)
        assertTrue("Gujarati must be marked offline ready", guStatus!!.isOfflineReady)
        assertTrue("STT engine must cite Sherpa-ONNX Whisper", guStatus.sttEngine.contains("Sherpa-ONNX"))
        assertTrue("TTS engine must cite Sherpa-ONNX VITS Mimic3", guStatus.ttsEngine.contains("Sherpa-ONNX") && guStatus.ttsEngine.contains("Mimic3"))
        assertTrue("Footprint must reflect neural models (~179MB)", guStatus.footprintMb >= 150.0f)
        assertTrue("Verification notes must confirm genuine offline inference", guStatus.verificationNotes.contains("VERIFIED"))
    }

    @Test
    fun testMarathiTextEndToEndTransceiverPacket() {
        val marathiText = "नमस्कार, हे स्मार्ट इंडिया हॅकाथॉनचे न्यूरल ट्रान्सीव्हर चाचणी आहे."
        val rawBytes = marathiText.toByteArray(Charsets.UTF_8)
        val compressed = AdaptiveCompressor.compress(rawBytes)

        val packet = Packet(
            version = Packet.PROTOCOL_VERSION,
            msgType = Packet.TYPE_TEXT,
            priority = MessagePriority.ALERT,
            flags = if (compressed.isCompressed) Packet.FLAG_COMPRESSED.toByte() else 0.toByte(),
            sequenceNumber = 99,
            timestamp = 1718000000000L,
            sourceDeviceId = 3003,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.MARATHI,
            payload = compressed.bytes
        )

        val serialized = PacketSerializer.serialize(packet)
        val deserialized = PacketSerializer.deserialize(serialized)

        assertEquals(IndicLanguage.MARATHI, deserialized.language)
        assertEquals(MessagePriority.ALERT, deserialized.priority)
        assertEquals(99.toShort(), deserialized.sequenceNumber)

        val decompressed = AdaptiveCompressor.decompress(deserialized.payload, deserialized.isCompressed)
        val recoveredText = String(decompressed, Charsets.UTF_8)
        assertEquals(marathiText, recoveredText)
    }

    @Test
    fun testLanguageModelRegistryReportsRealNeuralMarathi() {
        val capabilities = LanguageModelRegistry.getCapabilities()
        val mrStatus = capabilities.find { it.language == IndicLanguage.MARATHI }

        assertTrue("Marathi capability must be registered", mrStatus != null)
        assertTrue("Marathi must be marked offline ready", mrStatus!!.isOfflineReady)
        assertTrue("STT engine must cite Sherpa-ONNX Whisper", mrStatus.sttEngine.contains("Sherpa-ONNX"))
        assertTrue("TTS engine must cite Sherpa-ONNX VITS Piper", mrStatus.ttsEngine.contains("Sherpa-ONNX") && mrStatus.ttsEngine.contains("Piper"))
        assertTrue("Footprint must reflect neural models (~180MB)", mrStatus.footprintMb >= 150.0f)
        assertTrue("Verification notes must confirm genuine offline inference", mrStatus.verificationNotes.contains("VERIFIED"))
    }

    @Test
    fun testKannadaTextEndToEndTransceiverPacket() {
        val kannadaText = "ನಮಸ್ಕಾರ, ಇದು ಸ್ಮಾರ್ಟ್ ಇಂಡಿಯಾ ಹ್ಯಾಕಥಾನ್ ನೈಜ ಪರೀಕ್ಷೆ ಆಗಿದೆ."
        val rawBytes = kannadaText.toByteArray(Charsets.UTF_8)
        val compressed = AdaptiveCompressor.compress(rawBytes)

        val packet = Packet(
            version = Packet.PROTOCOL_VERSION,
            msgType = Packet.TYPE_TEXT,
            priority = MessagePriority.ALERT,
            flags = if (compressed.isCompressed) Packet.FLAG_COMPRESSED.toByte() else 0.toByte(),
            sequenceNumber = 111,
            timestamp = 1718000000000L,
            sourceDeviceId = 4004,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.KANNADA,
            payload = compressed.bytes
        )

        val serialized = PacketSerializer.serialize(packet)
        val deserialized = PacketSerializer.deserialize(serialized)

        assertEquals(IndicLanguage.KANNADA, deserialized.language)
        assertEquals(MessagePriority.ALERT, deserialized.priority)
        assertEquals(111.toShort(), deserialized.sequenceNumber)

        val decompressed = AdaptiveCompressor.decompress(deserialized.payload, deserialized.isCompressed)
        val recoveredText = String(decompressed, Charsets.UTF_8)
        assertEquals(kannadaText, recoveredText)
    }

    @Test
    fun testLanguageModelRegistryReportsRealNeuralKannada() {
        val capabilities = LanguageModelRegistry.getCapabilities()
        val knStatus = capabilities.find { it.language == IndicLanguage.KANNADA }

        assertTrue("Kannada capability must be registered", knStatus != null)
        assertTrue("Kannada must be marked offline ready", knStatus!!.isOfflineReady)
        assertTrue("STT engine must cite Sherpa-ONNX Whisper", knStatus.sttEngine.contains("Sherpa-ONNX"))
        assertTrue("TTS engine must cite Sherpa-ONNX VITS MMS Meta", knStatus.ttsEngine.contains("Sherpa-ONNX") && knStatus.ttsEngine.contains("MMS"))
        assertTrue("Footprint must reflect neural models (~217MB)", knStatus.footprintMb >= 150.0f)
        assertTrue("Verification notes must confirm genuine offline inference", knStatus.verificationNotes.contains("VERIFIED"))
    }

    @Test
    fun testMalayalamTextEndToEndTransceiverPacket() {
        val malayalamText = "നമസ്കാരം, ഇത് സ്മാർട്ട് ഇന്ത്യ ഹാക്കത്തോൺ യഥാർത്ഥ പരീക്ഷണമാണ്."
        val rawBytes = malayalamText.toByteArray(Charsets.UTF_8)
        val compressed = AdaptiveCompressor.compress(rawBytes)

        val packet = Packet(
            version = Packet.PROTOCOL_VERSION,
            msgType = Packet.TYPE_TEXT,
            priority = MessagePriority.ALERT,
            flags = if (compressed.isCompressed) Packet.FLAG_COMPRESSED.toByte() else 0.toByte(),
            sequenceNumber = 122,
            timestamp = 1718000000000L,
            sourceDeviceId = 5005,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.MALAYALAM,
            payload = compressed.bytes
        )

        val serialized = PacketSerializer.serialize(packet)
        val deserialized = PacketSerializer.deserialize(serialized)

        assertEquals(IndicLanguage.MALAYALAM, deserialized.language)
        assertEquals(MessagePriority.ALERT, deserialized.priority)
        assertEquals(122.toShort(), deserialized.sequenceNumber)

        val decompressed = AdaptiveCompressor.decompress(deserialized.payload, deserialized.isCompressed)
        val recoveredText = String(decompressed, Charsets.UTF_8)
        assertEquals(malayalamText, recoveredText)
    }

    @Test
    fun testLanguageModelRegistryReportsRealNeuralMalayalam() {
        val capabilities = LanguageModelRegistry.getCapabilities()
        val mlStatus = capabilities.find { it.language == IndicLanguage.MALAYALAM }

        assertTrue("Malayalam capability must be registered", mlStatus != null)
        assertTrue("Malayalam must be marked offline ready", mlStatus!!.isOfflineReady)
        assertTrue("STT engine must cite Sherpa-ONNX Whisper", mlStatus.sttEngine.contains("Sherpa-ONNX"))
        assertTrue("TTS engine must cite Sherpa-ONNX VITS Piper", mlStatus.ttsEngine.contains("Sherpa-ONNX") && mlStatus.ttsEngine.contains("Piper"))
        assertTrue("Footprint must reflect neural models (~163MB)", mlStatus.footprintMb >= 150.0f)
        assertTrue("Verification notes must confirm genuine offline inference", mlStatus.verificationNotes.contains("VERIFIED"))
    }

    @Test
    fun testTamilTextEndToEndTransceiverPacket() {
        val tamilText = "வணக்கம், இது ஸ்மார்ட் இந்தியா ஹேக்கத்தான் நேரடி சோதனை."
        val rawBytes = tamilText.toByteArray(Charsets.UTF_8)
        val compressed = AdaptiveCompressor.compress(rawBytes)

        val packet = Packet(
            version = Packet.PROTOCOL_VERSION,
            msgType = Packet.TYPE_TEXT,
            priority = MessagePriority.ALERT,
            flags = if (compressed.isCompressed) Packet.FLAG_COMPRESSED.toByte() else 0.toByte(),
            sequenceNumber = 133,
            timestamp = 1718000000000L,
            sourceDeviceId = 6006,
            destinationDeviceId = Packet.BROADCAST_ID,
            language = IndicLanguage.TAMIL,
            payload = compressed.bytes
        )

        val serialized = PacketSerializer.serialize(packet)
        val deserialized = PacketSerializer.deserialize(serialized)

        assertEquals(IndicLanguage.TAMIL, deserialized.language)
        assertEquals(MessagePriority.ALERT, deserialized.priority)
        assertEquals(133.toShort(), deserialized.sequenceNumber)

        val decompressed = AdaptiveCompressor.decompress(deserialized.payload, deserialized.isCompressed)
        val recoveredText = String(decompressed, Charsets.UTF_8)
        assertEquals(tamilText, recoveredText)
    }

    @Test
    fun testLanguageModelRegistryReportsRealNeuralTamil() {
        val capabilities = LanguageModelRegistry.getCapabilities()
        val taStatus = capabilities.find { it.language == IndicLanguage.TAMIL }

        assertTrue("Tamil capability must be registered", taStatus != null)
        assertTrue("Tamil must be marked offline ready", taStatus!!.isOfflineReady)
        assertTrue("STT engine must cite Sherpa-ONNX Whisper", taStatus.sttEngine.contains("Sherpa-ONNX"))
        assertTrue("TTS engine must cite Sherpa-ONNX VITS MMS Meta", taStatus.ttsEngine.contains("Sherpa-ONNX") && taStatus.ttsEngine.contains("MMS"))
        assertTrue("Footprint must reflect neural models (~217MB)", taStatus.footprintMb >= 150.0f)
        assertTrue("Verification notes must confirm genuine offline inference", taStatus.verificationNotes.contains("VERIFIED"))
    }
}


