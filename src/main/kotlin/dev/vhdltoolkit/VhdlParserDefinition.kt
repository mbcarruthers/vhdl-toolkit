package dev.vhdltoolkit

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lexer.Lexer
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import dev.vhdltoolkit.lexer.VhdlLexer
import dev.vhdltoolkit.lexer.VhdlTokenTypes

class VhdlFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, VhdlLanguage) {
    override fun getFileType(): FileType = VhdlFileType
    override fun toString(): String = "VHDL file"
}

/**
 * Minimal parser definition: produces a flat PSI tree (all tokens as leaves of
 * the file node). Semantic understanding comes from VHDL-LS over LSP; this
 * exists so the platform has a proper lexer-backed file, which gives us
 * syntax highlighting, comment/uncomment, and brace matching.
 */
class VhdlParserDefinition : ParserDefinition {

    override fun createLexer(project: Project?): Lexer = VhdlLexer()

    override fun createParser(project: Project?): PsiParser = PsiParser { root: IElementType, builder: PsiBuilder ->
        val rootMarker = builder.mark()
        while (!builder.eof()) {
            builder.advanceLexer()
        }
        rootMarker.done(root)
        builder.treeBuilt
    }

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getCommentTokens(): TokenSet = VhdlTokenTypes.COMMENTS

    override fun getStringLiteralElements(): TokenSet = VhdlTokenTypes.STRINGS

    override fun createElement(node: ASTNode): PsiElement = ASTWrapperPsiElement(node)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = VhdlFile(viewProvider)

    companion object {
        val FILE = IFileElementType(VhdlLanguage)
    }
}
