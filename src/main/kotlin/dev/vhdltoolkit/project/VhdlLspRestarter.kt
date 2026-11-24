package dev.vhdltoolkit.project

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspServerManager
import dev.vhdltoolkit.lsp.VhdlLspServerSupportProvider

/**
 * Restarts VHDL-LS after the library mapping changes.
 *
 * The server reads `vhdl_ls.toml` once at startup, so a regenerated mapping
 * has no effect until it is restarted.
 *
 * Callers of this live in the always-loaded half of the plugin, while the
 * LSP classes come from an optional module. Touching [Managed] is therefore
 * the first thing that can fail, and it is held behind a nested object so
 * the class loading happens inside the catch rather than at the call site.
 * A missing module, or a signature change in an API that is still moving,
 * degrades to "restart the IDE yourself".
 */
internal object VhdlLspRestarter {

    private val LOG = logger<VhdlLspRestarter>()

    fun restart(project: Project) {
        try {
            Managed.restart(project)
        } catch (failure: Throwable) {
            LOG.info(
                "Could not restart VHDL-LS automatically; it will pick up the new mapping on IDE restart",
                failure,
            )
        }
    }

    /** Everything that needs the optional module, in one place. */
    private object Managed {
        fun restart(project: Project) {
            LspServerManager.getInstance(project)
                .stopAndRestartIfNeeded(VhdlLspServerSupportProvider::class.java)
        }
    }
}
