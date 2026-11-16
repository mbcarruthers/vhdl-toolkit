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
        val service = VhdlMappingService.getInstance(project)
        val source = service.detectSource() ?: return

        when {
            // Nothing there yet: offer to create it.
            !service.tomlExists() -> offer(
                project,
                title = "Vivado project detected".takeIf { source is VhdlMappingService.Source.Vivado }
                    ?: "Sigasi project detected",
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
