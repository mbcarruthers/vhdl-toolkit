package dev.vhdltoolkit.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import dev.vhdltoolkit.project.VivadoInstall
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.Icon
import kotlin.io.path.isRegularFile

class VivadoRunOptions : RunConfigurationOptions() {
    var xprPath: String? by string()
    var action: String? by string(VivadoAction.SYNTHESIS.name)
    var jobs: Int by property(4)
}

class VivadoConfigurationType : ConfigurationType {
    override fun getDisplayName(): String = "Vivado"
    override fun getConfigurationTypeDescription(): String = "Run Vivado synthesis, implementation or simulation"
    override fun getIcon(): Icon = IconLoader.getIcon("/icons/vhdl.svg", VivadoConfigurationType::class.java)
    override fun getId(): String = "VivadoRunConfiguration"
    override fun getConfigurationFactories(): Array<ConfigurationFactory> = arrayOf(VivadoConfigurationFactory(this))

    companion object {
        fun getInstance(): VivadoConfigurationType =
            ConfigurationTypeUtil.findConfigurationType(VivadoConfigurationType::class.java)
    }
}

class VivadoConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {
    override fun getId(): String = "Vivado"
    override fun getOptionsClass(): Class<out RunConfigurationOptions> = VivadoRunOptions::class.java
    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        VivadoRunConfiguration(project, this, "Vivado")
}

class VivadoRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String,
) : RunConfigurationBase<VivadoRunOptions>(project, factory, name) {

    public override fun getOptions(): VivadoRunOptions = super.getOptions() as VivadoRunOptions

    var xprPath: String?
        get() = options.xprPath
        set(value) { options.xprPath = value }

    var action: VivadoAction
        get() = VivadoAction.from(options.action)
        set(value) { options.action = value.name }

    var jobs: Int
        get() = options.jobs
        set(value) { options.jobs = value }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = VivadoSettingsEditor(project)

    override fun checkConfiguration() {
        val xpr = xprPath?.takeIf { it.isNotBlank() }
            ?: throw RuntimeConfigurationError("Select a Vivado project (.xpr)")
        if (!Path.of(xpr).isRegularFile()) {
            throw RuntimeConfigurationError("Project file does not exist: $xpr")
        }
        if (VivadoExecutable.find(VivadoInstall.versionFromXpr(Path.of(xpr))) == null) {
            throw RuntimeConfigurationError(
                "No Vivado installation found. Set XILINX_VIVADO to the installation directory.",
            )
        }
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        val xpr = Path.of(xprPath ?: error("no project"))
        return VivadoCommandLineState(environment, xpr, action, jobs)
    }
}

private class VivadoCommandLineState(
    environment: ExecutionEnvironment,
    private val xpr: Path,
    private val action: VivadoAction,
    private val jobs: Int,
) : CommandLineState(environment) {

    init {
        // Make Vivado's own error locations clickable.
        consoleBuilder.addFilter(VivadoLogFilter(environment.project))
    }

    override fun startProcess(): ProcessHandler {
        val version = VivadoInstall.versionFromXpr(xpr)
        val vivado = VivadoExecutable.find(version)
            ?: throw IllegalStateException("No Vivado installation found")

        val script = Files.createTempFile("vhdl-toolkit-", ".tcl").apply {
            Files.writeString(this, VivadoTcl.script(xpr, action, jobs))
            toFile().deleteOnExit()
        }

        val commandLine = GeneralCommandLine(vivado.toString())
            .withParameters(
                "-mode", "batch",
                "-nojournal",
                "-notrace",
                "-source", script.toString(),
            )
            .withWorkDirectory(xpr.parent?.toString())

        val handler = KillableColoredProcessHandler(commandLine)
        ProcessTerminatedListener.attach(handler)
        return handler
    }
}
