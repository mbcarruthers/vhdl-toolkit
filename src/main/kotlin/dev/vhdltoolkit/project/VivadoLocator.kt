package dev.vhdltoolkit.project

import com.intellij.util.EnvironmentUtil
import dev.vhdltoolkit.settings.VhdlSettings
import java.nio.file.Path

/**
 * Finds Vivado the way the IDE should: the plugin's own setting first, then
 * the user's *login shell* environment.
 *
 * [EnvironmentUtil] matters here. A desktop- or Toolbox-launched IDE inherits
 * the desktop session's environment, not the one `.bashrc` or `.zshrc` builds,
 * so `System.getenv` misses everything `settings64.sh` exported in a terminal.
 * EnvironmentUtil runs a login shell once and caches the result, which is how
 * the IDE sees the same `XILINX_VIVADO` and `PATH` the user does.
 */
object VivadoLocator {

    fun install(preferredVersion: String? = null): Path? =
        VivadoInstall.locate(
            preferredVersion = preferredVersion,
            explicitPath = VhdlSettings.getInstance().vivadoPath,
            env = ::shellEnv,
        )

    private fun shellEnv(name: String): String? =
        EnvironmentUtil.getValue(name) ?: System.getenv(name)

    /** A message that names every way to fix "Vivado not found". */
    fun notFoundMessage(): String =
        "No Vivado installation found. Either set the path under " +
            "Settings | Languages & Frameworks | VHDL Toolkit, or source Vivado's " +
            "settings64.sh (which exports XILINX_VIVADO) before launching the IDE. " +
            "A plain shell variable in .bashrc is not visible to a GUI application."
}
