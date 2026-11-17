package dev.vhdltoolkit.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.Lsp4jClient
import com.intellij.platform.lsp.api.LspServerNotificationsHandler
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import com.intellij.util.concurrency.AppExecutorUtil
import dev.vhdltoolkit.diagnostics.DiagnosticDebouncer
import dev.vhdltoolkit.diagnostics.DiagnosticFiltering
import dev.vhdltoolkit.settings.VhdlSettings
import org.eclipse.lsp4j.PublishDiagnosticsParams
import java.nio.file.Path
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

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
     * Intercepts diagnostics on their way into the IDE so they can be held
     * until typing stops and stripped of errors caused by another error in
     * the same file. This callback receives the complete set for one file,
     * which is what both need and what per-highlight filters cannot provide.
     *
     * `Lsp4jClient.publishDiagnostics` is final, so this goes into the
     * notifications handler it delegates to rather than the client.
     */
    override fun createLsp4jClient(handler: LspServerNotificationsHandler): Lsp4jClient =
        Lsp4jClient(DiagnosticGateHandler(handler))
}

/**
 * Passes every notification straight through except `publishDiagnostics`,
 * which is held until the author pauses and then collapsed to its root causes.
 *
 * Holding the set also makes the collapsing cheaper. The cascade analysis
 * re-reads the whole file to work out which names a broken line poisons, and
 * before this it ran on every keystroke for a result that was replaced
 * milliseconds later. Now it runs once per set actually shown, so the
 * expensive part happens at most a few times a second instead of on every
 * character.
 */
private class DiagnosticGateHandler(
    private val delegate: LspServerNotificationsHandler,
) : LspServerNotificationsHandler by delegate {

    private val debouncer = DiagnosticDebouncer()

    /** The newest set for each file, superseding any already waiting. */
    private val waiting = HashMap<String, PublishDiagnosticsParams>()
    private val timers = HashMap<String, ScheduledFuture<*>>()

    override fun publishDiagnostics(params: PublishDiagnosticsParams) {
        if (!VhdlSettings.getInstance().debounceDiagnostics) {
            show(params)
            return
        }

        val uri = params.uri
        val decision = synchronized(this) {
            waiting[uri] = params
            timers.remove(uri)?.cancel(false)
            debouncer.arrived(uri, System.currentTimeMillis())
        }

        when (decision) {
            DiagnosticDebouncer.Decision.Now -> release(uri)
            is DiagnosticDebouncer.Decision.After -> {
                val future = AppExecutorUtil.getAppScheduledExecutorService()
                    .schedule({ release(uri) }, decision.delayMs, TimeUnit.MILLISECONDS)
                synchronized(this) {
                    // A timer that has already fired leaves nothing waiting,
                    // so recording it here is harmless either way.
                    if (waiting.containsKey(uri)) timers[uri] = future else future.cancel(false)
                }
            }
        }
    }

    private fun release(uri: String) {
        // The lock is released before the set is handed on: the platform's
        // handler goes on to touch the editor, and nothing of ours should be
        // held while it does.
        val params = synchronized(this) {
            timers.remove(uri)
            debouncer.released(uri)
            waiting.remove(uri)
        } ?: return
        show(params)
    }

    private fun show(params: PublishDiagnosticsParams) {
        if (VhdlSettings.getInstance().collapseCascadedDiagnostics) {
            params.diagnostics = DiagnosticFiltering.filter(params)
        }
        delegate.publishDiagnostics(params)
    }
}
