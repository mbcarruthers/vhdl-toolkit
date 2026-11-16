package dev.vhdltoolkit.diagnostics

import com.intellij.openapi.diagnostic.logger
import org.eclipse.lsp4j.Diagnostic as LspDiagnostic
import org.eclipse.lsp4j.PublishDiagnosticsParams
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/**
 * Applies [DiagnosticCascade] to a `publishDiagnostics` notification in the
 * IDE's own process, where the language client already hands us the complete
 * diagnostic set for one file.
 *
 * An earlier attempt did this in a separate proxy process. That was the wrong
 * level: it needed its own JVM and classpath for no benefit, since the client
 * callback has exactly the same information. Doing it here also means a bug
 * costs a filtered notification rather than the whole language server.
 */
object DiagnosticFiltering {

    private val LOG = logger<DiagnosticFiltering>()

    /**
     * Returns the diagnostics worth showing, with the suppressed count noted
     * on the surviving root. On any doubt the input is returned unchanged.
     */
    fun filter(params: PublishDiagnosticsParams, readText: (Path) -> String? = ::readFile): List<LspDiagnostic> {
        val original = params.diagnostics ?: return emptyList()
        if (original.size < 2) return original

        return try {
            val path = pathOf(params.uri) ?: return original
            val text = readText(path) ?: return original

            val converted = original.map { it.toCascadeDiagnostic() }
            val analysis = DiagnosticCascade.analyze(text, converted)
            if (analysis.derivedCount == 0) return original

            val kept = ArrayList<LspDiagnostic>(original.size - analysis.derivedCount)
            for (i in original.indices) {
                if (analysis.isDerived(i)) continue
                kept.add(original[i])
            }
            if (kept.isEmpty()) {
                // Should not happen: a cascade always has a root. If it does,
                // show everything rather than leaving a broken file looking clean.
                LOG.warn("Cascade analysis suppressed every diagnostic for ${params.uri}; showing all")
                return original
            }

            val suppressedErrors = original.indices.count {
                analysis.isDerived(it) && original[it].severity == org.eclipse.lsp4j.DiagnosticSeverity.Error
            }
            note(kept.first(), analysis.derivedCount, suppressedErrors)
            kept
        } catch (e: Exception) {
            LOG.warn("Diagnostic filtering failed for ${params.uri}; showing all", e)
            original
        }
    }

    /**
     * Records on the root how much else followed from it. Says "problems"
     * rather than "errors" when warnings were collapsed too, since claiming
     * errors were hidden when they were warnings would misrepresent what was
     * taken away.
     */
    private fun note(diagnostic: LspDiagnostic, suppressed: Int, suppressedErrors: Int) {
        val noun = when {
            suppressedErrors == suppressed -> if (suppressed == 1) "error" else "errors"
            else -> if (suppressed == 1) "problem" else "problems"
        }
        diagnostic.message =
            "${diagnostic.message}\n($suppressed further $noun in this file follow from this one)"
    }

    private fun LspDiagnostic.toCascadeDiagnostic(): DiagnosticCascade.Diagnostic =
        DiagnosticCascade.Diagnostic(
            range = DiagnosticCascade.Range(
                DiagnosticCascade.Position(range.start.line, range.start.character),
                DiagnosticCascade.Position(range.end.line, range.end.character),
            ),
            message = message.orEmpty(),
            severity = severity?.value ?: DiagnosticCascade.SEVERITY_ERROR,
        )

    private fun pathOf(uri: String?): Path? =
        uri?.let { runCatching { Path.of(URI(it)) }.getOrNull() }

    private fun readFile(path: Path): String? = runCatching { Files.readString(path) }.getOrNull()
}
