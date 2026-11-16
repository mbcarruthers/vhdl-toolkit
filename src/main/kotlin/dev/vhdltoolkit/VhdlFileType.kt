package dev.vhdltoolkit

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object VhdlFileType : LanguageFileType(VhdlLanguage) {
    override fun getName(): String = "VHDL"
    override fun getDescription(): String = "VHDL hardware description language"
    override fun getDefaultExtension(): String = "vhd"
    override fun getIcon(): Icon = IconLoader.getIcon("/icons/vhdl.svg", VhdlFileType::class.java)
}
