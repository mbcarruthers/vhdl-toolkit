package dev.vhdltoolkit.editing

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The file from the report, reproduced exactly.
 *
 * Typing `else` left it at column 12 instead of lining up with its `if` at
 * column 8. Every synthetic case says 8, so either something about this
 * particular file changes the answer, or the indent never reaches the
 * document and the fault is in the editor wiring rather than the rule.
 *
 * This decides which. If these pass, the rule is sound and the glue is at
 * fault.
 */
class VhdlReportedFileTest {

    /**
     * As typed, including the things that differ from the synthetic cases:
     * an entity with a port clause above, a blank line after the
     * architecture header, `process(Clk)` with no space, and parentheses
     * inside the assignment.
     */
    private val upToTheElse = buildString {
        append("entity tb_messaround is\n")
        append("    Port(\n")
        append("     Clk : in std_logic;\n")
        append("     A : in std_logic;\n")
        append("     Y : out std_logic\n")
        append("    );\n")
        append("end entity tb_messaround;\n")
        append("\n")
        append("architecture RTL of tb_messaround is\n")
        append("\n")
        append("begin\n")
        append("    process(Clk)\n")
        append("    begin\n")
        append("        if Clk = '1' then\n")
        append("            Y <= (not A) xor Clk;\n")
    }

    private fun indentFor(text: String, word: String?): Int =
        VhdlBlockStructure.indentFor(text, text.length, indentSize = 4, lineStartsWith = word)

    @Test
    fun elseLinesUpWithTheIfInTheReportedFile() {
        assertEquals(8, indentFor(upToTheElse, "else"))
    }

    @Test
    fun endLinesUpWithTheIfInTheReportedFile() {
        assertEquals(8, indentFor(upToTheElse, "end"))
    }

    @Test
    fun anOrdinaryLineSitsInsideTheIf() {
        assertEquals(12, indentFor(upToTheElse, null))
    }

    /** The closers below the `else`, which came out right in the report. */
    @Test
    fun theRemainingClosersLineUp() {
        val afterElse = upToTheElse + "            else\n            Y <= (A and Clk);\n"
        assertEquals("end if", 8, indentFor(afterElse, "end"))

        val afterEndIf = afterElse + "        end if;\n"
        assertEquals("end process", 4, indentFor(afterEndIf, "end"))

        val afterEndProcess = afterEndIf + "    end process;\n"
        assertEquals("end architecture", 0, indentFor(afterEndProcess, "end"))
    }

    // ------------------------------- the things this file has and others do not

    @Test
    fun aClosedEntityAboveDoesNotStayOnTheStack() {
        val withoutEntity = upToTheElse.substringAfter("end entity tb_messaround;\n")
        assertEquals(
            "an entity that is already closed must not change the answer",
            indentFor(withoutEntity, "else"),
            indentFor(upToTheElse, "else"),
        )
    }

    @Test
    fun aBlankLineAfterTheArchitectureHeaderChangesNothing() {
        val tightened = upToTheElse.replace("tb_messaround is\n\nbegin", "tb_messaround is\nbegin")
        assertEquals(8, indentFor(tightened, "else"))
    }

    @Test
    fun parenthesesInTheAssignmentAreBalancedAway() {
        val plain = upToTheElse.replace("Y <= (not A) xor Clk;", "Y <= A;")
        assertEquals(8, indentFor(plain, "else"))
    }

    @Test
    fun aProcessWithNoSpaceBeforeItsBracketIsStillAProcess() {
        val spaced = upToTheElse.replace("process(Clk)", "process (Clk)")
        assertEquals(8, indentFor(spaced, "else"))
    }
}
