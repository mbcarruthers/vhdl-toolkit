package dev.vhdltoolkit.project

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name

/**
 * Builds a mapping by walking the tree for VHDL sources.
 *
 * The fallback when nothing describes the project. Plenty of VHDL lives in a
 * plain folder, or is built by a Makefile, a shell script or VUnit — none of
 * which leaves anything readable behind. Previously such a project got no
 * mapping at all, which meant no cross-file resolution: every file analysed
 * alone, every reference to another file unresolved.
 *
 * It is a guess, and it is offered rather than applied. Three things it
 * cannot know: which files are genuinely part of the design rather than
 * vendored copies, whether the sources were meant to be split across several
 * libraries, and what those libraries are called. The reference validator
 * catches the third.
 */
object SourceScanReader {

    private val HDL_EXTENSIONS = setOf("vhd", "vhdl")

    /** Directories that hold generated or foreign code, not project sources. */
    private val SKIPPED_NAMES = setOf(
        "build", "out", "target", "node_modules", "__pycache__",
        "vunit_out", "work", "ghdl_out", "sim_build", "packaged_ip",
    )

    /** Vivado's generated trees, recognised by suffix. */
    private val SKIPPED_SUFFIXES = listOf(
        ".runs", ".cache", ".gen", ".hw", ".sim", ".ip_user_files", ".srcs", ".Xil",
    )

    private const val MAX_FILES = 5000
    private const val MAX_DEPTH = 12

    fun read(root: Path): LibraryMappingModel {
        val warnings = ArrayList<ImportWarning>()
        val files = ArrayList<Path>()
        var truncated = false

        fun walk(dir: Path, depth: Int) {
            if (depth > MAX_DEPTH || truncated) return
            val entries = runCatching { Files.list(dir).use { it.toList() } }.getOrNull() ?: return
            for (entry in entries.sorted()) {
                if (files.size >= MAX_FILES) {
                    truncated = true
                    return
                }
                when {
                    Files.isDirectory(entry) -> if (!isSkipped(entry)) walk(entry, depth + 1)
                    entry.extension.lowercase() in HDL_EXTENSIONS -> files.add(entry.normalize())
                }
            }
        }
        walk(root, 0)

        if (files.isEmpty()) {
            return LibraryMappingModel(
                libraries = emptyList(),
                source = "source scan",
                warnings = listOf(ImportWarning("No VHDL sources found under $root.")),
            )
        }

        if (truncated) {
            warnings.add(ImportWarning("Stopped after $MAX_FILES files; the mapping may be incomplete."))
        }
        warnings.add(
            ImportWarning(
                "No project file was found, so every VHDL source under the project root was placed " +
                    "in '${LibraryNames.DEFAULT_REPLACEMENT}'. Edit this file if your design uses " +
                    "several libraries or excludes some of these sources.",
            ),
        )

        return LibraryMappingModel(
            libraries = listOf(SourceLibrary(LibraryNames.DEFAULT_REPLACEMENT, files)),
            standard = "2008",
            warnings = warnings,
            source = "source scan",
        ).normalized()
    }

    private fun isSkipped(dir: Path): Boolean {
        val name = dir.name
        if (name.startsWith(".")) return true
        if (name.lowercase() in SKIPPED_NAMES) return true
        return SKIPPED_SUFFIXES.any { name.endsWith(it, ignoreCase = true) }
    }
}
