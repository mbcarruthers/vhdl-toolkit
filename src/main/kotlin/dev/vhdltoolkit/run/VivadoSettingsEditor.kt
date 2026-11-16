package dev.vhdltoolkit.run

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SpinnerNumberModel
import javax.swing.JSpinner

class VivadoSettingsEditor(private val project: Project) : SettingsEditor<VivadoRunConfiguration>() {

    private val xprField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            "Vivado Project",
            "Select the .xpr file to build",
            project,
            FileChooserDescriptorFactory.createSingleFileDescriptor("xpr"),
        )
    }

    private val actionCombo = JComboBox(DefaultComboBoxModel(VivadoAction.entries.toTypedArray())).apply {
        renderer = com.intellij.ui.SimpleListCellRenderer.create("") { it.displayName }
    }

    private val jobsSpinner = JSpinner(SpinnerNumberModel(4, 1, 64, 1))

    override fun resetEditorFrom(configuration: VivadoRunConfiguration) {
        xprField.text = configuration.xprPath.orEmpty()
        actionCombo.selectedItem = configuration.action
        jobsSpinner.value = configuration.jobs.coerceIn(1, 64)
    }

    override fun applyEditorTo(configuration: VivadoRunConfiguration) {
        configuration.xprPath = xprField.text.takeIf { it.isNotBlank() }
        configuration.action = actionCombo.selectedItem as? VivadoAction ?: VivadoAction.SYNTHESIS
        configuration.jobs = (jobsSpinner.value as? Int) ?: 4
    }

    override fun createEditor(): JComponent {
        val note = JBLabel(
            "<html>Runs Vivado in batch mode. Errors in the output link back to the source.<br>" +
                "Implementation and bitstream re-run synthesis first.</html>",
        ).apply { foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND }

        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Vivado project (.xpr):", xprField)
            .addLabeledComponent("Action:", actionCombo)
            .addLabeledComponent("Parallel jobs:", jobsSpinner)
            .addComponentToRightColumn(note)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }
}
