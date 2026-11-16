package dev.vhdltoolkit.project

import java.nio.file.Path

/**
 * The shared description of "which source files belong to which VHDL library".
 *
 * Every importer (Vivado `.xpr`, Sigasi `.library_mapping.xml`, and whatever
 * comes later) produces this, and the single toml writer consumes it. Keeping
 * one model means library-naming rules and path handling are implemented once.
 */
data class SourceLibrary(
    val name: String,
    /** Absolute paths to source files, de-duplicated and in discovery order. */
    val files: List<Path>,
    /**
     * Vendor or generated code. VHDL-LS still analyses third-party libraries but
     * does not report style or correctness diagnostics inside them, which keeps
     * vendor sources from flooding the problem list.
     */
    val isThirdParty: Boolean = false,
)

/**
 * A problem found while importing. Importers never throw on malformed input:
 * a partially understood project is more useful than none, so they record what
 * they could not interpret and carry on.
 */
data class ImportWarning(val message: String)

data class LibraryMappingModel(
    val libraries: List<SourceLibrary>,
    /** VHDL revision, e.g. "2008". Null means leave it to the VHDL-LS default. */
    val standard: String? = null,
    val warnings: List<ImportWarning> = emptyList(),
    /** Human-readable note on where this came from, used in the file header. */
    val source: String = "unknown",
) {
    val fileCount: Int get() = libraries.sumOf { it.files.size }

    /** Drops empty libraries and merges any that ended up sharing a name. */
    fun normalized(): LibraryMappingModel {
        val merged = LinkedHashMap<String, MutableList<Path>>()
        val thirdParty = LinkedHashMap<String, Boolean>()
        for (lib in libraries) {
            val bucket = merged.getOrPut(lib.name) { ArrayList() }
            for (f in lib.files) if (f !in bucket) bucket.add(f)
            // A library is only third-party if every contributor says so.
            thirdParty[lib.name] = (thirdParty[lib.name] ?: true) && lib.isThirdParty
        }
        val libs = merged.entries
            .filter { it.value.isNotEmpty() }
            .map { SourceLibrary(it.key, it.value, thirdParty[it.key] == true) }
        return copy(libraries = libs)
    }
}

object LibraryNames {

    /**
     * `work` is not a library in VHDL: it is an alias for whichever library the
     * current unit is being compiled into. VHDL-LS rejects a config that defines
     * one, case-insensitively, so any importer that encounters it must rename.
     * Source code may still say `work.foo` — that keeps resolving.
     */
    const val DEFAULT_REPLACEMENT = "defaultlib"

    fun isReserved(name: String): Boolean = name.equals("work", ignoreCase = true)

    /**
     * VHDL basic identifiers are letters, digits and underscores, starting with
     * a letter. Library names that would not round-trip are rewritten rather
     * than emitted and rejected later.
     */
    fun sanitize(raw: String): String {
        if (isReserved(raw)) return DEFAULT_REPLACEMENT
        val cleaned = buildString {
            for ((i, c) in raw.withIndex()) {
                val ok = if (i == 0) c.isLetter() else c.isLetterOrDigit() || c == '_'
                append(if (ok) c else '_')
            }
        }
        val trimmed = cleaned.trim('_')
        return when {
            trimmed.isEmpty() -> DEFAULT_REPLACEMENT
            !trimmed.first().isLetter() -> "lib_$trimmed"
            else -> trimmed
        }
    }
}
