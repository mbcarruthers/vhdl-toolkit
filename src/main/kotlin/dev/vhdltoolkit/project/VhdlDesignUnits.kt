package dev.vhdltoolkit.project

import dev.vhdltoolkit.lexer.VhdlTokenizer

/**
 * Finds the design units and instantiations in a VHDL file.
 *
 * VHDL splits a component across two declarations that are often in two
 * different files: the entity says what it looks like, the architecture says
 * what it does. Moving between them is constant, and the editor cannot offer
 * that jump without knowing where both halves are.
 *
 * This is a scan, not a parse: it walks the token stream looking for the
 * handful of shapes that open a design unit. That is enough to link the
 * halves, and it costs one tokenizer pass per file.
 */
object VhdlDesignUnits {

    enum class Kind {
        ENTITY,
        ARCHITECTURE,
        PACKAGE,
        PACKAGE_BODY,
        CONFIGURATION,
        COMPONENT,

        /** A component or entity instantiation inside an architecture. */
        INSTANTIATION,
    }

    /**
     * @param name the unit's own name, lower-cased. For an instantiation this
     *   is the instance label.
     * @param target what this unit refers to, lower-cased: the entity an
     *   architecture implements, or the entity or component an instantiation
     *   names. Null for a primary unit.
     * @param library the library an instantiation named explicitly, as in
     *   `entity work.counter`. Null when none was written.
     * @param nameOffset where the name token begins, for anchoring a gutter
     *   icon and for navigating to it.
     * @param keywordOffset where the opening keyword begins.
     */
    data class Unit(
        val kind: Kind,
        val name: String,
        val target: String? = null,
        val library: String? = null,
        val nameOffset: Int = 0,
        val keywordOffset: Int = 0,
    )

    fun scan(text: CharSequence): List<Unit> {
        val tokens = significantTokens(text)
        val out = ArrayList<Unit>()

        for (i in tokens.indices) {
            val word = tokens[i].text
            val previous = tokens.getOrNull(i - 1)?.text

            // `end entity foo;` closes a unit rather than opening one.
            if (previous == "end") continue

            when (word) {
                "entity" ->
                    if (previous == ":") {
                        out += instantiation(tokens, i) ?: continue
                    } else {
                        out += simpleUnit(Kind.ENTITY, tokens, i) ?: continue
                    }

                "component" ->
                    if (previous == ":") {
                        out += instantiation(tokens, i) ?: continue
                    } else {
                        out += simpleUnit(Kind.COMPONENT, tokens, i) ?: continue
                    }

                "architecture" -> out += secondaryUnit(Kind.ARCHITECTURE, tokens, i) ?: continue

                "configuration" ->
                    if (previous == ":") {
                        out += instantiation(tokens, i) ?: continue
                    } else {
                        out += secondaryUnit(Kind.CONFIGURATION, tokens, i) ?: continue
                    }

                "package" -> {
                    // `use ieee.numeric_std.all` never reaches here, but a
                    // context clause's `package` would, so require a name.
                    val isBody = tokens.getOrNull(i + 1)?.text == "body"
                    val nameAt = if (isBody) i + 2 else i + 1
                    val name = tokens.getOrNull(nameAt)?.takeIf { it.isIdentifier } ?: continue
                    out += Unit(
                        kind = if (isBody) Kind.PACKAGE_BODY else Kind.PACKAGE,
                        name = name.text,
                        nameOffset = name.start,
                        keywordOffset = tokens[i].start,
                    )
                }
            }
        }

        // An unlabelled instantiation — `u1 : counter port map (...)` with a
        // declared component — has no keyword to key on, so it is found by
        // shape instead.
        out += bareInstantiations(tokens)

        return out.sortedBy { it.keywordOffset }
    }

    // ------------------------------------------------------------- shapes

    /** `entity foo is`, `component foo is` — the name follows the keyword. */
    private fun simpleUnit(kind: Kind, tokens: List<Tok>, i: Int): Unit? {
        val name = tokens.getOrNull(i + 1)?.takeIf { it.isIdentifier } ?: return null
        return Unit(kind, name.text, nameOffset = name.start, keywordOffset = tokens[i].start)
    }

    /** `architecture rtl of counter is`, `configuration c of counter is`. */
    private fun secondaryUnit(kind: Kind, tokens: List<Tok>, i: Int): Unit? {
        val name = tokens.getOrNull(i + 1)?.takeIf { it.isIdentifier } ?: return null
        if (tokens.getOrNull(i + 2)?.text != "of") return null
        val target = tokens.getOrNull(i + 3)?.takeIf { it.isIdentifier } ?: return null
        return Unit(
            kind = kind,
            name = name.text,
            target = target.text,
            nameOffset = name.start,
            keywordOffset = tokens[i].start,
        )
    }

    /**
     * `u1 : entity work.counter`, `u1 : component counter`. The label sits two
     * tokens back, behind the colon.
     */
    private fun instantiation(tokens: List<Tok>, i: Int): Unit? {
        val label = tokens.getOrNull(i - 2)?.takeIf { it.isIdentifier } ?: return null
        val named = tokens.getOrNull(i + 1)?.takeIf { it.isIdentifier } ?: return null

        return Unit(
            kind = Kind.INSTANTIATION,
            name = label.text,
            target = simpleNameOf(named.text),
            library = libraryOf(named.text),
            nameOffset = label.start,
            keywordOffset = label.start,
        )
    }

    /**
     * The tokenizer treats a selected name as one identifier, so
     * `work.counter` arrives whole rather than as three tokens.
     */
    private fun simpleNameOf(name: String): String = name.substringAfterLast('.')

    private fun libraryOf(name: String): String? =
        name.substringBeforeLast('.', missingDelimiterValue = "").substringAfterLast('.')
            .takeIf { it.isNotEmpty() }

    /**
     * `u1 : counter port map (...)` — a component instantiation written
     * without the `component` keyword, which is the common form once the
     * component has been declared.
     *
     * The shape alone is ambiguous with a signal or port declaration, so a
     * `map` must follow before the statement ends. That is what separates
     * `u1 : counter port map (…)` from `s : std_logic;`.
     */
    private fun bareInstantiations(tokens: List<Tok>): List<Unit> {
        val out = ArrayList<Unit>()
        for (i in tokens.indices) {
            if (tokens[i].text != ":") continue
            val label = tokens.getOrNull(i - 1)?.takeIf { it.isIdentifier } ?: continue
            val target = tokens.getOrNull(i + 1)?.takeIf { it.isIdentifier } ?: continue
            // The explicit forms are already handled above.
            if (target.text in setOf("entity", "component", "configuration")) continue
            if (!mapFollows(tokens, i + 2)) continue
            out += Unit(
                kind = Kind.INSTANTIATION,
                name = label.text,
                target = simpleNameOf(target.text),
                library = libraryOf(target.text),
                nameOffset = label.start,
                keywordOffset = label.start,
            )
        }
        return out
    }

    /** Whether a `map` appears before this statement ends. */
    private fun mapFollows(tokens: List<Tok>, from: Int): Boolean {
        var depth = 0
        for (j in from until tokens.size) {
            when (tokens[j].text) {
                "(" -> depth++
                ")" -> depth--
                "map" -> if (depth == 0) return true
                ";" -> if (depth <= 0) return false
            }
        }
        return false
    }

    // ------------------------------------------------------------- tokens

    private class Tok(val text: String, val start: Int, val isIdentifier: Boolean)

    /**
     * Words, brackets and the punctuation the shapes above depend on, with
     * comments and whitespace dropped so that a comment between two words
     * does not hide the pattern. Identifiers and keywords are lower-cased,
     * because VHDL is case-insensitive.
     */
    private fun significantTokens(text: CharSequence): List<Tok> {
        val out = ArrayList<Tok>()
        for (token in VhdlTokenizer.tokenize(text)) {
            val value = text.subSequence(token.start, token.end).toString()
            when (token.kind) {
                VhdlTokenizer.Kind.KEYWORD, VhdlTokenizer.Kind.IDENTIFIER ->
                    out += Tok(value.lowercase(), token.start, isIdentifier = true)

                VhdlTokenizer.Kind.LPAREN -> out += Tok("(", token.start, isIdentifier = false)
                VhdlTokenizer.Kind.RPAREN -> out += Tok(")", token.start, isIdentifier = false)
                VhdlTokenizer.Kind.SEMICOLON -> out += Tok(";", token.start, isIdentifier = false)

                VhdlTokenizer.Kind.OPERATOR ->
                    if (value == ":" || value == ".") {
                        out += Tok(value, token.start, isIdentifier = false)
                    }

                else -> Unit
            }
        }
        return out
    }
}
