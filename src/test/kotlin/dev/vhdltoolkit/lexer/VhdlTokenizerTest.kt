package dev.vhdltoolkit.lexer

import dev.vhdltoolkit.lexer.VhdlTokenizer.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VhdlTokenizerTest {

    private fun kinds(text: String): List<Kind> =
        VhdlTokenizer.tokenize(text).map { it.kind }.filter { it != Kind.WHITESPACE }

    private fun texts(text: String): List<String> =
        VhdlTokenizer.tokenize(text)
            .filter { it.kind != Kind.WHITESPACE }
            .map { text.substring(it.start, it.end) }

    @Test
    fun keywordsAreCaseInsensitive() {
        assertEquals(
            listOf(Kind.KEYWORD, Kind.KEYWORD, Kind.KEYWORD),
            kinds("ENTITY Entity entity"),
        )
    }

    @Test
    fun identifiersVsKeywords() {
        assertEquals(
            listOf(Kind.KEYWORD, Kind.IDENTIFIER, Kind.KEYWORD),
            kinds("entity my_entity is"),
        )
    }

    @Test
    fun lineComment() {
        val tokens = VhdlTokenizer.tokenize("-- if x then\nsignal")
        assertEquals(Kind.LINE_COMMENT, tokens[0].kind)
        assertEquals("-- if x then", "-- if x then\nsignal".substring(tokens[0].start, tokens[0].end))
        assertTrue(tokens.any { it.kind == Kind.KEYWORD })
    }

    @Test
    fun blockComment() {
        assertEquals(listOf(Kind.BLOCK_COMMENT, Kind.KEYWORD), kinds("/* if then else */ signal"))
    }

    @Test
    fun unterminatedBlockCommentDoesNotLoop() {
        assertEquals(listOf(Kind.BLOCK_COMMENT), kinds("/* never closed"))
    }

    @Test
    fun stringWithEscapedQuotes() {
        val src = """x <= "ab""cd";"""
        val t = texts(src)
        assertTrue(t.contains("\"ab\"\"cd\""))
    }

    @Test
    fun charLiteralVsAttributeTick() {
        assertEquals(
            listOf(Kind.IDENTIFIER, Kind.OPERATOR, Kind.CHAR_LITERAL, Kind.SEMICOLON),
            kinds("q <= '1';"),
        )
        // clk'event: tick must be an operator, not the start of a char literal
        assertEquals(
            listOf(Kind.IDENTIFIER, Kind.OPERATOR, Kind.IDENTIFIER),
            kinds("clk'event"),
        )
    }

    @Test
    fun numbers() {
        assertEquals(listOf(Kind.NUMBER), kinds("1_000"))
        assertEquals(listOf(Kind.NUMBER), kinds("3.14"))
        assertEquals(listOf(Kind.NUMBER), kinds("1.0e-9"))
        assertEquals(listOf(Kind.NUMBER), kinds("16#FF#"))
        assertEquals(listOf(Kind.NUMBER), kinds("2#1010_1010#"))
    }

    @Test
    fun extendedIdentifier() {
        assertEquals(listOf(Kind.IDENTIFIER), kinds("""\weird name\"""))
    }

    @Test
    fun operators() {
        assertEquals(
            listOf(Kind.IDENTIFIER, Kind.OPERATOR, Kind.IDENTIFIER, Kind.SEMICOLON),
            kinds("a <= b;"),
        )
        assertEquals(
            listOf(Kind.IDENTIFIER, Kind.OPERATOR, Kind.NUMBER, Kind.SEMICOLON),
            kinds("v := 7;"),
        )
        assertEquals(
            listOf(Kind.KEYWORD, Kind.IDENTIFIER, Kind.OPERATOR, Kind.IDENTIFIER),
            kinds("when others => null_x"),
        )
    }

    @Test
    fun parensAndSemicolons() {
        assertEquals(
            listOf(
                Kind.IDENTIFIER, Kind.LPAREN, Kind.NUMBER, Kind.KEYWORD, Kind.NUMBER,
                Kind.RPAREN, Kind.SEMICOLON,
            ),
            kinds("v(7 downto 0);"),
        )
    }

    @Test
    fun everyInputTerminates() {
        // Fuzz-ish: tokenizer must always advance on arbitrary input.
        val nasty = "\\'\"#\u0000@` \n\t\\x\\ '''\"\"16## /*/ --"
        val tokens = VhdlTokenizer.tokenize(nasty)
        assertTrue(tokens.isNotEmpty())
        assertEquals(nasty.length, tokens.last().end)
        for (w in tokens.windowed(2)) {
            assertEquals(w[0].end, w[1].start)
        }
    }

    @Test
    fun fullArchitectureSnippet() {
        val src = """
            library ieee;
            use ieee.std_logic_1164.all;

            architecture rtl of counter is
                signal count : unsigned(7 downto 0) := (others => '0');
            begin
                process (clk)
                begin
                    if rising_edge(clk) then
                        count <= count + 1;
                    end if;
                end process;
            end architecture rtl;
        """.trimIndent()
        val tokens = VhdlTokenizer.tokenize(src)
        // Lossless tokenization
        assertEquals(src.length, tokens.last().end)
        assertEquals(0, tokens.first().start)
        for (w in tokens.windowed(2)) {
            assertEquals(w[0].end, w[1].start)
        }
        // No BAD tokens in valid code
        assertTrue(tokens.none { it.kind == Kind.BAD })
    }
}
