package dev.android.ide.saf

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Encoding details retained when search performs a text replacement. */
internal data class TextEncoding(
    val charset: Charset,
    val byteOrderMark: ByteArray = byteArrayOf(),
) {
    fun encode(text: String): ByteArray = byteOrderMark + text.toByteArray(charset)
}

internal data class DecodedTextDocument(
    val text: String,
    val encoding: TextEncoding,
)

/**
 * Small content-based detector shared by streaming search and replacement.
 * No filename or extension allow-list is used: every regular file can be
 * considered, while binary-looking byte streams are rejected by their content.
 */
internal object TextDocumentCodec {
    const val PROBE_BYTES = 16 * 1024

    fun detectEncoding(bytes: ByteArray, byteCount: Int = bytes.size): TextEncoding? {
        val count = byteCount.coerceIn(0, bytes.size)
        if (count == 0) return TextEncoding(StandardCharsets.UTF_8)

        val encoding = when {
            hasPrefix(bytes, count, 0x00, 0x00, 0xFE, 0xFF) ->
                TextEncoding(charsetOrNull("UTF-32BE") ?: return null, bytes.copyOfRange(0, 4))
            hasPrefix(bytes, count, 0xFF, 0xFE, 0x00, 0x00) ->
                TextEncoding(charsetOrNull("UTF-32LE") ?: return null, bytes.copyOfRange(0, 4))
            hasPrefix(bytes, count, 0xEF, 0xBB, 0xBF) ->
                TextEncoding(StandardCharsets.UTF_8, bytes.copyOfRange(0, 3))
            hasPrefix(bytes, count, 0xFF, 0xFE) ->
                TextEncoding(StandardCharsets.UTF_16LE, bytes.copyOfRange(0, 2))
            hasPrefix(bytes, count, 0xFE, 0xFF) ->
                TextEncoding(StandardCharsets.UTF_16BE, bytes.copyOfRange(0, 2))
            else -> inferUtf16WithoutBom(bytes, count) ?: TextEncoding(StandardCharsets.UTF_8)
        }

        return encoding.takeIf { isTextSample(bytes, count, it) }
    }

    fun decode(bytes: ByteArray): DecodedTextDocument? {
        val probeLength = minOf(bytes.size, PROBE_BYTES)
        val encoding = detectEncoding(bytes, probeLength) ?: return null
        val textBytes = bytes.size - encoding.byteOrderMark.size
        val text = decoder(encoding.charset)
            .decode(ByteBuffer.wrap(bytes, encoding.byteOrderMark.size, textBytes))
            .toString()
        return DecodedTextDocument(text, encoding)
    }

    fun isText(bytes: ByteArray): Boolean =
        detectEncoding(bytes, minOf(bytes.size, PROBE_BYTES)) != null

    fun decoder(charset: Charset): CharsetDecoder = charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)

    private fun inferUtf16WithoutBom(bytes: ByteArray, count: Int): TextEncoding? {
        if (count < 8) return null
        var evenZeros = 0
        var oddZeros = 0
        var evenCount = 0
        var oddCount = 0
        for (index in 0 until count) {
            if (index % 2 == 0) {
                evenCount++
                if (bytes[index] == 0.toByte()) evenZeros++
            } else {
                oddCount++
                if (bytes[index] == 0.toByte()) oddZeros++
            }
        }
        val threshold = maxOf(2, count / 8)
        return when {
            oddZeros >= threshold && evenZeros * 3 < oddZeros -> TextEncoding(StandardCharsets.UTF_16LE)
            evenZeros >= threshold && oddZeros * 3 < evenZeros -> TextEncoding(StandardCharsets.UTF_16BE)
            else -> null
        }
    }

    private fun isTextSample(bytes: ByteArray, count: Int, encoding: TextEncoding): Boolean {
        val start = encoding.byteOrderMark.size
        if (start >= count) return true
        val sample = runCatching {
            decoder(encoding.charset).decode(ByteBuffer.wrap(bytes, start, count - start)).toString()
        }.getOrNull() ?: return false
        if (sample.isEmpty()) return true
        val controls = sample.count { char ->
            char.code < 32 && char != '\n' && char != '\r' && char != '\t' && char != '\u000C'
        }
        return controls <= maxOf(1, sample.length / 100)
    }

    private fun hasPrefix(bytes: ByteArray, count: Int, vararg expected: Int): Boolean =
        count >= expected.size && expected.indices.all { (bytes[it].toInt() and 0xFF) == expected[it] }

    private fun charsetOrNull(name: String): Charset? = runCatching { Charset.forName(name) }.getOrNull()
}
