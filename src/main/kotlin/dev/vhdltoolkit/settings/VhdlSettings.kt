package dev.vhdltoolkit.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import dev.vhdltoolkit.editing.VhdlBlockCompletion
import dev.vhdltoolkit.editing.VhdlBlockCompletion.CaretSpot
import dev.vhdltoolkit.editing.VhdlBlockStructure
import dev.vhdltoolkit.editing.VhdlBlockStructure.EndStyle

@Service(Service.Level.APP)
@State(name = "VhdlToolkitSettings", storages = [Storage("vhdl-toolkit.xml")])
class VhdlSettings : PersistentStateComponent<VhdlSettings> {

    /**
     * Collapse diagnostics that follow from another error in the same file.
     * On by default: one typo producing thirty errors makes the real one hard
     * to find. Off restores exactly what VHDL-LS reports.
     */
    var collapseCascadedDiagnostics: Boolean = true

    /**
     * Hold diagnostics until typing stops, as the IDE's own analyser does.
     * On by default: the server reports on every keystroke, so without this
     * half-written code is flagged as broken while it is being written.
     */
    var debounceDiagnostics: Boolean = true

    /**
     * Explicit Vivado installation directory. Empty means "work it out":
     * `XILINX_VIVADO`, then `vivado` on `PATH`, then the usual locations.
     * Needed because a GUI-launched IDE does not read `.bashrc`.
     */
    var vivadoPath: String = ""

    /**
     * Indent `when` alternatives one level inside their `case`.
     *
     * Both styles are widely used and VHDL prefers neither, so this is the
     * author's call rather than the plugin's. The default is the more common
     * of the two.
     */
    var indentWhenInCase: Boolean = true

    /**
     * Where the caret lands after a construct that has a `begin` is
     * completed, one entry per such construct.
     *
     * Stored by name so the choice survives a reordering of the enum, and
     * kept per construct because the right answer differs: most processes
     * declare nothing, most architectures declare signals.
     */
    var caretInArchitecture: String = CaretSpot.DECLARATIVE.name
    var caretInProcess: String = CaretSpot.BODY.name
    var caretInSubprogram: String = CaretSpot.BODY.name
    var caretInBlock: String = CaretSpot.DECLARATIVE.name
    var caretInGenerate: String = CaretSpot.BODY.name

    /** Insert the matching `end` when a block-opening line is completed. */
    var completeBlocks: Boolean = true

    /** Also place `begin` for constructs that separate declarations from body. */
    var insertBegin: Boolean = true

    /** How explicit inserted `end` lines are; see [VhdlBlockStructure.EndStyle]. */
    var endStyle: String = EndStyle.KEYWORD_AND_NAME.name

    val endStyleEnum: EndStyle
        get() = runCatching { EndStyle.valueOf(endStyle) }.getOrDefault(EndStyle.KEYWORD_AND_NAME)

    override fun getState(): VhdlSettings = this

    override fun loadState(state: VhdlSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): VhdlSettings =
            ApplicationManager.getApplication().getService(VhdlSettings::class.java)
    }

    /** The configured spot for [construct], falling back to the default. */
    fun caretSpotFor(construct: VhdlBlockStructure.Construct): CaretSpot {
        val stored = when (construct) {
            VhdlBlockStructure.Construct.ARCHITECTURE -> caretInArchitecture
            VhdlBlockStructure.Construct.PROCESS -> caretInProcess
            VhdlBlockStructure.Construct.SUBPROGRAM -> caretInSubprogram
            VhdlBlockStructure.Construct.BLOCK -> caretInBlock
            VhdlBlockStructure.Construct.GENERATE -> caretInGenerate
            else -> return VhdlBlockCompletion.defaultCaretSpot(construct)
        }
        return runCatching { CaretSpot.valueOf(stored) }
            .getOrElse { VhdlBlockCompletion.defaultCaretSpot(construct) }
    }

    /** The stored spot for a construct, named as the settings page names it. */
    fun caretSpot(key: String): String = when (key) {
        "architecture" -> caretInArchitecture
        "process" -> caretInProcess
        "subprogram" -> caretInSubprogram
        "block" -> caretInBlock
        "generate" -> caretInGenerate
        else -> CaretSpot.DECLARATIVE.name
    }

    fun setCaretSpot(key: String, spot: CaretSpot) {
        when (key) {
            "architecture" -> caretInArchitecture = spot.name
            "process" -> caretInProcess = spot.name
            "subprogram" -> caretInSubprogram = spot.name
            "block" -> caretInBlock = spot.name
            "generate" -> caretInGenerate = spot.name
        }
    }
}
