package dev.vhdltoolkit.editing

import dev.vhdltoolkit.editing.VhdlBlockStructure.Construct
import dev.vhdltoolkit.editing.VhdlBlockStructure.EndStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VhdlBlockStructureTest {

    /** Marks the caret with `|`, returning the text and its offset. */
    private fun at(marked: String): Pair<String, Int> {
        val offset = marked.indexOf('|')
        require(offset >= 0) { "no caret marker" }
        return marked.removeRange(offset, offset + 1) to offset
    }

    private fun indent(marked: String, startsWith: String? = null, size: Int = 4): Int {
        val (text, offset) = at(marked)
        return VhdlBlockStructure.indentFor(text, offset, size, startsWith)
    }

    private fun frames(marked: String): List<Construct> {
        val (text, offset) = at(marked)
        return VhdlBlockStructure.analyze(text, offset).frames.map { it.construct }
    }

    // ------------------------------------------------------------- indent

    @Test
    fun entityBodyIndentsOneLevel() {
        assertEquals(4, indent("entity foo is\n|"))
    }

    @Test
    fun portListIndentsInsideTheEntity() {
        assertEquals(8, indent("entity foo is\n    port (\n|"))
    }

    @Test
    fun closingParenAlignsWithItsOpeningLine() {
        assertEquals(4, indent("entity foo is\n    port (\n        a : in bit\n|", ")"))
    }

    @Test
    fun endAlignsWithItsConstruct() {
        assertEquals(0, indent("entity foo is\n    port (\n        a : in bit\n    );\n|", "end"))
    }

    @Test
    fun beginSitsLevelWithItsHeaderNotInsideIt() {
        assertEquals(0, indent("architecture rtl of foo is\n    signal s : bit;\n|", "begin"))
    }

    @Test
    fun architectureBodyIndentsAfterBegin() {
        assertEquals(4, indent("architecture rtl of foo is\nbegin\n|"))
    }

    @Test
    fun nestedProcessAndIf() {
        val src = """
            architecture rtl of foo is
            begin
                regs : process (clk)
                begin
                    if rising_edge(clk) then
            |
        """.trimIndent()
        assertEquals(12, indent(src))
    }

    @Test
    fun elsifAndElseAlignWithTheirIf() {
        val src = "architecture a of b is\nbegin\n    if x then\n        y <= '1';\n|"
        assertEquals(4, indent(src, "elsif"))
        assertEquals(4, indent(src, "else"))
    }

    @Test
    fun caseAlternativesAlignWithEachOther() {
        val src = """
            architecture a of b is
            begin
                process (x)
                begin
                    case x is
                        when "00" =>
                            y <= '1';
            |
        """.trimIndent()
        assertEquals("statements sit inside the alternative", 16, indent(src))
        assertEquals("the next alternative lines up with the previous", 12, indent(src, "when"))
        assertEquals("end case closes the case", 8, indent(src, "end"))
    }

    @Test
    fun indentSizeIsRespected() {
        assertEquals(2, indent("entity foo is\n|", size = 2))
        assertEquals(8, indent("entity foo is\n|", size = 8))
    }

    @Test
    fun aLabelledStatementClosesAtTheLabelColumn() {
        val src = "architecture a of b is\nbegin\n    u0 : process (clk)\n    begin\n|"
        assertEquals(8, indent(src))
        assertEquals(4, indent(src, "end"))
    }

    // ------------------------------------------------------------- frames

    @Test
    fun instantiationIsNotADeclaration() {
        // `entity work.foo` references; it does not open a design unit.
        assertEquals(
            listOf(Construct.ARCHITECTURE),
            frames("architecture a of b is\nbegin\n    u0 : entity work.foo\n        port map (x => y);\n|"),
        )
    }

    @Test
    fun enumerationTypeDoesNotOpenABlockButRecordDoes() {
        assertTrue(frames("package p is\n    type t is (a, b);\n|").none { it == Construct.RECORD })
        assertTrue(frames("package p is\n    type t is record\n|").contains(Construct.RECORD))
    }

    @Test
    fun ifGenerateIsAGenerateNotAnIf() {
        val got = frames("architecture a of b is\nbegin\n    g : if cond generate\n|")
        assertTrue(got.contains(Construct.GENERATE))
        assertTrue(got.none { it == Construct.IF })
    }

    @Test
    fun subprogramDeclarationHasNoBodyButDefinitionDoes() {
        assertTrue(frames("package p is\n    function f return bit;\n|").none { it == Construct.SUBPROGRAM })
        assertTrue(
            frames("package body p is\n    function f return bit is\n|").contains(Construct.SUBPROGRAM),
        )
    }

    @Test
    fun eachDesignUnitResetsTheStack() {
        val src = """
            entity one is
            end entity;

            entity two is
            |
        """.trimIndent()
        assertEquals(listOf(Construct.ENTITY), frames(src))
        assertEquals(4, indent(src))
    }

    @Test
    fun commentsAndStringsDoNotOpenBlocks() {
        val src = "entity foo is\n    -- if x then\n    constant c : string := \"process (clk)\";\n|"
        assertEquals(listOf(Construct.ENTITY), frames(src))
    }

    // ------------------------------------------------------------ closers

    private fun closerOf(marked: String, style: EndStyle): String? {
        val (text, offset) = at(marked)
        val frame = VhdlBlockStructure.analyze(text, offset).innermost ?: return null
        return VhdlBlockStructure.closerFor(frame, style)
    }

    @Test
    fun closerStylesDiffer() {
        val src = "architecture rtl of top is\n|"
        assertEquals("end;", closerOf(src, EndStyle.BARE))
        assertEquals("end architecture;", closerOf(src, EndStyle.KEYWORD))
        assertEquals("end architecture rtl;", closerOf(src, EndStyle.KEYWORD_AND_NAME))
    }

    @Test
    fun ifAndLoopAlwaysNameTheirKeyword() {
        // `end;` is not valid for these, whatever the preference.
        assertEquals("end if;", closerOf("architecture a of b is\nbegin\n    if x then\n|", EndStyle.BARE))
        assertEquals("end loop;", closerOf("architecture a of b is\nbegin\n    while x loop\n|", EndStyle.BARE))
    }

    @Test
    fun labelledProcessIsClosedByItsLabel() {
        assertEquals(
            "end process regs;",
            closerOf("architecture a of b is\nbegin\n    regs : process (clk)\n|", EndStyle.KEYWORD_AND_NAME),
        )
    }

    @Test
    fun parenHasNoCloser() {
        assertNull(closerOf("entity foo is\n    port (\n|", EndStyle.KEYWORD))
    }
}
