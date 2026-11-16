package dev.vhdltoolkit.editing

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import dev.vhdltoolkit.lexer.VhdlTokenTypes

/** Highlights and auto-inserts matching parentheses. */
class VhdlBraceMatcher : PairedBraceMatcher {

    override fun getPairs(): Array<BracePair> = PAIRS

    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean = true

    override fun getCodeConstructStart(file: PsiFile, openingBraceOffset: Int): Int = openingBraceOffset

    companion object {
        private val PAIRS = arrayOf(
            BracePair(VhdlTokenTypes.LPAREN, VhdlTokenTypes.RPAREN, false),
        )
    }
}
