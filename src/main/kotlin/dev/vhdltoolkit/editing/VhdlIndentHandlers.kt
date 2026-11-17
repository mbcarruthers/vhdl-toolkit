package dev.vhdltoolkit.editing

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyle
import com.intellij.psi.codeStyle.lineIndent.LineIndentProvider
import dev.vhdltoolkit.VhdlLanguage
import dev.vhdltoolkit.settings.VhdlSettings

/**
 * Supplies the indent for a new line without a formatting model.
 *
 * The plugin has no VHDL parser, so the IntelliJ formatter — which builds its
 * blocks from a structured syntax tree — has nothing to work with. This
 * extension point exists for exactly that situation: it is handed a document
 * and an offset and returns the indent string directly.
 */
class VhdlLineIndentProvider : LineIndentProvider {

    override fun isSuitableFor(language: Language?): Boolean = language is VhdlLanguage

    override fun getLineIndent(project: Project, editor: Editor, language: Language?, offset: Int): String? {
        if (language !is VhdlLanguage) return null
        val document = editor.document
        val indentOptions = runCatching {
            CodeStyle.getIndentOptions(project, document)
        }.getOrNull()
        val indentSize = indentOptions?.INDENT_SIZE?.takeIf { it > 0 } ?: DEFAULT_INDENT
        val useTabs = indentOptions?.USE_TAB_CHARACTER ?: false
        val tabSize = indentOptions?.TAB_SIZE?.takeIf { it > 0 } ?: indentSize

        val columns = VhdlBlockStructure.indentFor(
            text = document.charsSequence,
            offset = offset,
            indentSize = indentSize,
            lineStartsWith = null,
        )
        return renderIndent(columns, useTabs, tabSize)
    }

    companion object {
        const val DEFAULT_INDENT = 4

        fun renderIndent(columns: Int, useTabs: Boolean, tabSize: Int): String {
            if (columns <= 0) return ""
            if (!useTabs || tabSize <= 0) return " ".repeat(columns)
            return "\t".repeat(columns / tabSize) + " ".repeat(columns % tabSize)
        }
    }
}

/**
 * Pulls a line back to its enclosing construct's column as a closing keyword
 * is typed, the way `}` snaps back in a brace language.
 *
 * It only fires when the keyword is the entire content of the line so far, so
 * typing `end` inside an expression, a comment or a string leaves the line
 * alone.
 */
class VhdlTypedHandler : TypedHandlerDelegate() {

    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (file.language !is VhdlLanguage) return Result.CONTINUE
        if (!c.isLetter() && c != ')') return Result.CONTINUE

        val document = editor.document
        val caret = editor.caretModel.offset
        val lineNumber = document.getLineNumber(caret)
        val lineStart = document.getLineStartOffset(lineNumber)
        val typedSoFar = document.getText(com.intellij.openapi.util.TextRange(lineStart, caret))
        val word = typedSoFar.trim()

        // Only act when the line holds nothing but this keyword.
        if (word.lowercase() !in REINDENT_TRIGGERS) return Result.CONTINUE
        if (typedSoFar.substringBefore(word).isNotBlank()) return Result.CONTINUE

        PsiDocumentManager.getInstance(project).commitDocument(document)
        reindentLine(project, document, lineNumber, word)
        return Result.CONTINUE
    }

    private fun reindentLine(project: Project, document: Document, lineNumber: Int, firstWord: String) {
        val indentOptions = runCatching { CodeStyle.getIndentOptions(project, document) }.getOrNull()
        val indentSize = indentOptions?.INDENT_SIZE?.takeIf { it > 0 } ?: VhdlLineIndentProvider.DEFAULT_INDENT
        val useTabs = indentOptions?.USE_TAB_CHARACTER ?: false
        val tabSize = indentOptions?.TAB_SIZE?.takeIf { it > 0 } ?: indentSize

        val lineStart = document.getLineStartOffset(lineNumber)
        val columns = VhdlBlockStructure.indentFor(
            text = document.charsSequence,
            offset = lineStart,
            indentSize = indentSize,
            lineStartsWith = firstWord,
        )
        val wanted = VhdlLineIndentProvider.renderIndent(columns, useTabs, tabSize)

        val lineEnd = document.getLineEndOffset(lineNumber)
        val lineText = document.getText(com.intellij.openapi.util.TextRange(lineStart, lineEnd))
        val currentIndent = lineText.takeWhile { it == ' ' || it == '\t' }
        if (currentIndent == wanted) return

        document.replaceString(lineStart, lineStart + currentIndent.length, wanted)
    }

    private companion object {
        val REINDENT_TRIGGERS = setOf("end", "begin", "else", "elsif", "when", ")")
    }
}

/**
 * Completes a block when its opening line is finished.
 *
 * Typing `architecture rtl of top is` and pressing Enter leaves the caret in
 * the declarative part with `begin` and `end architecture rtl;` already in
 * place, the same courtesy a brace language gets from `}`.
 *
 * Nothing is inserted if the construct already has a matching `end` ahead of
 * the caret, so editing existing code does not accumulate duplicates.
 */
class VhdlEnterHandler : com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter() {

    override fun postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): Result {
        if (file.language !is VhdlLanguage) return Result.Continue
        val settings = VhdlSettings.getInstance()
        if (!settings.completeBlocks) return Result.Continue

        val project = file.project
        val document = editor.document
        val caret = editor.caretModel.offset

        val completion = VhdlBlockCompletion.completionAt(
            text = document.charsSequence,
            caret = caret,
            indentSize = indentSize(project, document),
            endStyle = settings.endStyleEnum,
            insertBegin = settings.insertBegin,
        ) ?: return Result.Continue

        document.insertString(caret, completion.textToInsert)
        editor.caretModel.moveToOffset(caret + completion.caretOffsetInInsertion)
        PsiDocumentManager.getInstance(project).commitDocument(document)
        return Result.Continue
    }

    private fun indentSize(project: Project, document: Document): Int =
        runCatching { CodeStyle.getIndentOptions(project, document).INDENT_SIZE }
            .getOrNull()?.takeIf { it > 0 } ?: VhdlLineIndentProvider.DEFAULT_INDENT
}
