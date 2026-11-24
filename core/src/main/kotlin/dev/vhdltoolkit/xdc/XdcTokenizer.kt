package dev.vhdltoolkit.xdc

/**
 * Tokenizer for Xilinx Design Constraints files.
 *
 * XDC is Tcl, so the lexical rules are Tcl's: `#` comments, `$` variable
 * references, `[...]` command substitution, `{...}` literal quoting, and
 * `-switch` options. On top of that it recognises the constraint commands and
 * the property names that make up almost every real constraints file, since
 * telling `set_property` from `PACKAGE_PIN` from a pin name is what makes
 * these files readable.
 */
object XdcTokenizer {

    enum class Kind {
        WHITESPACE,
        COMMENT,
        COMMAND,
        PROPERTY,
        OPTION,
        VARIABLE,
        STRING,
        NUMBER,
        BRACKET,
        BRACE,
        IDENTIFIER,
        OPERATOR,
    }

    data class Token(val kind: Kind, val start: Int, val end: Int)

    /** Constraint and Tcl commands that appear at the head of a statement. */
    val COMMANDS: Set<String> = setOf(
        // Physical
        "set_property", "get_ports", "get_pins", "get_nets", "get_cells", "get_clocks",
        "get_iobanks", "get_sites", "get_tiles", "get_bels", "get_timing_paths", "get_debug_cores",
        // Timing
        "create_clock", "create_generated_clock", "set_input_delay", "set_output_delay",
        "set_clock_groups", "set_false_path", "set_multicycle_path", "set_max_delay",
        "set_min_delay", "set_disable_timing", "set_case_analysis", "set_clock_latency",
        "set_clock_uncertainty", "set_data_check", "set_bus_skew", "set_system_jitter",
        "set_input_jitter", "set_external_delay", "set_load", "set_operating_conditions",
        "set_switching_activity", "set_units", "group_path",
        // Collections
        "all_inputs", "all_outputs", "all_clocks", "all_registers", "all_ffs", "all_latches",
        "all_rams", "all_dsps", "all_hsios", "current_design", "current_instance", "filter",
        // Floorplanning and debug
        "create_pblock", "add_cells_to_pblock", "resize_pblock", "delete_pblock",
        "create_debug_core", "create_debug_port", "connect_debug_port", "set_debug_port",
        "create_waiver",
        // Tcl
        "set", "unset", "if", "else", "elseif", "foreach", "for", "while", "proc", "return",
        "expr", "list", "lappend", "lindex", "llength", "lsort", "string", "regexp", "regsub",
        "puts", "catch", "error", "source", "concat", "join", "split", "format", "incr",
    )

    /** Property names used with `set_property`. */
    val PROPERTIES: Set<String> = setOf(
        "PACKAGE_PIN", "IOSTANDARD", "SLEW", "DRIVE", "PULLUP", "PULLDOWN", "PULLTYPE",
        "IN_TERM", "OUT_TERM", "OFFCHIP_TERM", "IOB", "KEEPER", "DCI_CASCADE",
        "INTERNAL_VREF", "CLOCK_DEDICATED_ROUTE", "CFGBVS", "CONFIG_VOLTAGE",
        "CONFIG_MODE", "BITSTREAM", "LOC", "BEL", "PBLOCK", "DONT_TOUCH", "MARK_DEBUG",
        "ASYNC_REG", "CLOCK_BUFFER_TYPE", "SEVERITY", "IS_ENABLED", "CONTAIN_ROUTING",
        "EXCLUDE_PLACEMENT", "HD_PARTPIN_RANGE", "USER_CLOCK_ROOT", "PROHIBIT",
    )

    fun scanToken(text: CharSequence, start: Int): Token? {
        if (start >= text.length) return null
        val c = text[start]

        if (c.isWhitespace()) {
            var i = start + 1
            while (i < text.length && text[i].isWhitespace()) i++
            return Token(Kind.WHITESPACE, start, i)
        }

        // Comment to end of line. A '#' only starts one at the head of a
        // command, but treating any '#' outside a string as a comment matches
        // how constraints files are actually written.
        if (c == '#') {
            var i = start + 1
            while (i < text.length && text[i] != '\n') i++
            return Token(Kind.COMMENT, start, i)
        }

        if (c == '"') {
            var i = start + 1
            while (i < text.length) {
                if (text[i] == '\\' && i + 1 < text.length) { i += 2; continue }
                if (text[i] == '"') return Token(Kind.STRING, start, i + 1)
                i++
            }
            return Token(Kind.STRING, start, text.length) // unterminated
        }

        // $var and ${var}
        if (c == '$') {
            var i = start + 1
            if (i < text.length && text[i] == '{') {
                while (i < text.length && text[i] != '}') i++
                if (i < text.length) i++
                return Token(Kind.VARIABLE, start, i)
            }
            while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_' || text[i] == ':')) i++
            return if (i == start + 1) Token(Kind.OPERATOR, start, i) else Token(Kind.VARIABLE, start, i)
        }

        if (c == '[' || c == ']') return Token(Kind.BRACKET, start, start + 1)
        if (c == '{' || c == '}') return Token(Kind.BRACE, start, start + 1)

        // -switch, but not a bare minus or a negative number.
        if (c == '-' && start + 1 < text.length && text[start + 1].isLetter()) {
            var i = start + 1
            while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_')) i++
            return Token(Kind.OPTION, start, i)
        }

        if (c.isDigit() || (c == '.' && start + 1 < text.length && text[start + 1].isDigit())) {
            var i = start
            while (i < text.length && (text[i].isDigit() || text[i] == '.' || text[i] == '_')) i++
            if (i < text.length && (text[i] == 'e' || text[i] == 'E')) {
                var j = i + 1
                if (j < text.length && (text[j] == '+' || text[j] == '-')) j++
                if (j < text.length && text[j].isDigit()) {
                    i = j
                    while (i < text.length && text[i].isDigit()) i++
                }
            }
            return Token(Kind.NUMBER, start, i)
        }

        // Bare words: commands, properties, pin names, net names.
        if (c.isLetter() || c == '_' || c == '/') {
            var i = start
            while (i < text.length && (text[i].isLetterOrDigit() || text[i] in WORD_CHARS)) i++
            val word = text.subSequence(start, i).toString()
            return when {
                word.lowercase() in COMMANDS -> Token(Kind.COMMAND, start, i)
                word.uppercase() in PROPERTIES -> Token(Kind.PROPERTY, start, i)
                else -> Token(Kind.IDENTIFIER, start, i)
            }
        }

        return Token(Kind.OPERATOR, start, start + 1)
    }

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

    private val WORD_CHARS = setOf('_', '/', '.', '*', '?', '[', ']').minus(setOf('[', ']')) + setOf('\\', '-')
}
