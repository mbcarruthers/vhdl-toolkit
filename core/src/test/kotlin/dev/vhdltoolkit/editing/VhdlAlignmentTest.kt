package dev.vhdltoolkit.editing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VhdlAlignmentTest {

    /** Aligns around the line marked `|`, returning the whole text back. */
    private fun align(marked: String): String {
        val lines = marked.trimIndent().lines()
        val caret = lines.indexOfFirst { it.contains('|') }
        require(caret >= 0) { "mark a line with |" }
        val text = lines.map { it.replace("|", "") }

        val edits = VhdlAlignment.alignAround(text.joinToString("\n"), caret)
        val out = text.toMutableList()
        for (edit in edits) out[edit.line] = edit.text
        return out.joinToString("\n")
    }

    // ------------------------------------------------------- one column

    @Test
    fun portDeclarationsLineUpOnTheirColon() {
        assertEquals(
            """
            entity counter is
                port (
                    clk     : in  std_logic;
                    reset_n : in  std_logic;
                    q       : out std_logic_vector(3 downto 0)
                );
            """.trimIndent(),
            align(
                """
                entity counter is
                    port (
                        |clk : in  std_logic;
                        reset_n : in  std_logic;
                        q : out std_logic_vector(3 downto 0)
                    );
                """,
            ),
        )
    }

    @Test
    fun signalDeclarationsLineUpTheSameWay() {
        assertEquals(
            """
            architecture rtl of counter is
                signal count : unsigned(3 downto 0);
                signal ready : std_logic;
            begin
            """.trimIndent(),
            align(
                """
                architecture rtl of counter is
                    |signal count : unsigned(3 downto 0);
                    signal ready       : std_logic;
                begin
                """,
            ),
        )
    }

    @Test
    fun portMapsLineUpOnTheArrow() {
        assertEquals(
            """
                    clk   => sys_clk,
                    reset => sys_reset,
                    q     => counter_q
            """.trimIndent(),
            align(
                """
                        |clk => sys_clk,
                        reset => sys_reset,
                        q => counter_q
                """,
            ),
        )
    }

    @Test
    fun caseAlternativesLineUpOnTheArrow() {
        val result = align(
            """
            |when "00" => y <= a;
            when others => y <= b;
            """,
        )
        assertEquals(
            """
            when "00"   => y <= a;
            when others => y <= b;
            """.trimIndent(),
            result,
        )
    }

    // ---------------------------------------------------- several columns

    /**
     * The case a list of per-construct operations cannot express: two anchors
     * on one line, both wanting a column.
     */
    @Test
    fun aLineWithTwoAnchorsGetsTwoColumns() {
        assertEquals(
            """
            constant WIDTH : integer := 8;
            constant N     : integer := 16;
            """.trimIndent(),
            align(
                """
                |constant WIDTH : integer := 8;
                constant N : integer := 16;
                """,
            ),
        )
    }

    @Test
    fun trailingCommentsGetAColumnToo() {
        assertEquals(
            """
            signal a   : std_logic;  -- clock
            signal bbb : std_logic;  -- reset
            """.trimIndent(),
            align(
                """
                |signal a : std_logic; -- clock
                signal bbb : std_logic;     -- reset
                """,
            ),
        )
    }

    @Test
    fun assignmentsLineUpOnTheirArrow() {
        assertEquals(
            """
            y        <= a and b;
            long_one <= c or d;
            """.trimIndent(),
            align(
                """
                |y <= a and b;
                long_one <= c or d;
                """,
            ),
        )
    }

    // ------------------------------------------------------ where runs end

    /**
     * A blank line is a deliberate separation. Lining up across it would drag
     * the first pair out to the width of a name the author had set apart.
     */
    @Test
    fun aBlankLineEndsTheRun() {
        assertEquals(
            """
            signal a   : std_logic;
            signal bbb : std_logic;

            signal c                : unsigned(3 downto 0);
            signal dddddddddddddddd : unsigned(3 downto 0);
            """.trimIndent(),
            align(
                """
                |signal a : std_logic;
                signal bbb : std_logic;

                signal c                : unsigned(3 downto 0);
                signal dddddddddddddddd : unsigned(3 downto 0);
                """,
            ),
        )
    }

    /** A comment on its own line is a heading, not a row: it divides nothing. */
    @Test
    fun aCommentOnItsOwnLineDoesNotEndTheRun() {
        assertEquals(
            """
            signal a   : std_logic;
            -- the wide one
            signal bbb : std_logic;
            """.trimIndent(),
            align(
                """
                |signal a : std_logic;
                -- the wide one
                signal bbb : std_logic;
                """,
            ),
        )
    }

    /**
     * A line of a different shape ends the run without having to be
     * recognised as anything — `begin`, `);` and a header all carry no
     * anchor, so they stop it by having nothing to line up.
     */
    @Test
    fun aDifferentShapeEndsTheRun() {
        assertEquals(
            """
            signal a   : std_logic;
            signal bbb : std_logic;
            begin
            y <= a;
            """.trimIndent(),
            align(
                """
                |signal a : std_logic;
                signal bbb : std_logic;
                begin
                y <= a;
                """,
            ),
        )
    }

    @Test
    fun aDeclarationDoesNotJoinAnAssignment() {
        // `:` and `<=` are different shapes, so the run stops between them.
        val result = align(
            """
            |signal a : std_logic;
            yy <= a;
            """,
        )
        assertEquals("signal a : std_logic;\nyy <= a;", result)
    }

    // ------------------------------------------------------- left alone

    @Test
    fun anAnchorInsideBracketsIsPartOfTheExpression() {
        // The `=>` of an aggregate belongs to the value, not the line.
        assertEquals(
            """
            signal a   : std_logic_vector(3 downto 0) := (others => '0');
            signal bbb : std_logic_vector(3 downto 0) := (others => '1');
            """.trimIndent(),
            align(
                """
                |signal a : std_logic_vector(3 downto 0) := (others => '0');
                signal bbb : std_logic_vector(3 downto 0) := (others => '1');
                """,
            ),
        )
    }

    @Test
    fun anAnchorInsideAStringIsNotAnAnchor() {
        val result = align(
            """
            |report "a : b" severity note;
            report "c : d" severity note;
            """,
        )
        assertEquals("report \"a : b\" severity note;\nreport \"c : d\" severity note;", result)
    }

    @Test
    fun aLineWithNoAnchorYieldsNothing() {
        assertTrue(VhdlAlignment.alignAround("begin\nend architecture rtl;", 0).isEmpty())
    }

    @Test
    fun aRowWithNoNeighbourIsLeftAlone() {
        assertTrue(VhdlAlignment.alignAround("signal a : std_logic;\n\nbegin", 0).isEmpty())
    }

    @Test
    fun alreadyAlignedTextProducesNoEdits() {
        val text = "signal a   : std_logic;\nsignal bbb : std_logic;"
        assertTrue(VhdlAlignment.alignAround(text, 0).isEmpty())
    }

    /**
     * Padding is interior: it goes between a cell and its anchor, never in
     * front of the line. Built without trimIndent, which would strip the
     * very thing being checked.
     */
    @Test
    fun theLeadingIndentIsUntouched() {
        val text = "\t\tclk : in std_logic;\n\t\treset_n : in std_logic;"
        val edits = VhdlAlignment.alignAround(text, 0)
        assertEquals(1, edits.size)
        assertEquals("\t\tclk     : in std_logic;", edits.single().text)
    }

    /** A tab counts as its width when measuring, not as one character. */
    @Test
    fun aTabInsideACellIsMeasuredAsDisplayed() {
        val text = "signal\ta : std_logic;\nsignal bbbbbbbbb : std_logic;"
        val edits = VhdlAlignment.alignAround(text, 0, tabSize = 4)
        // `signal` is 6 wide, the tab carries to 8, then `a` ends the cell at 9.
        assertEquals("signal\ta        : std_logic;", edits.first { it.line == 0 }.text)
    }

    @Test
    fun anOutOfRangeLineIsRefused() {
        assertTrue(VhdlAlignment.alignAround("signal a : std_logic;", 99).isEmpty())
        assertTrue(VhdlAlignment.alignAround("signal a : std_logic;", -1).isEmpty())
    }

    // ----------------------------------------------------------- a range

    @Test
    fun aRangeAlignsEveryRunInsideIt() {
        val text = """
            signal a : std_logic;
            signal bbb : std_logic;

            clk => sys_clk,
            reset_n => sys_reset,
        """.trimIndent()

        val edits = VhdlAlignment.alignRange(text, 0, text.lines().lastIndex)
        val out = text.lines().toMutableList()
        for (edit in edits) out[edit.line] = edit.text

        assertEquals(
            """
            signal a   : std_logic;
            signal bbb : std_logic;

            clk     => sys_clk,
            reset_n => sys_reset,
            """.trimIndent(),
            out.joinToString("\n"),
        )
    }

    // ------------------------------------------------------- port modes

    /**
     * With the modes ragged the types behind them never line up, which is
     * most of what makes a port clause look unfinished.
     */
    @Test
    fun portModesGetAColumnOfTheirOwn() {
        assertEquals(
            """
            port (
                clk   : in    std_logic;
                q     : out   std_logic_vector(3 downto 0);
                bidir : inout std_logic
            );
            """.trimIndent(),
            align(
                """
                port (
                    |clk : in std_logic;
                    q : out std_logic_vector(3 downto 0);
                    bidir : inout std_logic
                );
                """,
            ),
        )
    }

    @Test
    fun bufferAndLinkageAreModesToo() {
        val result = align(
            """
            |a : buffer  std_logic;
            b : linkage std_logic;
            c : in      std_logic;
            """,
        )
        assertEquals(
            """
            a : buffer  std_logic;
            b : linkage std_logic;
            c : in      std_logic;
            """.trimIndent(),
            result,
        )
    }

    /**
     * A mode is a column, so a line without one has a different shape. The
     * run stops rather than inventing a column that is not there.
     */
    @Test
    fun aDeclarationWithNoModeDoesNotJoinPorts() {
        val result = align(
            """
            |signal aa : std_logic;
            clk : in std_logic;
            """,
        )
        assertEquals("signal aa : std_logic;\nclk : in std_logic;", result)
    }

    /** `in` names a mode only after a colon; elsewhere it is ordinary text. */
    @Test
    fun theWordInsideARangeIsNotAMode() {
        val result = align(
            """
            |x    <= a;
            yyyy <= b;
            """,
        )
        assertEquals("x    <= a;\nyyyy <= b;", result)
    }
}
