package dev.vhdltoolkit.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import dev.vhdltoolkit.editing.VhdlBlockCompletion
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import dev.vhdltoolkit.editing.VhdlBlockStructure.EndStyle
import dev.vhdltoolkit.project.VhdlLspRestarter
import javax.swing.JComponent

/** Settings | Languages &amp; Frameworks | VHDL Toolkit */
class VhdlSettingsConfigurable : Configurable {

    private var collapse: JBCheckBox? = null
    private var debounce: JBCheckBox? = null
    private var vivadoPath: TextFieldWithBrowseButton? = null
    private var completeBlocks: JBCheckBox? = null
    private var insertBegin: JBCheckBox? = null
    private var whenIndent: javax.swing.JComboBox<Boolean>? = null
    private val caretSpots = LinkedHashMap<String, javax.swing.JComboBox<VhdlBlockCompletion.CaretSpot>>()
    private var endStyle: javax.swing.JComboBox<EndStyle>? = null

    override fun getDisplayName(): String = "VHDL Toolkit"

    override fun createComponent(): JComponent {
        val checkbox = JBCheckBox("Collapse errors caused by another error in the same file")
            .also { collapse = it }

        val holdBox = JBCheckBox("Wait until typing stops before reporting problems")
            .also { debounce = it }

        val holdNote = JBLabel(
            "<html>The language server re-checks the file on every keystroke, so an unfinished " +
                "line is reported as an error while it is still being written. When enabled, " +
                "problems are shown once typing pauses, as the IDE does for its own " +
                "languages.</html>",
        ).apply { foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND }

        val pathField = TextFieldWithBrowseButton().also { field ->
            vivadoPath = field
            field.addBrowseFolderListener(
                "Vivado Installation",
                "Select the Vivado installation directory, for example /tools/Xilinx/Vivado/2022.2",
                null,
                FileChooserDescriptorFactory.createSingleFolderDescriptor(),
            )
        }

        val complete = JBCheckBox("Insert the matching 'end' when a block is opened")
            .also { completeBlocks = it }
        val begin = JBCheckBox("Also insert 'begin' for architectures, processes and subprograms")
            .also { insertBegin = it }
        val endStyleCombo = javax.swing.JComboBox(
            javax.swing.DefaultComboBoxModel(EndStyle.entries.toTypedArray()),
        ).also { box ->
            endStyle = box
            box.renderer = com.intellij.ui.SimpleListCellRenderer.create("") { style ->
                when (style) {
                    EndStyle.BARE -> "end;"
                    EndStyle.KEYWORD -> "end architecture;"
                    EndStyle.KEYWORD_AND_NAME -> "end architecture rtl;"
                }
            }
        }

        fun caretCombo(label: String): javax.swing.JComboBox<VhdlBlockCompletion.CaretSpot> =
            javax.swing.JComboBox(
                javax.swing.DefaultComboBoxModel(VhdlBlockCompletion.CaretSpot.values()),
            ).also { box ->
                caretSpots[label] = box
                box.renderer = com.intellij.ui.SimpleListCellRenderer.create("") { spot ->
                    if (spot == VhdlBlockCompletion.CaretSpot.BODY) {
                        "after begin, in the body"
                    } else {
                        "before begin, in the declarations"
                    }
                }
            }

        val caretNote = JBLabel(
            "<html>Where the caret lands once a block is completed. The default follows what " +
                "each region is normally used for: most processes declare nothing, while an " +
                "architecture almost always declares signals first.</html>",
        ).apply { foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND }

        val whenCombo = javax.swing.JComboBox(
            javax.swing.DefaultComboBoxModel(arrayOf(true, false)),
        ).also { box ->
            whenIndent = box
            box.renderer = com.intellij.ui.SimpleListCellRenderer.create("") { indented ->
                if (indented) "indented inside the case" else "level with the case"
            }
        }

        val whenNote = JBLabel(
            "<html>Both are in common use and VHDL prefers neither, so this is yours to pick. " +
                "It decides the indent suggested for a line beginning <code>when</code>; " +
                "statements inside an alternative are measured from the <code>when</code> " +
                "wherever you actually put it.</html>",
        ).apply { foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND }

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
            .addComponent(holdBox)
            .addComponentToRightColumn(holdNote)
            .addSeparator()
            .addComponent(checkbox)
            .addComponentToRightColumn(explanation)
            .addSeparator()
            .addComponent(complete)
            .addComponent(begin)
            .addLabeledComponent("Closing style:", endStyleCombo)
            .addLabeledComponent("Case alternatives:", whenCombo)
            .addComponentToRightColumn(whenNote)
            .addSeparator()
            .addComponent(JBLabel("Caret after completing a block:"))
            .addComponentToRightColumn(caretNote)
            .addLabeledComponent("Architecture:", caretCombo("architecture"))
            .addLabeledComponent("Process:", caretCombo("process"))
            .addLabeledComponent("Function or procedure:", caretCombo("subprogram"))
            .addLabeledComponent("Block:", caretCombo("block"))
            .addLabeledComponent("Generate:", caretCombo("generate"))
            .addSeparator()
            .addLabeledComponent("Vivado installation:", pathField)
            .addComponentToRightColumn(pathNote)
            .addComponentFillVertically(javax.swing.JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean {
        val settings = VhdlSettings.getInstance()
        return collapse?.isSelected != settings.collapseCascadedDiagnostics ||
            debounce?.isSelected != settings.debounceDiagnostics ||
            vivadoPath?.text.orEmpty() != settings.vivadoPath ||
            completeBlocks?.isSelected != settings.completeBlocks ||
            insertBegin?.isSelected != settings.insertBegin ||
            whenIndent?.selectedItem != settings.indentWhenInCase ||
            caretSpots.any { (key, box) -> box.selectedItem?.toString() != settings.caretSpot(key) } ||
            (endStyle?.selectedItem as? EndStyle)?.name != settings.endStyle
    }

    override fun apply() {
        val settings = VhdlSettings.getInstance()
        settings.collapseCascadedDiagnostics = collapse?.isSelected ?: true
        settings.debounceDiagnostics = debounce?.isSelected ?: true
        settings.vivadoPath = vivadoPath?.text.orEmpty().trim()
        settings.completeBlocks = completeBlocks?.isSelected ?: true
        settings.insertBegin = insertBegin?.isSelected ?: true
        settings.indentWhenInCase = whenIndent?.selectedItem as? Boolean ?: true
        caretSpots.forEach { (key, box) ->
            (box.selectedItem as? VhdlBlockCompletion.CaretSpot)?.let { settings.setCaretSpot(key, it) }
        }
        settings.endStyle = (endStyle?.selectedItem as? EndStyle)?.name ?: EndStyle.KEYWORD_AND_NAME.name
        // The proxy is chosen at launch, so the change needs a server restart.
        for (project in ProjectManager.getInstance().openProjects) {
            VhdlLspRestarter.restart(project)
        }
    }

    override fun reset() {
        val settings = VhdlSettings.getInstance()
        collapse?.isSelected = settings.collapseCascadedDiagnostics
        debounce?.isSelected = settings.debounceDiagnostics
        vivadoPath?.text = settings.vivadoPath
        completeBlocks?.isSelected = settings.completeBlocks
        insertBegin?.isSelected = settings.insertBegin
        whenIndent?.selectedItem = settings.indentWhenInCase
        caretSpots.forEach { (key, box) ->
            box.selectedItem = runCatching {
                VhdlBlockCompletion.CaretSpot.valueOf(settings.caretSpot(key))
            }.getOrNull()
        }
        endStyle?.selectedItem = settings.endStyleEnum
    }

    override fun disposeUIResources() {
        collapse = null
        debounce = null
        vivadoPath = null
        completeBlocks = null
        insertBegin = null
        whenIndent = null
        caretSpots.clear()
        endStyle = null
    }
}
