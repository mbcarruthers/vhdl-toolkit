package dev.vhdltoolkit.diagnostics

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * Reads and writes the Language Server Protocol's base framing:
 * `Content-Length: N`, a blank line, then exactly N bytes of UTF-8 JSON.
 *
 * Byte-oriented throughout. Reading headers with a character Reader risks
 * buffering past the blank line and swallowing part of the body, which
 * desynchronises the stream and kills the connection — so headers are read one
 * byte at a time and the body by exact count.
 */
object LspFraming {

    private const val CONTENT_LENGTH = "content-length"

    /** One framed message, or null at clean end of stream. */
    fun readMessage(input: InputStream): String? {
        var contentLength = -1

        while (true) {
            val line = readHeaderLine(input) ?: return null // stream closed
            if (line.isEmpty()) break // blank line ends the headers
            val colon = line.indexOf(':')
            if (colon > 0) {
                val name = line.substring(0, colon).trim().lowercase()
                val value = line.substring(colon + 1).trim()
                if (name == CONTENT_LENGTH) contentLength = value.toIntOrNull() ?: -1
            }
        }

        if (contentLength < 0) return null // malformed; caller gives up on filtering
        val body = ByteArray(contentLength)
        var read = 0
        while (read < contentLength) {
            val n = input.read(body, read, contentLength - read)
            if (n < 0) return null
            read += n
        }
        return String(body, StandardCharsets.UTF_8)
    }

    fun writeMessage(output: OutputStream, json: String) {
        val body = json.toByteArray(StandardCharsets.UTF_8)
        // Content-Length counts bytes, not characters: a non-ASCII identifier
        // in a diagnostic would otherwise truncate the message.
        output.write("Content-Length: ${body.size}\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
        output.write(body)
        output.flush()
    }

    /** Reads up to CRLF (or LF), returning the line without its terminator. */
    private fun readHeaderLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream(64)
        while (true) {
            val b = input.read()
            if (b < 0) return if (buffer.size() == 0) null else buffer.toString(StandardCharsets.US_ASCII)
            if (b == '\n'.code) {
                val bytes = buffer.toByteArray()
                val end = if (bytes.isNotEmpty() && bytes.last() == '\r'.code.toByte()) bytes.size - 1 else bytes.size
                return String(bytes, 0, end, StandardCharsets.US_ASCII)
            }
            buffer.write(b)
        }
    }
}
