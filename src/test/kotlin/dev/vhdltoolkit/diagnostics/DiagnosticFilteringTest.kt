package dev.vhdltoolkit.diagnostics

import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class DiagnosticFilteringTest {

    private val source = """
        library IEEE;
        use IEEE.STD_LOGIC_1164.AL;
        use IEEE.NUMERIC_STD.ALL;

        entity counter4_ud is
            Port(
                clk   : in  STD_LOGIC;
                reset : in  STD_LOGIC
            );
        end counter4_ud;
    """.trimIndent()

    private fun diag(line: Int, message: String, severity: DiagnosticSeverity = DiagnosticSeverity.Error) =
        Diagnostic(Range(Position(line, 0), Position(line, 8)), message).apply { this.severity = severity }

    private fun params(vararg diagnostics: Diagnostic) =
        PublishDiagnosticsParams("file:///tmp/counter4_ud.vhd", diagnostics.toMutableList())

    private fun filter(p: PublishDiagnosticsParams) = DiagnosticFiltering.filter(p) { source }

    @Test
    fun collapsesToTheBrokenUseClause() {
        val kept = filter(
            params(
                diag(1, "No declaration of 'AL' within package 'STD_LOGIC_1164'"),
                diag(6, "No declaration of 'STD_LOGIC'"),
                diag(7, "No declaration of 'STD_LOGIC'"),
            ),
        )
        assertEquals(1, kept.size)
        assertTrue(kept[0].message.startsWith("No declaration of 'AL'"))
        assertTrue("says what it caused", kept[0].message.contains("2 further errors"))
    }

    /**
     * The failure that shipped in 0.5.0 looked like this from the outside: a
     * file with a real error showing nothing at all. Whatever goes wrong, a
     * broken file must never look clean.
     */
    @Test
    fun neverReturnsEmptyWhenThereWereDiagnostics() {
        val inputs = listOf(
            params(diag(1, "No declaration of 'AL'"), diag(6, "No declaration of 'STD_LOGIC'")),
            params(diag(1, "something unparseable"), diag(2, "also unparseable")),
            params(diag(1, "No declaration of 'x'"), diag(1, "No declaration of 'x'")),
        )
        for (p in inputs) {
            assertTrue("never empties a file that had errors", filter(p).isNotEmpty())
        }
    }

    @Test
    fun unreadableSourceShowsEverything() {
        val input = params(
            diag(1, "No declaration of 'AL' within package 'STD_LOGIC_1164'"),
            diag(6, "No declaration of 'STD_LOGIC'"),
        )
        val kept = DiagnosticFiltering.filter(input) { null }
        assertEquals("cannot judge without the source, so judge nothing", 2, kept.size)
    }

    @Test
    fun aSingleDiagnosticIsLeftAlone() {
        val one = diag(1, "No declaration of 'AL'")
        val kept = filter(params(one))
        assertEquals(1, kept.size)
        assertEquals("message untouched when nothing was suppressed", "No declaration of 'AL'", kept[0].message)
    }

    @Test
    fun noCascadeMeansNoChange() {
        val input = params(
            diag(6, "No declaration of 'alpha'"),
            diag(7, "No declaration of 'beta'"),
        )
        val kept = filter(input)
        assertEquals(2, kept.size)
        assertTrue("no note added when nothing was hidden", kept.none { it.message.contains("follow from") })
    }

    @Test
    fun emptyDiagnosticsStayEmpty() {
        assertEquals(0, filter(PublishDiagnosticsParams("file:///tmp/x.vhd", mutableListOf())).size)
    }

    @Test
    fun badUriDoesNotThrow() {
        val p = PublishDiagnosticsParams(
            "not a uri at all",
            mutableListOf(diag(1, "No declaration of 'AL'"), diag(6, "No declaration of 'STD_LOGIC'")),
        )
        assertEquals("falls back to showing everything", 2, DiagnosticFiltering.filter(p) { source }.size)
    }

    @Test
    fun readerReceivesTheFileSystemPath() {
        var seen: Path? = null
        DiagnosticFiltering.filter(
            params(diag(1, "No declaration of 'AL'"), diag(6, "No declaration of 'STD_LOGIC'")),
        ) { path -> seen = path; source }
        assertEquals("/tmp/counter4_ud.vhd", seen?.toString())
    }
}
