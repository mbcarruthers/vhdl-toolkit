package dev.vhdltoolkit.diagnostics

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class PublishDiagnosticsFilterTest {

    private val source = """
        library IEEE;
        use IEEE.std_logic_1164.al;

        entity d_flop is
            Port(
                D   : in  std_logic;
                Clk : in  std_logic
            );
        end entity;
    """.trimIndent()

    private fun notification(vararg diagnostics: Pair<Int, String>): String {
        val arr = diagnostics.joinToString(",") { (line, message) ->
            """
            {"range":{"start":{"line":$line,"character":0},"end":{"line":$line,"character":5}},
             "severity":1,"message":${quote(message)}}
            """.trimIndent()
        }
        return """{"jsonrpc":"2.0","method":"textDocument/publishDiagnostics",
                   "params":{"uri":"file:///tmp/d_flop.vhd","diagnostics":[$arr]}}"""
    }

    private fun quote(s: String) = com.google.gson.JsonPrimitive(s).toString()

    private fun filtered(json: String) = PublishDiagnosticsFilter.filter(json) { source }

    private fun diagnosticsOf(json: String): List<JsonObject> =
        (JsonParser.parseString(json) as JsonObject)
            .getAsJsonObject("params")
            .getAsJsonArray("diagnostics")
            .map { it.asJsonObject }

    @Test
    fun cascadeCollapsesToTheRootCause() {
        val input = notification(
            1 to "No declaration of 'al' within package 'std_logic_1164'",
            5 to "No declaration of 'std_logic'",
            6 to "No declaration of 'std_logic'",
        )
        val kept = diagnosticsOf(filtered(input))
        assertEquals(1, kept.size)
        assertTrue(kept[0].get("message").asString.startsWith("No declaration of 'al'"))
    }

    @Test
    fun theSurvivingErrorSaysHowManyItCaused() {
        val input = notification(
            1 to "No declaration of 'al' within package 'std_logic_1164'",
            5 to "No declaration of 'std_logic'",
            6 to "No declaration of 'std_logic'",
        )
        val message = diagnosticsOf(filtered(input))[0].get("message").asString
        assertTrue("reports the count: $message", message.contains("2 further errors"))
    }

    @Test
    fun singularWording() {
        val input = notification(
            1 to "No declaration of 'al' within package 'std_logic_1164'",
            5 to "No declaration of 'std_logic'",
        )
        val message = diagnosticsOf(filtered(input))[0].get("message").asString
        assertTrue(message, message.contains("1 further error "))
    }

    @Test
    fun unrelatedNotificationsPassThroughByteForByte() {
        val json = """{"jsonrpc":"2.0","method":"window/logMessage","params":{"type":3,"message":"hi"}}"""
        assertEquals(json, PublishDiagnosticsFilter.filter(json) { source })
    }

    @Test
    fun nothingToCollapseLeavesTheMessageUnchanged() {
        val input = notification(1 to "No declaration of 'al' within package 'std_logic_1164'")
        assertEquals(input, filtered(input))
    }

    @Test
    fun unreadableSourceFallsBackToPassingEverythingThrough() {
        val input = notification(
            1 to "No declaration of 'al' within package 'std_logic_1164'",
            5 to "No declaration of 'std_logic'",
        )
        val out = PublishDiagnosticsFilter.filter(input) { null }
        assertEquals("cannot judge without the source", input, out)
    }

    @Test
    fun malformedJsonIsReturnedUntouched() {
        val broken = """{"method":"textDocument/publishDiagnostics","params":"""
        assertEquals(broken, PublishDiagnosticsFilter.filter(broken) { source })
    }

    // ------------------------------------------------------------- framing

    @Test
    fun framingRoundTrips() {
        val out = ByteArrayOutputStream()
        LspFraming.writeMessage(out, """{"a":1}""")
        val back = LspFraming.readMessage(ByteArrayInputStream(out.toByteArray()))
        assertEquals("""{"a":1}""", back)
    }

    @Test
    fun contentLengthCountsBytesNotCharacters() {
        val payload = """{"message":"überraschung ✓"}"""
        val out = ByteArrayOutputStream()
        LspFraming.writeMessage(out, payload)

        val bytes = out.toByteArray()
        val header = String(bytes, 0, bytes.indexOf('\r'.code.toByte()), StandardCharsets.US_ASCII)
        val declared = header.substringAfter(':').trim().toInt()
        assertEquals(payload.toByteArray(StandardCharsets.UTF_8).size, declared)

        assertEquals(payload, LspFraming.readMessage(ByteArrayInputStream(bytes)))
    }

    @Test
    fun severalMessagesReadInSequence() {
        val out = ByteArrayOutputStream()
        LspFraming.writeMessage(out, """{"n":1}""")
        LspFraming.writeMessage(out, """{"n":2}""")
        LspFraming.writeMessage(out, """{"n":3}""")

        val input = ByteArrayInputStream(out.toByteArray())
        assertEquals("""{"n":1}""", LspFraming.readMessage(input))
        assertEquals("""{"n":2}""", LspFraming.readMessage(input))
        assertEquals("""{"n":3}""", LspFraming.readMessage(input))
        assertEquals(null, LspFraming.readMessage(input))
    }

    @Test
    fun extraHeadersAreTolerated() {
        val body = """{"ok":true}"""
        val raw = "Content-Type: application/vscode-jsonrpc; charset=utf-8\r\n" +
            "Content-Length: ${body.toByteArray().size}\r\n\r\n$body"
        assertEquals(body, LspFraming.readMessage(ByteArrayInputStream(raw.toByteArray())))
    }

    @Test
    fun truncatedStreamEndsCleanlyInsteadOfHanging() {
        val raw = "Content-Length: 100\r\n\r\n{\"partial\":"
        assertEquals(null, LspFraming.readMessage(ByteArrayInputStream(raw.toByteArray())))
    }
}
