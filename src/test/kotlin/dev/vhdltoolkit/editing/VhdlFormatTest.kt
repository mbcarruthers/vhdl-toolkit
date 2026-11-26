package dev.vhdltoolkit.editing

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Alignment reached through the editor rather than called directly.
 *
 * The engine is covered in `:core`; what cannot be tested there is whether
 * the action is wired up and whether Reformat Code reaches a post-format
 * processor at all when the language has no formatting model. That second
 * question is the one worth asking — it is the difference between the
 * shortcut working and it doing nothing.
 */
class VhdlFormatTest : BasePlatformTestCase() {

    private val ragged =
        "architecture rtl of top is\n" +
            "    signal a : std_logic;\n" +
            "    signal bbbbb : std_logic;\n" +
            "begin\n" +
            "end architecture rtl;\n"

    private fun lineAt(index: Int): String {
        val document = myFixture.editor.document
        return document.getText(
            com.intellij.openapi.util.TextRange(
                document.getLineStartOffset(index),
                document.getLineEndOffset(index),
            ),
        )
    }

    fun testTheActionAlignsAroundTheCaret() {
        myFixture.configureByText("align_probe.vhd", ragged)
        myFixture.editor.caretModel.moveToOffset(
            myFixture.editor.document.getLineStartOffset(1) + 4,
        )
        myFixture.performEditorAction("dev.vhdltoolkit.AlignColumns")

        assertEquals("    signal a     : std_logic;", lineAt(1))
        assertEquals("    signal bbbbb : std_logic;", lineAt(2))
    }

    /**
     * The question a formatting model would normally answer. If this passes,
     * Reformat Code can be given behaviour without one.
     */
    fun testReformatCodeReachesThePostFormatProcessor() {
        myFixture.configureByText("reformat_probe.vhd", ragged)
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_REFORMAT)

        assertEquals("    signal a     : std_logic;", lineAt(1))
        assertEquals("    signal bbbbb : std_logic;", lineAt(2))
    }

    fun testAlreadyAlignedTextIsNotTouched() {
        val tidy =
            "architecture rtl of top is\n" +
                "    signal a     : std_logic;\n" +
                "    signal bbbbb : std_logic;\n" +
                "begin\n"
        myFixture.configureByText("idempotent_probe.vhd", tidy)
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_REFORMAT)
        assertEquals(tidy, myFixture.editor.document.text)
    }
}
