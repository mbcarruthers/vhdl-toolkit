package dev.vhdltoolkit.xdc

import dev.vhdltoolkit.xdc.XdcTokenizer.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XdcTokenizerTest {

    private fun kinds(text: String): List<Kind> =
        XdcTokenizer.tokenize(text).map { it.kind }.filter { it != Kind.WHITESPACE }

    private fun kindOf(text: String, word: String): Kind? =
        XdcTokenizer.tokenize(text)
            .firstOrNull { text.substring(it.start, it.end) == word }
            ?.kind

    @Test
    fun commentRunsToEndOfLine() {
        val src = "# Clock signal\nset_property X Y"
        assertEquals(Kind.COMMENT, XdcTokenizer.tokenize(src).first().kind)
        assertEquals(Kind.COMMAND, kindOf(src, "set_property"))
    }

    @Test
    fun constraintCommandsAndPropertiesAreDistinct() {
        val src = "set_property -dict { PACKAGE_PIN W5 IOSTANDARD LVCMOS33 } [get_ports clk]"
        assertEquals(Kind.COMMAND, kindOf(src, "set_property"))
        assertEquals(Kind.COMMAND, kindOf(src, "get_ports"))
        assertEquals(Kind.PROPERTY, kindOf(src, "PACKAGE_PIN"))
        assertEquals(Kind.PROPERTY, kindOf(src, "IOSTANDARD"))
        assertEquals("a pin name is just a name", Kind.IDENTIFIER, kindOf(src, "W5"))
        assertEquals(Kind.IDENTIFIER, kindOf(src, "clk"))
    }

    @Test
    fun optionSwitches() {
        val src = "create_clock -add -name sys_clk_pin -period 10.00 [get_ports clk]"
        assertEquals(Kind.OPTION, kindOf(src, "-add"))
        assertEquals(Kind.OPTION, kindOf(src, "-name"))
        assertEquals(Kind.NUMBER, kindOf(src, "10.00"))
    }

    @Test
    fun negativeNumberIsNotAnOption() {
        assertEquals(listOf(Kind.OPERATOR, Kind.NUMBER), kinds("-5"))
    }

    @Test
    fun variables() {
        assertEquals(Kind.VARIABLE, kindOf("set x \$bank_pins", "\$bank_pins"))
        assertEquals(Kind.VARIABLE, kindOf("puts \${my_var}", "\${my_var}"))
    }

    @Test
    fun bracketsAndBracesAreSeparate() {
        val src = "[get_ports {sw[0]}]"
        val tokens = XdcTokenizer.tokenize(src).filter { it.kind != Kind.WHITESPACE }
        assertTrue(tokens.any { it.kind == Kind.BRACKET })
        assertTrue(tokens.any { it.kind == Kind.BRACE })
    }

    @Test
    fun stringsWithEscapes() {
        val src = """get_ports -filter { DIRECTION == "IN" }"""
        assertEquals(Kind.STRING, kindOf(src, "\"IN\""))
    }

    @Test
    fun unterminatedStringDoesNotLoop() {
        assertEquals(listOf(Kind.COMMAND, Kind.STRING), kinds("""puts "never closed"""))
    }

    @Test
    fun commandsAreCaseInsensitiveAndPropertiesUppercase() {
        assertEquals(Kind.COMMAND, kindOf("SET_PROPERTY a b", "SET_PROPERTY"))
        assertEquals(Kind.PROPERTY, kindOf("set_property package_pin W5", "package_pin"))
    }

    @Test
    fun realConstraintsFileTokenizesLosslessly() {
        val src = """
            ## Clock signal
            set_property -dict { PACKAGE_PIN W5 IOSTANDARD LVCMOS33 } [get_ports clk]
            create_clock -add -name sys_clk_pin -period 10.00 -waveform {0 5} [get_ports clk]

            ## Switches
            set_property PACKAGE_PIN V17 [get_ports {sw[0]}]
            set_property IOSTANDARD LVCMOS33 [get_ports {sw[0]}]

            set_false_path -from [get_clocks sys_clk_pin] -to [get_clocks other_clk]
            set_property CFGBVS VCCO [current_design]
        """.trimIndent()

        val tokens = XdcTokenizer.tokenize(src)
        assertEquals("covers the whole file", src.length, tokens.last().end)
        assertEquals(0, tokens.first().start)
        for (w in tokens.windowed(2)) {
            assertEquals("no gaps between tokens", w[0].end, w[1].start)
        }
    }
}
