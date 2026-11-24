package dev.vhdltoolkit.editing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VhdlParenCompletionTest {

    /** Asks about the `(` marked by `|`, which stands in its place. */
    private fun needsSemicolon(marked: String): Boolean {
        val at = marked.indexOf('|')
        require(at >= 0)
        val text = marked.replaceRange(at, at + 1, "(")
        return VhdlParenCompletion.needsSemicolon(text, at)
    }

    @Test
    fun anEntityPortClauseEndsItsStatement() {
        assertTrue(needsSemicolon("entity foo is\n    port |"))
    }

    @Test
    fun aGenericClauseDoesToo() {
        assertTrue(needsSemicolon("entity foo is\n    generic |"))
    }

    @Test
    fun aComponentPortClauseIsNoDifferent() {
        assertTrue(needsSemicolon("component foo is\n    port |"))
    }

    @Test
    fun aPortMapClosesTheInstantiation() {
        assertTrue(needsSemicolon("    u1 : entity work.foo\n        port map |"))
    }

    /**
     * The exception worth encoding: a generic map is followed by the port map
     * of the same instantiation, so it takes no semicolon of its own.
     */
    @Test
    fun aGenericMapDoesNotCloseTheInstantiation() {
        assertFalse(needsSemicolon("    u1 : entity work.foo\n        generic map |"))
    }

    @Test
    fun caseIsIgnored() {
        assertTrue(needsSemicolon("entity foo is\n    PORT |"))
        assertTrue(needsSemicolon("    u1 : foo PORT MAP |"))
    }

    @Test
    fun anOrdinaryExpressionGetsNothing() {
        assertFalse(needsSemicolon("    q <= std_logic_vector|"))
        assertFalse(needsSemicolon("    if rising_edge|"))
        assertFalse(needsSemicolon("    signal s : std_logic_vector|"))
    }

    @Test
    fun aNestedParenInsideAPortListIsStillAnExpression() {
        assertFalse(needsSemicolon("    port (\n        q : out std_logic_vector|"))
    }

    @Test
    fun aParenInACommentIsLeftAlone() {
        assertFalse(needsSemicolon("    -- port |"))
    }

    @Test
    fun aCommentBetweenTheKeywordAndTheBracketDoesNotBreakIt() {
        assertTrue(needsSemicolon("    port -- the interface\n    |"))
    }

    @Test
    fun aWordEndingInPortIsNotPort() {
        // `export` ends in the letters but is one identifier, not the keyword.
        assertFalse(needsSemicolon("    export |"))
    }

    @Test
    fun mapAloneIsNotEnough() {
        assertFalse(needsSemicolon("    map |"))
    }

    @Test
    fun anOffsetThatIsNotAnOpeningBracketIsRefused() {
        assertFalse(VhdlParenCompletion.needsSemicolon("port x", 5))
        assertFalse(VhdlParenCompletion.needsSemicolon("port (", 99))
        assertFalse(VhdlParenCompletion.needsSemicolon("port (", -1))
    }
}
