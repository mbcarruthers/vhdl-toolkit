package dev.vhdltoolkit.editing

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import dev.vhdltoolkit.VhdlLanguage

/**
 * Lines the current declarations, associations or assignments into columns.
 *
 * There is one action rather than one per construct. What to align is read
 * from where the caret is: the line's own anchors decide the shape, and the
 * neighbouring lines sharing that shape decide how far the run reaches. A
 * selection aligns every run inside it.
 */
class VhdlAlignAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val file = event.getData(CommonDataKeys.PSI_FILE)
        event.presentation.isEnabledAndVisible =
            file?.language is VhdlLanguage && event.getData(CommonDataKeys.EDITOR) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.getData(CommonDataKeys.PROJECT) ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val file = event.getData(CommonDataKeys.PSI_FILE) ?: return
        if (file.language !is VhdlLanguage) return

        val document = editor.document
        val tabSize = tabSizeOf(project, document)
        val selection = editor.selectionModel

        val edits = if (selection.hasSelection()) {
            VhdlAlignment.alignRange(
                text = document.charsSequence,
                from = document.getLineNumber(selection.selectionStart),
                to = document.getLineNumber(selection.selectionEnd),
                tabSize = tabSize,
            )
        } else {
            VhdlAlignment.alignAround(
                text = document.charsSequence,
                line = document.getLineNumber(editor.caretModel.offset),
                tabSize = tabSize,
            )
        }
        if (edits.isEmpty()) return

        WriteCommandAction.runWriteCommandAction(project, NAME, null, {
            applyEdits(document, edits)
        }, file)
    }

    private fun tabSizeOf(project: Project, document: Document): Int =
        runCatching { CodeStyle.getIndentOptions(project, document).TAB_SIZE }
            .getOrNull()?.takeIf { it > 0 } ?: VhdlLineIndentProvider.DEFAULT_INDENT

    private companion object {
        const val NAME = "Align VHDL Columns"

        /**
         * Applied from the bottom up, so that rewriting one line cannot move
         * the lines still to be rewritten.
         */
        fun applyEdits(document: Document, edits: List<VhdlAlignment.Edit>) {
            for (edit in edits.sortedByDescending { it.line }) {
                if (edit.line !in 0 until document.lineCount) continue
                document.replaceString(
                    document.getLineStartOffset(edit.line),
                    document.getLineEndOffset(edit.line),
                    edit.text,
                )
            }
        }
    }
}
