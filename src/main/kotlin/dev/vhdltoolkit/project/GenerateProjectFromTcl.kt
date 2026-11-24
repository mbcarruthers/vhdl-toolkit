package dev.vhdltoolkit.project

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.nio.file.Path

/**
 * Runs a project-creating Tcl script through Vivado and imports the project
 * it leaves behind.
 *
 * Vivado interprets its own language, which is the only dependable way to
 * handle these scripts: they use `proc`, `glob`, `foreach` and variable
 * expansion, so anything resembling static parsing would break on the first
 * real example.
 *
 * The script comes from a repository the user cloned, so it is never run
 * without being asked. Running a build script is ordinary, but it should be
 * a decision rather than a side effect of opening a folder.
 */
object GenerateProjectFromTcl {

    fun confirmAndRun(project: Project, source: VhdlMappingService.Source.TclScript) {
        val answer = Messages.showYesNoDialog(
            project,
            buildString {
                append("No Vivado project (.xpr) was found, but ${source.file.fileName} creates one.\n\n")
                append("Vivado will run this script to generate the project, which may take a few minutes. ")
                append("The script comes from this repository and will execute as written.\n\n")
                append("Run it?")
            },
            "Generate Vivado Project?",
            "Run Script",
            "Cancel",
            Messages.getQuestionIcon(),
        )
        if (answer != Messages.YES) return

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "Generating Vivado project", true) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    indicator.text = "Running ${source.file.fileName} in Vivado"
                    execute(project, source)
                }
            },
        )
    }

    private fun execute(project: Project, source: VhdlMappingService.Source.TclScript) {
        val vivado = VivadoLocator.executable()
        if (vivado == null) {
            notify(project, "Cannot run ${source.file.fileName}", VivadoLocator.notFoundMessage(), NotificationType.WARNING)
            return
        }

        val workDir = source.file.toAbsolutePath().normalize().parent ?: return
        val startedAt = System.currentTimeMillis()

        val commandLine = GeneralCommandLine(vivado.toString())
            .withParameters("-mode", "batch", "-nojournal", "-notrace", "-source", source.file.toString())
            .withWorkDirectory(workDir.toString())

        val output = runCatching { ExecUtil.execAndGetOutput(commandLine, TIMEOUT_MS) }.getOrElse { e ->
            notify(project, "Vivado failed to start", e.message ?: "Unknown error", NotificationType.ERROR)
            return
        }

        // A script can report failure through its exit code or by writing
        // nothing useful; the project's presence is the real test.
        val searchRoot = VhdlMappingService.getInstance(project).projectDir() ?: workDir
        val generated = TclProjectScript.findGeneratedProject(searchRoot, startedAt, source.analysis.projectName)
            ?: TclProjectScript.findGeneratedProject(workDir, startedAt, source.analysis.projectName)

        if (generated == null) {
            notify(
                project,
                "No project was generated",
                buildString {
                    append("${source.file.fileName} finished with exit code ${output.exitCode} ")
                    append("but no .xpr appeared.<br><br>")
                    append(lastMeaningfulLines(output.stdout + "\n" + output.stderr))
                },
                NotificationType.WARNING,
            )
            return
        }

        val service = VhdlMappingService.getInstance(project)
        service.sync(VhdlMappingService.Source.Vivado(generated))
    }

    /** The tail of Vivado's output, which is where its errors are. */
    private fun lastMeaningfulLines(text: String, count: Int = 5): String {
        val interesting = text.lineSequence()
            .filter { it.isNotBlank() }
            .filter { it.startsWith("ERROR") || it.startsWith("CRITICAL") || it.startsWith("WARNING") }
            .toList()
        val lines = interesting.ifEmpty { text.lineSequence().filter { it.isNotBlank() }.toList() }
        return lines.takeLast(count).joinToString("<br>") { it.take(200) }
    }

    private fun notify(project: Project, title: String, body: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("VHDL Toolkit")
            .createNotification(title, body, type)
            .notify(project)
    }

    /** Project creation is not synthesis, but some scripts do a lot of work. */
    private const val TIMEOUT_MS = 15 * 60 * 1000
}
