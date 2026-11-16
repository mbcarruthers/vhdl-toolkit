package dev.vhdltoolkit.highlight

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import dev.vhdltoolkit.lexer.VhdlSemanticClassifier.Category

/**
 * Every colour this plugin can paint, in one place.
 *
 * Each key inherits from a platform default so it looks right in any theme the
 * user has, while remaining individually overridable under
 * Settings | Editor | Color Scheme | VHDL.
 */
object VhdlColors {

    // --- Lexical ---------------------------------------------------------
    val KEYWORD: TextAttributesKey =
        createTextAttributesKey("VHDL_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
    val IDENTIFIER: TextAttributesKey =
        createTextAttributesKey("VHDL_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER)
    val LINE_COMMENT: TextAttributesKey =
        createTextAttributesKey("VHDL_LINE_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
    val BLOCK_COMMENT: TextAttributesKey =
        createTextAttributesKey("VHDL_BLOCK_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT)
    val STRING: TextAttributesKey =
        createTextAttributesKey("VHDL_STRING", DefaultLanguageHighlighterColors.STRING)
    val CHAR_LITERAL: TextAttributesKey =
        createTextAttributesKey("VHDL_CHAR_LITERAL", DefaultLanguageHighlighterColors.NUMBER)
    val NUMBER: TextAttributesKey =
        createTextAttributesKey("VHDL_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
    val OPERATOR: TextAttributesKey =
        createTextAttributesKey("VHDL_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)
    val PARENTHESES: TextAttributesKey =
        createTextAttributesKey("VHDL_PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES)
    val SEMICOLON: TextAttributesKey =
        createTextAttributesKey("VHDL_SEMICOLON", DefaultLanguageHighlighterColors.SEMICOLON)
    val BAD_CHARACTER: TextAttributesKey =
        createTextAttributesKey("VHDL_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)

    // --- Semantic --------------------------------------------------------
    val TYPE: TextAttributesKey =
        createTextAttributesKey("VHDL_TYPE", DefaultLanguageHighlighterColors.CLASS_NAME)
    val UNIT_NAME: TextAttributesKey =
        createTextAttributesKey("VHDL_UNIT_NAME", DefaultLanguageHighlighterColors.INTERFACE_NAME)
    val LIBRARY: TextAttributesKey =
        createTextAttributesKey("VHDL_LIBRARY", DefaultLanguageHighlighterColors.CLASS_REFERENCE)
    val PACKAGE: TextAttributesKey =
        createTextAttributesKey("VHDL_PACKAGE", DefaultLanguageHighlighterColors.STATIC_FIELD)
    val FUNCTION: TextAttributesKey =
        createTextAttributesKey("VHDL_FUNCTION", DefaultLanguageHighlighterColors.FUNCTION_CALL)
    val LABEL: TextAttributesKey =
        createTextAttributesKey("VHDL_LABEL", DefaultLanguageHighlighterColors.LABEL)
    val DECLARED_NAME: TextAttributesKey =
        createTextAttributesKey("VHDL_DECLARED_NAME", DefaultLanguageHighlighterColors.INSTANCE_FIELD)
    val ATTRIBUTE: TextAttributesKey =
        createTextAttributesKey("VHDL_ATTRIBUTE", DefaultLanguageHighlighterColors.METADATA)

    fun forCategory(category: Category): TextAttributesKey = when (category) {
        Category.TYPE -> TYPE
        Category.UNIT_NAME -> UNIT_NAME
        Category.LIBRARY -> LIBRARY
        Category.PACKAGE -> PACKAGE
        Category.FUNCTION -> FUNCTION
        Category.LABEL -> LABEL
        Category.DECLARED_NAME -> DECLARED_NAME
        Category.ATTRIBUTE -> ATTRIBUTE
    }
}
