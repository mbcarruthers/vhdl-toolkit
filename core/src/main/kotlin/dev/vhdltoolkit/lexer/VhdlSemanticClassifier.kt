package dev.vhdltoolkit.lexer

/**
 * Context-aware classification of VHDL identifiers, built on [VhdlTokenizer].
 *
 * A lexer alone can only separate keywords from identifiers, which leaves every
 * meaningful name — types, libraries, entity names, ports, labels — rendered
 * identically. This pass looks at each identifier's neighbours in the token
 * stream and assigns a finer category, without needing a full parser.
 *
 * It is deliberately conservative: a rule fires only when the surrounding
 * tokens make the meaning unambiguous. Anything uncertain stays a plain
 * identifier rather than risking a wrong colour.
 */
object VhdlSemanticClassifier {

    enum class Category {
        /** A type mark: `std_logic`, `unsigned`, or a user type in declaration position. */
        TYPE,

        /** Name of a design unit being declared or referenced: entity, architecture, package. */
        UNIT_NAME,

        /** Library name: `ieee` in `library ieee;` or `work` in `entity work.foo`. */
        LIBRARY,

        /** Package name: `std_logic_1164` in `use ieee.std_logic_1164.all`. */
        PACKAGE,

        /** Call of a standard subprogram: `rising_edge(clk)`. */
        FUNCTION,

        /** Statement label: `b0` in `b0 : entity work.d_flop`. */
        LABEL,

        /** A name being declared: port, signal, variable, constant, or a formal in a map. */
        DECLARED_NAME,

        /** Attribute after a tick: `event` in `clk'event`. */
        ATTRIBUTE,
    }

    data class Span(val start: Int, val end: Int, val category: Category)

    /** Predefined types from STD.STANDARD and the common IEEE packages. */
    val STANDARD_TYPES: Set<String> = setOf(
        // STD.STANDARD
        "boolean", "bit", "character", "severity_level", "integer", "real", "time",
        "delay_length", "natural", "positive", "string", "bit_vector", "file_open_kind",
        "file_open_status", "boolean_vector", "integer_vector", "real_vector", "time_vector",
        // IEEE.STD_LOGIC_1164
        "std_ulogic", "std_ulogic_vector", "std_logic", "std_logic_vector",
        // IEEE.NUMERIC_STD
        "unsigned", "signed",
        // IEEE.FIXED/FLOAT
        "ufixed", "sfixed", "float", "float32", "float64", "float128",
        // TEXTIO
        "line", "text", "side", "width",
    )

    /** Predefined subprograms worth distinguishing from ordinary names. */
    val STANDARD_FUNCTIONS: Set<String> = setOf(
        "rising_edge", "falling_edge", "to_integer", "to_unsigned", "to_signed",
        "to_stdlogicvector", "to_stdulogicvector", "to_bitvector", "to_bit",
        "to_x01", "to_x01z", "to_ux01", "to_01", "is_x", "std_match", "resize",
        "shift_left", "shift_right", "rotate_left", "rotate_right",
        "maximum", "minimum", "now", "readline", "writeline", "read", "write",
        "hread", "hwrite", "endfile", "deallocate", "image", "value",
    )

    /** Port/parameter modes, which sit between `:` and the type mark. */
    private val MODES = setOf("in", "out", "inout", "buffer", "linkage")

    /** Keywords that introduce a design unit name. */
    private val UNIT_INTRODUCERS = setOf(
        "entity", "architecture", "package", "component", "configuration", "context",
    )

    /** Keywords that introduce a declared object name. */
    private val OBJECT_INTRODUCERS = setOf(
        "signal", "variable", "constant", "file", "alias", "attribute",
    )

    /** Keywords that may follow a label's colon. */
    private val LABELLED_STATEMENTS = setOf(
        "entity", "component", "configuration", "process", "block", "for", "if",
        "case", "while", "loop", "postponed", "assert", "with", "generate",
    )

    private data class Tok(
        val kind: VhdlTokenizer.Kind,
        val start: Int,
        val end: Int,
        val lower: String,
    )

    /**
     * Returns spans for identifiers whose role is determinable from context.
     * Spans never overlap and are ordered by position.
     */
    fun classify(text: CharSequence): List<Span> {
        val toks = significantTokens(text)
        val out = ArrayList<Span>()

        for (i in toks.indices) {
            val t = toks[i]
            if (t.kind != VhdlTokenizer.Kind.IDENTIFIER) continue

            val category = categoryOf(toks, i) ?: continue
            out.add(Span(t.start, t.end, category))
        }
        return out
    }

    private fun categoryOf(toks: List<Tok>, i: Int): Category? {
        val prev = toks.getOrNull(i - 1)
        val next = toks.getOrNull(i + 1)
        val lower = toks[i].lower

        // clk'event — attribute after a tick.
        if (prev != null && prev.kind == VhdlTokenizer.Kind.OPERATOR && prev.lower == "'") {
            return Category.ATTRIBUTE
        }

        // Dotted selected name: resolve from what preceded the dot.
        if (prev != null && prev.kind == VhdlTokenizer.Kind.OPERATOR && prev.lower == ".") {
            val owner = toks.getOrNull(i - 2) ?: return null
            if (owner.kind != VhdlTokenizer.Kind.IDENTIFIER) return null
            return when (categoryOf(toks, i - 2)) {
                // ieee.std_logic_1164 → the second element is the package.
                Category.LIBRARY -> if (isInUseClause(toks, i)) Category.PACKAGE else Category.UNIT_NAME
                else -> null
            }
        }

        // library ieee;  /  use ieee...
        if (prev != null && prev.kind == VhdlTokenizer.Kind.KEYWORD &&
            (prev.lower == "library" || prev.lower == "use")
        ) {
            return Category.LIBRARY
        }

        // entity foo / architecture rtl / package pkg …
        if (prev != null && prev.kind == VhdlTokenizer.Kind.KEYWORD && prev.lower in UNIT_INTRODUCERS) {
            // `entity work.d_flop` — a dotted reference means this is the library.
            return if (next != null && next.kind == VhdlTokenizer.Kind.OPERATOR && next.lower == ".") {
                Category.LIBRARY
            } else {
                Category.UNIT_NAME
            }
        }

        // architecture rtl of d_flop — the entity being implemented.
        if (prev != null && prev.kind == VhdlTokenizer.Kind.KEYWORD && prev.lower == "of") {
            return Category.UNIT_NAME
        }

        // type t is … / subtype s is …
        if (prev != null && prev.kind == VhdlTokenizer.Kind.KEYWORD &&
            (prev.lower == "type" || prev.lower == "subtype")
        ) {
            return Category.TYPE
        }

        // signal n : … / variable v : … / constant c : …
        if (prev != null && prev.kind == VhdlTokenizer.Kind.KEYWORD && prev.lower in OBJECT_INTRODUCERS) {
            return Category.DECLARED_NAME
        }

        // Predefined type marks anywhere.
        if (lower in STANDARD_TYPES) return Category.TYPE

        // rising_edge(clk) — a known subprogram applied to arguments.
        if (lower in STANDARD_FUNCTIONS &&
            next != null && next.kind == VhdlTokenizer.Kind.LPAREN
        ) {
            return Category.FUNCTION
        }

        // Type position: `: std_logic`, or `: in std_logic` with a mode between.
        if (prev != null && isTypePosition(toks, i)) return Category.TYPE

        // `name :` — either a label or a declared object, decided by what follows.
        if (next != null && next.kind == VhdlTokenizer.Kind.OPERATOR && next.lower == ":") {
            val after = toks.getOrNull(i + 2) ?: return Category.DECLARED_NAME
            return if (after.kind == VhdlTokenizer.Kind.KEYWORD && after.lower in LABELLED_STATEMENTS) {
                Category.LABEL
            } else {
                Category.DECLARED_NAME
            }
        }

        // Formal in an association list: `D => ...`
        if (next != null && next.kind == VhdlTokenizer.Kind.OPERATOR && next.lower == "=>") {
            val before = prev ?: return null
            val opensList = before.kind == VhdlTokenizer.Kind.LPAREN ||
                (before.kind == VhdlTokenizer.Kind.OPERATOR && before.lower == ",")
            if (opensList) return Category.DECLARED_NAME
        }

        return null
    }

    /**
     * True when this identifier sits where a type mark belongs: directly after a
     * `:`, or after a `:` plus a port mode.
     */
    private fun isTypePosition(toks: List<Tok>, i: Int): Boolean {
        val prev = toks.getOrNull(i - 1) ?: return false
        if (prev.kind == VhdlTokenizer.Kind.OPERATOR && prev.lower == ":") return true
        if (prev.kind == VhdlTokenizer.Kind.KEYWORD && prev.lower in MODES) {
            val beforeMode = toks.getOrNull(i - 2) ?: return false
            return beforeMode.kind == VhdlTokenizer.Kind.OPERATOR && beforeMode.lower == ":"
        }
        return false
    }

    /** Walks back from [i] to see whether we are inside a `use` clause on this statement. */
    private fun isInUseClause(toks: List<Tok>, i: Int): Boolean {
        var j = i - 1
        while (j >= 0) {
            val t = toks[j]
            if (t.kind == VhdlTokenizer.Kind.SEMICOLON) return false
            if (t.kind == VhdlTokenizer.Kind.KEYWORD) {
                if (t.lower == "use") return true
                if (t.lower == "entity" || t.lower == "component" || t.lower == "configuration") return false
            }
            j--
        }
        return false
    }

    private fun significantTokens(text: CharSequence): List<Tok> {
        val out = ArrayList<Tok>()
        for (t in VhdlTokenizer.tokenize(text)) {
            when (t.kind) {
                VhdlTokenizer.Kind.WHITESPACE,
                VhdlTokenizer.Kind.LINE_COMMENT,
                VhdlTokenizer.Kind.BLOCK_COMMENT,
                -> continue
                else -> out.add(
                    Tok(t.kind, t.start, t.end, text.subSequence(t.start, t.end).toString().lowercase()),
                )
            }
        }
        return out
    }
}
