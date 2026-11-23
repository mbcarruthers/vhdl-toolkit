package dev.vhdltoolkit.editing

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Indentation in a tab-indented file.
 *
 * Columns used to be counted in characters while indents were written with
 * tabs, so a line written at column 8 measured back as column 2. Every
 * construct after it was then aligned to a column nobody asked for, and the
 * error compounded: a real file came out with closers at columns 1, 2 and 6
 * that should all have lined up.
 *
 * The two halves have to agree, so these check both directions — what a tab
 * file measures as, and that what is written measures back the same.
 */
class VhdlTabIndentTest {

    /** The same design, written with tabs. Tab width 4. */
    private val tabbed = buildString {
        append("architecture rtl of top is\n")
        append("begin\n")
        append("\tprocess (clk)\n")
        append("\tbegin\n")
        append("\t\tif x = '1' then\n")
        append("\t\t\tq <= '1';\n")
    }

    private fun indentFor(text: String, word: String?, tabSize: Int = 4): Int =
        VhdlBlockStructure.indentFor(
            text = text,
            offset = text.length,
            indentSize = 4,
            lineStartsWith = word,
            tabSize = tabSize,
        )

    // ------------------------------------------------------------ measuring

    @Test
    fun aTabCountsAsItsWidthNotAsOneCharacter() {
        // `if` sits behind two tabs, which is column 8, not column 2.
        assertEquals(8, indentFor(tabbed, "else"))
    }

    @Test
    fun theSameFileInTabsAndSpacesIndentsIdentically() {
        val spaced = tabbed.replace("\t", "    ")
        for (word in listOf(null, "else", "elsif", "end", "begin")) {
            assertEquals(
                "`$word` must not depend on how the file is indented",
                indentFor(spaced, word),
                indentFor(tabbed, word),
            )
        }
    }

    @Test
    fun aTabAdvancesToTheNextStopNotByAFullWidth() {
        // A space then a tab lands on column 4, not column 5.
        val text = "architecture rtl of top is\nbegin\n \tprocess (clk)\n"
        assertEquals(4 + 4, indentFor(text, null))
    }

    @Test
    fun anEightWideTabIsHonoured() {
        assertEquals(16, indentFor(tabbed, "else", tabSize = 8))
    }

    @Test
    fun aMixOfTabsAndSpacesIsMeasuredAsDisplayed() {
        val text = "architecture rtl of top is\nbegin\n\t  process (clk)\n"
        // Tab to 4, then two spaces, so the process sits at column 6.
        assertEquals(6 + 4, indentFor(text, null))
    }

    // ------------------------------------------------------------- writing

    @Test
    fun whatIsWrittenMeasuresBackToTheSameColumn() {
        // The round trip that used to lose information: render a column as
        // tabs, then measure that text and expect the column back.
        for (columns in listOf(0, 4, 8, 12, 16)) {
            val indent = VhdlBlockStructure.renderIndent(columns, useTabs = true, tabSize = 4)
            val text = "architecture rtl of top is\nbegin\n" + indent + "process (clk)\n"
            assertEquals(columns + 4, indentFor(text, null))
        }
    }

    @Test
    fun aColumnThatIsNotAWholeNumberOfTabsIsPaddedWithSpaces() {
        assertEquals("\t  ", VhdlBlockStructure.renderIndent(6, useTabs = true, tabSize = 4))
        assertEquals("      ", VhdlBlockStructure.renderIndent(6, useTabs = false, tabSize = 4))
        assertEquals("", VhdlBlockStructure.renderIndent(0, useTabs = true, tabSize = 4))
    }

    @Test
    fun aNonsenseTabWidthDoesNotBreakAnything() {
        assertEquals("   ", VhdlBlockStructure.renderIndent(3, useTabs = true, tabSize = 0))
        // With no usable width a tab can only count as a single column.
        val text = "architecture rtl of top is\nbegin\n\tprocess (clk)\n"
        assertEquals(1 + 4, indentFor(text, null, tabSize = 0))
    }

    // ------------------------------------------------- the reported symptom

    /**
     * The closers in a tab-indented file all used to drift to different
     * columns. Each should line up with what it closes.
     */
    @Test
    fun everyCloserLinesUpWithWhatItCloses() {
        val afterElse = tabbed + "\t\telse\n\t\t\tq <= '0';\n"
        assertEquals("end if", 8, indentFor(afterElse, "end"))

        val afterEndIf = afterElse + "\t\tend if;\n"
        assertEquals("end process", 4, indentFor(afterEndIf, "end"))

        val afterEndProcess = afterEndIf + "\tend process;\n"
        assertEquals("end architecture", 0, indentFor(afterEndProcess, "end"))
    }
}
