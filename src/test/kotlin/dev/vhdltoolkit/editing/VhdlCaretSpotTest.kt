package dev.vhdltoolkit.editing

import dev.vhdltoolkit.editing.VhdlBlockCompletion.CaretSpot
import dev.vhdltoolkit.editing.VhdlBlockStructure.Construct
import dev.vhdltoolkit.editing.VhdlBlockStructure.EndStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the caret comes to rest once a block is completed.
 *
 * Both answers are legitimate and the right one differs by construct, so this
 * is a setting with defaults rather than a rule. These check that each spot
 * produces a sensible file and that the caret actually lands in the region it
 * names.
 */
class VhdlCaretSpotTest {

    private class Applied(val text: String, val caret: Int) {
        /** The line the caret ended up on, trailing spaces included. */
        val caretLine: String
            get() {
                val start = text.lastIndexOf('\n', (caret - 1).coerceAtLeast(0)).let {
                    if (it < 0) 0 else it + 1
                }
                var end = caret
                while (end < text.length && text[end] != '\n') end++
                return text.substring(start, end)
            }

        /** Lines above the caret, nearest first. */
        val linesAbove: List<String>
            get() = text.substring(0, caret).lines().dropLast(1).asReversed()

        val linesBelow: List<String>
            get() = text.substring(caret).lines().drop(1)
    }

    private fun apply(marked: String, spot: CaretSpot): Applied? {
        val caret = marked.indexOf('|')
        require(caret >= 0)
        val text = marked.removeRange(caret, caret + 1)
        val completion = VhdlBlockCompletion.completionAt(
            text = text,
            caret = caret,
            indentSize = 4,
            endStyle = EndStyle.KEYWORD_AND_NAME,
            insertBegin = true,
            caretSpot = { spot },
        ) ?: return null
        val from = caret - completion.replaceBefore
        return Applied(
            text = text.substring(0, from) + completion.textToInsert + text.substring(caret),
            caret = from + completion.caretOffsetInInsertion,
        )
    }

    private val process = "architecture a of b is\nbegin\n    process (clk)\n        |"
    private val architecture = "architecture rtl of top is\n    |"

    // --------------------------------------------------------------- body

    @Test
    fun theBodySpotPutsTheCaretUnderBegin() {
        val result = apply(process, CaretSpot.BODY)!!
        assertEquals("    begin", result.linesAbove.first())
        assertEquals("    end process;", result.linesBelow.first())
    }

    @Test
    fun theBodySpotIndentsTheCaretOneLevelIn() {
        val result = apply(process, CaretSpot.BODY)!!
        assertEquals("        ", result.caretLine)
    }

    /**
     * The caret's line already held an indent before `begin` was written
     * above it. Leaving that behind would strand a blank indented line.
     */
    @Test
    fun theBodySpotLeavesNoStrayLineAboveBegin() {
        val result = apply(process, CaretSpot.BODY)!!
        assertEquals(
            "architecture a of b is\nbegin\n    process (clk)\n    begin\n        \n    end process;",
            result.text,
        )
    }

    // -------------------------------------------------------- declarative

    @Test
    fun theDeclarativeSpotPutsTheCaretAboveBegin() {
        val result = apply(architecture, CaretSpot.DECLARATIVE)!!
        assertEquals("begin", result.linesBelow.first())
        assertEquals(
            "architecture rtl of top is\n    \nbegin\nend architecture rtl;",
            result.text,
        )
    }

    @Test
    fun theDeclarativeSpotReplacesNothing() {
        val caret = architecture.indexOf('|')
        val text = architecture.removeRange(caret, caret + 1)
        val completion = VhdlBlockCompletion.completionAt(
            text, caret, 4, EndStyle.KEYWORD_AND_NAME, insertBegin = true,
            caretSpot = { CaretSpot.DECLARATIVE },
        )!!
        assertEquals(0, completion.replaceBefore)
    }

    // ------------------------------------------------------------ defaults

    @Test
    fun theDefaultsFollowWhatEachRegionIsUsedFor() {
        assertEquals(CaretSpot.BODY, VhdlBlockCompletion.defaultCaretSpot(Construct.PROCESS))
        assertEquals(CaretSpot.BODY, VhdlBlockCompletion.defaultCaretSpot(Construct.SUBPROGRAM))
        assertEquals(CaretSpot.BODY, VhdlBlockCompletion.defaultCaretSpot(Construct.GENERATE))
        assertEquals(
            CaretSpot.DECLARATIVE,
            VhdlBlockCompletion.defaultCaretSpot(Construct.ARCHITECTURE),
        )
        assertEquals(CaretSpot.DECLARATIVE, VhdlBlockCompletion.defaultCaretSpot(Construct.BLOCK))
    }

    /** A construct with no `begin` cannot have a body for the caret to be in. */
    @Test
    fun aConstructWithoutABeginIgnoresTheSetting() {
        val entity = "entity foo is\n    |"
        val body = apply(entity, CaretSpot.BODY)!!
        val declarative = apply(entity, CaretSpot.DECLARATIVE)!!
        assertEquals(declarative.text, body.text)
        assertTrue("no begin for an entity", !body.text.contains("begin"))
    }
}
