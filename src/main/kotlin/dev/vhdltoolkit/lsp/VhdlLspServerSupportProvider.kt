package dev.vhdltoolkit.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
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
}
