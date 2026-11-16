package dev.vhdltoolkit.highlight

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import dev.vhdltoolkit.lexer.VhdlLexer
import dev.vhdltoolkit.lexer.VhdlTokenTypes

class VhdlSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer(): Lexer = VhdlLexer()

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> {
        val key = when (tokenType) {
            VhdlTokenTypes.KEYWORD -> VhdlColors.KEYWORD
            VhdlTokenTypes.LINE_COMMENT -> VhdlColors.LINE_COMMENT
            VhdlTokenTypes.BLOCK_COMMENT -> VhdlColors.BLOCK_COMMENT
            VhdlTokenTypes.STRING -> VhdlColors.STRING
            VhdlTokenTypes.CHAR_LITERAL -> VhdlColors.CHAR_LITERAL
            VhdlTokenTypes.NUMBER -> VhdlColors.NUMBER
            VhdlTokenTypes.OPERATOR -> VhdlColors.OPERATOR
            VhdlTokenTypes.LPAREN, VhdlTokenTypes.RPAREN -> VhdlColors.PARENTHESES
            VhdlTokenTypes.SEMICOLON -> VhdlColors.SEMICOLON
            VhdlTokenTypes.IDENTIFIER -> VhdlColors.IDENTIFIER
            TokenType.BAD_CHARACTER -> VhdlColors.BAD_CHARACTER
            else -> return EMPTY
        }
        return arrayOf(key)
    }

    private companion object {
        val EMPTY = emptyArray<TextAttributesKey>()
    }
}
