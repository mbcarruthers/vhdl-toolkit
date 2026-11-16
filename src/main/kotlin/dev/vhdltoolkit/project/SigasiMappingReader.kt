package dev.vhdltoolkit.project

import dev.vhdltoolkit.project.XmlDocuments.attr
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory

/**
 * Reads a Sigasi `.library_mapping.xml` into a [LibraryMappingModel], so a
 * project already set up in Sigasi Studio works here without being re-described
 * by hand.
 *
 * Sigasi maps *locations* (folders, recursively) to libraries:
 * ```xml
 * <Mappings Location="" Library="work"/>
 * <Mappings Location="Common Libraries/IEEE" Library="ieee"/>
 * <Mappings Location="src/legacy" Library="not mapped"/>
 * ```
 * An empty location is the project root. Two kinds of entry are dropped:
 * `Common Libraries/*`, which are Sigasi's own bundled `std`/`ieee` sources —
 * VHDL-LS ships its own copies — and anything mapped to `not mapped`, which is
 * Sigasi's way of excluding a folder.
 */
object SigasiMappingReader {

    const val FILE_NAME = ".library_mapping.xml"

    private val VHDL_EXTENSIONS = setOf("vhd", "vhdl")
    private const val EXCLUDED_LIBRARY = "not mapped"
    private const val BUILT_IN_PREFIX = "common libraries"

    fun read(mappingFile: Path): LibraryMappingModel {
        val warnings = ArrayList<ImportWarning>()
        val projectDir = mappingFile.toAbsolutePath().normalize().parent
            ?: return LibraryMappingModel(emptyList(), source = FILE_NAME)

        val doc = runCatching { XmlDocuments.parse(Files.newInputStream(mappingFile)) }.getOrElse { e ->
            return LibraryMappingModel(
                libraries = emptyList(),
                source = "Sigasi $FILE_NAME",
                warnings = listOf(ImportWarning("Could not parse $FILE_NAME: ${e.message}")),
            )
        }

        // Collect rules first; a deeper location wins over a shallower one.
        data class Rule(val dir: Path, val depth: Int, val library: String)

        val rules = ArrayList<Rule>()
        val excluded = ArrayList<Path>()

        for (el in XmlDocuments.elementsNamed(doc, "Mappings")) {
            val location = el.attr("Location").orEmpty().trim().replace('\\', '/')
            val library = el.attr("Library")?.trim() ?: continue

            if (location.lowercase().startsWith(BUILT_IN_PREFIX)) continue // Sigasi's own std/ieee

            val dir = if (location.isEmpty()) projectDir else projectDir.resolve(location).normalize()
            val depth = if (location.isEmpty()) 0 else location.trim('/').count { it == '/' } + 1

            if (library.equals(EXCLUDED_LIBRARY, ignoreCase = true)) {
                excluded.add(dir)
            } else {
                rules.add(Rule(dir, depth, library))
            }
        }

        if (rules.isEmpty()) {
            warnings.add(ImportWarning("No project libraries found in $FILE_NAME."))
            return LibraryMappingModel(emptyList(), source = "Sigasi $FILE_NAME", warnings = warnings)
        }

        val byLibrary = LinkedHashMap<String, MutableList<Path>>()
        for (file in collectVhdlFiles(projectDir)) {
            if (excluded.any { file.startsWith(it) }) continue
            // Most specific mapping that contains this file.
            val rule = rules
                .filter { file.startsWith(it.dir) }
                .maxByOrNull { it.depth }
                ?: continue
            byLibrary.getOrPut(rule.library) { ArrayList() }.add(file)
        }

        val renamed = byLibrary.keys.filter { LibraryNames.isReserved(it) }
        if (renamed.isNotEmpty()) {
            warnings.add(
                ImportWarning(
                    "Sigasi library 'work' renamed to '${LibraryNames.DEFAULT_REPLACEMENT}'; " +
                        "VHDL-LS rejects 'work' as a library name. References to work.* in source still resolve.",
                ),
            )
        }

        return LibraryMappingModel(
            libraries = byLibrary.map { (name, files) -> SourceLibrary(name, files.distinct()) },
            standard = "2008",
            warnings = warnings,
            source = "Sigasi $FILE_NAME",
        ).normalized()
    }

    private fun collectVhdlFiles(root: Path): List<Path> {
        if (!root.isDirectory()) return emptyList()
        val out = ArrayList<Path>()
        runCatching {
            Files.walk(root).use { stream ->
                stream.filter { Files.isRegularFile(it) }
                    .filter { it.extension.lowercase() in VHDL_EXTENSIONS }
                    .filter { path -> path.none { it.toString().startsWith(".") } }
                    .forEach { out.add(it.normalize()) }
            }
        }
        return out.sorted()
    }
}
