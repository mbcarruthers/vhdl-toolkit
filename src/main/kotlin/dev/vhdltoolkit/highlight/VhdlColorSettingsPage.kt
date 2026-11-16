package dev.vhdltoolkit.highlight

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Settings | Editor | Color Scheme | VHDL — lists every colour the plugin
 * paints, with a live preview, so each one can be customized independently.
 */
class VhdlColorSettingsPage : ColorSettingsPage {

    override fun getIcon(): Icon = IconLoader.getIcon("/icons/vhdl.svg", VhdlColorSettingsPage::class.java)

    override fun getHighlighter(): SyntaxHighlighter = VhdlSyntaxHighlighter()

    override fun getDisplayName(): String = "VHDL"

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = TAGS

    override fun getDemoText(): String = DEMO

    private companion object {
        val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Keyword", VhdlColors.KEYWORD),
            AttributesDescriptor("Identifier", VhdlColors.IDENTIFIER),
            AttributesDescriptor("Comments//Line comment", VhdlColors.LINE_COMMENT),
            AttributesDescriptor("Comments//Block comment", VhdlColors.BLOCK_COMMENT),
            AttributesDescriptor("Literals//String", VhdlColors.STRING),
            AttributesDescriptor("Literals//Character", VhdlColors.CHAR_LITERAL),
            AttributesDescriptor("Literals//Number", VhdlColors.NUMBER),
            AttributesDescriptor("Punctuation//Operator", VhdlColors.OPERATOR),
            AttributesDescriptor("Punctuation//Parentheses", VhdlColors.PARENTHESES),
            AttributesDescriptor("Punctuation//Semicolon", VhdlColors.SEMICOLON),
            AttributesDescriptor("Names//Type", VhdlColors.TYPE),
            AttributesDescriptor("Names//Design unit", VhdlColors.UNIT_NAME),
            AttributesDescriptor("Names//Library", VhdlColors.LIBRARY),
            AttributesDescriptor("Names//Package", VhdlColors.PACKAGE),
            AttributesDescriptor("Names//Subprogram call", VhdlColors.FUNCTION),
            AttributesDescriptor("Names//Label", VhdlColors.LABEL),
            AttributesDescriptor("Names//Declared object", VhdlColors.DECLARED_NAME),
            AttributesDescriptor("Names//Attribute", VhdlColors.ATTRIBUTE),
            AttributesDescriptor("Bad character", VhdlColors.BAD_CHARACTER),
        )

        val TAGS = mapOf(
            "type" to VhdlColors.TYPE,
            "unit" to VhdlColors.UNIT_NAME,
            "lib" to VhdlColors.LIBRARY,
            "pkg" to VhdlColors.PACKAGE,
            "func" to VhdlColors.FUNCTION,
            "label" to VhdlColors.LABEL,
            "decl" to VhdlColors.DECLARED_NAME,
            "attr" to VhdlColors.ATTRIBUTE,
        )

        val DEMO = """
            -- Counter with a synchronous clear.
            library <lib>IEEE</lib>;
            use <lib>IEEE</lib>.<pkg>std_logic_1164</pkg>.all;
            use <lib>IEEE</lib>.<pkg>numeric_std</pkg>.all;

            entity <unit>counter</unit> is
                generic ( <decl>WIDTH</decl> : <type>positive</type> := 8 );
                port (
                    <decl>Clk</decl>   : in  <type>std_logic</type>;
                    <decl>Clear</decl> : in  <type>std_logic</type>;
                    <decl>Q</decl>     : out <type>std_logic_vector</type>(<decl>WIDTH</decl> - 1 downto 0)
                );
            end entity <unit>counter</unit>;

            architecture <unit>RTL</unit> of <unit>counter</unit> is
                type <type>state_t</type> is (idle, running);
                signal <decl>N</decl> : <type>unsigned</type>(<decl>WIDTH</decl> - 1 downto 0) := (others => '0');
            begin
                <label>regs</label> : process (<decl>Clk</decl>)
                begin
                    if <func>rising_edge</func>(<decl>Clk</decl>) then
                        if <decl>Clear</decl>'<attr>stable</attr> and <decl>Clear</decl> = '1' then
                            <decl>N</decl> <= (others => '0');
                        else
                            <decl>N</decl> <= <decl>N</decl> + 1;
                        end if;
                    end if;
                end process;

                <label>u0</label> : entity <lib>work</lib>.<unit>d_flop</unit>
                    port map ( <decl>D</decl> => <decl>Clk</decl>, <decl>Output</decl> => open );

                <decl>Q</decl> <= <type>std_logic_vector</type>(<decl>N</decl>);
            end architecture <unit>RTL</unit>;
        """.trimIndent()
    }
}
