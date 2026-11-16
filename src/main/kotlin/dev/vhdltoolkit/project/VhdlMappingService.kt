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
 * Finds a project description that already exists (a Vivado `.xpr`, a Sigasi
 * mapping) and keeps `vhdl_ls.toml` in step with it, so the library mapping is
 * never maintained by hand.
 */
@Service(Service.Level.PROJECT)
class VhdlMappingService(private val project: Project) {

    companion object {
        const val TOML_NAME = "vhdl_ls.toml"
        fun getInstance(project: Project): VhdlMappingService = project.service()
    }

    /** Where a mapping can be imported from, in order of preference. */
    sealed interface Source {
        val file: Path
        val label: String

        data class Vivado(override val file: Path) : Source {
            override val label: String get() = "Vivado project ${file.name}"
        }

        data class Sigasi(override val file: Path) : Source {
            override val label: String get() = "Sigasi library mapping"
        }
    }

    fun projectDir(): Path? = project.basePath?.let { Path.of(it) }

    /**
     * Looks for an importable project file in the project root. Vivado wins
     * when both exist, since an `.xpr` is the authoritative build description.
     */
    fun detectSource(): Source? {
        val dir = projectDir() ?: return null
        if (!Files.isDirectory(dir)) return null

        val xpr = runCatching {
            Files.list(dir).use { stream ->
                stream.filter { it.isRegularFile() && it.extension.equals("xpr", ignoreCase = true) }
                    .sorted()
                    .findFirst()
                    .orElse(null)
            }
        }.getOrNull()
        if (xpr != null) return Source.Vivado(xpr)

        val sigasi = dir.resolve(SigasiMappingReader.FILE_NAME)
        if (sigasi.isRegularFile()) return Source.Sigasi(sigasi)

        return null
    }

    fun read(source: Source): LibraryMappingModel = when (source) {
        // Inside the IDE, Vivado is located with the plugin setting and the
        // login-shell environment, neither of which the bare reader can see.
        is Source.Vivado -> VivadoXprReader.read(source.file) { version -> VivadoLocator.install(version) }
        is Source.Sigasi -> SigasiMappingReader.read(source.file)
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

        val model = read(source)
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
