package dev.vhdltoolkit.editing

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Both `when` styles must be reachable. The plugin has a default but no
 * opinion: the language permits either, so this is the author's choice.
 */
class VhdlWhenIndentTest {

    private val case = """
        architecture a of b is
        begin
            process (sel)
            begin
                case sel is
        """.trimIndent() + "\n"

    private fun indentOfWhen(indented: Boolean): Int =
        VhdlBlockStructure.indentFor(
            text = case,
            offset = case.length,
            indentSize = 4,
            lineStartsWith = "when",
            indentWhenInCase = indented,
        )

    @Test
    fun indentedIsTheDefaultStyle() {
        // `case` sits at column 8, so its alternatives sit at 12.
        assertEquals(12, indentOfWhen(indented = true))
    }

    @Test
    fun levelWithTheCaseIsAvailable() {
        assertEquals(8, indentOfWhen(indented = false))
    }

    @Test
    fun theDefaultArgumentKeepsTheOlderBehaviour() {
        assertEquals(
            indentOfWhen(indented = true),
            VhdlBlockStructure.indentFor(case, case.length, 4, "when"),
        )
    }

    /**
     * Whichever style is chosen, what sits inside an alternative is measured
     * from the `when` as written, so a file formatted either way stays
     * self-consistent.
     */
    @Test
    fun statementsFollowTheWhenAsWritten() {
        for (column in listOf(8, 12)) {
            val text = case + " ".repeat(column) + "when '0' =>\n"
            assertEquals(
                column + 4,
                VhdlBlockStructure.indentFor(text, text.length, 4, null),
            )
        }
    }
}
