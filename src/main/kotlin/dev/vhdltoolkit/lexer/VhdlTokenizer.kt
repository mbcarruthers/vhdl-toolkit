package dev.vhdltoolkit.lexer

/**
 * Pure-Kotlin VHDL tokenizer with no IntelliJ dependencies, so it is unit-testable
 * headlessly and reusable by later features (smart `end` insertion, indentation).
 *
 * Covers VHDL-2008 lexical rules: line comments, delimited (block) comments,
 * string literals with "" escapes, character literals vs. attribute ticks,
 * extended identifiers, decimal/based literals, and case-insensitive keywords.
 */
object VhdlTokenizer {

    enum class Kind {
        WHITESPACE,
        LINE_COMMENT,
        BLOCK_COMMENT,
        KEYWORD,
        IDENTIFIER,
        STRING,
        CHAR_LITERAL,
        NUMBER,
        OPERATOR,
        LPAREN,
        RPAREN,
        SEMICOLON,
        BAD,
    }

    data class Token(val kind: Kind, val start: Int, val end: Int)

    /** All reserved words of IEEE 1076 up to and including VHDL-2019, lowercase. */
    val KEYWORDS: Set<String> = setOf(
        "abs", "access", "after", "alias", "all", "and", "architecture", "array",
        "assert", "assume", "attribute", "begin", "block", "body", "buffer", "bus",
        "case", "component", "configuration", "constant", "context", "cover",
        "default", "disconnect", "downto", "else", "elsif", "end", "entity", "exit",
        "fairness", "file", "for", "force", "function", "generate", "generic",
        "group", "guarded", "if", "impure", "in", "inertial", "inout", "is",
        "label", "library", "linkage", "literal", "loop", "map", "mod", "nand",
        "new", "next", "nor", "not", "null", "of", "on", "open", "or", "others",
        "out", "package", "parameter", "port", "postponed", "private", "procedure",
        "process", "property", "protected", "pure", "range", "record", "register",
        "reject", "release", "rem", "report", "restrict", "return", "rol", "ror",
        "select", "sequence", "severity", "shared", "signal", "sla", "sll", "sra",
        "srl", "strong", "subtype", "then", "to", "transport", "type", "unaffected",
        "units", "until", "use", "variable", "view", "vmode", "vpkg", "vprop",
        "vunit", "wait", "when", "while", "with", "xnor", "xor",
    )

    /** Scans one token starting at [start]. Always advances at least one char. */
    fun scanToken(text: CharSequence, start: Int): Token? {
        if (start >= text.length) return null
        val c = text[start]

        // Whitespace
        if (c.isWhitespace()) {
            var i = start + 1
            while (i < text.length && text[i].isWhitespace()) i++
            return Token(Kind.WHITESPACE, start, i)
        }

        // Line comment: -- to end of line
        if (c == '-' && start + 1 < text.length && text[start + 1] == '-') {
            var i = start + 2
            while (i < text.length && text[i] != '\n') i++
            return Token(Kind.LINE_COMMENT, start, i)
        }

        // Block comment (VHDL-2008): /* ... */
        if (c == '/' && start + 1 < text.length && text[start + 1] == '*') {
            var i = start + 2
            while (i + 1 < text.length && !(text[i] == '*' && text[i + 1] == '/')) i++
            return if (i + 1 < text.length) {
                Token(Kind.BLOCK_COMMENT, start, i + 2)
            } else {
                Token(Kind.BLOCK_COMMENT, start, text.length) // unterminated
            }
        }

        // String literal: " ... " with "" as escaped quote
        if (c == '"') {
            var i = start + 1
            while (i < text.length) {
                if (text[i] == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { i += 2; continue }
                    return Token(Kind.STRING, start, i + 1)
                }
                if (text[i] == '\n') break // strings do not span lines
                i++
            }
            return Token(Kind.STRING, start, i) // unterminated
        }

        // Character literal 'x' vs. attribute tick (clk'event) vs. qualified ticks.
        if (c == '\'') {
            if (start + 2 < text.length && text[start + 2] == '\'' && text[start + 1] != '\n') {
                return Token(Kind.CHAR_LITERAL, start, start + 3)
            }
            return Token(Kind.OPERATOR, start, start + 1)
        }

        // Extended identifier: \ ... \ with \\ as escaped backslash
        if (c == '\\') {
            var i = start + 1
            while (i < text.length) {
                if (text[i] == '\\') {
                    if (i + 1 < text.length && text[i + 1] == '\\') { i += 2; continue }
                    return Token(Kind.IDENTIFIER, start, i + 1)
                }
                if (text[i] == '\n') break
                i++
            }
            return Token(Kind.BAD, start, i)
        }

        // Numbers: decimal, real, exponents, based literals like 16#FF.8#E2
        if (c.isDigit()) {
            var i = start + 1
            while (i < text.length && (text[i].isDigit() || text[i] == '_')) i++
            // Based literal
            if (i < text.length && text[i] == '#') {
                i++
                while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_' || text[i] == '.')) i++
                if (i < text.length && text[i] == '#') i++
            } else {
                // Fraction
                if (i + 1 < text.length && text[i] == '.' && text[i + 1].isDigit()) {
                    i += 2
                    while (i < text.length && (text[i].isDigit() || text[i] == '_')) i++
                }
            }
            // Exponent
            if (i < text.length && (text[i] == 'e' || text[i] == 'E')) {
                var j = i + 1
                if (j < text.length && (text[j] == '+' || text[j] == '-')) j++
                if (j < text.length && text[j].isDigit()) {
                    i = j + 1
                    while (i < text.length && (text[i].isDigit() || text[i] == '_')) i++
                }
            }
            return Token(Kind.NUMBER, start, i)
        }

        // Identifiers and keywords (basic identifiers: letter, then letter/digit/_)
        if (c.isLetter()) {
            var i = start + 1
            while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_')) i++
            val word = text.subSequence(start, i).toString().lowercase()
            return if (word in KEYWORDS) {
                Token(Kind.KEYWORD, start, i)
            } else {
                Token(Kind.IDENTIFIER, start, i)
            }
        }

        // Structural single chars
        when (c) {
            '(' -> return Token(Kind.LPAREN, start, start + 1)
            ')' -> return Token(Kind.RPAREN, start, start + 1)
            ';' -> return Token(Kind.SEMICOLON, start, start + 1)
        }

        // Multi-character delimiters, longest match first
        for (op in MULTI_CHAR_OPERATORS) {
            if (text.startsWith(op, start)) {
                return Token(Kind.OPERATOR, start, start + op.length)
            }
        }

        if (c in SINGLE_CHAR_OPERATORS) {
            return Token(Kind.OPERATOR, start, start + 1)
        }

        return Token(Kind.BAD, start, start + 1)
    }

    /** Tokenizes the whole text. */
    fun tokenize(text: CharSequence): List<Token> {
        val out = ArrayList<Token>()
        var i = 0
        while (true) {
            val t = scanToken(text, i) ?: break
            out.add(t)
            i = t.end
        }
        return out
    }

    private val MULTI_CHAR_OPERATORS = listOf(
        "?/=", "?<=", "?>=", "**", ":=", "/=", "<=", ">=", "=>", "<>", "<<", ">>", "??", "?=", "?<", "?>",
    )

    private val SINGLE_CHAR_OPERATORS = "+-*/=<>&|.,:?@^![]".toSet()

    private fun CharSequence.startsWith(prefix: String, offset: Int): Boolean {
        if (offset + prefix.length > length) return false
        for (k in prefix.indices) {
            if (this[offset + k] != prefix[k]) return false
        }
        return true
    }
}
