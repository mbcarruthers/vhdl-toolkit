package dev.vhdltoolkit.lexer

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import dev.vhdltoolkit.VhdlLanguage

class VhdlElementType(debugName: String) : IElementType(debugName, VhdlLanguage)

object VhdlTokenTypes {
    @JvmField val KEYWORD = VhdlElementType("KEYWORD")
    @JvmField val IDENTIFIER = VhdlElementType("IDENTIFIER")
    @JvmField val LINE_COMMENT = VhdlElementType("LINE_COMMENT")
    @JvmField val BLOCK_COMMENT = VhdlElementType("BLOCK_COMMENT")
    @JvmField val STRING = VhdlElementType("STRING")
    @JvmField val CHAR_LITERAL = VhdlElementType("CHAR_LITERAL")
    @JvmField val NUMBER = VhdlElementType("NUMBER")
    @JvmField val OPERATOR = VhdlElementType("OPERATOR")
    @JvmField val LPAREN = VhdlElementType("LPAREN")
    @JvmField val RPAREN = VhdlElementType("RPAREN")
    @JvmField val SEMICOLON = VhdlElementType("SEMICOLON")

    @JvmField val COMMENTS: TokenSet = TokenSet.create(LINE_COMMENT, BLOCK_COMMENT)
    @JvmField val STRINGS: TokenSet = TokenSet.create(STRING)
}
