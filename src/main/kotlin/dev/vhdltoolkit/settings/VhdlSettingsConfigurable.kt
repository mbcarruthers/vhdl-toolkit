package dev.vhdltoolkit.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import dev.vhdltoolkit.project.VhdlLspRestarter
import javax.swing.JComponent

/** Settings | Languages &amp; Frameworks | VHDL Toolkit */
class VhdlSettingsConfigurable : Configurable {

    private var collapse: JBCheckBox? = null
    private var vivadoPath: TextFieldWithBrowseButton? = null

    override fun getDisplayName(): String = "VHDL Toolkit"

    override fun createComponent(): JComponent {
        val checkbox = JBCheckBox("Collapse errors caused by another error in the same file")
            .also { collapse = it }

        val pathField = TextFieldWithBrowseButton().also { field ->
            vivadoPath = field
            field.addBrowseFolderListener(
                "Vivado Installation",
                "Select the Vivado installation directory, for example /tools/Xilinx/Vivado/2022.2",
                null,
                FileChooserDescriptorFactory.createSingleFolderDescriptor(),
            )
        }

        val pathNote = JBLabel(
            "<html>Leave empty to detect it from <code>XILINX_VIVADO</code> or <code>PATH</code>. " +
                "Set it if the IDE cannot find Vivado — a GUI-launched IDE does not read " +
                "<code>.bashrc</code>, so a variable defined there is invisible to it.</html>",
        ).apply { foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND }

        val explanation = JBLabel(
            "<html>A mistake in a <code>use</code> clause makes every name from that package " +
                "unresolvable, so one typo can produce dozens of errors and bury the real one. " +
                "When enabled, only the root cause is reported, and it says how many other " +
                "errors follow from it.<br><br>" +
                "Turn this off to see exactly what VHDL-LS reports.</html>",
        ).apply {
            foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
            setCopyable(true)
        }

        return FormBuilder.createFormBuilder()
            .addComponent(checkbox)
            .addComponentToRightColumn(explanation)
            .addSeparator()
            .addLabeledComponent("Vivado installation:", pathField)
            .addComponentToRightColumn(pathNote)
            .addComponentFillVertically(javax.swing.JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean {
        val settings = VhdlSettings.getInstance()
        return collapse?.isSelected != settings.collapseCascadedDiagnostics ||
            vivadoPath?.text.orEmpty() != settings.vivadoPath
    }

    override fun apply() {
        val settings = VhdlSettings.getInstance()
        settings.collapseCascadedDiagnostics = collapse?.isSelected ?: true
        settings.vivadoPath = vivadoPath?.text.orEmpty().trim()
        // The proxy is chosen at launch, so the change needs a server restart.
        for (project in ProjectManager.getInstance().openProjects) {
            VhdlLspRestarter.restart(project)
        }
    }

    override fun reset() {
        val settings = VhdlSettings.getInstance()
        collapse?.isSelected = settings.collapseCascadedDiagnostics
        vivadoPath?.text = settings.vivadoPath
    }

    override fun disposeUIResources() {
        collapse = null
        vivadoPath = null
    }
}
