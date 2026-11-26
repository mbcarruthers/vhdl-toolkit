package dev.vhdltoolkit.editing

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import dev.vhdltoolkit.VhdlLanguage

/**
 * Makes Reformat Code do something for VHDL.
 *
 * Reformatting normally runs through a formatting model, which wants a parse
 * tree to hang its rules on. There is no VHDL parser here — the tree is a
 * flat run of tokens, and the semantics come from the language server — so
 * there is no model to build and <b>Reformat Code</b> currently does nothing
 * at all in a VHDL file.
 *
 * A post-format processor is the way in. It runs after formatting, is handed
 * the range that was reformatted, and may rewrite the text itself. That is
 * enough to hang the column alignment off the shortcut every JetBrains user
 * reaches for without reproducing a formatting model to get there.
 */
class VhdlPostFormatProcessor : PostFormatProcessor {

    override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement = source

    override fun processText(
        source: PsiFile,
        rangeToReformat: TextRange,
        settings: CodeStyleSettings,
    ): TextRange {
        if (source.language !is VhdlLanguage) return rangeToReformat

        val manager = PsiDocumentManager.getInstance(source.project)
        val document = manager.getDocument(source) ?: return rangeToReformat

        val tabSize = settings.getIndentOptions(source.fileType).TAB_SIZE
            .takeIf { it > 0 } ?: VhdlLineIndentProvider.DEFAULT_INDENT

        val from = document.getLineNumber(rangeToReformat.startOffset.coerceIn(0, document.textLength))
        val to = document.getLineNumber(rangeToReformat.endOffset.coerceIn(0, document.textLength))

        val edits = VhdlAlignment.alignRange(document.charsSequence, from, to, tabSize)
        if (edits.isEmpty()) return rangeToReformat

        val lengthBefore = document.textLength
        for (edit in edits.sortedByDescending { it.line }) {
            if (edit.line !in 0 until document.lineCount) continue
            document.replaceString(
                document.getLineStartOffset(edit.line),
                document.getLineEndOffset(edit.line),
                edit.text,
            )
        }
        manager.commitDocument(document)

        // The caller uses the returned range to carry on, so it has to
        // account for the padding just inserted.
        return TextRange(
            rangeToReformat.startOffset,
            (rangeToReformat.endOffset + (document.textLength - lengthBefore))
                .coerceIn(rangeToReformat.startOffset, document.textLength),
        )
    }
}
