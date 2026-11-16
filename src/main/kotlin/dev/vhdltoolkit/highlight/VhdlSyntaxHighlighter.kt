package dev.vhdltoolkit.highlight

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import dev.vhdltoolkit.lexer.VhdlLexer
import dev.vhdltoolkit.lexer.VhdlTokenTypes

class VhdlSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer(): Lexer = VhdlLexer()

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> {
        return when (tokenType) {
            VhdlTokenTypes.KEYWORD -> KEYWORD_KEYS
            VhdlTokenTypes.LINE_COMMENT -> LINE_COMMENT_KEYS
            VhdlTokenTypes.BLOCK_COMMENT -> BLOCK_COMMENT_KEYS
            VhdlTokenTypes.STRING -> STRING_KEYS
            VhdlTokenTypes.CHAR_LITERAL -> STRING_KEYS
            VhdlTokenTypes.NUMBER -> NUMBER_KEYS
            VhdlTokenTypes.OPERATOR -> OPERATOR_KEYS
            VhdlTokenTypes.LPAREN, VhdlTokenTypes.RPAREN -> PAREN_KEYS
            VhdlTokenTypes.SEMICOLON -> SEMICOLON_KEYS
            VhdlTokenTypes.IDENTIFIER -> IDENTIFIER_KEYS
            TokenType.BAD_CHARACTER -> BAD_CHAR_KEYS
            else -> EMPTY_KEYS
        }
    }

    companion object {
        val KEYWORD: TextAttributesKey =
            createTextAttributesKey("VHDL_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
        val LINE_COMMENT: TextAttributesKey =
            createTextAttributesKey("VHDL_LINE_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
        val BLOCK_COMMENT: TextAttributesKey =
            createTextAttributesKey("VHDL_BLOCK_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT)
        val STRING: TextAttributesKey =
            createTextAttributesKey("VHDL_STRING", DefaultLanguageHighlighterColors.STRING)
        val NUMBER: TextAttributesKey =
            createTextAttributesKey("VHDL_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
        val OPERATOR: TextAttributesKey =
            createTextAttributesKey("VHDL_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)
        val PARENTHESES: TextAttributesKey =
            createTextAttributesKey("VHDL_PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES)
        val SEMICOLON: TextAttributesKey =
            createTextAttributesKey("VHDL_SEMICOLON", DefaultLanguageHighlighterColors.SEMICOLON)
        val IDENTIFIER: TextAttributesKey =
            createTextAttributesKey("VHDL_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER)
        val BAD_CHARACTER: TextAttributesKey =
            createTextAttributesKey("VHDL_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)

        private val KEYWORD_KEYS = arrayOf(KEYWORD)
        private val LINE_COMMENT_KEYS = arrayOf(LINE_COMMENT)
        private val BLOCK_COMMENT_KEYS = arrayOf(BLOCK_COMMENT)
        private val STRING_KEYS = arrayOf(STRING)
        private val NUMBER_KEYS = arrayOf(NUMBER)
        private val OPERATOR_KEYS = arrayOf(OPERATOR)
        private val PAREN_KEYS = arrayOf(PARENTHESES)
        private val SEMICOLON_KEYS = arrayOf(SEMICOLON)
        private val IDENTIFIER_KEYS = arrayOf(IDENTIFIER)
        private val BAD_CHAR_KEYS = arrayOf(BAD_CHARACTER)
        private val EMPTY_KEYS = emptyArray<TextAttributesKey>()
    }
}
