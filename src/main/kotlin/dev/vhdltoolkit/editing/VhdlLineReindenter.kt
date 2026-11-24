package dev.vhdltoolkit.editing

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import dev.vhdltoolkit.settings.VhdlSettings

/**
 * Pulls a line back to the column of whatever it closes.
 *
 * There are two ways a closing keyword reaches the document and they have
 * nothing in common: it can be typed a character at a time, or it can arrive
 * whole when a completion is accepted. Only the first was handled, so
 * finishing `else` by picking it from the popup left the line where it was —
 * and because the editor never moved, the feature looked broken rather than
 * partial.
 *
 * Both entry points call this, so the behaviour cannot drift between them
 * again.
 */
object VhdlLineReindenter {

    /** Words whose line belongs at its construct's column rather than inside it. */
    val CLOSING_WORDS = setOf("end", "begin", "else", "elsif", "when", ")")

    /**
     * Reindents [lineNumber] for a line whose entire content is [firstWord].
     *
     * Does nothing when the line holds anything else, so a keyword typed
     * inside an expression, a comment or a string is left alone.
     */
    fun reindentIfClosing(project: Project, document: Document, lineNumber: Int) {
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)
        val lineText = document.getText(TextRange(lineStart, lineEnd))

        val word = lineText.trim()
        if (word.lowercase() !in CLOSING_WORDS) return

        reindent(project, document, lineNumber, word)
    }

    /** Reindents [lineNumber], aligning it as a line that starts with [firstWord]. */
    fun reindent(project: Project, document: Document, lineNumber: Int, firstWord: String?) {
        val options = runCatching { CodeStyle.getIndentOptions(project, document) }.getOrNull()
        val indentSize = options?.INDENT_SIZE?.takeIf { it > 0 } ?: VhdlLineIndentProvider.DEFAULT_INDENT
        val useTabs = options?.USE_TAB_CHARACTER ?: false
        val tabSize = options?.TAB_SIZE?.takeIf { it > 0 } ?: indentSize

        val lineStart = document.getLineStartOffset(lineNumber)
        val columns = VhdlBlockStructure.indentFor(
            text = document.charsSequence,
            offset = lineStart,
            indentSize = indentSize,
            lineStartsWith = firstWord,
            indentWhenInCase = VhdlSettings.getInstance().indentWhenInCase,
            tabSize = tabSize,
        )
        val wanted = VhdlBlockStructure.renderIndent(columns, useTabs, tabSize)

        val lineEnd = document.getLineEndOffset(lineNumber)
        val lineText = document.getText(TextRange(lineStart, lineEnd))
        val currentIndent = lineText.takeWhile { it == ' ' || it == '\t' }
        if (currentIndent == wanted) return

        document.replaceString(lineStart, lineStart + currentIndent.length, wanted)
    }
}
