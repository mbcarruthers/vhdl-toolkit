package dev.vhdltoolkit.lexer

import dev.vhdltoolkit.lexer.VhdlSemanticClassifier.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VhdlSemanticClassifierTest {

    /** Maps identifier text -> category for easy assertions. */
    private fun classify(src: String): List<Pair<String, Category>> =
        VhdlSemanticClassifier.classify(src).map { src.substring(it.start, it.end) to it.category }

    private fun categoryOf(src: String, name: String): Category? =
        classify(src).firstOrNull { it.first == name }?.second

    @Test
    fun libraryAndPackageInUseClause() {
        val src = "library IEEE;\nuse IEEE.std_logic_1164.all;"
        val got = classify(src)
        assertTrue("IEEE should be a library", got.contains("IEEE" to Category.LIBRARY))
        assertTrue(
            "std_logic_1164 should be a package",
            got.contains("std_logic_1164" to Category.PACKAGE),
        )
    }

    @Test
    fun entityDeclarationName() {
        assertEquals(Category.UNIT_NAME, categoryOf("entity d_flop is", "d_flop"))
    }

    @Test
    fun architectureNameAndImplementedEntity() {
        val src = "architecture RTL of d_flop is"
        assertEquals(Category.UNIT_NAME, categoryOf(src, "RTL"))
        assertEquals(Category.UNIT_NAME, categoryOf(src, "d_flop"))
    }

    @Test
    fun instantiationSplitsLibraryFromUnit() {
        val src = "b0 : entity work.d_flop"
        assertEquals("b0 is a statement label", Category.LABEL, categoryOf(src, "b0"))
        assertEquals("work is the library", Category.LIBRARY, categoryOf(src, "work"))
        assertEquals("d_flop is the unit", Category.UNIT_NAME, categoryOf(src, "d_flop"))
    }

    @Test
    fun portDeclarationNameAndType() {
        val src = "D : in std_logic;"
        assertEquals(Category.DECLARED_NAME, categoryOf(src, "D"))
        assertEquals(Category.TYPE, categoryOf(src, "std_logic"))
    }

    @Test
    fun portWithoutModeStillTypesCorrectly() {
        val src = "constant W : integer := 8;"
        assertEquals(Category.DECLARED_NAME, categoryOf(src, "W"))
        assertEquals(Category.TYPE, categoryOf(src, "integer"))
    }

    @Test
    fun signalDeclaration() {
        val src = "signal N : std_logic_vector(7 downto 0);"
        assertEquals(Category.DECLARED_NAME, categoryOf(src, "N"))
        assertEquals(Category.TYPE, categoryOf(src, "std_logic_vector"))
    }

    @Test
    fun standardFunctionCall() {
        assertEquals(Category.FUNCTION, categoryOf("if rising_edge(Clk) then", "rising_edge"))
    }

    @Test
    fun indexingIsNotMistakenForFunctionCall() {
        // N(0) is an array index, not a call; N must stay unclassified.
        assertEquals(null, categoryOf("Output <= N(0);", "N"))
    }

    @Test
    fun attributeAfterTick() {
        val src = "if Clk'event then"
        assertEquals(Category.ATTRIBUTE, categoryOf(src, "event"))
        // The signal itself is not an attribute.
        assertEquals(null, categoryOf(src, "Clk"))
    }

    @Test
    fun formalInPortMap() {
        val src = "port map( D => Clk, Output => N(0) );"
        assertEquals(Category.DECLARED_NAME, categoryOf(src, "D"))
        assertEquals(Category.DECLARED_NAME, categoryOf(src, "Output"))
    }

    @Test
    fun typeDeclaration() {
        assertEquals(Category.TYPE, categoryOf("type state_t is (idle, run);", "state_t"))
    }

    @Test
    fun commentsDoNotAffectClassification() {
        val src = "-- entity fake is\nentity real_one is"
        val got = classify(src)
        assertTrue(got.contains("real_one" to Category.UNIT_NAME))
        assertTrue("nothing inside the comment is classified", got.none { it.first == "fake" })
    }

    @Test
    fun spansAreOrderedAndNonOverlapping() {
        val src = """
            library IEEE;
            use IEEE.numeric_std.all;
            entity counter is
                Port( Clk : in std_logic; Clear : in std_logic );
            end entity;
            architecture RTL of counter is
                signal N : std_logic_vector(7 downto 0);
            begin
                b0 : entity work.d_flop
                    port map( D => not N(0), Clk => Clk );
            end architecture;
        """.trimIndent()
        val spans = VhdlSemanticClassifier.classify(src)
        assertTrue(spans.isNotEmpty())
        for (w in spans.windowed(2)) {
            assertTrue("spans must be ordered and disjoint", w[0].end <= w[1].start)
        }
        // Every span must land exactly on an identifier token.
        val identRanges = VhdlTokenizer.tokenize(src)
            .filter { it.kind == VhdlTokenizer.Kind.IDENTIFIER }
            .map { it.start to it.end }
            .toSet()
        for (s in spans) {
            assertTrue("span ${s.start}..${s.end} aligns to an identifier", (s.start to s.end) in identRanges)
        }
    }
}
