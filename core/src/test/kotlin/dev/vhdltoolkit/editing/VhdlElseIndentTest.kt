package dev.vhdltoolkit.editing

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Characterisation of the `else` family, in spaces only.
 *
 * Reported symptom: typing `else` inside an `if` does not line up with its
 * `if`. There are two candidate causes — the rule itself, and the fact that
 * columns are counted in characters while indents are written with tabs.
 * Using spaces throughout removes the second, because a space is one
 * character and one column. Whatever these assertions say is therefore about
 * the rule alone.
 */
class VhdlElseIndentTest {

    /**
     * ```
     * architecture rtl of top is   column 0
     * begin                        column 0
     *     process (clk)            column 4
     *     begin                    column 4
     *         if x = '1' then      column 8
     *             q <= '1';        column 12
     * ```
     */
    private val insideAnIf = buildString {
        append("architecture rtl of top is\n")
        append("begin\n")
        append("    process (clk)\n")
        append("    begin\n")
        append("        if x = '1' then\n")
        append("            q <= '1';\n")
    }

    private fun indentFor(text: String, word: String?): Int =
        VhdlBlockStructure.indentFor(text, text.length, indentSize = 4, lineStartsWith = word)

    // ------------------------------------------------------- the `if` family

    @Test
    fun elseLinesUpWithItsIf() {
        assertEquals(8, indentFor(insideAnIf, "else"))
    }

    @Test
    fun elsifLinesUpWithItsIf() {
        assertEquals(8, indentFor(insideAnIf, "elsif"))
    }

    @Test
    fun endLinesUpWithItsIf() {
        assertEquals(8, indentFor(insideAnIf, "end"))
    }

    @Test
    fun anOrdinaryLineSitsInsideTheIf() {
        assertEquals(12, indentFor(insideAnIf, null))
    }

    // ------------------------------------------- what follows an `else` line

    @Test
    fun theBranchBodyIsIndentedFromTheElse() {
        val text = insideAnIf + "        else\n"
        assertEquals(12, indentFor(text, null))
    }

    @Test
    fun endStillLinesUpWithTheIfAfterAnElse() {
        val text = insideAnIf + "        else\n            q <= '0';\n"
        assertEquals(8, indentFor(text, "end"))
    }

    // ---------------------------------------------------- enclosing closers

    @Test
    fun endProcessLinesUpWithItsProcess() {
        val text = insideAnIf + "        else\n            q <= '0';\n        end if;\n"
        assertEquals(4, indentFor(text, "end"))
    }

    @Test
    fun endArchitectureLinesUpWithItsArchitecture() {
        val text = insideAnIf +
            "        else\n            q <= '0';\n        end if;\n    end process;\n"
        assertEquals(0, indentFor(text, "end"))
    }

    // --------------------------------------------------------- nested `if`s

    @Test
    fun elseBindsToTheInnermostIf() {
        val text = insideAnIf + "            if y = '1' then\n                q <= '0';\n"
        assertEquals(12, indentFor(text, "else"))
    }

    @Test
    fun afterTheInnerIfIsClosedElseBindsToTheOuter() {
        val text = insideAnIf +
            "            if y = '1' then\n                q <= '0';\n            end if;\n"
        assertEquals(8, indentFor(text, "else"))
    }

    // ------------------------------------------------- an `else` in a a case

    /**
     * `when` alternatives are skipped when looking for what a closer belongs
     * to, so an `else` inside a case alternative must still find its `if`.
     */
    @Test
    fun elseInsideACaseAlternativeFindsItsIf() {
        val text = buildString {
            append("architecture rtl of top is\n")
            append("begin\n")
            append("    process (clk)\n")
            append("    begin\n")
            append("        case sel is\n")
            append("            when \"00\" =>\n")
            append("                if x = '1' then\n")
            append("                    q <= '1';\n")
        }
        assertEquals(16, indentFor(text, "else"))
    }
}
