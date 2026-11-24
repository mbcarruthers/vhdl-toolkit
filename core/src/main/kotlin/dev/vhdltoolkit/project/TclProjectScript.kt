package dev.vhdltoolkit.project

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory

/**
 * Works out what a Vivado Tcl script is for.
 *
 * Most serious projects ship a Tcl script rather than a `.xpr`, because the
 * project file is generated, machine-specific and version-locked. But "a Tcl
 * script" covers several unrelated things, and only some of them produce a
 * project at all:
 *
 *  - **Project mode** — `create_project`, `add_files`. Writes a `.xpr`. This
 *    is the "rebuild my project" format that `write_project_tcl` emits.
 *  - **Non-project mode** — `read_vhdl`, `synth_design`, `write_bitstream`.
 *    Vivado holds the design in memory and never writes a `.xpr`. Common in
 *    CI, because there is no binary artifact to version.
 *  - **IP packaging** — `ipx::` commands. Creates a scratch project on the
 *    way to producing an IP core.
 *  - **Block design** — `create_bd_design`, from `write_bd_tcl`.
 *
 * Classification only looks for marker commands. Tcl is a real programming
 * language — the scripts use `proc`, `glob`, `foreach` and variable expansion
 * — so anything resembling a parse would break constantly. Deciding *which
 * kind* of script this is needs far less than understanding it.
 */
object TclProjectScript {

    enum class Kind {
        /** Creates a Vivado project we can then import. */
        PROJECT_MODE,

        /** Builds without a project; no `.xpr` will ever exist. */
        NON_PROJECT_MODE,

        /** Packages IP, creating a scratch project as a side effect. */
        IP_PACKAGING,

        /** Builds a block design. */
        BLOCK_DESIGN,

        /** Not recognisably a Vivado build script. */
        UNKNOWN,
    }

    data class Analysis(
        val kind: Kind,
        /** True when running it leaves a `.xpr` behind. */
        val createsProject: Boolean,
        /** Project name from `create_project`, when it is a literal. */
        val projectName: String?,
    ) {
        /** Worth offering to run, because importing can follow. */
        val isImportable: Boolean get() = createsProject
    }

    fun classify(text: CharSequence): Analysis {
        val code = stripComments(text)

        val createsProject = CREATE_PROJECT.containsMatchIn(code)
        val kind = when {
            IPX.containsMatchIn(code) -> Kind.IP_PACKAGING
            CREATE_BD.containsMatchIn(code) -> Kind.BLOCK_DESIGN
            createsProject -> Kind.PROJECT_MODE
            NON_PROJECT.containsMatchIn(code) -> Kind.NON_PROJECT_MODE
            else -> Kind.UNKNOWN
        }

        return Analysis(
            kind = kind,
            createsProject = createsProject,
            projectName = projectNameIn(code),
        )
    }

    /**
     * The project name from `create_project [options] <name> <dir>`.
     *
     * Tokenised rather than matched, because options that take an argument
     * and options that do not look identical: in
     * `create_project -force -part xc7a35t mydesign ./build` a pattern cannot
     * tell that `-force` stands alone while `xc7a35t` belongs to `-part`. The
     * name is the first token that is neither an option nor an option's
     * argument.
     */
    private fun projectNameIn(code: String): String? {
        val line = CREATE_PROJECT.find(code)?.groupValues?.get(1)?.substringBefore('\n') ?: return null

        val tokens = line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            if (token.startsWith("-")) {
                i += if (token.lowercase() in OPTIONS_WITH_ARGUMENT) 2 else 1
                continue
            }
            val name = token.trim('"', '{', '}', '\'')
            // A name built from a variable cannot be known without running it.
            return name.takeIf { it.isNotBlank() && !it.contains('$') }
        }
        return null
    }

    /** `create_project` options that consume the token after them. */
    private val OPTIONS_WITH_ARGUMENT = setOf("-part", "-template", "-ip_name")

    fun classify(file: Path): Analysis? =
        runCatching { classify(Files.readString(file)) }.getOrNull()

    /** True for a file extension we would consider analysing. */
    fun isTclScript(path: Path): Boolean = path.extension.equals("tcl", ignoreCase = true)

    /**
     * Tcl comments run from an unquoted `#` to end of line. Stripping only
     * whole comment lines is deliberate: a `#` inside a brace-quoted value is
     * not a comment, and treating it as one could hide a `create_project`.
     * Missing a trailing comment never changes the classification.
     */
    private fun stripComments(text: CharSequence): String =
        text.lineSequence()
            .filterNot { it.trimStart().startsWith("#") }
            .joinToString("\n")

    /** `create_project` and whatever follows it on that line. */
    private val CREATE_PROJECT = Regex("""\bcreate_project\b(.*)""")

    private val IPX = Regex("""\bipx::\w+""")
    private val CREATE_BD = Regex("""\bcreate_bd_design\b""")

    /** Commands that only appear when building without a project. */
    private val NON_PROJECT = Regex(
        """\b(?:read_vhdl|read_verilog|read_xdc|read_checkpoint|synth_design|link_design)\b""",
    )

    /**
     * Finds the `.xpr` a script just produced, searching [root] and its
     * immediate subdirectories. Scripts put the project wherever they like,
     * so the newest one written after [since] is a surer bet than guessing a
     * path from the script's text.
     */
    fun findGeneratedProject(root: Path, since: Long, projectName: String? = null): Path? {
        val candidates = ArrayList<Path>()

        fun collect(dir: Path, depth: Int) {
            if (depth > 2 || !dir.isDirectory()) return
            runCatching {
                Files.list(dir).use { stream ->
                    for (entry in stream) {
                        when {
                            entry.extension.equals("xpr", ignoreCase = true) -> candidates.add(entry)
                            entry.isDirectory() -> collect(entry, depth + 1)
                        }
                    }
                }
            }
        }
        collect(root, 0)

        val fresh = candidates.filter {
            runCatching { Files.getLastModifiedTime(it).toMillis() >= since }.getOrDefault(false)
        }
        val pool = fresh.ifEmpty { candidates }

        projectName?.let { name ->
            pool.firstOrNull { it.fileName.toString().removeSuffix(".xpr") == name }?.let { return it }
        }
        return pool.maxByOrNull {
            runCatching { Files.getLastModifiedTime(it).toMillis() }.getOrDefault(0L)
        }
    }
}
