package dev.vhdltoolkit.editing

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import dev.vhdltoolkit.VhdlLanguage
import dev.vhdltoolkit.settings.VhdlSettings

/**
 * Ctrl+Shift+Enter: finish the current line and move on.
 *
 * The work of deciding what is missing lives in [VhdlSmartEnter], which is
 * plain text in and plain text out; this only applies the result to the
 * document.
 */
class VhdlSmartEnterProcessor : SmartEnterProcessor() {

    override fun process(project: Project, editor: Editor, psiFile: PsiFile): Boolean {
        if (psiFile.language !is VhdlLanguage) return false

        val document = editor.document
        val caret = editor.caretModel.offset
        val settings = VhdlSettings.getInstance()

        val result = VhdlSmartEnter.completionFor(
            text = document.charsSequence,
            caret = caret,
            indentSize = indentSize(project, editor),
            endStyle = settings.endStyleEnum,
            insertBegin = settings.insertBegin && settings.completeBlocks,
        ) ?: return false

        val lineNumber = document.getLineNumber(caret)
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)

        document.replaceString(lineStart, lineEnd, result.line + result.tail)
        editor.caretModel.moveToOffset((lineStart + result.caret).coerceIn(0, document.textLength))
        PsiDocumentManager.getInstance(project).commitDocument(document)
        return true
    }

    private fun indentSize(project: Project, editor: Editor): Int =
        runCatching { CodeStyle.getIndentOptions(project, editor.document).INDENT_SIZE }
            .getOrNull()?.takeIf { it > 0 } ?: VhdlLineIndentProvider.DEFAULT_INDENT
}
