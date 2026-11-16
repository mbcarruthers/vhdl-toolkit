package dev.vhdltoolkit.highlight

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import dev.vhdltoolkit.VhdlFile
import dev.vhdltoolkit.lexer.VhdlSemanticClassifier

/**
 * Paints the finer identifier categories that the lexer cannot distinguish on
 * its own. Runs once per file, since the classifier needs surrounding tokens
 * for context rather than a single leaf.
 */
class VhdlAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is VhdlFile) return

        val text = element.text
        val fileRange = element.textRange

        for (span in VhdlSemanticClassifier.classify(text)) {
            val range = TextRange(span.start, span.end)
            if (!fileRange.contains(range)) continue
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(range)
                .textAttributes(VhdlColors.forCategory(span.category))
                .create()
        }
    }
}
