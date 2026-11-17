package dev.vhdltoolkit.editing

import dev.vhdltoolkit.editing.VhdlBlockStructure.EndStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VhdlBlockCompletionTest {

    /**
     * Applies the completion at `|`, returning the resulting text — what the
     * editor would actually show.
     */
    private fun complete(
        marked: String,
        style: EndStyle = EndStyle.KEYWORD_AND_NAME,
        insertBegin: Boolean = true,
    ): String? {
        val caret = marked.indexOf('|')
        require(caret >= 0)
        val text = marked.removeRange(caret, caret + 1)
        val completion = VhdlBlockCompletion.completionAt(text, caret, 4, style, insertBegin)
            ?: return null
        return text.substring(0, caret) + completion.textToInsert + text.substring(caret)
    }

    @Test
    fun architectureGetsBeginAndEnd() {
        assertEquals(
            "architecture rtl of top is\n    \nbegin\nend architecture rtl;",
            complete("architecture rtl of top is\n    |"),
        )
    }

    @Test
    fun processGetsBeginAndEndAtItsOwnColumn() {
        assertEquals(
            "architecture a of b is\nbegin\n    process (clk)\n        \n    begin\n    end process;",
            complete("architecture a of b is\nbegin\n    process (clk)\n        |"),
        )
    }

    @Test
    fun ifGetsOnlyAnEnd() {
        assertEquals(
            "architecture a of b is\nbegin\n    if x then\n        \n    end if;",
            complete("architecture a of b is\nbegin\n    if x then\n        |"),
        )
    }

    @Test
    fun entityIsClosedWithoutABegin() {
        assertEquals(
            "entity foo is\n    \nend entity foo;",
            complete("entity foo is\n    |"),
        )
    }

    @Test
    fun beginCanBeTurnedOff() {
        assertEquals(
            "architecture rtl of top is\n    \nend architecture rtl;",
            complete("architecture rtl of top is\n    |", insertBegin = false),
        )
    }

    @Test
    fun closingStyleIsHonoured() {
        assertEquals(
            "architecture rtl of top is\n    \nbegin\nend;",
            complete("architecture rtl of top is\n    |", style = EndStyle.BARE),
        )
    }

    @Test
    fun nothingHappensWhenTheBlockIsAlreadyClosed() {
        assertNull(
            "editing existing code must not add a second end",
            complete("architecture rtl of top is\n    |\nbegin\nend architecture rtl;"),
        )
    }

    @Test
    fun nothingHappensMidLine() {
        assertNull(
            "pressing Enter before existing code must not split it",
            complete("architecture rtl of top is\n    |signal s : bit;"),
        )
    }

    @Test
    fun nothingHappensOnAnOrdinaryLine() {
        assertNull(complete("architecture rtl of top is\nbegin\n    x <= '1';\n    |"))
    }

    @Test
    fun nothingHappensWhileTheHeaderIsStillOpen() {
        // The port list is unfinished; the entity header is not complete yet.
        assertNull(complete("entity foo is\n    port (\n        |"))
    }

    @Test
    fun aCommentDoesNotOpenABlock() {
        assertNull(complete("architecture a of b is\nbegin\n    -- if x then\n    |"))
    }

    @Test
    fun labelledProcessClosesWithItsLabel() {
        val result = complete("architecture a of b is\nbegin\n    regs : process (clk)\n        |")
        assertEquals(true, result?.contains("end process regs;"))
    }
}
