package dev.vhdltoolkit.project

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * On opening a project, notices that it is a Vivado or Sigasi project and
 * offers to derive the VHDL library mapping from it.
 *
 * It offers rather than acts: writing a file into someone's project without
 * asking is the kind of thing a tool should not do on first contact. Once the
 * mapping is ours, later refreshes happen without prompting.
 */
class VhdlProjectStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        warnAboutStaleHomeConfig(project)

        val service = VhdlMappingService.getInstance(project)
        val source = service.detectSource() ?: return

        when {
            // A Tcl script has to be run before anything can be imported, so
            // it gets its own prompt explaining that.
            !service.tomlExists() && source is VhdlMappingService.Source.TclScript ->
                offerScript(project, source)

            // Nothing there yet: offer to create it.
            !service.tomlExists() -> offer(
                project,
                title = when (source) {
                    is VhdlMappingService.Source.Vivado -> "Vivado project detected"
                    is VhdlMappingService.Source.Sigasi -> "Sigasi project detected"
                    is VhdlMappingService.Source.FileList -> "HDL file list detected"
                    is VhdlMappingService.Source.TclScript -> "Vivado script detected"
                },
                body = "Generate ${VhdlMappingService.TOML_NAME} from ${source.label} so VHDL-LS can " +
                    "resolve names across your files?",
                source = source,
            )

            // Ours and out of date: refresh quietly, that is what it is for.
            service.existingTomlIsGenerated() && service.isStale(source) ->
                ApplicationManager.getApplication().executeOnPooledThread {
                    service.sync(source)
                }

            // Hand-written: leave it alone.
            else -> Unit
        }
    }

    /**
     * A home-level config applies to every project, so a stale one reports
     * missing files that have nothing to do with what is open. The symptom
     * points at the wrong place entirely, so it is worth saying plainly.
     */
    private fun warnAboutStaleHomeConfig(project: Project) {
        val finding = HomeConfigChecker.check() ?: return
        if (!finding.isStale) return

        val examples = finding.missingFiles.take(3).joinToString("<br>") { "• $it" }
        NotificationGroupManager.getInstance()
            .getNotificationGroup("VHDL Toolkit")
            .createNotification(
                "Stale ${HomeConfigChecker.FILE_NAME} in your home folder",
                "VHDL-LS loads <code>${finding.path}</code> for every project, and none of the " +
                    "${finding.totalFiles} file(s) it lists exist any more:<br>$examples<br><br>" +
                    "That is why unrelated projects report missing files. It was most likely left " +
                    "behind by another tool.",
                NotificationType.WARNING,
            )
            .addAction(
                NotificationAction.createSimpleExpiring("Rename it aside") {
                    ApplicationManager.getApplication().executeOnPooledThread {
                        val moved = HomeConfigChecker.disable(finding.path)
                        val group = NotificationGroupManager.getInstance().getNotificationGroup("VHDL Toolkit")
                        if (moved != null) {
                            VhdlLspRestarter.restart(project)
                            group.createNotification(
                                "Renamed to ${moved.fileName}",
                                "VHDL-LS no longer loads it. Delete it when you are sure it is not needed.",
                                NotificationType.INFORMATION,
                            ).notify(project)
                        } else {
                            group.createNotification(
                                "Could not rename ${HomeConfigChecker.FILE_NAME}",
                                "Remove or fix <code>${finding.path}</code> by hand.",
                                NotificationType.WARNING,
                            ).notify(project)
                        }
                    }
                },
            )
            .notify(project)
    }

    /**
     * A Tcl script cannot simply be read: Vivado has to run it. That is a
     * heavier thing to agree to than generating a config file, so it is asked
     * for separately and in plainer terms.
     */
    private fun offerScript(project: Project, source: VhdlMappingService.Source.TclScript) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("VHDL Toolkit")
            .createNotification(
                "Vivado script detected",
                "${source.file.fileName} creates a Vivado project, but none exists yet. " +
                    "Running it would let the library mapping be derived from the result.",
                NotificationType.INFORMATION,
            )
            .addAction(
                NotificationAction.createSimpleExpiring("Run it…") {
                    GenerateProjectFromTcl.confirmAndRun(project, source)
                },
            )
            .notify(project)
    }

    private fun offer(project: Project, title: String, body: String, source: VhdlMappingService.Source) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("VHDL Toolkit")
            .createNotification(title, body, NotificationType.INFORMATION)
            .addAction(
                NotificationAction.createSimpleExpiring("Generate") {
                    ApplicationManager.getApplication().executeOnPooledThread {
                        VhdlMappingService.getInstance(project).sync(source)
                    }
                },
            )
            .notify(project)
    }
}
