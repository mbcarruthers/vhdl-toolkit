package dev.vhdltoolkit.project

import dev.vhdltoolkit.lexer.VhdlSemanticClassifier
import java.nio.file.Files
import java.nio.file.Path

/**
 * Checks a mapping against what the sources actually reference.
 *
 * Every import format describes libraries differently, and one of them —
 * the `.f` file list — cannot describe them at all, because the format has no
 * concept of a library. Rather than guess a name per format, this compares
 * the libraries the code *uses* with the ones the mapping *defines* and
 * reports the difference.
 *
 * That is deliberately a statement of fact rather than an inference. It
 * catches a file list whose sources expect a named library, an `.xpr` whose
 * vendor libraries are unmapped, and a source scan that guessed a name —
 * without any of those cases needing their own special rule.
 */
object LibraryReferenceValidator {

    /**
     * Libraries that never need mapping: the ones every VHDL tool provides,
     * plus `work`, which names whichever library a file is compiled into.
     */
    private val IMPLICIT = setOf("std", "ieee", "work")

    /**
     * Vendor libraries. Their absence is worth reporting separately, since
     * the fix is installing or locating a toolchain rather than correcting
     * the mapping.
     */
    private val VENDOR = setOf(
        // AMD/Xilinx
        "unisim", "unimacro", "unifast", "xpm", "xilinxcorelib", "secureip",
        "simprim", "unisims_ver", "xil_defaultlib",
        // Intel/Altera
        "altera", "altera_mf", "altera_lnsim", "lpm", "arriav", "cyclonev", "stratixv",
        // Lattice / Microchip
        "ecp5u", "machxo2", "ice40up", "polarfire", "igloo2",
    )

    /** Reading every source has a cost; a very large project is sampled. */
    private const val MAX_FILES = 2000
    private const val MAX_BYTES = 32L * 1024 * 1024

    data class Findings(
        /** Referenced, not defined, not implicit, not vendor. */
        val unmappedProjectLibraries: Set<String>,
        /** Referenced vendor libraries that are not mapped. */
        val unmappedVendorLibraries: Set<String>,
        val filesScanned: Int,
        val truncated: Boolean,
    )

    fun inspect(model: LibraryMappingModel): Findings {
        val defined = model.libraries.map { LibraryNames.sanitize(it.name).lowercase() }.toMutableSet()
        // `work` resolves to whichever library the file belongs to, and the
        // sanitizer renames a `work` mapping, so treat both spellings as met.
        defined.addAll(model.libraries.map { it.name.lowercase() })

        val referenced = HashSet<String>()
        var scanned = 0
        var bytes = 0L
        var truncated = false

        outer@ for (library in model.libraries) {
            for (file in library.files) {
                if (scanned >= MAX_FILES || bytes >= MAX_BYTES) {
                    truncated = true
                    break@outer
                }
                val text = readIfReasonable(file) ?: continue
                bytes += text.length
                scanned++
                referenced.addAll(librariesReferencedIn(text))
            }
        }

        val outstanding = referenced
            .map { it.lowercase() }
            .filterNot { it in IMPLICIT }
            .filterNot { it in defined }
            .toSet()

        return Findings(
            unmappedProjectLibraries = outstanding.filterNot { it in VENDOR }.toSet(),
            unmappedVendorLibraries = outstanding.filter { it in VENDOR }.toSet(),
            filesScanned = scanned,
            truncated = truncated,
        )
    }

    /** Returns [model] with any findings added as warnings. */
    fun validate(model: LibraryMappingModel): LibraryMappingModel {
        if (model.libraries.isEmpty()) return model
        val findings = runCatching { inspect(model) }.getOrNull() ?: return model

        val extra = ArrayList<ImportWarning>()

        if (findings.unmappedProjectLibraries.isNotEmpty()) {
            val names = findings.unmappedProjectLibraries.sorted().joinToString(", ")
            val defined = model.libraries.joinToString(", ") { LibraryNames.sanitize(it.name) }
            extra.add(
                ImportWarning(
                    "Sources reference librar${plural(findings.unmappedProjectLibraries.size)} $names, " +
                        "which the mapping does not define (it defines: $defined). " +
                        "If these sources belong in that library, rename it here.",
                ),
            )
        }

        if (findings.unmappedVendorLibraries.isNotEmpty()) {
            val names = findings.unmappedVendorLibraries.sorted().joinToString(", ")
            extra.add(
                ImportWarning(
                    "Sources reference vendor librar${plural(findings.unmappedVendorLibraries.size)} " +
                        "$names, which could not be located. Primitives from them will appear undefined.",
                ),
            )
        }

        return if (extra.isEmpty()) model else model.copy(warnings = model.warnings + extra)
    }

    private fun plural(n: Int) = if (n == 1) "y" else "ies"

    /**
     * Library names in the context clauses: `library ieee;` and the first
     * element of `use ieee.std_logic_1164.all`.
     *
     * Classified rather than pattern-matched, so the word "library" inside a
     * comment or a string does not count.
     */
    fun librariesReferencedIn(text: CharSequence): Set<String> =
        VhdlSemanticClassifier.classify(text)
            .asSequence()
            .filter { it.category == VhdlSemanticClassifier.Category.LIBRARY }
            .map { text.subSequence(it.start, it.end).toString().lowercase() }
            .toSet()

    private fun readIfReasonable(file: Path): String? = runCatching {
        if (Files.size(file) > 4L * 1024 * 1024) return null // not hand-written VHDL
        Files.readString(file)
    }.getOrNull()
}
