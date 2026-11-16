package dev.vhdltoolkit.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.APP)
@State(name = "VhdlToolkitSettings", storages = [Storage("vhdl-toolkit.xml")])
class VhdlSettings : PersistentStateComponent<VhdlSettings> {

    /**
     * Collapse diagnostics that follow from another error in the same file.
     * On by default: one typo producing thirty errors makes the real one hard
     * to find. Off restores exactly what VHDL-LS reports.
     */
    var collapseCascadedDiagnostics: Boolean = true

    override fun getState(): VhdlSettings = this

    override fun loadState(state: VhdlSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): VhdlSettings =
            ApplicationManager.getApplication().getService(VhdlSettings::class.java)
    }
}
