package dev.vhdltoolkit.completion

import dev.vhdltoolkit.completion.VhdlKeywordCompletion.Region
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VhdlKeywordCompletionTest {

    private fun at(marked: String): Pair<String, Int> {
        val offset = marked.indexOf('|')
        require(offset >= 0)
        return marked.removeRange(offset, offset + 1) to offset
    }

    private fun region(marked: String): Region {
        val (text, offset) = at(marked)
        return VhdlKeywordCompletion.regionAt(text, offset)
    }

    private fun words(marked: String): Set<String> {
        val (text, offset) = at(marked)
        return VhdlKeywordCompletion.suggestionsFor(text, offset).map { it.text.lowercase() }.toSet()
    }

    // ------------------------------------------------------------ regions

    @Test
    fun topOfFileIsDesignFileLevel() {
        assertEquals(Region.DESIGN_FILE, region("|"))
        assertEquals(Region.DESIGN_FILE, region("library ieee;\n|"))
    }

    @Test
    fun entityHeaderIsItsOwnRegion() {
        assertEquals(Region.ENTITY_HEADER, region("entity foo is\n    |"))
    }

    @Test
    fun architectureSwitchesAtBegin() {
        assertEquals(Region.DECLARATIVE, region("architecture rtl of foo is\n    |"))
        assertEquals(Region.CONCURRENT, region("architecture rtl of foo is\nbegin\n    |"))
    }

    @Test
    fun processSwitchesAtBegin() {
        val decl = "architecture a of b is\nbegin\n    process (clk)\n        |"
        val body = "architecture a of b is\nbegin\n    process (clk)\n    begin\n        |"
        assertEquals(Region.DECLARATIVE, region(decl))
        assertEquals(Region.SEQUENTIAL, region(body))
    }

    @Test
    fun insideAnIfWithinAProcessIsStillSequential() {
        assertEquals(
            Region.SEQUENTIAL,
            region("architecture a of b is\nbegin\n    process (clk)\n    begin\n        if x then\n            |"),
        )
    }

    // -------------------------------------------------------- suggestions

    @Test
    fun designUnitKeywordsAtTopLevel() {
        val got = words("|")
        assertTrue(got.containsAll(setOf("library", "use", "entity", "architecture")))
        assertFalse("a process cannot appear here", "process" in got)
    }

    @Test
    fun declarativeRegionOffersDeclarationsNotStatements() {
        val got = words("architecture rtl of foo is\n    |")
        assertTrue(got.containsAll(setOf("signal", "constant", "type", "component")))
        assertFalse("statements belong after begin", "wait" in got)
    }

    @Test
    fun concurrentRegionOffersProcessNotIf() {
        val got = words("architecture rtl of foo is\nbegin\n    |")
        assertTrue("process" in got)
        assertFalse("a bare if is sequential; if/generate is separate", "wait" in got)
    }

    @Test
    fun sequentialRegionOffersStatements() {
        val got = words("architecture a of b is\nbegin\n    process (clk)\n    begin\n        |")
        assertTrue(got.containsAll(setOf("if", "case", "wait", "loop", "report")))
    }

    @Test
    fun beginIsOfferedOnceAndOnlyWhereABodyFollows() {
        assertTrue("begin" in words("architecture rtl of foo is\n    |"))
        assertFalse(
            "already has one",
            "begin" in words("architecture rtl of foo is\nbegin\n    |"),
        )
        assertFalse("an entity has no body", "begin" in words("entity foo is\n    |"))
    }

    @Test
    fun theSpecificCloserIsOffered() {
        assertTrue("end architecture rtl;" in words("architecture rtl of foo is\n    |"))
        assertTrue(
            "end process regs;" in
                words("architecture a of b is\nbegin\n    regs : process (clk)\n    begin\n        |"),
        )
        assertTrue(
            "end if;" in
                words("architecture a of b is\nbegin\n    process (c)\n    begin\n        if x then\n            |"),
        )
    }

    @Test
    fun standardTypesAreOfferedInsideADesignUnitButNotAboveOne() {
        assertTrue("std_logic" in words("entity foo is\n    port (\n        a : |"))
        assertFalse("no types at file level", "std_logic" in words("|"))
    }

    @Test
    fun skeletonsCarryATemplate() {
        val (text, offset) = at("architecture rtl of foo is\nbegin\n    |")
        val process = VhdlKeywordCompletion.suggestionsFor(text, offset)
            .first { it.text == "process" && it.template != null }
        assertTrue(process.template!!.contains("end process;"))
        assertTrue("the caret lands in the body", process.template.contains("\$CURSOR$"))
    }

    @Test
    fun aKeywordAndItsSkeletonBothSurvive() {
        val (text, offset) = at("architecture rtl of foo is\nbegin\n    |")
        val named = VhdlKeywordCompletion.suggestionsFor(text, offset).filter { it.text == "process" }
        assertEquals("the bare keyword and the block", 2, named.size)
        assertEquals(1, named.count { it.template == null })
        assertEquals(1, named.count { it.template != null })
    }

    @Test
    fun noExactDuplicatesAreOffered() {
        val all = VhdlKeywordCompletion
            .suggestionsFor("architecture a of b is\nbegin\n    process (c)\n    begin\n        ", 70)
            .map { it.text.lowercase() to (it.template != null) }
        assertEquals(all.size, all.distinct().size)
    }

    /**
     * The gap that made the popup look wrong: typing a reserved word that
     * joins a header offered nothing of ours, leaving only whatever the
     * language server matched loosely.
     */
    @Test
    fun headerWordsAreOffered() {
        assertTrue("is" in words("architecture rtl of foo |"))
        assertTrue("of" in words("architecture rtl |"))
        assertTrue("all" in words("use ieee.std_logic_1164.|"))
        assertTrue("is" in words("architecture rtl of foo is\n    type t |"))
    }

    @Test
    fun operatorsThatAreReservedWordsAreOffered() {
        val seq = words("architecture a of b is\nbegin\n    process (c)\n    begin\n        |")
        assertTrue(seq.containsAll(setOf("and", "or", "not", "xor", "nand", "mod", "sll")))
    }

    @Test
    fun rangeVocabularyIsOfferedWhereASubtypeCanAppear() {
        assertTrue(words("entity foo is\n    port (\n        a : in std_logic_vector(3 |").contains("downto"))
        assertTrue("range" in words("architecture rtl of foo is\n    subtype s |"))
        assertTrue("others" in words("architecture a of b is\nbegin\n    process (c)\n    begin\n        |"))
    }

    @Test
    fun recordAndArrayAreOfferedInDeclarations() {
        val got = words("architecture rtl of foo is\n    type t is |")
        assertTrue(got.containsAll(setOf("array", "record")))
    }

    @Test
    fun aPortMapIsOfferedInAConcurrentBody() {
        assertTrue("map" in words("architecture rtl of foo is\nbegin\n    |"))
    }
}
