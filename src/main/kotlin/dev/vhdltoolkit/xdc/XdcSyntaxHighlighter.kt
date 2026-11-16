package dev.vhdltoolkit.xdc

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType
import javax.swing.Icon

object XdcColors {
    val COMMENT: TextAttributesKey =
        createTextAttributesKey("XDC_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
    val COMMAND: TextAttributesKey =
        createTextAttributesKey("XDC_COMMAND", DefaultLanguageHighlighterColors.KEYWORD)
    val PROPERTY: TextAttributesKey =
        createTextAttributesKey("XDC_PROPERTY", DefaultLanguageHighlighterColors.CLASS_NAME)
    val OPTION: TextAttributesKey =
        createTextAttributesKey("XDC_OPTION", DefaultLanguageHighlighterColors.METADATA)
    val VARIABLE: TextAttributesKey =
        createTextAttributesKey("XDC_VARIABLE", DefaultLanguageHighlighterColors.INSTANCE_FIELD)
    val STRING: TextAttributesKey =
        createTextAttributesKey("XDC_STRING", DefaultLanguageHighlighterColors.STRING)
    val NUMBER: TextAttributesKey =
        createTextAttributesKey("XDC_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
    val BRACKET: TextAttributesKey =
        createTextAttributesKey("XDC_BRACKET", DefaultLanguageHighlighterColors.BRACKETS)
    val BRACE: TextAttributesKey =
        createTextAttributesKey("XDC_BRACE", DefaultLanguageHighlighterColors.BRACES)
    val IDENTIFIER: TextAttributesKey =
        createTextAttributesKey("XDC_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER)
    val OPERATOR: TextAttributesKey =
        createTextAttributesKey("XDC_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)
}

class XdcSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer(): Lexer = XdcLexer()

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> {
        val key = when (tokenType) {
            XdcTokenTypes.COMMENT -> XdcColors.COMMENT
            XdcTokenTypes.COMMAND -> XdcColors.COMMAND
            XdcTokenTypes.PROPERTY -> XdcColors.PROPERTY
            XdcTokenTypes.OPTION -> XdcColors.OPTION
            XdcTokenTypes.VARIABLE -> XdcColors.VARIABLE
            XdcTokenTypes.STRING -> XdcColors.STRING
            XdcTokenTypes.NUMBER -> XdcColors.NUMBER
            XdcTokenTypes.BRACKET -> XdcColors.BRACKET
            XdcTokenTypes.BRACE -> XdcColors.BRACE
            XdcTokenTypes.IDENTIFIER -> XdcColors.IDENTIFIER
            XdcTokenTypes.OPERATOR -> XdcColors.OPERATOR
            else -> return emptyArray()
        }
        return arrayOf(key)
    }
}

class XdcSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter =
        XdcSyntaxHighlighter()
}

class XdcColorSettingsPage : com.intellij.openapi.options.colors.ColorSettingsPage {

    override fun getIcon(): Icon = IconLoader.getIcon("/icons/xdc.svg", XdcColorSettingsPage::class.java)
    override fun getHighlighter(): SyntaxHighlighter = XdcSyntaxHighlighter()
    override fun getDisplayName(): String = "XDC constraints"
    override fun getColorDescriptors(): Array<com.intellij.openapi.options.colors.ColorDescriptor> =
        com.intellij.openapi.options.colors.ColorDescriptor.EMPTY_ARRAY

    override fun getAttributeDescriptors() = arrayOf(
        com.intellij.openapi.options.colors.AttributesDescriptor("Comment", XdcColors.COMMENT),
        com.intellij.openapi.options.colors.AttributesDescriptor("Command", XdcColors.COMMAND),
        com.intellij.openapi.options.colors.AttributesDescriptor("Property", XdcColors.PROPERTY),
        com.intellij.openapi.options.colors.AttributesDescriptor("Option switch", XdcColors.OPTION),
        com.intellij.openapi.options.colors.AttributesDescriptor("Variable", XdcColors.VARIABLE),
        com.intellij.openapi.options.colors.AttributesDescriptor("String", XdcColors.STRING),
        com.intellij.openapi.options.colors.AttributesDescriptor("Number", XdcColors.NUMBER),
        com.intellij.openapi.options.colors.AttributesDescriptor("Brackets", XdcColors.BRACKET),
        com.intellij.openapi.options.colors.AttributesDescriptor("Braces", XdcColors.BRACE),
        com.intellij.openapi.options.colors.AttributesDescriptor("Identifier", XdcColors.IDENTIFIER),
    )

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null

    override fun getDemoText(): String = """
        # Clock signal
        set_property -dict { PACKAGE_PIN W5   IOSTANDARD LVCMOS33 } [get_ports clk]
        create_clock -add -name sys_clk_pin -period 10.00 -waveform {0 5} [get_ports clk]

        # Switches
        set_property PACKAGE_PIN V17 [get_ports {sw[0]}]
        set_property IOSTANDARD LVCMOS33 [get_ports {sw[0]}]

        # Timing exceptions
        set_false_path -from [get_clocks sys_clk_pin] -to [get_clocks other_clk]
        set_max_delay -datapath_only 5.0 -from [get_cells src_reg] -to [get_cells dst_reg]

        set bank_pins [get_ports -filter { DIRECTION == "IN" }]
        set_property CFGBVS VCCO [current_design]
    """.trimIndent()
}
