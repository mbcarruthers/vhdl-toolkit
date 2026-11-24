package dev.vhdltoolkit.editing

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.vhdltoolkit.VhdlLanguage

/**
 * Typing in a real editor.
 *
 * Everything the indent rule decides is already covered in `:core`, and it
 * says `else` belongs at column 8. The editor puts it at 12. That gap can
 * only live between the two, in the handler that is supposed to carry the
 * answer into the document — and nothing has ever exercised that path.
 */
class VhdlTypingTest : BasePlatformTestCase() {

    private fun openProcess() {
        myFixture.configureByText(
            "indent_probe.vhd",
            "architecture rtl of top is\n" +
                "begin\n" +
                "    process (clk)\n" +
                "    begin\n" +
                "        if x = '1' then\n" +
                "            q <= '1';\n" +
                "            <caret>",
        )
    }

    /** The line the caret is on, indent included. */
    private fun caretLine(): String {
        val document = myFixture.editor.document
        val line = document.getLineNumber(myFixture.editor.caretModel.offset)
        return document.getText(
            TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)),
        )
    }

    /**
     * Whether the fixture loads the plugin at all.
     *
     * Every handler begins by checking the file's language, so if a .vhd
     * file is not recognised here then nothing else this class asserts means
     * anything — the handlers would be declining to run for a reason that
     * has no bearing on the real IDE.
     */
    fun testTheFixtureSeesVhdl() {
        openProcess()
        assertEquals("VHDL", myFixture.file.fileType.name)
        assertSame(VhdlLanguage, myFixture.file.language)
    }

    fun testElseIsPulledBackToItsIf() {
        openProcess()
        myFixture.type("else")
        assertEquals("        else", caretLine())
    }

    fun testEndIsPulledBackToItsIf() {
        openProcess()
        myFixture.type("end")
        assertEquals("        end", caretLine())
    }

    /**
     * The other thing [VhdlTypedHandler.charTyped] does, reached through the
     * same method. If this works and the dedent does not, the handler is
     * being called and the fault is inside it; if neither works, the handler
     * is never reached at all.
     */
    fun testTheTypedHandlerIsReachedAtAll() {
        myFixture.configureByText("paren_probe.vhd", "entity foo is\n    port<caret>\n")
        myFixture.type("(")
        assertTrue("expected a semicolon after the port list, got: ${caretLine()}",
            caretLine().contains(";"))
    }

    /**
     * What the indent handler reads when it decides how to write an indent.
     * The settings dialog reports these as "Other File Types", which would
     * mean the plugin's own defaults never reach a VHDL file — and the
     * handler would be reading someone else's settings.
     */
    fun testVhdlHasItsOwnIndentOptions() {
        myFixture.configureByText("options_probe.vhd", "entity foo is\nend entity foo;\n")
        val options = CodeStyle.getIndentOptions(project, myFixture.editor.document)
        // These match the platform defaults, so on their own they prove
        // nothing. What makes the check meaningful is the language assertion
        // above: the options must belong to a file the IDE calls VHDL.
        assertEquals("VHDL", myFixture.file.fileType.name)
        assertFalse("VHDL is configured for spaces", options.USE_TAB_CHARACTER)
        assertEquals(4, options.INDENT_SIZE)
    }
}
