package dev.vhdltoolkit.editing

import dev.vhdltoolkit.lexer.VhdlTokenizer

/**
 * Lines up declarations, associations and assignments into columns.
 *
 * The obvious reading of the problem is a list of constructs — align port
 * declarations, align signal declarations, align record fields — but those
 * are the same operation wearing different hats. All of them put a `:` in a
 * column. What actually differs between them is nothing, and writing them
 * separately would be several chances for them to disagree.
 *
 * What does differ is the token a column is built around, and there are only
 * five: `:`, `:=`, `<=`, `=>` and a trailing comment. So a line is read as a
 * row of cells split at those tokens, and cells at the same index across
 * neighbouring rows share a column. One pass lines up every construct, and
 * lines that happen to carry two anchors get both columns rather than one:
 *
 * ```vhdl
 * constant WIDTH : integer := 8;   -- data path
 * constant N     : integer := 16;  -- counters
 * ```
 *
 * Rows join a run only when their anchors match in kind and number, so a
 * signal assignment never drags a declaration out of shape, and a line
 * carrying no anchor at all — `begin`, `);`, a header — ends the run without
 * needing to be recognised as anything in particular.
 */
object VhdlAlignment {

    /**
     * A column's worth of a line: the text, trimmed, and the token that ends
     * it. The spacing around that token is this object's to decide, not the
     * author's.
     *
     * [anchor] is null for a column that is padded but separated by nothing —
     * a port's mode, which wants a column of its own without a symbol between
     * it and the type that follows.
     */
    data class Cell(val content: String, val anchor: String?)

    /**
     * One line, read as cells. [tail] is whatever follows the last anchor and
     * is left exactly as written.
     */
    data class Row(
        val line: Int,
        val indent: String,
        val cells: List<Cell>,
        val tail: String,
        /** The line exactly as written, for deciding whether anything moved. */
        val raw: String,
    ) {
        /** Two rows belong to the same run when their shapes match. */
        val shape: List<String> get() = cells.map { it.anchor ?: BARE_COLUMN }
    }

    /** A line to replace, and what to replace it with. */
    data class Edit(val line: Int, val text: String)

    /** `--` is not an operator, so it is recognised by the tokenizer's kind. */
    const val COMMENT_ANCHOR = "--"

    private val OPERATOR_ANCHORS = setOf(":", ":=", "<=", "=>")

    /**
     * Port modes. A mode is not a separator, but it reads as a column: with
     * `in`, `out` and `inout` ragged, the types behind them never line up,
     * which is most of what makes a port clause look unfinished. `buffer`
     * and `linkage` are here for the same reason — a port using one would
     * otherwise be the single line that refuses to join its neighbours.
     */
    private val MODES = setOf("in", "out", "inout", "buffer", "linkage")

    /** Stands in for a padded column with no separator, when comparing shapes. */
    private const val BARE_COLUMN = "\u0000mode"


    /**
     * A trailing comment reads better set further off than an operator does,
     * and this is the width every formatter seems to settle on.
     */
    private const val COMMENT_GAP = 2

    // ------------------------------------------------------------- aligning

    /**
     * Aligns the run of lines containing [line], returning only the lines
     * whose text actually changes.
     *
     * Returns nothing when the line carries no anchor, or when it stands
     * alone — a single row has no neighbour to line up with, and padding it
     * would only add trailing space.
     */
    fun alignAround(text: CharSequence, line: Int, tabSize: Int = 4): List<Edit> {
        val lines = text.lines()
        if (line !in lines.indices) return emptyList()

        val anchor = rowOf(lines, line) ?: return emptyList()
        val run = runAround(lines, anchor)
        if (run.size < 2) return emptyList()

        return align(run, tabSize)
    }

    /**
     * Aligns every run between [from] and [to] inclusive, which is what a
     * selection asks for.
     */
    fun alignRange(text: CharSequence, from: Int, to: Int, tabSize: Int = 4): List<Edit> {
        val lines = text.lines()
        val edits = ArrayList<Edit>()
        var at = from.coerceAtLeast(0)
        val last = to.coerceAtMost(lines.lastIndex)

        while (at <= last) {
            val row = rowOf(lines, at)
            if (row == null) {
                at++
                continue
            }
            // The run may reach outside the selection; only its rows inside
            // are rewritten, but the whole of it decides where to resume so
            // the walk cannot stall on a row it has already handled.
            val run = runAround(lines, row)
            val selected = run.filter { it.line in from..last }
            if (selected.size >= 2) edits += align(selected, tabSize)
            at = maxOf(run.last().line, at) + 1
        }
        return edits
    }

    private fun align(run: List<Row>, tabSize: Int): List<Edit> {
        val widths = IntArray(run.first().cells.size) { column ->
            run.maxOf { displayWidth(it.cells[column].content, tabSize) }
        }

        return run.mapNotNull { row ->
            val rendered = render(row, widths, tabSize)
            if (rendered == row.raw) null else Edit(row.line, rendered)
        }
    }

    private fun render(row: Row, widths: IntArray, tabSize: Int): String = buildString {
        append(row.indent)
        row.cells.forEachIndexed { index, cell ->
            append(cell.content)
            repeat(widths[index] - displayWidth(cell.content, tabSize)) { append(' ') }

            if (cell.anchor == null) {
                // Nothing separates a mode from its type but the gap that
                // squares the column.
                append(' ')
                return@forEachIndexed
            }

            // A cell with no content carries no separator before its anchor,
            // which keeps `y <= a;` from becoming ` y <= a;`.
            if (cell.content.isNotEmpty()) {
                repeat(if (cell.anchor == COMMENT_ANCHOR) COMMENT_GAP else 1) { append(' ') }
            }
            append(cell.anchor)
            if (row.tail.isNotEmpty() || index < row.cells.lastIndex) append(' ')
        }
        append(row.tail)
    }.trimEnd()

    // -------------------------------------------------------------- reading

    /** The run of rows sharing [seed]'s shape, with [seed] inside it. */
    private fun runAround(lines: List<String>, seed: Row): List<Row> {
        val run = ArrayDeque<Row>()
        run.add(seed)

        for (direction in listOf(-1, 1)) {
            var at = seed.line + direction
            while (at in lines.indices) {
                // A blank line is a deliberate separation: lining up across
                // it would force a group's widest name onto lines the author
                // had set apart.
                if (lines[at].isBlank()) break

                // A comment on its own line is a heading, not a row. It
                // neither joins the run nor ends it, so a commented block
                // still lines up as one.
                if (isCommentOnly(lines[at])) {
                    at += direction
                    continue
                }

                val row = rowOf(lines, at) ?: break
                if (row.shape != seed.shape) break
                if (direction < 0) run.addFirst(row) else run.addLast(row)
                at += direction
            }
        }
        return run.toList()
    }

    /** Splits a line into cells, or null when it carries no anchor. */
    fun rowOf(lines: List<String>, line: Int): Row? {
        if (line !in lines.indices) return null
        val text = lines[line]
        if (text.isBlank() || isCommentOnly(text)) return null

        val indent = text.takeWhile { it == ' ' || it == '\t' }
        val cells = ArrayList<Cell>()
        var cellStart = indent.length
        var depth = 0

        // Indexed rather than streamed, because a mode is recognised by what
        // follows the colon and so needs a look ahead.
        val tokens = VhdlTokenizer.tokenize(text).toList()

        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            val value = text.substring(token.start, token.end)

            when {
                token.kind == VhdlTokenizer.Kind.LPAREN -> depth++
                token.kind == VhdlTokenizer.Kind.RPAREN -> depth--

                // An anchor inside brackets belongs to the expression it sits
                // in — the `=>` of an aggregate, the `:` of a qualified
                // choice — not to the line's own shape.
                depth != 0 -> Unit

                token.kind == VhdlTokenizer.Kind.OPERATOR && value in OPERATOR_ANCHORS -> {
                    cells += Cell(text.substring(cellStart, token.start).trim(), value)
                    cellStart = token.end

                    if (value == ":") {
                        val mode = tokens.drop(i + 1).firstOrNull { it.kind != VhdlTokenizer.Kind.WHITESPACE }
                        val word = mode?.let { text.substring(it.start, it.end) }
                        if (word != null && word.lowercase() in MODES) {
                            cells += Cell(word, null)
                            cellStart = mode.end
                            i = tokens.indexOf(mode)
                        }
                    }
                }

                token.kind == VhdlTokenizer.Kind.LINE_COMMENT -> {
                    // Only the marker anchors; the comment's own text is tail.
                    cells += Cell(text.substring(cellStart, token.start).trim(), COMMENT_ANCHOR)
                    cellStart = token.start + COMMENT_ANCHOR.length
                }

                else -> Unit
            }
            i++
        }

        if (cells.isEmpty()) return null
        return Row(line, indent, cells, text.substring(cellStart).trim(), text)
    }

    private fun isCommentOnly(text: String): Boolean = text.trimStart().startsWith("--")

    private fun displayWidth(text: String, tabSize: Int): Int {
        var width = 0
        for (c in text) {
            width = if (c == '\t' && tabSize > 0) (width / tabSize + 1) * tabSize else width + 1
        }
        return width
    }
}
