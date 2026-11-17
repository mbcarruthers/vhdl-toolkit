package dev.vhdltoolkit.diagnostics

import dev.vhdltoolkit.lexer.VhdlSemanticClassifier

/**
 * Separates root-cause diagnostics from the errors they caused.
 *
 * A single typo in a context clause — `use ieee.std_logic_1164.al;` — makes
 * every name from that package unresolvable, so every port declared with one
 * of those types fails, and then every reference to those ports fails. One
 * mistake becomes thirty errors, and the real one is lost among them.
 *
 * Compilers solve this by poisoning: once a name fails to resolve it is bound
 * to an error value that absorbs further diagnostics instead of generating
 * them. VHDL-LS does not do that yet, so this reconstructs the causal chain
 * after the fact, from the diagnostics and the source text.
 *
 * Three rules, applied in order:
 *
 *  1. **Broken context clause.** A diagnostic on a `library` or `use` line
 *     poisons the names that clause would have imported. We cannot know which
 *     names those are without resolving the package, so every unresolved-name
 *     diagnostic in the file is treated as fallout.
 *  2. **Poisoned declarations.** A name declared on a line that already has an
 *     error has an unknown type, so complaints about *that* name are fallout
 *     too. Applied to a fixed point, since a poisoned signal can poison the
 *     declaration it appears in.
 *  3. **Repeats.** The same unresolved name reported many times is worth
 *     saying once.
 *
 * Nothing is deleted: callers decide whether to hide derived diagnostics or
 * merely weaken them. When in doubt the analyzer keeps a diagnostic, because
 * hiding a real error is worse than showing a redundant one.
 */
object DiagnosticCascade {

    data class Position(val line: Int, val character: Int)
    data class Range(val start: Position, val end: Position)

    data class Diagnostic(val range: Range, val message: String, val severity: Int = SEVERITY_ERROR)

    const val SEVERITY_ERROR = 1
    const val SEVERITY_WARNING = 2

    enum class Verdict {
        /** A root cause, or something we could not attribute to one. */
        ROOT,

        /** Caused by another diagnostic in the same file. */
        DERIVED,
    }

    data class Analysis(val verdicts: List<Verdict>) {
        val derivedCount: Int get() = verdicts.count { it == Verdict.DERIVED }
        val rootCount: Int get() = verdicts.count { it == Verdict.ROOT }
        fun isDerived(index: Int): Boolean = verdicts.getOrNull(index) == Verdict.DERIVED
    }

    /**
     * Messages that mean "this name could not be resolved". Matching is
     * deliberately narrow: an unrecognised message is never suppressed.
     */
    private val UNRESOLVED_PATTERNS = listOf(
        Regex("""[Nn]o declaration of '([^']+)'"""),
        // A missing library is reported in its own words, and is usually the
        // first link in the chain rather than a consequence of one.
        Regex("""[Nn]o such (?:library|package|entity|component|design unit) '([^']+)'"""),
        Regex("""[Cc]ould not resolve '([^']+)'"""),
        Regex("""'([^']+)' is not declared"""),
        Regex("""[Uu]nknown (?:type|identifier|name) '([^']+)'"""),
    )

    /**
     * Lint warnings whose premise fails once something upstream did not
     * resolve. "Unused" is a claim about the whole design: a generic looks
     * unused when the entity it is passed to could not be found, so these are
     * not evidence of anything while a context clause is broken.
     */
    private val UNRELIABLE_WHEN_POISONED = listOf(
        Regex("""[Uu]nused declaration"""),
        Regex("""[Uu]nused (?:generic|port|signal|variable|constant|type|subprogram)"""),
        Regex("""is never (?:read|used|assigned)"""),
    )

    private fun isUnreliableWhenPoisoned(message: String): Boolean =
        UNRELIABLE_WHEN_POISONED.any { it.containsMatchIn(message) }

    /**
     * A parser complaining about what it wanted to see. After the first of
     * these the parser is no longer where it thinks it is, so the rest
     * describe its recovery attempts rather than anything about the code.
     */
    private val PARSE_ERROR_PATTERNS = listOf(
        Regex("""^\s*Expected\b"""),
        Regex("""[Uu]nexpected (?:token|character|end of file)"""),
        Regex("""[Ss]yntax error"""),
        Regex("""[Mm]issing ['`]"""),
    )

    fun isParseError(message: String): Boolean =
        PARSE_ERROR_PATTERNS.any { it.containsMatchIn(message) }

    /** A context clause: the statements whose failure poisons a whole package. */
    private val CONTEXT_CLAUSE = Regex("""^\s*(library|use|context)\b""", RegexOption.IGNORE_CASE)

    fun analyze(text: CharSequence, diagnostics: List<Diagnostic>): Analysis {
        if (diagnostics.isEmpty()) return Analysis(emptyList())

        val lines = text.lineSequence().toList()
        val lineStarts = computeLineStarts(text)

        val unresolvedSymbol = diagnostics.map { symbolOf(it.message) }
        val onContextClause = diagnostics.map { d ->
            lines.getOrNull(d.range.start.line)?.let { CONTEXT_CLAUSE.containsMatchIn(it) } == true
        }

        val verdicts = MutableList(diagnostics.size) { Verdict.ROOT }

        // Rule 0: a syntax error derails the parser. Everything it reports
        // afterwards is a guess about where to resume, so the positions are
        // meaningless and only the first is worth showing. This is what
        // compilers have always done: report the first parse error, then stop
        // trusting the parse until it resynchronises.
        val parseErrors = diagnostics.indices
            .filter { diagnostics[it].severity == SEVERITY_ERROR && isParseError(diagnostics[it].message) }
            .sortedWith(compareBy({ diagnostics[it].range.start.line }, { diagnostics[it].range.start.character }))
        for (index in parseErrors.drop(1)) {
            verdicts[index] = Verdict.DERIVED
        }
        val hasParseError = parseErrors.isNotEmpty()

        // Rule 1: a broken context clause poisons every name it would import,
        // and makes "unused" judgements meaningless for the whole file. A
        // failed parse does the same, and for the same reason: nothing
        // resolved from a file that did not parse can be relied on.
        val brokenContextClause = diagnostics.indices.any {
            onContextClause[it] && diagnostics[it].severity == SEVERITY_ERROR
        }
        if (brokenContextClause || hasParseError) {
            for (i in diagnostics.indices) {
                if (onContextClause[i] && !hasParseError) continue
                if (i == parseErrors.firstOrNull()) continue // the surviving root
                if (unresolvedSymbol[i] != null || isUnreliableWhenPoisoned(diagnostics[i].message)) {
                    verdicts[i] = Verdict.DERIVED
                }
            }
        }

        // Rule 2: names declared on a line that already has an error are
        // themselves poisoned, so complaints about them are fallout. Repeat
        // until nothing new is poisoned.
        val declaredByLine = declaredNamesByLine(text, lineStarts)
        val poisoned = HashSet<String>()
        var changed = true
        while (changed) {
            changed = false

            val erroredLines = diagnostics.indices
                .filter { diagnostics[it].severity == SEVERITY_ERROR }
                .map { diagnostics[it].range.start.line }
                .toSet()

            for (line in erroredLines) {
                for (name in declaredByLine[line].orEmpty()) {
                    if (poisoned.add(name.lowercase())) changed = true
                }
            }

            for (i in diagnostics.indices) {
                if (verdicts[i] == Verdict.DERIVED) continue
                val symbol = unresolvedSymbol[i] ?: continue
                if (onContextClause[i]) continue // the root itself
                if (symbol.lowercase() in poisoned) {
                    verdicts[i] = Verdict.DERIVED
                    // Anything declared on this line is now poisoned too.
                    for (name in declaredByLine[diagnostics[i].range.start.line].orEmpty()) {
                        if (poisoned.add(name.lowercase())) changed = true
                    }
                    changed = true
                }
            }
        }

        // Rule 3: say each unresolved name once.
        val seen = HashSet<String>()
        for (i in diagnostics.indices) {
            if (verdicts[i] == Verdict.DERIVED) continue
            val symbol = unresolvedSymbol[i]?.lowercase() ?: continue
            if (!seen.add(symbol)) verdicts[i] = Verdict.DERIVED
        }

        return Analysis(verdicts)
    }

    /** The name a message complains about, or null if it is not that kind of message. */
    fun symbolOf(message: String): String? {
        for (pattern in UNRESOLVED_PATTERNS) {
            val m = pattern.find(message) ?: continue
            return m.groupValues[1].takeIf { it.isNotBlank() }
        }
        return null
    }

    /**
     * Names declared on each line, from the semantic classifier, so the
     * "declared here" question is answered the same way the editor colours it.
     */
    private fun declaredNamesByLine(text: CharSequence, lineStarts: List<Int>): Map<Int, List<String>> {
        val out = HashMap<Int, MutableList<String>>()
        for (span in VhdlSemanticClassifier.classify(text)) {
            if (span.category != VhdlSemanticClassifier.Category.DECLARED_NAME) continue
            val line = lineOf(span.start, lineStarts)
            out.getOrPut(line) { ArrayList() }.add(text.subSequence(span.start, span.end).toString())
        }
        return out
    }

    private fun computeLineStarts(text: CharSequence): List<Int> {
        val starts = ArrayList<Int>()
        starts.add(0)
        for (i in text.indices) {
            if (text[i] == '\n') starts.add(i + 1)
        }
        return starts
    }

    private fun lineOf(offset: Int, lineStarts: List<Int>): Int {
        var lo = 0
        var hi = lineStarts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (lineStarts[mid] <= offset) lo = mid else hi = mid - 1
        }
        return lo
    }
}
