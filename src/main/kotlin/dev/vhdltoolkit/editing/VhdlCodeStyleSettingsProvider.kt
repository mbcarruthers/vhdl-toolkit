package dev.vhdltoolkit.editing

import com.intellij.application.options.IndentOptionsEditor
import com.intellij.application.options.SmartIndentOptionsEditor
import com.intellij.lang.Language
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider
import dev.vhdltoolkit.VhdlLanguage

/**
 * Gives VHDL a page under Settings | Editor | Code Style.
 *
 * Beyond letting indent size and tabs be chosen, registering this is what
 * makes `.editorconfig` apply to VHDL files: the platform reads EditorConfig
 * into the same indent options, and without a provider there is nothing for
 * it to populate.
 */
class VhdlCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {

    override fun getLanguage(): Language = VhdlLanguage

    override fun getIndentOptionsEditor(): IndentOptionsEditor = SmartIndentOptionsEditor()

    override fun customizeDefaults(
        commonSettings: CommonCodeStyleSettings,
        indentOptions: CommonCodeStyleSettings.IndentOptions,
    ) {
        indentOptions.INDENT_SIZE = 4
        indentOptions.CONTINUATION_INDENT_SIZE = 4
        indentOptions.TAB_SIZE = 4
        indentOptions.USE_TAB_CHARACTER = false
    }

    override fun customizeSettings(
        consumer: CodeStyleSettingsCustomizable,
        settingsType: SettingsType,
    ) {
        if (settingsType == SettingsType.INDENT_SETTINGS) {
            consumer.showStandardOptions("INDENT_SIZE", "CONTINUATION_INDENT_SIZE", "TAB_SIZE", "USE_TAB_CHARACTER")
        }
    }

    override fun getCodeSample(settingsType: SettingsType): String = SAMPLE

    private companion object {
        val SAMPLE = """
            library ieee;
            use ieee.std_logic_1164.all;

            entity counter is
                generic (
                    WIDTH : positive := 8
                );
                port (
                    clk   : in  std_logic;
                    reset : in  std_logic;
                    q     : out std_logic_vector(WIDTH - 1 downto 0)
                );
            end entity counter;

            architecture rtl of counter is
                signal count : unsigned(WIDTH - 1 downto 0);
            begin
                regs : process (clk)
                begin
                    if rising_edge(clk) then
                        if reset = '1' then
                            count <= (others => '0');
                        else
                            count <= count + 1;
                        end if;
                    end if;
                end process regs;

                q <= std_logic_vector(count);
            end architecture rtl;
        """.trimIndent()
    }
}
