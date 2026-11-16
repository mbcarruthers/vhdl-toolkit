package dev.vhdltoolkit.diagnostics

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/**
 * Rewrites `textDocument/publishDiagnostics` notifications, dropping the
 * diagnostics that [DiagnosticCascade] attributes to another error in the
 * same file.
 *
 * The suppressed count is appended to the surviving root diagnostic, so the
 * information is not simply lost: you see one error that says how much else
 * it caused, instead of thirty that say the same thing.
 *
 * Every failure path returns the message untouched. A filter that breaks the
 * protocol is far worse than one that occasionally lets noise through.
 */
object PublishDiagnosticsFilter {

    const val METHOD = "textDocument/publishDiagnostics"

    /**
     * @param readText how to load the source for a file URI; injectable for tests
     * @return the rewritten message, or [json] unchanged if it is not a
     *         publishDiagnostics notification or anything goes wrong
     */
    fun filter(json: String, readText: (Path) -> String? = ::readFile): String {
        return try {
            val root = JsonParser.parseString(json) as? JsonObject ?: return json
            if (root.get("method")?.asString != METHOD) return json

            val params = root.getAsJsonObject("params") ?: return json
            val array = params.getAsJsonArray("diagnostics") ?: return json
            if (array.size() < 2) return json // nothing can cascade from one

            val uri = params.get("uri")?.asString ?: return json
            val path = pathOf(uri) ?: return json
            val text = readText(path) ?: return json

            val parsed = array.mapNotNull { toDiagnostic(it as? JsonObject ?: return@mapNotNull null) }
            if (parsed.size != array.size()) return json // something unexpected; leave it alone

            val analysis = DiagnosticCascade.analyze(text, parsed)
            if (analysis.derivedCount == 0) return json

            val kept = JsonArray()
            var firstRoot: JsonObject? = null
            for (i in 0 until array.size()) {
                if (analysis.isDerived(i)) continue
                val element = array[i].asJsonObject.deepCopy()
                if (firstRoot == null) firstRoot = element
                kept.add(element)
            }

            // Say what was hidden, on the error that caused it.
            firstRoot?.let { note(it, analysis.derivedCount) }

            params.add("diagnostics", kept)
            root.toString()
        } catch (_: Exception) {
            json
        }
    }

    private fun note(diagnostic: JsonObject, suppressed: Int) {
        val message = diagnostic.get("message")?.asString ?: return
        val plural = if (suppressed == 1) "error" else "errors"
        diagnostic.addProperty(
            "message",
            "$message\n($suppressed further $plural in this file follow from this one)",
        )
    }

    private fun toDiagnostic(obj: JsonObject): DiagnosticCascade.Diagnostic? {
        val range = obj.getAsJsonObject("range") ?: return null
        val start = range.getAsJsonObject("start") ?: return null
        val end = range.getAsJsonObject("end") ?: return null
        val message = obj.get("message")?.asString ?: return null
        val severity = obj.get("severity")?.asInt ?: DiagnosticCascade.SEVERITY_ERROR
        return DiagnosticCascade.Diagnostic(
            range = DiagnosticCascade.Range(
                DiagnosticCascade.Position(start.get("line").asInt, start.get("character").asInt),
                DiagnosticCascade.Position(end.get("line").asInt, end.get("character").asInt),
            ),
            message = message,
            severity = severity,
        )
    }

    private fun pathOf(uri: String): Path? = runCatching { Path.of(URI(uri)) }.getOrNull()

    private fun readFile(path: Path): String? = runCatching { Files.readString(path) }.getOrNull()
}
