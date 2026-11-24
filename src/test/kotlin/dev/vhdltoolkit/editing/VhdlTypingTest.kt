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

    // ---------------------------------------------------- completion path

    /**
     * The gap that let the reported bug through: the suite typed in a way
     * nobody types. A closing keyword can arrive two ways, and only one of
     * them went through the typed handler, so five passing tests sat beside
     * an editor that visibly did not work.
     *
     * Accepting the completion has to land in the same column as typing it.
     */
    fun testElseLinesUpWhenItsCompletionIsAccepted() {
        openProcess()
        myFixture.type("el")
        val suggestions = myFixture.completeBasic()
        val item = suggestions?.firstOrNull { it.lookupString.equals("else", ignoreCase = true) }
        assertNotNull("completion should offer else", item)
        myFixture.lookup.currentItem = item
        myFixture.finishLookup('\n')
        assertEquals("        else", caretLine())
    }

    fun testEndLinesUpWhenItsCompletionIsAccepted() {
        openProcess()
        myFixture.type("en")
        val suggestions = myFixture.completeBasic()
        val item = suggestions?.firstOrNull { it.lookupString.equals("end", ignoreCase = true) }
        assertNotNull("completion should offer end", item)
        myFixture.lookup.currentItem = item
        myFixture.finishLookup('\n')
        assertEquals("        end", caretLine())
    }

    /**
     * A skeleton is not a closing keyword and must not be dragged to the
     * construct's column; it belongs where the author was writing.
     */
    fun testAcceptingAnOrdinaryKeywordDoesNotMoveTheLine() {
        openProcess()
        myFixture.type("wa")
        val suggestions = myFixture.completeBasic()
        val item = suggestions?.firstOrNull { it.lookupString.equals("wait", ignoreCase = true) }
        if (item != null) {
            myFixture.lookup.currentItem = item
            myFixture.finishLookup('\n')
            assertEquals("            wait", caretLine())
        }
    }
}
