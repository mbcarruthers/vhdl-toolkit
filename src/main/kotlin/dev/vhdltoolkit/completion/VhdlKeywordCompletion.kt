package dev.vhdltoolkit.completion

import dev.vhdltoolkit.editing.VhdlBlockStructure
import dev.vhdltoolkit.editing.VhdlBlockStructure.Construct
import dev.vhdltoolkit.lexer.VhdlSemanticClassifier

/**
 * Works out which VHDL keywords make sense at a position.
 *
 * The language server completes *names* — signals, entities, packages — but
 * not the language itself, so typing `pro` offers nothing until a process
 * already exists to copy from. That is the wrong way round for someone
 * learning VHDL, where the keywords are exactly what you do not yet know.
 *
 * Context comes from the block structure already used for indentation: what
 * is legal depends on whether you are in a declarative region, a concurrent
 * body or a sequential one, and that is precisely what the frame stack says.
 */
object VhdlKeywordCompletion {

    /** Where in a design the caret sits, which decides what may be written. */
    enum class Region {
        /** Before or between design units. */
        DESIGN_FILE,

        /** Entity header: ports, generics. */
        ENTITY_HEADER,

        /** Declarations of an architecture, package or process. */
        DECLARATIVE,

        /** Concurrent statements: an architecture or block body. */
        CONCURRENT,

        /** Sequential statements: inside a process or subprogram body. */
        SEQUENTIAL,
    }

    data class Suggestion(
        val text: String,
        /** Multi-line body for skeletons; null for a plain keyword. */
        val template: String? = null,
        val description: String? = null,
    )

    fun regionAt(text: CharSequence, offset: Int): Region {
        val state = VhdlBlockStructure.analyze(text, offset)
        val frames = state.frames.filter { it.construct != Construct.PAREN }
        val frame = frames.lastOrNull() ?: return Region.DESIGN_FILE

        return when (frame.construct) {
            Construct.ENTITY, Construct.COMPONENT -> Region.ENTITY_HEADER

            Construct.PROCESS, Construct.SUBPROGRAM ->
                if (frame.hasBegin) Region.SEQUENTIAL else Region.DECLARATIVE

            Construct.ARCHITECTURE, Construct.BLOCK ->
                if (frame.hasBegin) Region.CONCURRENT else Region.DECLARATIVE

            Construct.PACKAGE, Construct.PACKAGE_BODY, Construct.RECORD,
            Construct.UNITS, Construct.PROTECTED,
            -> Region.DECLARATIVE

            Construct.IF, Construct.CASE, Construct.WHEN, Construct.LOOP ->
                if (frames.any { it.construct == Construct.ARCHITECTURE && !it.hasBegin }) {
                    Region.DECLARATIVE
                } else if (frames.any { it.construct == Construct.PROCESS || it.construct == Construct.SUBPROGRAM }) {
                    Region.SEQUENTIAL
                } else {
                    Region.CONCURRENT
                }

            Construct.GENERATE -> Region.CONCURRENT
            Construct.CONFIGURATION, Construct.CONTEXT -> Region.DECLARATIVE
            Construct.PAREN -> Region.DECLARATIVE
        }
    }

    fun suggestionsFor(text: CharSequence, offset: Int): List<Suggestion> {
        val region = regionAt(text, offset)
        val state = VhdlBlockStructure.analyze(text, offset)
        val out = ArrayList<Suggestion>()

        out += when (region) {
            Region.DESIGN_FILE -> DESIGN_FILE_KEYWORDS
            Region.ENTITY_HEADER -> ENTITY_KEYWORDS
            Region.DECLARATIVE -> DECLARATIVE_KEYWORDS
            Region.CONCURRENT -> CONCURRENT_KEYWORDS
            Region.SEQUENTIAL -> SEQUENTIAL_KEYWORDS
        }

        // `begin` only makes sense once, and only where a body follows.
        val frame = state.frames.lastOrNull { it.construct != Construct.PAREN }
        if (frame != null && frame.construct.takesBegin && !frame.hasBegin) {
            out += Suggestion("begin", description = "start of ${frame.construct.name.lowercase()} body")
        }

        // The specific closer for whatever is open beats a bare `end`.
        if (frame != null) {
            VhdlBlockStructure.closerFor(frame, VhdlBlockStructure.EndStyle.KEYWORD_AND_NAME)
                ?.let { out += Suggestion(it, description = "close ${frame.construct.name.lowercase()}") }
        }

        if (region != Region.DESIGN_FILE) {
            out += TYPES.map { Suggestion(it, description = "standard type") }
        }

        return out.distinctBy { it.text.lowercase() }
    }

    // --------------------------------------------------------- vocabulary

    private fun kw(vararg words: String) = words.map { Suggestion(it) }

    private val DESIGN_FILE_KEYWORDS = kw(
        "library", "use", "entity", "architecture", "package", "package body",
        "configuration", "context",
    ) + listOf(
        Suggestion(
            "entity",
            template = "entity \$NAME\$ is\n    port (\n        \$CURSOR$\n    );\nend entity \$NAME\$;",
            description = "entity skeleton",
        ),
        Suggestion(
            "architecture",
            template = "architecture rtl of \$NAME\$ is\n\$CURSOR$\nbegin\n\nend architecture rtl;",
            description = "architecture skeleton",
        ),
    )

    private val ENTITY_KEYWORDS = kw(
        "port", "generic", "in", "out", "inout", "buffer", "signal", "constant", "end",
    )

    private val DECLARATIVE_KEYWORDS = kw(
        "signal", "constant", "variable", "shared variable", "type", "subtype",
        "component", "function", "procedure", "impure function", "alias",
        "attribute", "file", "use", "for",
    )

    private val CONCURRENT_KEYWORDS = kw(
        "process", "block", "generate", "with", "assert", "signal", "component", "for",
    ) + listOf(
        Suggestion(
            "process",
            template = "process (\$SENSITIVITY\$)\nbegin\n    \$CURSOR$\nend process;",
            description = "process skeleton",
        ),
        Suggestion(
            "clocked process",
            template = "process (clk)\nbegin\n    if rising_edge(clk) then\n        \$CURSOR$\n    end if;\nend process;",
            description = "synchronous process",
        ),
    )

    private val SEQUENTIAL_KEYWORDS = kw(
        "if", "then", "elsif", "else", "case", "when", "others", "for", "while",
        "loop", "exit", "next", "wait", "report", "assert", "severity", "null",
        "return", "variable",
    ) + listOf(
        Suggestion(
            "if",
            template = "if \$COND\$ then\n    \$CURSOR$\nend if;",
            description = "if statement",
        ),
        Suggestion(
            "case",
            template = "case \$EXPR\$ is\n    when \$CHOICE\$ =>\n        \$CURSOR$\n    when others =>\n        null;\nend case;",
            description = "case statement",
        ),
        Suggestion(
            "for loop",
            template = "for i in 0 to \$N\$ loop\n    \$CURSOR$\nend loop;",
            description = "for loop",
        ),
    )

    /** Offered everywhere a type mark may appear. */
    private val TYPES = VhdlSemanticClassifier.STANDARD_TYPES.sorted()
}
