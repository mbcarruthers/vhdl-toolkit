package dev.vhdltoolkit.editing

/**
 * Decides what to insert when a block-opening line is completed.
 *
 * Kept free of IDE types so the behaviour can be tested as text in, text out.
 * The handler is then only responsible for talking to the editor.
 */
object VhdlBlockCompletion {

    /** Where the caret should come to rest in a construct that has a `begin`. */
    enum class CaretSpot {
        /** Above `begin`, among the declarations. */
        DECLARATIVE,

        /** Below `begin`, among the statements. */
        BODY,
    }

    /**
     * Where the caret goes by default, chosen by what the region is usually
     * used for rather than by symmetry.
     *
     * A process declarative part takes only variables and local types, and
     * most processes declare nothing at all, so landing there means stepping
     * past an empty region nearly every time. An architecture is the reverse:
     * it almost always declares signals before it can do anything.
     */
    fun defaultCaretSpot(construct: VhdlBlockStructure.Construct): CaretSpot =
        when (construct) {
            VhdlBlockStructure.Construct.PROCESS -> CaretSpot.BODY
            VhdlBlockStructure.Construct.SUBPROGRAM -> CaretSpot.BODY
            VhdlBlockStructure.Construct.GENERATE -> CaretSpot.BODY
            else -> CaretSpot.DECLARATIVE
        }

    data class Completion(
        /** Inserted at the caret; the caret itself does not move. */
        val textToInsert: String,
        val caretOffsetInInsertion: Int = 0,
        /**
         * Characters immediately before the caret that this replaces.
         *
         * Zero for the usual case, where the text is simply appended below.
         * Placing the caret in the body needs the `begin` to go *above* the
         * line the caret is on, and that line already holds its indent, so
         * the indent is replaced rather than left behind as a blank line.
         */
        val replaceBefore: Int = 0,
    )

    /**
     * @param caret position immediately after the newline that was just typed,
     *   with any automatic indent already applied
     * @return what to append below the caret, or null to leave the file alone
     */
    fun completionAt(
        text: CharSequence,
        caret: Int,
        indentSize: Int,
        endStyle: VhdlBlockStructure.EndStyle,
        insertBegin: Boolean,
        useTabs: Boolean = false,
        tabSize: Int = VhdlBlockStructure.DEFAULT_TAB_SIZE,
        caretSpot: (VhdlBlockStructure.Construct) -> CaretSpot = ::defaultCaretSpot,
    ): Completion? {
        if (caret <= 0 || caret > text.length) return null

        // Only complete at the end of a line: pressing Enter in the middle of
        // existing code should not push an `end` into the middle of it.
        if (!restOfLineIsBlank(text, caret)) return null

        val previousLineStart = startOfPreviousLine(text, caret) ?: return null

        val before = VhdlBlockStructure.analyze(text, previousLineStart, tabSize)
        val after = VhdlBlockStructure.analyze(text, caret, tabSize)

        // The line just finished must have opened something.
        val opened = after.frames.lastOrNull {
            it.construct != VhdlBlockStructure.Construct.PAREN &&
                it.construct != VhdlBlockStructure.Construct.WHEN
        } ?: return null
        if (opened.openOffset < previousLineStart) return null
        if (after.depth <= before.depth) return null

        // An unclosed `(` means the header itself is unfinished.
        if (after.frames.lastOrNull()?.construct == VhdlBlockStructure.Construct.PAREN) return null

        val closer = VhdlBlockStructure.closerFor(opened, endStyle) ?: return null
        if (VhdlBlockStructure.isClosedAfter(text, opened, caret)) return null

        val pad = VhdlBlockStructure.renderIndent(opened.headerColumn, useTabs, tabSize)
        val wantsBegin = insertBegin && opened.construct.takesBegin
        val wantsBody = wantsBegin && caretSpot(opened.construct) == CaretSpot.BODY

        if (!wantsBody) {
            val inserted = buildString {
                if (wantsBegin) append('\n').append(pad).append("begin")
                append('\n').append(pad).append(closer)
            }
            return Completion(inserted)
        }

        // The caret belongs below `begin`, so `begin` has to be written above
        // the line the caret sits on. That line is already indented, and the
        // indent is what gets replaced — otherwise it would be left behind as
        // a stray blank line above `begin`.
        val existingIndent = indentBefore(text, caret)
        val body = VhdlBlockStructure.renderIndent(opened.headerColumn + indentSize, useTabs, tabSize)
        val head = buildString {
            append(pad).append("begin").append('\n').append(body)
        }
        return Completion(
            textToInsert = head + "\n" + pad + closer,
            caretOffsetInInsertion = head.length,
            replaceBefore = existingIndent,
        )
    }

    /** How much whitespace sits between the start of the caret's line and it. */
    private fun indentBefore(text: CharSequence, caret: Int): Int {
        var i = caret
        while (i > 0 && text[i - 1] != '\n' && (text[i - 1] == ' ' || text[i - 1] == '\t')) i--
        return caret - i
    }

    private fun restOfLineIsBlank(text: CharSequence, from: Int): Boolean {
        var i = from
        while (i < text.length && text[i] != '\n') {
            if (!text[i].isWhitespace()) return false
            i++
        }
        return true
    }

    /** Start offset of the line before the one containing [offset]. */
    private fun startOfPreviousLine(text: CharSequence, offset: Int): Int? {
        val currentLineStart = startOfLine(text, offset)
        if (currentLineStart == 0) return null
        return startOfLine(text, currentLineStart - 1)
    }

    private fun startOfLine(text: CharSequence, offset: Int): Int {
        var i = (offset - 1).coerceAtMost(text.length - 1)
        while (i >= 0 && text[i] != '\n') i--
        return i + 1
    }
}
