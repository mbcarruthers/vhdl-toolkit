package dev.vhdltoolkit.diagnostics

import dev.vhdltoolkit.diagnostics.DiagnosticCascade.Diagnostic
import dev.vhdltoolkit.diagnostics.DiagnosticCascade.Position
import dev.vhdltoolkit.diagnostics.DiagnosticCascade.Range
import dev.vhdltoolkit.diagnostics.DiagnosticCascade.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticCascadeTest {

    private fun diag(line: Int, message: String, severity: Int = DiagnosticCascade.SEVERITY_ERROR) =
        Diagnostic(Range(Position(line, 0), Position(line, 10)), message, severity)

    /** The file from the screenshot: `all` mistyped as `al` on line 2. */
    private val brokenUseClause = """
        library IEEE;
        use IEEE.numeric_std.all;
        use IEEE.std_logic_1164.al;

        entity d_flop is
            Port(
                D      : in  std_logic;
                Clk    : in  std_logic;
                Output : out std_logic
            );
        end entity;

        architecture RTL of d_flop is
        begin
            process(Clk)
            begin
                if rising_edge(Clk) then
                    if (D = '1') then
                        Output <= '1';
                    end if;
                end if;
            end process;
        end architecture;
    """.trimIndent()

    @Test
    fun brokenContextClauseIsTheOnlyRootCause() {
        val diagnostics = listOf(
            diag(2, "No declaration of 'al' within package 'std_logic_1164'"), // the real error
            diag(6, "No declaration of 'std_logic'"),
            diag(7, "No declaration of 'std_logic'"),
            diag(8, "No declaration of 'std_logic'"),
            diag(17, "No declaration of 'D'"),
            diag(18, "No declaration of 'Output'"),
        )

        val analysis = DiagnosticCascade.analyze(brokenUseClause, diagnostics)

        assertEquals("the use clause is the root", Verdict.ROOT, analysis.verdicts[0])
        for (i in 1 until diagnostics.size) {
            assertEquals("index $i is fallout", Verdict.DERIVED, analysis.verdicts[i])
        }
        assertEquals(1, analysis.rootCount)
        assertEquals(5, analysis.derivedCount)
    }

    @Test
    fun libraryClauseAlsoCountsAsContext() {
        val src = "library IEEEE;\nuse IEEEE.std_logic_1164.all;\nsignal x : std_logic;"
        val diagnostics = listOf(
            diag(0, "No declaration of 'IEEEE'"),
            diag(2, "No declaration of 'std_logic'"),
        )
        val analysis = DiagnosticCascade.analyze(src, diagnostics)
        assertEquals(Verdict.ROOT, analysis.verdicts[0])
        assertEquals(Verdict.DERIVED, analysis.verdicts[1])
    }

    @Test
    fun withoutARootCauseEveryDistinctErrorSurvives() {
        val src = """
            architecture RTL of thing is
                signal a : std_logic;
            begin
                a <= undefined_one;
                a <= undefined_two;
            end architecture;
        """.trimIndent()
        val diagnostics = listOf(
            diag(3, "No declaration of 'undefined_one'"),
            diag(4, "No declaration of 'undefined_two'"),
        )
        val analysis = DiagnosticCascade.analyze(src, diagnostics)
        assertEquals("genuinely separate mistakes are both real", 2, analysis.rootCount)
    }

    @Test
    fun repeatedComplaintsAboutOneNameCollapse() {
        val src = """
            architecture RTL of thing is
            begin
                a <= missing;
                b <= missing;
                c <= missing;
            end architecture;
        """.trimIndent()
        val diagnostics = listOf(
            diag(2, "No declaration of 'missing'"),
            diag(3, "No declaration of 'missing'"),
            diag(4, "No declaration of 'missing'"),
        )
        val analysis = DiagnosticCascade.analyze(src, diagnostics)
        assertEquals("said once", 1, analysis.rootCount)
        assertEquals(2, analysis.derivedCount)
        assertEquals("the first occurrence is the one kept", Verdict.ROOT, analysis.verdicts[0])
    }

    @Test
    fun poisonedDeclarationSuppressesComplaintsAboutWhatItDeclared() {
        // No context-clause error here: the type itself is misspelled.
        val src = """
            entity e is
                Port(
                    D : in std_logik
                );
            end entity;
            architecture a of e is
            begin
                x <= D;
            end architecture;
        """.trimIndent()
        val diagnostics = listOf(
            diag(2, "No declaration of 'std_logik'"), // root: the typo
            diag(7, "No declaration of 'D'"), // fallout: D has no usable type
        )
        val analysis = DiagnosticCascade.analyze(src, diagnostics)
        assertEquals(Verdict.ROOT, analysis.verdicts[0])
        assertEquals(Verdict.DERIVED, analysis.verdicts[1])
    }

    @Test
    fun unrecognisedMessagesAreNeverSuppressed() {
        val diagnostics = listOf(
            diag(2, "No declaration of 'al' within package 'std_logic_1164'"),
            diag(9, "Missing closing parenthesis"),
            diag(12, "Duplicate declaration of 'foo'"),
        )
        val analysis = DiagnosticCascade.analyze(brokenUseClause, diagnostics)
        assertEquals("only messages we understand are classified", Verdict.ROOT, analysis.verdicts[1])
        assertEquals(Verdict.ROOT, analysis.verdicts[2])
    }

    @Test
    fun warningsDoNotPoisonDeclarations() {
        val src = "entity e is\n    Port( D : in std_logic );\nend entity;"
        val diagnostics = listOf(
            diag(1, "Port 'D' is never read", DiagnosticCascade.SEVERITY_WARNING),
            diag(2, "No declaration of 'D'"),
        )
        val analysis = DiagnosticCascade.analyze(src, diagnostics)
        assertEquals("a warning is not a failure", 2, analysis.rootCount)
    }

    @Test
    fun emptyInputIsHandled() {
        assertEquals(0, DiagnosticCascade.analyze("", emptyList()).verdicts.size)
        assertEquals(0, DiagnosticCascade.analyze(brokenUseClause, emptyList()).verdicts.size)
    }

    @Test
    fun symbolExtractionCoversTheMessageFormsWeAct0n() {
        assertEquals("foo", DiagnosticCascade.symbolOf("No declaration of 'foo'"))
        assertEquals(
            "al",
            DiagnosticCascade.symbolOf("No declaration of 'al' within package 'std_logic_1164'"),
        )
        assertEquals("bar", DiagnosticCascade.symbolOf("Could not resolve 'bar'"))
        assertEquals("baz", DiagnosticCascade.symbolOf("'baz' is not declared"))
        assertNull("an unrelated message yields nothing", DiagnosticCascade.symbolOf("Missing semicolon"))
    }

    @Test
    fun analysisIsStableAndTerminatesOnPathologicalInput() {
        // Mutually poisoning declarations must not loop forever.
        val src = """
            architecture a of e is
                signal x : bad_type;
                signal y : bad_type;
            begin
                x <= y;
                y <= x;
            end architecture;
        """.trimIndent()
        val diagnostics = listOf(
            diag(1, "No declaration of 'bad_type'"),
            diag(2, "No declaration of 'bad_type'"),
            diag(4, "No declaration of 'y'"),
            diag(5, "No declaration of 'x'"),
        )
        val analysis = DiagnosticCascade.analyze(src, diagnostics)
        assertEquals(diagnostics.size, analysis.verdicts.size)
        assertTrue("something survives to point at the real problem", analysis.rootCount >= 1)
        assertTrue("and the fallout is collapsed", analysis.derivedCount >= 2)
    }
}
