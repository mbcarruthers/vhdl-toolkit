package dev.vhdltoolkit.editing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VhdlSmartEnterTest {

    /** Applies the completion at `|` and returns the whole resulting text. */
    private fun apply(marked: String): String? {
        val caret = marked.indexOf('|')
        require(caret >= 0)
        val text = marked.removeRange(caret, caret + 1)
        val result = VhdlSmartEnter.completionFor(text, caret, indentSize = 4) ?: return null

        val lineStart = text.lastIndexOf('\n', (caret - 1).coerceAtLeast(0))
            .let { if (it < 0 || caret == 0) 0 else it + 1 }
        var lineEnd = lineStart
        while (lineEnd < text.length && text[lineEnd] != '\n') lineEnd++

        return text.substring(0, lineStart) + result.line + result.tail + text.substring(lineEnd)
    }

    /** Just the completed line, for the cases that only repair punctuation. */
    private fun line(marked: String): String? {
        val caret = marked.indexOf('|')
        require(caret >= 0)
        val text = marked.removeRange(caret, caret + 1)
        return VhdlSmartEnter.completionFor(text, caret, indentSize = 4)?.line
    }

    // ------------------------------------------------- missing punctuation

    @Test
    fun aStatementGetsItsSemicolon() {
        assertEquals("    q <= '1';", line("architecture a of b is\nbegin\n    q <= '1'|"))
    }

    @Test
    fun aStatementThatAlreadyEndsWellIsLeftAlone() {
        // Nothing to repair and no block opened, so there is nothing to do
        // that plain Enter would not already do.
        assertNull(apply("architecture a of b is\nbegin\n    q <= '1';|"))
    }

    @Test
    fun anOpenBracketIsClosed() {
        assertEquals(
            "    q <= std_logic_vector(count);",
            line("architecture a of b is\nbegin\n    q <= std_logic_vector(count|"),
        )
    }

    @Test
    fun nestedOpenBracketsAreAllClosed() {
        assertEquals(
            "    q <= f(g(x));",
            line("architecture a of b is\nbegin\n    q <= f(g(x|"),
        )
    }

    @Test
    fun aPortClauseIsClosedWithItsSemicolon() {
        assertEquals(
            "    port (clk : in std_logic);",
            line("entity foo is\n    port (clk : in std_logic|"),
        )
    }

    // ------------------------------------------------------ missing keyword

    @Test
    fun anIfGetsItsThen() {
        val result = apply("architecture a of b is\nbegin\n    process (c)\n    begin\n        if x = '1'|")
        assertTrue(result!!.contains("if x = '1' then"))
    }

    @Test
    fun aCaseGetsItsIs() {
        val result = apply("architecture a of b is\nbegin\n    process (c)\n    begin\n        case sel|")
        assertTrue(result!!.contains("case sel is"))
    }

    @Test
    fun aWhileGetsItsLoop() {
        val result = apply("architecture a of b is\nbegin\n    process (c)\n    begin\n        while i < 8|")
        assertTrue(result!!.contains("while i < 8 loop"))
    }

    @Test
    fun aKeywordAlreadyPresentIsNotDoubled() {
        val result = apply("architecture a of b is\nbegin\n    process (c)\n    begin\n        if x then|")
        assertTrue("no second then", result == null || !result.contains("then then"))
    }

    @Test
    fun aHalfTypedConditionIsLeftAlone() {
        // The bracket is still open, so the condition is unfinished and `then`
        // would land in the middle of it.
        assertTrue(line("...\n        if (x and |")?.contains("then") != true)
    }

    // ------------------------------------------------------ block completion

    @Test
    fun finishingABlockHeaderAlsoClosesTheBlock() {
        val result = apply("architecture a of b is\nbegin\n    process (clk)|")
        assertTrue("begin is placed", result!!.contains("begin"))
        assertTrue("end is placed", result.contains("end process;"))
    }

    @Test
    fun anIfCompletedFromAFragmentIsClosedToo() {
        val result = apply("architecture a of b is\nbegin\n    process (c)\n    begin\n        if x = '1'|")
        assertTrue(result!!.contains("if x = '1' then"))
        assertTrue(result.contains("end if;"))
    }

    // ----------------------------------------------------------- left alone

    @Test
    fun aBlankLineIsLeftAlone() {
        assertNull(apply("architecture a of b is\nbegin\n    |"))
    }

    @Test
    fun aCommentIsLeftAlone() {
        assertNull(apply("architecture a of b is\nbegin\n    -- a note|"))
    }

    @Test
    fun aHeaderEndingInAKeywordTakesNoSemicolon() {
        val result = apply("architecture rtl of top is|")
        assertTrue("no stray semicolon", !result!!.contains("is;"))
    }

    @Test
    fun anOutOfRangeCaretIsRefused() {
        assertNull(VhdlSmartEnter.completionFor("entity foo is", -1, 4))
        assertNull(VhdlSmartEnter.completionFor("entity foo is", 999, 4))
    }
}
