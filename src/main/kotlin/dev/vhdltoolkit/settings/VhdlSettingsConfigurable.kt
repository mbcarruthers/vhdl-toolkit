package dev.vhdltoolkit.settings

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

    override fun getDisplayName(): String = "VHDL Toolkit"

    override fun createComponent(): JComponent {
        val checkbox = JBCheckBox("Collapse errors caused by another error in the same file")
            .also { collapse = it }

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
            .addComponentFillVertically(javax.swing.JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean =
        collapse?.isSelected != VhdlSettings.getInstance().collapseCascadedDiagnostics

    override fun apply() {
        val settings = VhdlSettings.getInstance()
        settings.collapseCascadedDiagnostics = collapse?.isSelected ?: true
        // The proxy is chosen at launch, so the change needs a server restart.
        for (project in ProjectManager.getInstance().openProjects) {
            VhdlLspRestarter.restart(project)
        }
    }

    override fun reset() {
        collapse?.isSelected = VhdlSettings.getInstance().collapseCascadedDiagnostics
    }

    override fun disposeUIResources() {
        collapse = null
    }
}
