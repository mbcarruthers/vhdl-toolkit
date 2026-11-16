package dev.vhdltoolkit.project

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspServerManager
import dev.vhdltoolkit.lsp.VhdlLspServerSupportProvider

/**
 * Restarts VHDL-LS after the library mapping changes.
 *
 * The server reads `vhdl_ls.toml` once at startup, so a regenerated mapping
 * has no effect until it is restarted. Isolated here, and defensive, because
 * the LSP API is still evolving and a signature change should degrade to
 * "restart the IDE yourself" rather than break the import.
 */
internal object VhdlLspRestarter {

    private val LOG = logger<VhdlLspRestarter>()

    fun restart(project: Project) {
        runCatching {
            LspServerManager.getInstance(project)
                .stopAndRestartIfNeeded(VhdlLspServerSupportProvider::class.java)
        }.onFailure {
            LOG.info("Could not restart VHDL-LS automatically; it will pick up the new mapping on IDE restart", it)
        }
    }
}
