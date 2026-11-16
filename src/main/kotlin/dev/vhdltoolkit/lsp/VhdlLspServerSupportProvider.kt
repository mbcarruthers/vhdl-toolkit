package dev.vhdltoolkit.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import dev.vhdltoolkit.settings.VhdlSettings
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory

private val VHDL_EXTENSIONS = setOf("vhd", "vhdl")

private fun isVhdlFile(file: VirtualFile): Boolean =
    file.extension?.lowercase() in VHDL_EXTENSIONS

/** Starts VHDL-LS for any project that opens a VHDL file. */
class VhdlLspServerSupportProvider : LspServerSupportProvider {

    override fun fileOpened(
        project: Project,
        file: VirtualFile,
        serverStarter: LspServerSupportProvider.LspServerStarter,
    ) {
        if (!isVhdlFile(file)) return
        val binary = VhdlLsBinary.locate()
        if (binary == null) {
            VhdlLsBinary.notifyMissing(project)
            return
        }
        serverStarter.ensureServerStarted(VhdlLspServerDescriptor(project, binary))
    }
}

private class VhdlLspServerDescriptor(
    project: Project,
    private val binary: Path,
) : ProjectWideLspServerDescriptor(project, "VHDL-LS") {

    override fun isSupportedFile(file: VirtualFile): Boolean = isVhdlFile(file)

    override fun createCommandLine(): GeneralCommandLine {
        val cmd = if (VhdlSettings.getInstance().collapseCascadedDiagnostics) {
            DiagnosticProxyLauncher.wrap(binary) ?: GeneralCommandLine(binary.toString())
        } else {
            GeneralCommandLine(binary.toString())
        }
        project.basePath?.let { cmd.withWorkDirectory(it) }
        return cmd
    }
}

/**
 * Builds the command line that runs VHDL-LS behind the diagnostic proxy.
 *
 * The proxy needs its own JVM because it owns a stdio stream of its own; it
 * runs on the IDE's bundled runtime with the plugin's jars on the classpath.
 * If any part of that cannot be worked out, this returns null and the caller
 * launches VHDL-LS directly — losing the filtering, not the language server.
 */
internal object DiagnosticProxyLauncher {

    private val LOG = logger<DiagnosticProxyLauncher>()
    private const val MAIN_CLASS = "dev.vhdltoolkit.diagnostics.DiagnosticFilterProxy"

    fun wrap(binary: Path): GeneralCommandLine? {
        val java = javaExecutable() ?: return null
        val classpath = pluginClasspath() ?: return null
        return GeneralCommandLine(java.toString())
            .withParameters("-cp", classpath, MAIN_CLASS, binary.toString())
            .also { LOG.info("VHDL-LS running behind the diagnostic filter") }
    }

    private fun javaExecutable(): Path? {
        val home = System.getProperty("java.home")?.let { Path.of(it) } ?: return null
        val exe = if (SystemInfo.isWindows) "java.exe" else "java"
        return home.resolve("bin").resolve(exe).takeIf { Files.isRegularFile(it) }
    }

    /** Every jar the plugin ships, which is where our classes and Gson live. */
    private fun pluginClasspath(): String? {
        val plugin = PluginManagerCore.getPlugin(PluginId.getId(VhdlLsBinary.PLUGIN_ID)) ?: return null
        val lib = plugin.pluginPath.resolve("lib").takeIf { it.isDirectory() } ?: return null
        val jars = runCatching {
            Files.list(lib).use { stream ->
                stream.filter { it.extension.equals("jar", ignoreCase = true) }
                    .map { it.toString() }
                    .sorted()
                    .toList()
            }
        }.getOrNull().orEmpty()
        if (jars.isEmpty()) return null
        return jars.joinToString(java.io.File.pathSeparator)
    }
}
