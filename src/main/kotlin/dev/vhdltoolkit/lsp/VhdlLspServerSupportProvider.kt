package dev.vhdltoolkit.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.Lsp4jClient
import com.intellij.platform.lsp.api.LspServerNotificationsHandler
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import dev.vhdltoolkit.diagnostics.DiagnosticFiltering
import dev.vhdltoolkit.settings.VhdlSettings
import org.eclipse.lsp4j.PublishDiagnosticsParams
import java.nio.file.Path

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
        val cmd = GeneralCommandLine(binary.toString())
        project.basePath?.let { cmd.withWorkDirectory(it) }
        return cmd
    }

    /**
     * Intercepts diagnostics on their way into the IDE so cascaded errors can
     * be collapsed. This callback receives the complete set for one file,
     * which is what the analysis needs and what per-highlight filters cannot
     * provide.
     *
     * `Lsp4jClient.publishDiagnostics` is final, so the filtering goes into
     * the notifications handler it delegates to rather than the client.
     */
    override fun createLsp4jClient(handler: LspServerNotificationsHandler): Lsp4jClient =
        Lsp4jClient(CascadeFilteringHandler(handler))
}

/**
 * Passes every notification straight through except `publishDiagnostics`,
 * which is first stripped of errors caused by another error in the same file.
 */
private class CascadeFilteringHandler(
    private val delegate: LspServerNotificationsHandler,
) : LspServerNotificationsHandler by delegate {

    override fun publishDiagnostics(params: PublishDiagnosticsParams) {
        if (VhdlSettings.getInstance().collapseCascadedDiagnostics) {
            params.diagnostics = DiagnosticFiltering.filter(params)
        }
        delegate.publishDiagnostics(params)
    }
}
