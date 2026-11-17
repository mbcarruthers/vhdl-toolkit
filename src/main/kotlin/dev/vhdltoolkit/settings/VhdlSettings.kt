package dev.vhdltoolkit.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
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
     * Explicit Vivado installation directory. Empty means "work it out":
     * `XILINX_VIVADO`, then `vivado` on `PATH`, then the usual locations.
     * Needed because a GUI-launched IDE does not read `.bashrc`.
     */
    var vivadoPath: String = ""

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
}
