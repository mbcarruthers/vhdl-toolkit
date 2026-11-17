package dev.vhdltoolkit.project

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/**
 * Finds a project description that already exists (a Vivado `.xpr`, a Tcl
 * mapping) and keeps `vhdl_ls.toml` in step with it, so the library mapping is
 * never maintained by hand.
 */
@Service(Service.Level.PROJECT)
class VhdlMappingService(private val project: Project) {

    companion object {
        const val TOML_NAME = "vhdl_ls.toml"

        /** Deep enough for `rtl/system_integration/<build dir>/x.xpr`. */
        private const val MAX_PROJECT_SEARCH_DEPTH = 4

        /** Vivado's generated trees; a project file is never inside one. */
        private val GENERATED_SUFFIXES = listOf(
            ".runs", ".cache", ".gen", ".hw", ".sim", ".ip_user_files", ".srcs",
        )

        fun getInstance(project: Project): VhdlMappingService = project.service()
    }

    /** Where a mapping can be imported from, in order of preference. */
    sealed interface Source {
        val file: Path
        val label: String

        data class Vivado(override val file: Path) : Source {
            override val label: String get() = "Vivado project ${file.name}"
        }

        /** No project file; every VHDL source under the root. */
        data class SourceScan(override val file: Path) : Source {
            override val label: String get() = "source scan"
        }

        /** An HDL file list; readable directly, no Vivado needed. */
        data class FileList(override val file: Path) : Source {
            override val label: String get() = "file list ${file.name}"
        }

        /**
         * A Tcl script that builds a Vivado project. Importing means running
         * it first, so this is only ever offered, never acted on silently.
         */
        data class TclScript(
            override val file: Path,
            val analysis: TclProjectScript.Analysis,
        ) : Source {
            override val label: String get() = "Vivado script ${file.name}"
        }
    }

    fun projectDir(): Path? = project.basePath?.let { Path.of(it) }

    /**
     * Looks for something describing the project, in descending order of
     * directness: an existing `.xpr`, then a file list (readable without any
     * tool), then a Tcl script that would have to be run first, and finally a
     * plain scan of the sources on disk.
     *
     * A committed `.xpr` is rare in shared projects — it is generated,
     * machine-specific and version-locked — so the later options are what
     * most cloned repositories actually offer.
     */
    fun detectSource(): Source? {
        val dir = projectDir() ?: return null
        if (!Files.isDirectory(dir)) return null

        findVivadoProject(dir)?.let { return Source.Vivado(it) }

        findFileList(dir)?.let { return Source.FileList(it) }

        findProjectScript(dir)?.let { return it }

        // Nothing describes this project, so fall back to looking at what is
        // actually on disk. A plain folder of sources previously got no
        // mapping at all, and therefore no cross-file resolution.
        return Source.SourceScan(dir)
    }

    fun read(source: Source): LibraryMappingModel = when (source) {
        // Inside the IDE, Vivado is located with the plugin setting and the
        // login-shell environment, neither of which the bare reader can see.
        is Source.Vivado -> VivadoXprReader.read(source.file) { version -> VivadoLocator.install(version) }
        is Source.SourceScan -> SourceScanReader.read(source.file)
        is Source.FileList -> FileListReader.read(source.file)
        is Source.TclScript -> LibraryMappingModel(
            libraries = emptyList(),
            source = source.label,
            warnings = listOf(ImportWarning("Run the script first to generate a project.")),
        )
    }

    private fun firstFileWithExtension(dir: Path, extension: String): Path? = runCatching {
        Files.list(dir).use { stream ->
            stream.filter { it.isRegularFile() && it.extension.equals(extension, ignoreCase = true) }
                .sorted()
                .findFirst()
                .orElse(null)
        }
    }.getOrNull()

    /**
     * Finds a `.xpr` anywhere near the top of the tree, not only in the root.
     *
     * A script decides where it puts the project, and that is rarely the
     * repository root: NEORV32's packaging script writes to
     * `rtl/system_integration/neorv32_vivado_ip_work/`. Searching the root
     * alone meant falling through to a file list, which carries no library
     * names, when an authoritative `.xpr` was sitting three levels down.
     *
     * The walk is depth-limited and skips Vivado's own output directories,
     * which never contain a project file and can be enormous.
     */
    private fun findVivadoProject(root: Path): Path? {
        val found = ArrayList<Path>()

        fun walk(dir: Path, depth: Int) {
            if (depth > MAX_PROJECT_SEARCH_DEPTH) return
            val entries = runCatching {
                Files.list(dir).use { it.toList() }
            }.getOrNull() ?: return

            for (entry in entries) {
                when {
                    entry.isRegularFile() && entry.extension.equals("xpr", ignoreCase = true) ->
                        found.add(entry)

                    Files.isDirectory(entry) && !isSkippedDirectory(entry) ->
                        walk(entry, depth + 1)
                }
            }
        }
        walk(root, 0)

        // Shallowest first: a project at the root is more likely the one
        // meant than a scratch project nested in a build directory.
        return found.minByOrNull { root.relativize(it).nameCount }
    }

    private fun isSkippedDirectory(dir: Path): Boolean {
        val name = dir.fileName?.toString() ?: return true
        if (name.startsWith(".")) return true
        // Vivado's generated trees: no .xpr lives inside them, and they are big.
        return GENERATED_SUFFIXES.any { name.endsWith(it) }
    }

    /** Looks in the root and the directories build scripts conventionally use. */
    private fun searchDirs(dir: Path): List<Path> =
        listOf(dir) + listOf("rtl", "scripts", "vivado", "synthesis", "fpga", "build")
            .map { dir.resolve(it) }
            .filter { Files.isDirectory(it) }

    private fun findFileList(dir: Path): Path? =
        searchDirs(dir).firstNotNullOfOrNull { firstFileWithExtension(it, "f") }

    /**
     * The first Tcl script that would actually leave a project behind.
     * Non-project-mode and block-design scripts are skipped: running them
     * produces no `.xpr`, so offering to is a waste of the user's time.
     */
    private fun findProjectScript(dir: Path): Source.TclScript? {
        for (searchDir in searchDirs(dir)) {
            val scripts = runCatching {
                Files.list(searchDir).use { stream ->
                    stream.filter { it.isRegularFile() && TclProjectScript.isTclScript(it) }
                        .sorted()
                        .toList()
                }
            }.getOrNull().orEmpty()

            for (script in scripts) {
                val analysis = TclProjectScript.classify(script) ?: continue
                if (analysis.isImportable) return Source.TclScript(script, analysis)
            }
        }
        return null
    }

    fun tomlPath(): Path? = projectDir()?.resolve(TOML_NAME)

    /**
     * True when the existing toml was written by us. A hand-written file is
     * never overwritten without the user asking, since it may encode decisions
     * the importers cannot infer.
     */
    fun existingTomlIsGenerated(): Boolean {
        val toml = tomlPath() ?: return false
        if (!toml.isRegularFile()) return false
        return runCatching {
            Files.newBufferedReader(toml).use { it.readLine() }?.startsWith(VhdlLsTomlWriter.GENERATED_MARKER)
        }.getOrNull() == true
    }

    fun tomlExists(): Boolean = tomlPath()?.isRegularFile() == true

    /** True when the source file is newer than the toml we generated from it. */
    fun isStale(source: Source): Boolean {
        val toml = tomlPath() ?: return false
        if (!toml.isRegularFile()) return true
        return runCatching {
            Files.getLastModifiedTime(source.file) > Files.getLastModifiedTime(toml)
        }.getOrDefault(false)
    }

    /**
     * Imports [source] and writes `vhdl_ls.toml`, then restarts the language
     * server so the new mapping takes effect without reopening the project.
     *
     * @return the imported model, or null if nothing could be written.
     */
    fun sync(source: Source, notify: Boolean = true): LibraryMappingModel? {
        val dir = projectDir() ?: return null
        val toml = tomlPath() ?: return null

        // Check the mapping against what the sources actually reference, so a
        // format that cannot express libraries (a file list) or a mapping with
        // a gap in it is reported rather than silently producing a config that
        // does not resolve.
        val model = LibraryReferenceValidator.validate(read(source))
        val content = VhdlLsTomlWriter.render(model, dir)

        val written = runCatching {
            Files.writeString(toml, content)
            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(toml)
        }.isSuccess

        if (!written) {
            if (notify) warn("Could not write $TOML_NAME", "Check that the project directory is writable.")
            return null
        }

        VhdlLspRestarter.restart(project)

        if (notify) {
            val summary = "${model.fileCount} file(s) in ${model.libraries.size} librar" +
                if (model.libraries.size == 1) "y" else "ies"
            if (model.warnings.isEmpty()) {
                info("VHDL library mapping updated", "$summary, from ${source.label}.")
            } else {
                warn(
                    "VHDL library mapping updated with warnings",
                    buildString {
                        append("$summary, from ${source.label}.")
                        for (w in model.warnings) append("<br>• ${w.message}")
                    },
                )
            }
        }
        return model
    }

    private fun info(title: String, body: String) = notification(title, body, NotificationType.INFORMATION)
    private fun warn(title: String, body: String) = notification(title, body, NotificationType.WARNING)

    private fun notification(title: String, body: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("VHDL Toolkit")
            .createNotification(title, body, type)
            .notify(project)
    }
}
