package dev.vhdltoolkit.editing

import dev.vhdltoolkit.editing.VhdlBlockStructure.EndStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Completing a construct nested inside another of the same kind.
 *
 * Deciding whether a block is already closed by looking for an `end` of the
 * matching keyword works until two of the same kind are nested: the outer
 * one's `end if;` looks exactly like the inner one's, so the inner was
 * treated as closed and never completed. Telling them apart means counting
 * closers against the number of constructs of that kind still open.
 */
class VhdlNestedBlockTest {

    private fun complete(marked: String): String? {
        val caret = marked.indexOf('|')
        require(caret >= 0)
        val text = marked.removeRange(caret, caret + 1)
        val completion = VhdlBlockCompletion.completionAt(
            text = text,
            caret = caret,
            indentSize = 4,
            endStyle = EndStyle.KEYWORD_AND_NAME,
            insertBegin = true,
        ) ?: return null
        return text.substring(0, caret - completion.replaceBefore) +
            completion.textToInsert +
            text.substring(caret)
    }

    private fun endIfCount(text: String) = Regex("""\bend\s+if\s*;""").findAll(text).count()

    private val body = "architecture a of b is\nbegin\n    process (clk)\n    begin\n"

    @Test
    fun anIfInsideAnIfStillGetsItsOwnEnd() {
        // The outer `end if;` is already there, as block completion would
        // have left it when the outer `if` was written.
        val result = complete(
            body + "        if x then\n            if y then\n                |\n        end if;\n",
        )
        assertTrue("the inner if must be closed too", result != null)
        assertEquals("one end per if", 2, endIfCount(result!!))
    }

    @Test
    fun aThirdLevelIsNoDifferent() {
        val result = complete(
            body +
                "        if x then\n            if y then\n                if z then\n" +
                "                    |\n            end if;\n        end if;\n",
        )
        assertEquals(3, endIfCount(result!!))
    }

    @Test
    fun anAlreadyClosedInnerIfIsLeftAlone() {
        assertNull(
            "both ifs already have their end",
            complete(
                body +
                    "        if x then\n            if y then\n                |\n" +
                    "            end if;\n        end if;\n",
            ),
        )
    }

    @Test
    fun nestedLoopsBehaveTheSameWay() {
        val result = complete(
            body +
                "        for i in 0 to 3 loop\n            for j in 0 to 3 loop\n" +
                "                |\n        end loop;\n",
        )
        assertEquals(2, Regex("""\bend\s+loop\s*;""").findAll(result!!).count())
    }

    @Test
    fun aNestedProcessIsNotAThingButANestedBlockIs() {
        val result = complete(
            "architecture a of b is\nbegin\n    outer : block\n    begin\n" +
                "        inner : block\n        begin\n            |\n    end block outer;\n",
        )
        assertEquals(2, Regex("""\bend\s+block""").findAll(result!!).count())
    }

    // ------------------------------------- what must keep working as before

    @Test
    fun anEnclosingEndOfAnotherKindStillDoesNotCount() {
        val result = complete(
            body + "        if x then\n            |\n    end process;\nend architecture a;\n",
        )
        assertEquals(1, endIfCount(result!!))
    }

    @Test
    fun aSingleIfWithItsEndIsStillLeftAlone() {
        assertNull(
            complete(body + "        if x then\n            |\n        end if;\n"),
        )
    }

    @Test
    fun theOuterIfIsStillCompletedWhenNothingIsClosed() {
        val result = complete(body + "        if x then\n            |\n")
        assertEquals(1, endIfCount(result!!))
    }
}
