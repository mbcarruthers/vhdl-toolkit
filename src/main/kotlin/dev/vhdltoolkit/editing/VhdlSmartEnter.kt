package dev.vhdltoolkit.editing

import dev.vhdltoolkit.lexer.VhdlTokenizer

/**
 * Finishes the line the caret is on, the way Ctrl+Shift+Enter does elsewhere.
 *
 * The idea is that a half-written line has one obvious completion and the
 * editor should supply it rather than make you type the punctuation: an `if`
 * wants its `then`, an open bracket wants closing, a signal assignment wants
 * its semicolon. Once the line is whole, the caret moves on — and if the line
 * turned out to open a block, the block is completed too, which is the same
 * service [VhdlBlockCompletion] already provides on Enter.
 *
 * Deciding what is missing needs no parser: the token stream says whether a
 * bracket is open and which keyword the line began with.
 */
object VhdlSmartEnter {

    /**
     * @param line the caret's line, rewritten with whatever was missing.
     * @param tail text to place after it, starting with a newline. Empty when
     *   there is nothing to add.
     * @param caret where the caret should end up, measured from the start of
     *   [line].
     */
    data class Result(
        val line: String,
        val tail: String,
        val caret: Int,
    )

    fun completionFor(
        text: CharSequence,
        caret: Int,
        indentSize: Int,
        endStyle: VhdlBlockStructure.EndStyle = VhdlBlockStructure.EndStyle.KEYWORD_AND_NAME,
        insertBegin: Boolean = true,
    ): Result? {
        if (caret < 0 || caret > text.length) return null

        val lineStart = text.lastIndexOf('\n', (caret - 1).coerceAtLeast(0))
            .let { if (it < 0 || caret == 0) 0 else it + 1 }
        var lineEnd = lineStart
        while (lineEnd < text.length && text[lineEnd] != '\n') lineEnd++

        val original = text.subSequence(lineStart, lineEnd).toString()
        if (original.isBlank()) return null

        val fixed = finish(text.subSequence(0, lineStart), original)
        val indent = original.takeWhile { it == ' ' || it == '\t' }

        // What follows is worked out against the file as it would be with the
        // line already finished, so block completion sees a complete header
        // rather than the fragment that was actually typed.
        val repaired = StringBuilder()
            .append(text, 0, lineStart)
            .append(fixed)
            .append('\n')
        val nextIndent = VhdlLineIndentProvider.renderIndent(
            VhdlBlockStructure.indentFor(repaired, repaired.length, indentSize),
            useTabs = indent.startsWith("\t"),
            tabSize = indentSize,
        )
        repaired.append(nextIndent)

        val block = VhdlBlockCompletion.completionAt(
            text = repaired,
            caret = repaired.length,
            indentSize = indentSize,
            endStyle = endStyle,
            insertBegin = insertBegin,
        )

        val tail = "\n" + nextIndent + (block?.textToInsert ?: "")
        val caretInTail = 1 + nextIndent.length + (block?.caretOffsetInInsertion ?: 0)

        if (fixed == original && block == null) {
            // Nothing was missing and no block opened; moving to a fresh line
            // is all that is left, which plain Enter already does.
            return null
        }

        return Result(line = fixed, tail = tail, caret = fixed.length + caretInTail)
    }

    // -------------------------------------------------------------- repair

    /**
     * Adds what the line is missing: a keyword the construct requires, any
     * unclosed brackets, and a terminating semicolon.
     */
    private fun finish(before: CharSequence, line: String): String {
        val trimmed = line.trimEnd()
        if (trimmed.isEmpty()) return line
        if (isComment(trimmed)) return line

        val tokens = wordsOf(trimmed)
        if (tokens.isEmpty()) return line

        var out = trimmed

        // `if x` wants `then`; `while x` and `for i in r` want `loop`;
        // `case x` wants `is`. Only when the line does not already end in the
        // keyword and no bracket is still open, so a half-typed condition is
        // left alone.
        val open = unclosedBrackets(trimmed)
        if (open == 0) {
            val missing = missingKeyword(tokens)
            if (missing != null) out = "$out $missing"
        }

        if (open > 0) {
            out += closersFor(before, trimmed)
        }

        if (needsSemicolon(wordsOf(out), out)) out += ";"
        return out
    }

    /** The keyword this line's opening word requires but does not have. */
    private fun missingKeyword(words: List<String>): String? {
        val last = words.last()
        return when (words.first()) {
            "if", "elsif" -> "then".takeIf { last != "then" && words.size > 1 }
            "while" -> "loop".takeIf { last != "loop" && words.size > 1 }
            "for" -> "loop".takeIf { last != "loop" && last != "generate" && words.size > 1 }
            "case" -> "is".takeIf { last != "is" && words.size > 1 }
            else -> null
        }
    }

    /**
     * Closes brackets left open, giving a port or generic clause the
     * semicolon that ends its statement.
     */
    private fun closersFor(before: CharSequence, line: String): String {
        val full = before.toString() + line
        val open = unclosedParens(full)
        if (open.isEmpty()) return ""
        val closers = ")".repeat(open.size)
        // Only the outermost bracket can be the one that ends a statement, so
        // that is the one asked about.
        return if (VhdlParenCompletion.needsSemicolon(full, open.first())) "$closers;" else closers
    }

    private fun needsSemicolon(words: List<String>, line: String): Boolean {
        val trimmed = line.trimEnd()
        if (trimmed.endsWith(";") || trimmed.endsWith(",")) return false
        if (unclosedBrackets(trimmed) != 0) return false
        val first = words.firstOrNull() ?: return false
        val last = words.lastOrNull() ?: return false
        // A header ends in a keyword that opens a body, not in a semicolon.
        if (last in OPENERS_BY_SUFFIX) return false
        if (first in NEVER_TERMINATED) return false
        return true
    }

    // -------------------------------------------------------------- tokens

    private fun isComment(line: String): Boolean = line.trimStart().startsWith("--")

    private fun wordsOf(line: String): List<String> =
        VhdlTokenizer.tokenize(line)
            .filter {
                it.kind == VhdlTokenizer.Kind.KEYWORD || it.kind == VhdlTokenizer.Kind.IDENTIFIER
            }
            .map { line.substring(it.start, it.end).lowercase() }

    private fun unclosedBrackets(line: String): Int {
        var depth = 0
        for (token in VhdlTokenizer.tokenize(line)) {
            when (token.kind) {
                VhdlTokenizer.Kind.LPAREN -> depth++
                VhdlTokenizer.Kind.RPAREN -> depth--
                else -> Unit
            }
        }
        return depth.coerceAtLeast(0)
    }

    /** Offsets of every `(` still open at the end of [text], outermost first. */
    private fun unclosedParens(text: String): List<Int> {
        val stack = ArrayList<Int>()
        for (token in VhdlTokenizer.tokenize(text)) {
            when (token.kind) {
                VhdlTokenizer.Kind.LPAREN -> stack.add(token.start)
                VhdlTokenizer.Kind.RPAREN -> stack.removeLastOrNull()
                else -> Unit
            }
        }
        return stack
    }

    /** Words that end a header, so the line takes no semicolon. */
    private val OPENERS_BY_SUFFIX = setOf("is", "then", "begin", "loop", "generate", "else", "record", "unaffected")

    /** Lines that are headers or labels rather than statements. */
    private val NEVER_TERMINATED = setOf("if", "elsif", "else", "case", "when", "while", "begin", "end")
}
