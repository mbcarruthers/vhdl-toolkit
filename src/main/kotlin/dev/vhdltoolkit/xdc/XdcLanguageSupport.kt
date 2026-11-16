package dev.vhdltoolkit.xdc

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.lang.Commenter
import com.intellij.lang.Language
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.lexer.LexerBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import javax.swing.Icon

object XdcLanguage : Language("XDC") {
    private fun readResolve(): Any = XdcLanguage
    override fun getDisplayName(): String = "XDC constraints"
}

object XdcFileType : LanguageFileType(XdcLanguage) {
    override fun getName(): String = "XDC"
    override fun getDescription(): String = "Xilinx design constraints"
    override fun getDefaultExtension(): String = "xdc"
    override fun getIcon(): Icon = IconLoader.getIcon("/icons/xdc.svg", XdcFileType::class.java)
}

class XdcElementType(debugName: String) : IElementType(debugName, XdcLanguage)

object XdcTokenTypes {
    @JvmField val COMMENT = XdcElementType("COMMENT")
    @JvmField val COMMAND = XdcElementType("COMMAND")
    @JvmField val PROPERTY = XdcElementType("PROPERTY")
    @JvmField val OPTION = XdcElementType("OPTION")
    @JvmField val VARIABLE = XdcElementType("VARIABLE")
    @JvmField val STRING = XdcElementType("STRING")
    @JvmField val NUMBER = XdcElementType("NUMBER")
    @JvmField val BRACKET = XdcElementType("BRACKET")
    @JvmField val BRACE = XdcElementType("BRACE")
    @JvmField val IDENTIFIER = XdcElementType("IDENTIFIER")
    @JvmField val OPERATOR = XdcElementType("OPERATOR")

    @JvmField val COMMENTS: TokenSet = TokenSet.create(COMMENT)
    @JvmField val STRINGS: TokenSet = TokenSet.create(STRING)
}

class XdcLexer : LexerBase() {
    private var buffer: CharSequence = ""
    private var endOffset = 0
    private var tokenStart = 0
    private var tokenEnd = 0
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
    override fun getBufferSequence(): CharSequence = buffer
    override fun getBufferEnd(): Int = endOffset

    override fun advance() {
        tokenStart = tokenEnd
        if (tokenStart >= endOffset) {
            tokenType = null
            return
        }
        val token = XdcTokenizer.scanToken(buffer, tokenStart)
        if (token == null) {
            tokenType = null
            return
        }
        tokenEnd = minOf(token.end, endOffset)
        tokenType = when (token.kind) {
            XdcTokenizer.Kind.WHITESPACE -> TokenType.WHITE_SPACE
            XdcTokenizer.Kind.COMMENT -> XdcTokenTypes.COMMENT
            XdcTokenizer.Kind.COMMAND -> XdcTokenTypes.COMMAND
            XdcTokenizer.Kind.PROPERTY -> XdcTokenTypes.PROPERTY
            XdcTokenizer.Kind.OPTION -> XdcTokenTypes.OPTION
            XdcTokenizer.Kind.VARIABLE -> XdcTokenTypes.VARIABLE
            XdcTokenizer.Kind.STRING -> XdcTokenTypes.STRING
            XdcTokenizer.Kind.NUMBER -> XdcTokenTypes.NUMBER
            XdcTokenizer.Kind.BRACKET -> XdcTokenTypes.BRACKET
            XdcTokenizer.Kind.BRACE -> XdcTokenTypes.BRACE
            XdcTokenizer.Kind.IDENTIFIER -> XdcTokenTypes.IDENTIFIER
            XdcTokenizer.Kind.OPERATOR -> XdcTokenTypes.OPERATOR
        }
    }
}

class XdcFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, XdcLanguage) {
    override fun getFileType(): FileType = XdcFileType
    override fun toString(): String = "XDC file"
}

class XdcParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?): Lexer = XdcLexer()

    override fun createParser(project: Project?): PsiParser = PsiParser { root: IElementType, builder: PsiBuilder ->
        val marker = builder.mark()
        while (!builder.eof()) builder.advanceLexer()
        marker.done(root)
        builder.treeBuilt
    }

    override fun getFileNodeType(): IFileElementType = FILE
    override fun getCommentTokens(): TokenSet = XdcTokenTypes.COMMENTS
    override fun getStringLiteralElements(): TokenSet = XdcTokenTypes.STRINGS
    override fun createElement(node: ASTNode): PsiElement = ASTWrapperPsiElement(node)
    override fun createFile(viewProvider: FileViewProvider): PsiFile = XdcFile(viewProvider)

    companion object {
        val FILE = IFileElementType(XdcLanguage)
    }
}

class XdcCommenter : Commenter {
    override fun getLineCommentPrefix(): String = "# "
    override fun getBlockCommentPrefix(): String? = null
    override fun getBlockCommentSuffix(): String? = null
    override fun getCommentedBlockCommentPrefix(): String? = null
    override fun getCommentedBlockCommentSuffix(): String? = null
}
