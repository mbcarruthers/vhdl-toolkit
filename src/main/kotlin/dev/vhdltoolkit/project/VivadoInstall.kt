package dev.vhdltoolkit.project

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/**
 * Locates a Vivado installation and the vendor VHDL that ships with it.
 *
 * A design instantiating `BUFG`, a block RAM or a clocking wizard references
 * `unisim`, `unimacro` or `xpm`. Without those libraries mapped, every such
 * instantiation is an unresolved name, which is indistinguishable from a real
 * mistake — so finding them matters as much as finding the project's own
 * sources.
 *
 * Only the component-declaration packages are mapped, not the thousands of
 * primitive implementations. Component declarations are what an instantiation
 * needs to resolve, and pulling in the rest would cost startup time for no
 * benefit to editing.
 */
object VivadoInstall {

    /** Where a vendor library's component declarations live, relative to the install root. */
    private val VENDOR_LIBRARIES = listOf(
        VendorLibrary("unisim", "data/vhdl/src/unisims/unisim_VCOMP.vhd"),
        VendorLibrary("unimacro", "data/vhdl/src/unimacro/unimacro_VCOMP.vhd"),
        VendorLibrary("unifast", "data/vhdl/src/unifast/unifast_VCOMP.vhd"),
        VendorLibrary("xpm", "data/ip/xpm/xpm_VCOMP.vhd"),
    )

    private data class VendorLibrary(val name: String, val relativePath: String)

    /**
     * Finds an install, preferring one matching [preferredVersion] (as written
     * in the `.xpr` header) so a project built against 2022.2 is not analysed
     * against primitives from a different release.
     */
    fun locate(preferredVersion: String? = null): Path? {
        val candidates = ArrayList<Path>()

        // An explicit environment pointer wins: it is what the shell running
        // Vivado would use.
        System.getenv("XILINX_VIVADO")?.let { candidates.add(Path.of(it)) }

        for (root in INSTALL_ROOTS) {
            val base = runCatching { Path.of(root) }.getOrNull() ?: continue
            if (!base.isDirectory()) continue
            runCatching {
                Files.list(base).use { stream ->
                    stream.filter { it.isDirectory() }.forEach { candidates.add(it) }
                }
            }
        }

        val valid = candidates.filter { looksLikeVivado(it) }
        if (valid.isEmpty()) return null

        preferredVersion?.let { version ->
            valid.firstOrNull { it.name == version }?.let { return it }
        }
        // Otherwise the newest, by the version-numbered directory name.
        return valid.maxByOrNull { it.name }
    }

    private fun looksLikeVivado(path: Path): Boolean =
        path.resolve("data/vhdl/src").isDirectory()

    /**
     * Vendor libraries present in [install]. Missing ones are skipped rather
     * than reported: which libraries ship depends on the edition, and a
     * WebPACK install legitimately lacks some.
     */
    fun vendorLibraries(install: Path): List<SourceLibrary> =
        VENDOR_LIBRARIES.mapNotNull { lib ->
            val file = install.resolve(lib.relativePath)
            if (!file.isRegularFile()) return@mapNotNull null
            SourceLibrary(name = lib.name, files = listOf(file.normalize()), isThirdParty = true)
        }

    /**
     * The version in a `.xpr` header comment, e.g. "2022.2" from
     * `<!-- Product Version: Vivado v2022.2 (64-bit) -->`.
     */
    fun versionFromXpr(xpr: Path): String? = runCatching {
        Files.newBufferedReader(xpr).use { reader ->
            // The comment is in the first few lines; do not read the whole file.
            repeat(10) {
                val line = reader.readLine() ?: return@use null
                VERSION_PATTERN.find(line)?.let { return@use it.groupValues[1] }
            }
            null
        }
    }.getOrNull()

    private val VERSION_PATTERN = Regex("""Vivado\s+v?(\d{4}\.\d+)""")

    private val INSTALL_ROOTS = listOf(
        "/tools/Xilinx/Vivado",
        "/opt/Xilinx/Vivado",
        "/opt/xilinx/Vivado",
        System.getProperty("user.home") + "/Xilinx/Vivado",
        "C:/Xilinx/Vivado",
        "D:/Xilinx/Vivado",
    )
}
