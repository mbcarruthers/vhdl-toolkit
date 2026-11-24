package dev.vhdltoolkit.project

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

/**
 * Reads an HDL file list (`.f`), the plain-text format simulators and
 * synthesis tools have shared for decades: one source path per line, `#`
 * comments, `-f` to include another list, and tool switches mixed in.
 *
 * NEORV32 ships `rtl/file_list_core.f` and its Vivado script does nothing
 * cleverer than read that file and substitute `$NEORV32_HOME`. Supporting the
 * format directly means such a project can be understood without running
 * Vivado at all.
 *
 * Variables are the only real difficulty: a list written for a build system
 * refers to `$SOMETHING_HOME` that is set by that build system and not by us.
 * Rather than give up, unresolved variables are inferred by probing the
 * ancestors of the list file itself — the root almost always *is* one of
 * them.
 */
object FileListReader {

    private val HDL_EXTENSIONS = setOf("vhd", "vhdl")

    /** Switches that carry no file path, or whose argument is not a source. */
    private val IGNORED_PREFIXES = listOf("+", "-sv", "-v ", "-y ", "-I", "--", "-D", "-L")

    fun isFileList(path: Path): Boolean = path.extension.equals("f", ignoreCase = true)

    fun read(listFile: Path, variables: Map<String, String> = emptyMap()): LibraryMappingModel {
        val warnings = ArrayList<ImportWarning>()
        val files = LinkedHashSet<Path>()
        val resolver = VariableResolver(listFile, variables, warnings)

        expand(listFile, files, resolver, warnings, depth = 0, seen = HashSet())

        if (files.isEmpty() && warnings.isEmpty()) {
            warnings.add(ImportWarning("No VHDL sources listed in ${listFile.fileName}."))
        }

        // A file list has no concept of a library: the tool that consumes it
        // supplies one (GHDL's --work, ModelSim's -work, Vivado's
        // set_property library). Everything goes into one library and the
        // validator reports it if the sources expect another name.
        if (files.isNotEmpty()) {
            warnings.add(
                ImportWarning(
                    "A file list does not record library names, so every source was placed in " +
                        "'${LibraryNames.DEFAULT_REPLACEMENT}'.",
                ),
            )
        }

        return LibraryMappingModel(
            libraries = if (files.isEmpty()) {
                emptyList()
            } else {
                listOf(SourceLibrary(LibraryNames.DEFAULT_REPLACEMENT, files.toList()))
            },
            standard = "2008",
            warnings = warnings,
            source = "file list ${listFile.fileName}",
        ).normalized()
    }

    private fun expand(
        listFile: Path,
        into: MutableSet<Path>,
        resolver: VariableResolver,
        warnings: MutableList<ImportWarning>,
        depth: Int,
        seen: MutableSet<Path>,
    ) {
        if (depth > 10) {
            warnings.add(ImportWarning("Stopped expanding ${listFile.fileName}: includes nested too deeply."))
            return
        }
        val canonical = runCatching { listFile.toRealPath() }.getOrDefault(listFile.normalize())
        if (!seen.add(canonical)) return // a list that includes itself

        val lines = runCatching { Files.readAllLines(listFile) }.getOrElse {
            warnings.add(ImportWarning("Could not read ${listFile.fileName}: ${it.message}"))
            return
        }
        val dir = listFile.toAbsolutePath().normalize().parent ?: return

        var i = 0
        while (i < lines.size) {
            val raw = lines[i].substringBefore('#').trim()
            i++
            if (raw.isEmpty()) continue

            // -f other.f, possibly with the path on the same token
            val include = INCLUDE.find(raw)
            if (include != null) {
                val target = resolver.resolve(include.groupValues[2], dir) ?: continue
                expand(target, into, resolver, warnings, depth + 1, seen)
                continue
            }

            if (IGNORED_PREFIXES.any { raw.startsWith(it) }) continue

            val path = resolver.resolve(raw, dir) ?: continue
            if (path.extension.lowercase() !in HDL_EXTENSIONS) continue
            if (!path.isRegularFile()) {
                warnings.add(ImportWarning("Listed file does not exist: $path"))
                continue
            }
            into.add(path.normalize())
        }
    }

    private val INCLUDE = Regex("""^-(f|F)\s+(.+)$""")
    private val VARIABLE = Regex("""\$\{?(\w+)}?""")

    /**
     * Substitutes `$VAR` and `${VAR}`, falling back to inference.
     *
     * The caller's map wins, then the environment. For anything still
     * unresolved, each ancestor directory of the list file is tried as the
     * value, and the first that makes the path exist is remembered for the
     * rest of the list. That handles the common shape where a list sits at
     * `$ROOT/rtl/files.f` and refers to `$ROOT/rtl/core/thing.vhd`.
     */
    private class VariableResolver(
        listFile: Path,
        private val provided: Map<String, String>,
        private val warnings: MutableList<ImportWarning>,
    ) {
        private val ancestors: List<Path> = buildList {
            var p: Path? = listFile.toAbsolutePath().normalize().parent
            while (p != null && size < 8) {
                add(p)
                p = p.parent
            }
        }
        private val inferred = HashMap<String, String>()
        private val reported = HashSet<String>()

        fun resolve(raw: String, relativeTo: Path): Path? {
            var text = raw.trim().trim('"').replace('\\', '/')
            if (text.isEmpty()) return null

            for (match in VARIABLE.findAll(text).toList()) {
                val name = match.groupValues[1]
                val value = provided[name]
                    ?: System.getenv(name)
                    ?: inferred[name]
                    ?: infer(name, text, match.value)
                if (value == null) {
                    if (reported.add(name)) {
                        warnings.add(ImportWarning("Could not resolve \$$name in the file list."))
                    }
                    return null
                }
                text = text.replace(match.value, value.replace('\\', '/'))
            }

            val path = runCatching { Path.of(text) }.getOrNull() ?: return null
            return if (path.isAbsolute) path.normalize() else relativeTo.resolve(path).normalize()
        }

        /** Tries each ancestor as the variable's value, keeping the one that works. */
        private fun infer(name: String, template: String, token: String): String? {
            val remainder = template.substringAfter(token).trimStart('/')
            for (base in ancestors) {
                if (base.resolve(remainder).isRegularFile()) {
                    inferred[name] = base.toString()
                    return base.toString()
                }
            }
            return null
        }
    }
}
