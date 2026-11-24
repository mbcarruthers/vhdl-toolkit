package dev.vhdltoolkit.project

import java.nio.file.Files
import java.nio.file.Path

/**
 * Detects a stale `~/.vhdl_ls.toml`.
 *
 * VHDL-LS loads configuration from three places, later ones overriding
 * earlier: the home-level `.vhdl_ls.toml`, the `VHDL_LS_CONFIG` environment
 * variable, then the workspace `vhdl_ls.toml`. Because the home file applies
 * to *every* project, one left behind by another tool — TerosHDL writes one —
 * reports missing files in projects that have nothing to do with it. The
 * errors look like they come from the project you have open, which makes this
 * genuinely hard to diagnose from the symptoms.
 */
object HomeConfigChecker {

    const val FILE_NAME = ".vhdl_ls.toml"

    data class Finding(
        val path: Path,
        val missingFiles: List<String>,
        val totalFiles: Int,
    ) {
        /** Only worth reporting when the config is mostly or entirely broken. */
        val isStale: Boolean get() = totalFiles > 0 && missingFiles.size == totalFiles
    }

    fun check(home: Path = Path.of(System.getProperty("user.home"))): Finding? {
        val config = home.resolve(FILE_NAME)
        if (!Files.isRegularFile(config)) return null

        val text = runCatching { Files.readString(config) }.getOrNull() ?: return null
        val referenced = extractFilePaths(text)
        if (referenced.isEmpty()) return null

        val missing = referenced.filter { raw ->
            val p = runCatching { Path.of(raw) }.getOrNull() ?: return@filter false
            val resolved = if (p.isAbsolute) p else config.parent.resolve(p)
            // A glob cannot be checked by existence; only flag literal paths.
            !raw.contains('*') && !Files.exists(resolved)
        }

        return Finding(config, missing, referenced.size)
    }

    /**
     * Pulls quoted path strings out of the config without a full TOML parser.
     * Only file existence matters here, so recognising quoted strings inside
     * `*.files = [...]` arrays is enough, and it cannot fail on syntax this
     * code does not understand.
     */
    private fun extractFilePaths(text: String): List<String> {
        val out = ArrayList<String>()
        val filesKey = Regex("""\.files\s*=\s*\[([^\]]*)]""", RegexOption.DOT_MATCHES_ALL)
        val quoted = Regex("""['"]([^'"]+)['"]""")
        for (block in filesKey.findAll(text)) {
            for (m in quoted.findAll(block.groupValues[1])) {
                out.add(m.groupValues[1].trim())
            }
        }
        return out
    }

    /** Renames the config aside so VHDL-LS stops loading it. */
    fun disable(config: Path): Path? = runCatching {
        var target = config.resolveSibling("${config.fileName}.bak")
        var n = 2
        while (Files.exists(target)) {
            target = config.resolveSibling("${config.fileName}.bak$n")
            n++
        }
        Files.move(config, target)
        target
    }.getOrNull()
}
