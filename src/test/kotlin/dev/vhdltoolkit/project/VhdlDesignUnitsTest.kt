package dev.vhdltoolkit.project

import dev.vhdltoolkit.project.VhdlDesignUnits.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VhdlDesignUnitsTest {

    private fun scan(text: String) = VhdlDesignUnits.scan(text)

    private fun kinds(text: String) = scan(text).map { it.kind to it.name }

    @Test
    fun findsAnEntityAndItsArchitecture() {
        val text = """
            entity counter is
            end entity counter;

            architecture rtl of counter is
            begin
            end architecture rtl;
        """.trimIndent()

        assertEquals(
            listOf(Kind.ENTITY to "counter", Kind.ARCHITECTURE to "rtl"),
            kinds(text),
        )
        assertEquals("counter", scan(text).first { it.kind == Kind.ARCHITECTURE }.target)
    }

    @Test
    fun theClosingEndIsNotAnotherUnit() {
        // `end entity counter;` names the entity again; counting it would
        // double every declaration in the file.
        assertEquals(1, scan("entity counter is\nend entity counter;").size)
        assertEquals(
            1,
            scan("architecture rtl of counter is\nbegin\nend architecture rtl;").size,
        )
    }

    @Test
    fun caseIsIgnored() {
        assertEquals(
            listOf(Kind.ENTITY to "counter"),
            kinds("ENTITY Counter IS\nEND ENTITY Counter;"),
        )
    }

    @Test
    fun findsPackagesAndBodies() {
        val text = """
            package types is
            end package types;

            package body types is
            end package body types;
        """.trimIndent()
        assertEquals(
            listOf(Kind.PACKAGE to "types", Kind.PACKAGE_BODY to "types"),
            kinds(text),
        )
    }

    @Test
    fun findsAComponentDeclaration() {
        assertEquals(
            listOf(Kind.COMPONENT to "counter"),
            kinds("component counter is\nend component;"),
        )
    }

    @Test
    fun findsADirectEntityInstantiation() {
        val unit = scan("    u1 : entity work.counter\n        port map (clk => clk);")
            .single { it.kind == Kind.INSTANTIATION }
        assertEquals("u1", unit.name)
        assertEquals("counter", unit.target)
        assertEquals("work", unit.library)
    }

    @Test
    fun aSelectedNameArrivesAsOneToken() {
        // A basic identifier stops at the dot, so a selected name arrives as
        // alternating identifier and `.` tokens and has to be walked.
        val unit = scan("u1 : entity mylib.sub.counter port map (a => b);")
            .single { it.kind == Kind.INSTANTIATION }
        assertEquals("counter", unit.target)
        assertEquals("sub", unit.library)
    }

    @Test
    fun findsAComponentInstantiationWrittenWithoutTheKeyword() {
        val unit = scan("    u1 : counter port map (clk => clk);")
            .single { it.kind == Kind.INSTANTIATION }
        assertEquals("u1", unit.name)
        assertEquals("counter", unit.target)
        assertNull(unit.library)
    }

    /**
     * The shape `name : name` is also how every port and signal is declared,
     * so the `map` is what tells them apart.
     */
    @Test
    fun declarationsAreNotMistakenForInstantiations() {
        val text = """
            entity counter is
                port (
                    clk : in std_logic;
                    q   : out std_logic_vector(3 downto 0)
                );
            end entity counter;

            architecture rtl of counter is
                signal count : unsigned(3 downto 0);
                constant ZERO : integer := 0;
            begin
            end architecture rtl;
        """.trimIndent()
        assertTrue(
            "no instantiations here",
            scan(text).none { it.kind == Kind.INSTANTIATION },
        )
    }

    @Test
    fun aLabelledProcessIsNotAnInstantiation() {
        val text = """
            architecture rtl of counter is
            begin
                regs : process (clk)
                begin
                    q <= d;
                end process regs;
            end architecture rtl;
        """.trimIndent()
        assertTrue(scan(text).none { it.kind == Kind.INSTANTIATION })
    }

    @Test
    fun commentsDoNotHideOrInventUnits() {
        val text = """
            -- entity ghost is
            entity real_one is -- the actual one
            end entity real_one;
        """.trimIndent()
        assertEquals(listOf(Kind.ENTITY to "real_one"), kinds(text))
    }

    @Test
    fun offsetsPointAtTheName() {
        val text = "entity counter is"
        val unit = scan(text).single()
        assertEquals(0, unit.keywordOffset)
        assertEquals(text.indexOf("counter"), unit.nameOffset)
    }

    @Test
    fun severalArchitecturesForOneEntityAreAllFound() {
        val text = """
            entity counter is
            end entity counter;
            architecture rtl of counter is
            begin
            end architecture rtl;
            architecture sim of counter is
            begin
            end architecture sim;
        """.trimIndent()
        val archs = scan(text).filter { it.kind == Kind.ARCHITECTURE }
        assertEquals(listOf("rtl", "sim"), archs.map { it.name })
        assertTrue(archs.all { it.target == "counter" })
    }

    @Test
    fun unitsComeBackInSourceOrder() {
        val text = """
            entity a is
            end entity a;
            entity b is
            end entity b;
        """.trimIndent()
        assertEquals(listOf("a", "b"), scan(text).map { it.name })
    }

    @Test
    fun anEmptyFileYieldsNothing() {
        assertTrue(scan("").isEmpty())
        assertTrue(scan("-- just a comment\n").isEmpty())
    }
}
