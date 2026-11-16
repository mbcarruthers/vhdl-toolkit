package dev.vhdltoolkit.lexer

import com.intellij.lexer.LexerBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

/** Adapts [VhdlTokenizer] to the IntelliJ lexer interface. */
class VhdlLexer : LexerBase() {

    private var buffer: CharSequence = ""
    private var endOffset: Int = 0
    private var tokenStart: Int = 0
    private var tokenEnd: Int = 0
    private var tokenType: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.endOffset = endOffset
        this.tokenStart = startOffset
        this.tokenEnd = startOffset
        advance()
    }

    override fun getState(): Int = 0

    override fun getTokenType(): IElementType? = tokenType

    override fun getTokenStart(): Int = tokenStart

    override fun getTokenEnd(): Int = tokenEnd

    override fun advance() {
        tokenStart = tokenEnd
        if (tokenStart >= endOffset) {
            tokenType = null
            return
        }
        val token = VhdlTokenizer.scanToken(buffer, tokenStart)
        if (token == null) {
            tokenType = null
            return
        }
        tokenEnd = minOf(token.end, endOffset)
        tokenType = when (token.kind) {
            VhdlTokenizer.Kind.WHITESPACE -> TokenType.WHITE_SPACE
            VhdlTokenizer.Kind.LINE_COMMENT -> VhdlTokenTypes.LINE_COMMENT
            VhdlTokenizer.Kind.BLOCK_COMMENT -> VhdlTokenTypes.BLOCK_COMMENT
            VhdlTokenizer.Kind.KEYWORD -> VhdlTokenTypes.KEYWORD
            VhdlTokenizer.Kind.IDENTIFIER -> VhdlTokenTypes.IDENTIFIER
            VhdlTokenizer.Kind.STRING -> VhdlTokenTypes.STRING
            VhdlTokenizer.Kind.CHAR_LITERAL -> VhdlTokenTypes.CHAR_LITERAL
            VhdlTokenizer.Kind.NUMBER -> VhdlTokenTypes.NUMBER
            VhdlTokenizer.Kind.OPERATOR -> VhdlTokenTypes.OPERATOR
            VhdlTokenizer.Kind.LPAREN -> VhdlTokenTypes.LPAREN
            VhdlTokenizer.Kind.RPAREN -> VhdlTokenTypes.RPAREN
            VhdlTokenizer.Kind.SEMICOLON -> VhdlTokenTypes.SEMICOLON
            VhdlTokenizer.Kind.BAD -> TokenType.BAD_CHARACTER
        }
    }

    override fun getBufferSequence(): CharSequence = buffer

    override fun getBufferEnd(): Int = endOffset
}
