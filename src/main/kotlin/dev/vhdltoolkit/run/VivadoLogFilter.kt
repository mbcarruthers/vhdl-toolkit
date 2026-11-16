package dev.vhdltoolkit.run

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File

/**
 * Turns the file references in Vivado's output into clickable links.
 *
 * Vivado reports problems as:
 * ```
 * ERROR: [Synth 8-1031] counter4_ud is not declared [/home/me/src/top.vhd:12]
 * WARNING: [Synth 8-327] inferring latch for variable 'q' [/home/me/src/top.vhd:45]
 * ```
 * The trailing bracket holds the location. Without this the console is a wall
 * of text you have to navigate by hand, which is most of what makes building
 * outside the Vivado GUI unpleasant.
 */
class VivadoLogFilter(private val project: Project) : Filter {

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val match = LOCATION.find(line) ?: return null
        val path = match.groupValues[1]
        val lineNumber = match.groupValues[2].toIntOrNull() ?: return null

        val file = LocalFileSystem.getInstance().findFileByIoFile(File(path)) ?: return null

        // Offsets must be relative to the whole console, not this line.
        val lineStart = entireLength - line.length
        val start = lineStart + match.range.first + 1 // skip the '['
        val end = lineStart + match.range.last // before the ']'

        return Filter.Result(
            start,
            end,
            OpenFileHyperlinkInfo(project, file, (lineNumber - 1).coerceAtLeast(0)),
        )
    }

    private companion object {
        /** `[/absolute/path/file.vhd:123]` at the end of a message. */
        val LOCATION = Regex("""\[([^\[\]:]+\.(?:vhdl?|v|sv|xdc)):(\d+)]""", RegexOption.IGNORE_CASE)
    }
}
