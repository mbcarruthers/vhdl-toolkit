package dev.vhdltoolkit.editing

import dev.vhdltoolkit.lexer.VhdlTokenizer

/**
 * Works out whether an opening parenthesis closes with `)` or `);`.
 *
 * Most parentheses in VHDL are part of an expression, where a semicolon would
 * be wrong. Four are not: an entity or component's `port (` and `generic (`
 * clauses, and an instantiation's `port map (`, all end the statement they
 * belong to.
 *
 * `generic map (` is the exception that makes the rule worth encoding rather
 * than guessing, because it is followed by the `port map` of the same
 * instantiation and so takes no semicolon of its own:
 *
 * ```vhdl
 * u1 : entity work.counter
 *     generic map (WIDTH => 8)
 *     port map (clk => clk);
 * ```
 */
object VhdlParenCompletion {

    /**
     * Whether the parenthesis opened at [openParen] should be closed with
     * `);` rather than `)`.
     *
     * A parenthesis inside a comment or a string literal never qualifies: the
     * tokenizer hands back the whole comment or string as one token, so the
     * words inside it are not seen as keywords.
     */
    fun needsSemicolon(text: CharSequence, openParen: Int): Boolean {
        if (openParen < 0 || openParen >= text.length || text[openParen] != '(') return false

        val words = keywordsBefore(text, openParen, want = 2)
        return when (words.lastOrNull()) {
            "port" -> true
            "generic" -> true
            // `port map (` closes its instantiation; `generic map (` does not.
            "map" -> words.getOrNull(words.size - 2) == "port"
            else -> false
        }
    }

    /**
     * The last [want] word-like tokens before [offset], lower-cased and in
     * source order. Anything that is not a bare identifier or keyword — a
     * comment, a string, an operator — ends the run, because `port` only
     * counts when it is the word immediately to the left.
     */
    private fun keywordsBefore(text: CharSequence, offset: Int, want: Int): List<String> {
        val words = ArrayList<String>(want)
        for (token in VhdlTokenizer.tokenize(text.subSequence(0, offset))) {
            val value = text.subSequence(token.start, token.end).toString()
            when (token.kind) {
                VhdlTokenizer.Kind.KEYWORD, VhdlTokenizer.Kind.IDENTIFIER -> {
                    words += value.lowercase()
                    if (words.size > want) words.removeAt(0)
                }

                VhdlTokenizer.Kind.WHITESPACE,
                VhdlTokenizer.Kind.LINE_COMMENT,
                VhdlTokenizer.Kind.BLOCK_COMMENT,
                -> Unit

                // A string, a number, an operator or a bracket breaks the run
                // of words, so only an adjacent `port` or `generic` counts.
                else -> words.clear()
            }
        }
        return words
    }
}
