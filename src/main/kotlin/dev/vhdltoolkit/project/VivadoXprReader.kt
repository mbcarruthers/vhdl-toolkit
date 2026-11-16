package dev.vhdltoolkit.project

import dev.vhdltoolkit.project.XmlDocuments.attr
import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

/**
 * Reads a Vivado project file (`.xpr`) into a [LibraryMappingModel].
 *
 * The reader is deliberately tolerant. Vivado's schema shifts between releases,
 * and a project that is half understood still beats no analysis at all, so
 * anything unrecognised becomes an [ImportWarning] instead of an exception.
 *
 * Structure it relies on, all of which has been stable across releases:
 *  - sources live in `<FileSet Name="..." Type="...">` blocks
 *  - each source is a `<File Path="...">`
 *  - a file's VHDL library is an `<Attr Name="Library" Val="..."/>` under it,
 *    defaulting to `xil_defaultlib`
 *  - paths use `$PPRDIR` (the directory holding the .xpr) and `$PSRCDIR`
 *    (the `<name>.srcs` directory) as variables
 */
object VivadoXprReader {

    private val VHDL_EXTENSIONS = setOf("vhd", "vhdl")
    private const val DEFAULT_LIBRARY = "xil_defaultlib"

    /** Filesets whose contents are simulation-only. */
    private val SIM_FILESET_TYPES = setOf("simulationsrcs")

    /** Filesets that never hold VHDL worth analysing. */
    private val IGNORED_FILESET_TYPES = setOf("constrs", "constraints", "utils", "blockdiagrams")

    fun read(xpr: Path): LibraryMappingModel {
        val warnings = ArrayList<ImportWarning>()
        val projectDir = xpr.toAbsolutePath().normalize().parent
            ?: return LibraryMappingModel(emptyList(), source = xpr.name, warnings = warnings)
        val projectName = xpr.nameWithoutExtension

        val doc = runCatching { XmlDocuments.parse(Files.newInputStream(xpr)) }.getOrElse { e ->
            return LibraryMappingModel(
                libraries = emptyList(),
                source = "Vivado project ${xpr.name}",
                warnings = listOf(ImportWarning("Could not parse ${xpr.name}: ${e.message}")),
            )
        }

        val vars = PathVariables(projectDir, projectName)
        val defaultLibrary = configOption(doc, "DefaultLib") ?: DEFAULT_LIBRARY
        val byLibrary = LinkedHashMap<String, MutableList<Path>>()
        var ipCoreCount = 0
        var missingCount = 0

        for (fileEl in XmlDocuments.elementsNamed(doc, "File")) {
            val rawPath = fileEl.attr("Path") ?: continue
            val fileSet = XmlDocuments.ancestorNamed(fileEl, "FileSet")
            val fileSetType = fileSet?.attr("Type")?.lowercase().orEmpty()
            if (IGNORED_FILESET_TYPES.any { fileSetType.contains(it) }) continue

            val resolved = vars.resolve(rawPath)
            if (resolved == null) {
                warnings.add(ImportWarning("Unresolved path variable in '$rawPath'"))
                continue
            }

            when (resolved.extension.lowercase()) {
                "xci", "bd" -> {
                    ipCoreCount++
                    continue
                }
                !in VHDL_EXTENSIONS -> continue // Verilog, SystemVerilog, constraints, etc.
            }

            if (!Files.exists(resolved)) {
                missingCount++
                continue
            }

            val library = libraryOf(fileEl) ?: defaultLibrary
            byLibrary.getOrPut(library) { ArrayList() }.add(resolved)
        }

        // IP-generated VHDL is produced on disk and is not listed in the .xpr.
        val generated = findGeneratedSources(projectDir, projectName)
        if (generated.isNotEmpty()) {
            byLibrary.getOrPut(defaultLibrary) { ArrayList() }.addAll(generated)
        }
        if (ipCoreCount > 0 && generated.isEmpty()) {
            warnings.add(
                ImportWarning(
                    "$ipCoreCount IP core(s) found but no generated sources on disk. " +
                        "Run 'Generate Output Products' in Vivado, then regenerate this file.",
                ),
            )
        }
        if (missingCount > 0) {
            warnings.add(ImportWarning("$missingCount source file(s) listed in the project do not exist on disk."))
        }

        val libraries = byLibrary.map { (name, files) ->
            SourceLibrary(name = name, files = files.distinct())
        }
        if (libraries.isEmpty()) {
            warnings.add(ImportWarning("No VHDL sources found. A Verilog-only project needs no VHDL-LS mapping."))
        }

        return LibraryMappingModel(
            libraries = libraries,
            standard = "2008",
            warnings = warnings,
            source = "Vivado project ${xpr.name}",
        ).normalized()
    }

    /**
     * `<Attr Name="Library" Val="..."/>` nested under the file element.
     *
     * Vivado only writes this when a file has been moved off the project
     * default, so most files in a typical project have none and fall back to
     * the project-wide `DefaultLib`.
     */
    private fun libraryOf(fileEl: Element): String? =
        XmlDocuments.elementsNamed(fileEl, "Attr")
            .firstOrNull { it.attr("Name").equals("Library", ignoreCase = true) }
            ?.attr("Val")

    /**
     * A `<Option Name="..." Val="..."/>` from the project `<Configuration>`
     * block, which is where the default library, target language and part live.
     */
    private fun configOption(doc: org.w3c.dom.Document, name: String): String? {
        val configuration = XmlDocuments.elementsNamed(doc, "Configuration").firstOrNull() ?: return null
        return XmlDocuments.elementsNamed(configuration, "Option")
            .firstOrNull { it.attr("Name").equals(name, ignoreCase = true) }
            ?.attr("Val")
    }

    /**
     * Vivado writes IP output products under `<project>.gen` (2020.2 and later)
     * or `<project>.srcs/sources_1/ip` (earlier). Both are scanned.
     */
    private fun findGeneratedSources(projectDir: Path, projectName: String): List<Path> {
        val roots = listOf(
            projectDir.resolve("$projectName.gen"),
            projectDir.resolve("$projectName.srcs").resolve("sources_1").resolve("ip"),
        ).filter { it.isDirectory() }

        if (roots.isEmpty()) return emptyList()
        val out = ArrayList<Path>()
        for (root in roots) {
            runCatching {
                Files.walk(root).use { stream ->
                    stream.filter { Files.isRegularFile(it) }
                        .filter { it.extension.lowercase() in VHDL_EXTENSIONS }
                        .forEach { out.add(it.normalize()) }
                }
            }
        }
        return out.distinct()
    }

    /**
     * Vivado path variables. `$PPRDIR` is the project directory; `$PSRCDIR` is
     * the sources directory beside it. Both `$VAR` and `$(VAR)` spellings occur.
     */
    private class PathVariables(projectDir: Path, projectName: String) {
        private val values = mapOf(
            "PPRDIR" to projectDir,
            "PSRCDIR" to projectDir.resolve("$projectName.srcs"),
            "PGENDIR" to projectDir.resolve("$projectName.gen"),
            "PCACHEDIR" to projectDir.resolve("$projectName.cache"),
            "PRUNDIR" to projectDir.resolve("$projectName.runs"),
            "PIPUSERFILESDIR" to projectDir.resolve("$projectName.ip_user_files"),
        )
        private val projectDir = projectDir

        /** Returns null when an unknown `$VAR` remains after substitution. */
        fun resolve(raw: String): Path? {
            var text = raw.trim().replace('\\', '/')
            for ((name, value) in values) {
                val replacement = value.toString().replace('\\', '/')
                text = text.replace("\$($name)", replacement).replace("\$$name", replacement)
            }
            if (text.contains('$')) return null
            val path = Path.of(text)
            return if (path.isAbsolute) path.normalize() else projectDir.resolve(path).normalize()
        }
    }

    /** True when this file looks like a Vivado project. */
    fun isXpr(path: Path): Boolean = path.extension.equals("xpr", ignoreCase = true)
}
