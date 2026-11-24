package dev.vhdltoolkit.editing

import dev.vhdltoolkit.lexer.VhdlTokenizer

/**
 * Tracks which VHDL constructs enclose a position, and how deeply.
 *
 * This is the shared foundation for indentation, dedent-on-typing, `end`
 * completion and folding. Each of those needs the same two answers — what am I
 * inside, and what would close it — so they are computed once here rather than
 * by four scanners that would disagree at the edges.
 *
 * It works on the token stream rather than a parse tree, because the plugin
 * deliberately has no VHDL parser: semantics come from the language server.
 * That is enough for block structure, which is almost entirely a matter of
 * keywords, and it keeps the whole thing testable without a running IDE.
 *
 * Indentation is computed from the *actual column* of the enclosing
 * construct's keyword rather than from stack depth. A file that is already
 * formatted unusually then stays self-consistent instead of being dragged
 * toward a canonical shape one line at a time.
 */
object VhdlBlockStructure {

    enum class Construct {
        ENTITY,
        ARCHITECTURE,
        PACKAGE,
        PACKAGE_BODY,
        COMPONENT,
        CONFIGURATION,
        CONTEXT,
        SUBPROGRAM,
        PROCESS,
        BLOCK,
        IF,
        CASE,
        WHEN,
        LOOP,
        GENERATE,
        RECORD,
        UNITS,
        PROTECTED,

        /** An unclosed `(`; indents its contents like any other block. */
        PAREN,
        ;

        /** Design units reset indentation: several can share one file. */
        val isDesignUnit: Boolean
            get() = this == ENTITY || this == ARCHITECTURE || this == PACKAGE ||
                this == PACKAGE_BODY || this == CONFIGURATION || this == CONTEXT

        /**
         * Constructs the language lets you close with a plain `end;`.
         * Everywhere else the keyword is mandatory, which makes it a reliable
         * way to tell which construct a given `end` belongs to.
         */
        val allowsBareEnd: Boolean
            get() = this == ENTITY || this == ARCHITECTURE || this == PACKAGE ||
                this == PACKAGE_BODY || this == SUBPROGRAM || this == CONFIGURATION ||
                this == CONTEXT

        /** Constructs whose declarative part is separated from the body by `begin`. */
        val takesBegin: Boolean
            get() = this == ARCHITECTURE || this == PROCESS || this == SUBPROGRAM ||
                this == PACKAGE_BODY || this == BLOCK || this == PROTECTED
    }

    data class Frame(
        val construct: Construct,
        /** Name after the keyword: the `rtl` of `architecture rtl`. */
        val name: String?,
        /** Statement label: the `u0` of `u0 : process`. */
        val label: String?,
        /** Column of the opening keyword, which the closer lines up with. */
        val headerColumn: Int,
        val openOffset: Int,
        /** Whether a `begin` has been seen for this frame. */
        val hasBegin: Boolean = false,
    )

    data class State(
        val frames: List<Frame>,
        /** Offset where the innermost design unit starts, or 0. */
        val designUnitStart: Int,
    ) {
        val innermost: Frame? get() = frames.lastOrNull()
        val depth: Int get() = frames.size
    }

    /** Keywords that may begin a line and should sit at the enclosing header's column. */
    /** Used when the caller has no tab width to offer. */
    const val DEFAULT_TAB_SIZE = 4

    /**
     * An indent of [columns] columns, written the way the file is written.
     *
     * This is the counterpart to the column measurement: whatever this
     * writes must measure back to the same number of columns, or indentation
     * drifts. Keeping both in one place is what stops them diverging again.
     */
    fun renderIndent(columns: Int, useTabs: Boolean, tabSize: Int): String {
        if (columns <= 0) return ""
        if (!useTabs || tabSize <= 0) return " ".repeat(columns)
        return "\t".repeat(columns / tabSize) + " ".repeat(columns % tabSize)
    }

    private val DEDENTING_LINE_STARTS = setOf("end", "begin", "elsif", "else", "when")

    /**
     * Walks tokens from the start of [text] to [offset], returning the
     * enclosing frames.
     *
     * Scanning from the beginning each time is linear in file size and fast
     * enough for VHDL sources at typing speed; correctness is worth more here
     * than an incremental cache that could drift.
     */
    fun analyze(
        text: CharSequence,
        offset: Int = text.length,
        tabSize: Int = DEFAULT_TAB_SIZE,
    ): State {
        val tokens = significantTokens(text, offset)
        val frames = ArrayList<Frame>()
        var designUnitStart = 0

        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            val word = token.lower

            if (token.kind == VhdlTokenizer.Kind.LPAREN) {
                // Contents indent from the line holding the paren, not from
                // the paren's own column: `port (` should put its interface
                // elements one level in, not aligned under the bracket.
                frames.add(
                    Frame(Construct.PAREN, null, null, lineIndentOf(text, token.start), token.start),
                )
                i++
                continue
            }
            if (token.kind == VhdlTokenizer.Kind.RPAREN) {
                popTo(frames, Construct.PAREN)
                i++
                continue
            }
            if (token.kind != VhdlTokenizer.Kind.KEYWORD) {
                i++
                continue
            }

            when (word) {
                "end" -> {
                    closeFrame(frames)
                    i = skipToSemicolon(tokens, i)
                    continue
                }

                "begin" -> {
                    val last = frames.lastOrNull()
                    if (last != null && last.construct != Construct.PAREN) {
                        frames[frames.size - 1] = last.copy(hasBegin = true)
                    }
                }

                // `elsif`/`else` continue the current `if` rather than nesting.
                "elsif", "else" -> Unit

                "when" -> {
                    // A new alternative replaces the previous one.
                    if (frames.lastOrNull()?.construct == Construct.WHEN) frames.removeAt(frames.size - 1)
                    if (frames.lastOrNull()?.construct == Construct.CASE) {
                        frames.add(Frame(Construct.WHEN, null, null, columnOf(text, token.start, tabSize), token.start))
                    }
                }

                else -> {
                    val opened = openerAt(text, tokens, i)
                    if (opened != null) {
                        if (opened.first.isDesignUnit) {
                            frames.clear()
                            designUnitStart = token.start
                        }
                        frames.add(
                            Frame(
                                construct = opened.first,
                                name = opened.second,
                                label = labelBefore(tokens, i),
                                headerColumn = columnOf(text, lineKeywordStart(tokens, i, text), tabSize),
                                openOffset = token.start,
                            ),
                        )
                    }
                }
            }
            i++
        }

        return State(frames, designUnitStart)
    }

    /**
     * The indent, in columns, for a line at [offset].
     *
     * @param lineStartsWith the first word already present on that line, if
     *   any. A line beginning `end`, `begin`, `elsif`, `else` or `when` lines
     *   up with its enclosing header instead of being indented inside it.
     */
    fun indentFor(
        text: CharSequence,
        offset: Int,
        indentSize: Int,
        lineStartsWith: String? = null,
        indentWhenInCase: Boolean = true,
        tabSize: Int = DEFAULT_TAB_SIZE,
    ): Int {
        val state = analyze(text, offset, tabSize)
        val frame = state.innermost ?: return 0
        val word = lineStartsWith?.lowercase()

        if (word in DEDENTING_LINE_STARTS) {
            return when (word) {
                // Both styles are in common use and the language does not
                // prefer either, so this follows the setting rather than
                // imposing a house style. Whichever is chosen, statements
                // inside the alternative are measured from the `when` as the
                // author actually placed it.
                "when" -> {
                    val case = state.frames.lastOrNull { it.construct == Construct.CASE }
                    when {
                        case == null -> frame.headerColumn
                        indentWhenInCase -> case.headerColumn + indentSize
                        else -> case.headerColumn
                    }
                }
                // A closing keyword lines up with whatever it closes, skipping
                // any `when` alternatives still open inside a case.
                else -> closable(state.frames)?.headerColumn ?: 0
            }
        }
        if (lineStartsWith == ")") return frame.headerColumn

        return frame.headerColumn + indentSize
    }

    /**
     * What would close [frame], written the way the user asked for.
     * Null when the construct has no `end` of its own, such as a paren.
     */
    fun closerFor(frame: Frame, style: EndStyle): String? {
        val keyword = when (frame.construct) {
            Construct.ENTITY -> "entity"
            Construct.ARCHITECTURE -> "architecture"
            Construct.PACKAGE -> "package"
            Construct.PACKAGE_BODY -> "package body"
            Construct.COMPONENT -> "component"
            Construct.CONFIGURATION -> "configuration"
            Construct.CONTEXT -> "context"
            Construct.PROCESS -> "process"
            Construct.BLOCK -> "block"
            Construct.IF -> "if"
            Construct.CASE -> "case"
            Construct.LOOP -> "loop"
            Construct.GENERATE -> "generate"
            Construct.RECORD -> "record"
            Construct.UNITS -> "units"
            Construct.PROTECTED -> "protected"
            Construct.SUBPROGRAM -> null // `end function f;` needs the real keyword
            Construct.WHEN, Construct.PAREN -> return null
        }

        // `end if;` and `end loop;` always name the construct: `end;` alone is
        // ambiguous there, and VHDL requires the keyword.
        val keywordRequired = frame.construct == Construct.IF ||
            frame.construct == Construct.CASE ||
            frame.construct == Construct.LOOP ||
            frame.construct == Construct.GENERATE ||
            frame.construct == Construct.RECORD ||
            frame.construct == Construct.UNITS

        val includeKeyword = keywordRequired || style != EndStyle.BARE
        val includeName = style == EndStyle.KEYWORD_AND_NAME

        return buildString {
            append("end")
            if (includeKeyword && keyword != null) {
                append(' ').append(keyword)
            }
            if (includeName) {
                val trailing = frame.label ?: frame.name
                // A label names a statement; a name names a design unit.
                if (trailing != null && frame.construct != Construct.RECORD) {
                    append(' ').append(trailing)
                }
            }
            append(';')
        }
    }

    /** How explicit generated `end` lines should be. */
    enum class EndStyle {
        /** `end;` where the language allows it. */
        BARE,

        /** `end architecture;` */
        KEYWORD,

        /** `end architecture rtl;` */
        KEYWORD_AND_NAME,
    }

    /**
     * True when [frame] already has a matching `end` after [offset], so a
     * second one should not be inserted while editing existing code.
     */
    fun isClosedAfter(text: CharSequence, frame: Frame, offset: Int): Boolean {
        // Matching on the keyword alone cannot separate two constructs of
        // the same kind. Inside `if x then / if y then`, the outer `end if;`
        // answers "yes, an if ends here" for the inner one too, so the inner
        // never gets completed. What settles it is counting: as many closers
        // must follow as there are constructs of this kind still open.
        val stillOpen = analyze(text, offset).frames.count { it.construct == frame.construct }
        val tokens = significantTokens(text, text.length).filter { it.start >= offset }
        var depth = 0
        var closers = 0

        for ((index, token) in tokens.withIndex()) {
            if (token.kind != VhdlTokenizer.Kind.KEYWORD) continue
            when {
                token.lower == "end" -> {
                    if (depth > 0) {
                        depth--
                        continue
                    }
                    // An unmatched `end` closes either this kind of
                    // construct or an enclosing one. Once an enclosing one
                    // is reached, nothing further down can belong to this
                    // frame: in a real file there is nearly always an
                    // `end process;` or `end architecture;` below, and
                    // treating those as proof of closure would mean never
                    // completing anything.
                    if (!endBelongsTo(frame, tokens.getOrNull(index + 1))) return false
                    if (++closers >= stillOpen) return true
                }

                token.lower in OPENING_KEYWORDS -> depth++
            }
        }
        return false
    }

    /**
     * Whether an `end` followed by [next] could be closing [frame].
     *
     * VHDL names the construct in most closers — `end if;`, `end process;`,
     * `end component;` are all mandatory forms — so the keyword identifies
     * the owner. A bare `end;` is only legal for entities, architectures,
     * packages and subprograms, and is ambiguous among those.
     */
    private fun endBelongsTo(frame: Frame, next: Tok?): Boolean {
        val keyword = next?.takeIf { it.kind == VhdlTokenizer.Kind.KEYWORD }?.lower

        val expected = when (frame.construct) {
            Construct.ENTITY -> "entity"
            Construct.ARCHITECTURE -> "architecture"
            Construct.PACKAGE, Construct.PACKAGE_BODY -> "package"
            Construct.COMPONENT -> "component"
            Construct.CONFIGURATION -> "configuration"
            Construct.CONTEXT -> "context"
            Construct.PROCESS -> "process"
            Construct.BLOCK -> "block"
            Construct.IF -> "if"
            Construct.CASE -> "case"
            Construct.LOOP -> "loop"
            Construct.GENERATE -> "generate"
            Construct.RECORD -> "record"
            Construct.UNITS -> "units"
            Construct.PROTECTED -> "protected"
            Construct.SUBPROGRAM -> null // `end function f;` or just `end;`
            Construct.WHEN, Construct.PAREN -> return true
        }

        if (keyword == null) {
            // A bare `end;` cannot close a construct whose keyword the
            // language requires, so it must belong to something outside.
            return frame.construct.allowsBareEnd
        }
        if (expected == null) return keyword == "function" || keyword == "procedure"
        return keyword == expected
    }

    // ------------------------------------------------------------ internals

    private val OPENING_KEYWORDS = setOf(
        "entity", "architecture", "package", "component", "configuration", "context",
        "process", "block", "if", "case", "loop", "generate", "record", "units", "protected",
        "function", "procedure",
    )

    private data class Tok(
        val kind: VhdlTokenizer.Kind,
        val start: Int,
        val end: Int,
        val lower: String,
    )

    private fun significantTokens(text: CharSequence, upTo: Int): List<Tok> {
        val out = ArrayList<Tok>()
        for (t in VhdlTokenizer.tokenize(text)) {
            if (t.start >= upTo) break
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

    /**
     * Decides whether the keyword at [i] opens a block, and what it is called.
     *
     * The awkward cases live here: `entity` is a design unit in
     * `entity foo is` but a reference in `u0 : entity work.foo`, and `is`
     * opens a block for `type t is record` but not for `type t is (a, b)`.
     */
    private fun openerAt(text: CharSequence, tokens: List<Tok>, i: Int): Pair<Construct, String?>? {
        val word = tokens[i].lower
        val next = tokens.getOrNull(i + 1)
        val prev = tokens.getOrNull(i - 1)

        fun nameAfter(): String? =
            next?.takeIf { it.kind == VhdlTokenizer.Kind.IDENTIFIER }
                ?.let { text.subSequence(it.start, it.end).toString() }

        return when (word) {
            "entity" -> {
                // `u0 : entity work.foo` instantiates; it does not declare.
                if (prev?.lower == ":" || next?.lower == "work") null
                else if (followedByIsBefore(tokens, i, "entity")) Construct.ENTITY to nameAfter()
                else null
            }

            "architecture" -> Construct.ARCHITECTURE to nameAfter()

            "package" -> {
                if (prev?.lower == "use" || prev?.lower == "." ) null
                else if (next?.lower == "body") Construct.PACKAGE_BODY to
                    tokens.getOrNull(i + 2)?.let { text.subSequence(it.start, it.end).toString() }
                else if (next?.lower == "new") null // instantiation
                else Construct.PACKAGE to nameAfter()
            }

            "component" -> {
                if (prev?.lower == ":") null else Construct.COMPONENT to nameAfter()
            }

            "configuration" -> if (prev?.lower == ":") null else Construct.CONFIGURATION to nameAfter()
            "context" -> if (next?.lower == "is" || nameAfter() != null) Construct.CONTEXT to nameAfter() else null

            "process" -> Construct.PROCESS to null
            "block" -> Construct.BLOCK to null

            "if" -> {
                // `if ... generate` is a generate statement, not an if statement.
                if (closesWith(tokens, i, "generate")) Construct.GENERATE to null
                else if (closesWith(tokens, i, "then")) Construct.IF to null
                else null
            }

            "case" -> {
                if (closesWith(tokens, i, "generate")) Construct.GENERATE to null
                else Construct.CASE to null
            }

            "for" -> if (closesWith(tokens, i, "generate")) Construct.GENERATE to null else null
            "loop" -> Construct.LOOP to null

            "record" -> Construct.RECORD to null
            "units" -> Construct.UNITS to null
            "protected" -> if (prev?.lower == "end") null else Construct.PROTECTED to null

            "function", "procedure" -> {
                // A declaration ends in `;`; a body continues with `is`.
                if (closesWith(tokens, i, "is")) Construct.SUBPROGRAM to nameAfter() else null
            }

            else -> null
        }
    }

    /** Scans forward on the logical line for [keyword] before a `;` or line end. */
    private fun closesWith(tokens: List<Tok>, from: Int, keyword: String): Boolean {
        var depth = 0
        for (j in from + 1 until tokens.size) {
            val t = tokens[j]
            when {
                t.kind == VhdlTokenizer.Kind.LPAREN -> depth++
                t.kind == VhdlTokenizer.Kind.RPAREN -> depth--
                depth > 0 -> Unit
                t.kind == VhdlTokenizer.Kind.SEMICOLON -> return false
                t.kind == VhdlTokenizer.Kind.KEYWORD && t.lower == keyword -> return true
                // Another block keyword means we have run past this statement.
                t.kind == VhdlTokenizer.Kind.KEYWORD && t.lower in OPENING_KEYWORDS -> return false
            }
        }
        return false
    }

    private fun followedByIsBefore(tokens: List<Tok>, from: Int, stopAt: String): Boolean =
        closesWith(tokens, from, "is")

    private fun labelBefore(tokens: List<Tok>, i: Int): String? {
        val colon = tokens.getOrNull(i - 1) ?: return null
        if (colon.lower != ":") return null
        val label = tokens.getOrNull(i - 2) ?: return null
        if (label.kind != VhdlTokenizer.Kind.IDENTIFIER) return null
        return label.lower
    }

    /** Where the construct's line effectively begins, so closers can line up. */
    private fun lineKeywordStart(tokens: List<Tok>, i: Int, text: CharSequence): Int {
        val label = tokens.getOrNull(i - 2)
        val colon = tokens.getOrNull(i - 1)
        return if (colon?.lower == ":" && label != null && onSameLine(text, label.start, tokens[i].start)) {
            label.start
        } else {
            tokens[i].start
        }
    }

    private fun onSameLine(text: CharSequence, a: Int, b: Int): Boolean =
        (a until b).none { text[it] == '\n' }

    /** Width of the leading whitespace on the line containing [offset]. */
    private fun lineIndentOf(text: CharSequence, offset: Int): Int {
        var start = offset
        while (start > 0 && text[start - 1] != '\n') start--
        var width = 0
        var i = start
        while (i < text.length && (text[i] == ' ' || text[i] == '\t')) {
            width++
            i++
        }
        return width
    }

    /**
     * The display column at [offset], with tabs expanded to [tabSize].
     *
     * Counting characters instead would make a tab one column wide, while
     * [renderIndent] writes a tab for every [tabSize] columns. Those two
     * disagreeing is what made indentation drift in a tab-indented file: a
     * line written at column 8 measured back as column 2, so the next
     * construct was aligned to a column that was never asked for, and the
     * error compounded down the file.
     *
     * A tab advances to the next multiple of [tabSize], which can only be
     * worked out by reading forwards from the start of the line.
     */
    private fun columnOf(text: CharSequence, offset: Int, tabSize: Int): Int {
        var lineStart = offset
        while (lineStart > 0 && text[lineStart - 1] != '\n') lineStart--

        var column = 0
        for (i in lineStart until offset) {
            column = if (text[i] == '\t' && tabSize > 0) {
                (column / tabSize + 1) * tabSize
            } else {
                column + 1
            }
        }
        return column
    }

    /** The frame an `end` would close: the innermost that has one. */
    private fun closable(frames: List<Frame>): Frame? =
        frames.lastOrNull { it.construct != Construct.PAREN && it.construct != Construct.WHEN }

    private fun closeFrame(frames: MutableList<Frame>) {
        // Discard any alternatives or parens still open inside.
        while (frames.isNotEmpty() &&
            (frames.last().construct == Construct.WHEN || frames.last().construct == Construct.PAREN)
        ) {
            frames.removeAt(frames.size - 1)
        }
        if (frames.isNotEmpty()) frames.removeAt(frames.size - 1)
    }

    private fun popTo(frames: MutableList<Frame>, construct: Construct) {
        val index = frames.indexOfLast { it.construct == construct }
        if (index >= 0) {
            while (frames.size > index) frames.removeAt(frames.size - 1)
        }
    }

    private fun skipToSemicolon(tokens: List<Tok>, from: Int): Int {
        for (j in from + 1 until tokens.size) {
            if (tokens[j].kind == VhdlTokenizer.Kind.SEMICOLON) return j + 1
        }
        return tokens.size
    }
}
