package dev.vhdltoolkit.lsp

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.system.CpuArch
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/** Locates the VHDL-LS language server binary. */
object VhdlLsBinary {

    const val PLUGIN_ID = "dev.vhdltoolkit.vhdl"

    private val notified = ConcurrentHashMap.newKeySet<String>()

    /**
     * Resolution order:
     * 1. Binary bundled with the plugin under `<plugin>/vhdl_ls/<platform>/bin/`.
     *    The release layout (`bin/vhdl_ls` beside `vhdl_libraries/`) is preserved,
     *    because VHDL-LS finds its std/ieee libraries relative to the binary.
     * 2. `vhdl_ls` on PATH.
     */
    fun locate(): Path? {
        bundled()?.let { return it }
        return fromPath()
    }

    private fun platformDir(): String? = when {
        SystemInfo.isLinux && CpuArch.CURRENT == CpuArch.X86_64 -> "linux-x86_64"
        SystemInfo.isWindows && CpuArch.CURRENT == CpuArch.X86_64 -> "windows-x86_64"
        SystemInfo.isMac && CpuArch.CURRENT == CpuArch.ARM64 -> "darwin-aarch64"
        SystemInfo.isMac && CpuArch.CURRENT == CpuArch.X86_64 -> "darwin-x86_64"
        else -> null
    }

    private fun bundled(): Path? {
        val plugin = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID)) ?: return null
        val platform = platformDir() ?: return null
        val exe = if (SystemInfo.isWindows) "vhdl_ls.exe" else "vhdl_ls"
        val binary = plugin.pluginPath.resolve("vhdl_ls").resolve(platform).resolve("bin").resolve(exe)
        if (!Files.isRegularFile(binary)) return null
        if (!SystemInfo.isWindows) {
            val f = binary.toFile()
            if (!f.canExecute()) f.setExecutable(true, false)
        }
        return binary
    }

    private fun fromPath(): Path? {
        val exe = if (SystemInfo.isWindows) "vhdl_ls.exe" else "vhdl_ls"
        return PathEnvironmentVariableUtil.findInPath(exe)?.toPath()
    }

    fun notifyMissing(project: Project) {
        if (!notified.add(project.locationHash)) return
        NotificationGroupManager.getInstance()
            .getNotificationGroup("VHDL Toolkit")
            .createNotification(
                "VHDL-LS not found",
                "No bundled VHDL-LS binary for this platform and none on PATH. " +
                    "Install it from github.com/VHDL-LS/rust_hdl/releases and ensure 'vhdl_ls' is on PATH, " +
                    "then reopen the project. Highlighting still works; errors and navigation need the server.",
                NotificationType.WARNING,
            )
            .notify(project)
    }
}
