package dev.vhdltoolkit.editing

/**
 * Decides what to insert when a block-opening line is completed.
 *
 * Kept free of IDE types so the behaviour can be tested as text in, text out.
 * The handler is then only responsible for talking to the editor.
 */
object VhdlBlockCompletion {

    data class Completion(
        /** Inserted at the caret; the caret itself does not move. */
        val textToInsert: String,
        val caretOffsetInInsertion: Int = 0,
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
    ): Completion? {
        if (caret <= 0 || caret > text.length) return null

        // Only complete at the end of a line: pressing Enter in the middle of
        // existing code should not push an `end` into the middle of it.
        if (!restOfLineIsBlank(text, caret)) return null

        val previousLineStart = startOfPreviousLine(text, caret) ?: return null

        val before = VhdlBlockStructure.analyze(text, previousLineStart)
        val after = VhdlBlockStructure.analyze(text, caret)

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

        val pad = " ".repeat(opened.headerColumn)
        val wantsBegin = insertBegin && opened.construct.takesBegin

        val inserted = buildString {
            if (wantsBegin) {
                append('\n').append(pad).append("begin")
            }
            append('\n').append(pad).append(closer)
        }
        return Completion(inserted)
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
