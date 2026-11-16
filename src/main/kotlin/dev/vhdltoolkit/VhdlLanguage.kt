package dev.vhdltoolkit

import com.intellij.lang.Language

object VhdlLanguage : Language("VHDL") {
    private fun readResolve(): Any = VhdlLanguage
    override fun isCaseSensitive(): Boolean = false
    override fun getDisplayName(): String = "VHDL"
}
