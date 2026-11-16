package dev.vhdltoolkit.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class ProjectImportTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun write(relative: String, content: String = "-- vhdl\n"): Path {
        val p = tmp.root.toPath().resolve(relative)
        Files.createDirectories(p.parent)
        Files.writeString(p, content)
        return p.normalize()
    }

    // ---------------------------------------------------------------- Vivado

    /**
     * Mirrors the shape Vivado writes: filesets by type, `$PPRDIR`/`$PSRCDIR`
     * variables, and a per-file Library attribute.
     */
    private fun writeXpr(name: String = "counter"): Path = write(
        "$name.xpr",
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <Project Version="7" Minor="12" Path="${'$'}PPRDIR/$name.xpr">
          <FileSets Version="1" Minor="31">
            <FileSet Name="sources_1" Type="DesignSrcs" RelSrcDir="${'$'}PSRCDIR/sources_1">
              <File Path="${'$'}PPRDIR/src/counter.vhd">
                <FileInfo>
                  <Attr Name="UsedIn" Val="synthesis"/>
                  <Attr Name="Library" Val="xil_defaultlib"/>
                </FileInfo>
              </File>
              <File Path="${'$'}PPRDIR/src/pkg_util.vhd">
                <FileInfo>
                  <Attr Name="Library" Val="util_lib"/>
                </FileInfo>
              </File>
              <File Path="${'$'}PPRDIR/src/top.v">
                <FileInfo><Attr Name="Library" Val="xil_defaultlib"/></FileInfo>
              </File>
              <File Path="${'$'}PPRDIR/ip/fifo/fifo.xci">
                <FileInfo><Attr Name="Library" Val="xil_defaultlib"/></FileInfo>
              </File>
            </FileSet>
            <FileSet Name="sim_1" Type="SimulationSrcs">
              <File Path="${'$'}PPRDIR/tb/tb_counter.vhd">
                <FileInfo><Attr Name="Library" Val="xil_defaultlib"/></FileInfo>
              </File>
            </FileSet>
            <FileSet Name="constrs_1" Type="Constrs">
              <File Path="${'$'}PPRDIR/constraints/pins.xdc"/>
            </FileSet>
          </FileSets>
        </Project>
        """.trimIndent(),
    )

    @Test
    fun vivadoGroupsFilesByLibraryAttribute() {
        val xpr = writeXpr()
        write("src/counter.vhd")
        write("src/pkg_util.vhd")
        write("src/top.v")
        write("tb/tb_counter.vhd")
        write("constraints/pins.xdc")

        val model = VivadoXprReader.read(xpr)
        val names = model.libraries.map { it.name }.toSet()
        assertEquals(setOf("xil_defaultlib", "util_lib"), names)

        val default = model.libraries.first { it.name == "xil_defaultlib" }
        val fileNames = default.files.map { it.fileName.toString() }.toSet()
        assertTrue("design source included", "counter.vhd" in fileNames)
        assertTrue("simulation source included", "tb_counter.vhd" in fileNames)
        assertFalse("Verilog excluded", "top.v" in fileNames)

        val util = model.libraries.first { it.name == "util_lib" }
        assertEquals(listOf("pkg_util.vhd"), util.files.map { it.fileName.toString() })
    }

    @Test
    fun vivadoSkipsConstraintsAndFlagsUngeneratedIp() {
        val xpr = writeXpr()
        write("src/counter.vhd")
        write("src/pkg_util.vhd")
        write("tb/tb_counter.vhd")
        write("constraints/pins.xdc")

        val model = VivadoXprReader.read(xpr)
        val all = model.libraries.flatMap { it.files }.map { it.fileName.toString() }
        assertFalse("xdc is not VHDL", all.any { it.endsWith(".xdc") })
        assertTrue(
            "ungenerated IP is reported",
            model.warnings.any { it.message.contains("IP core") },
        )
    }

    @Test
    fun vivadoPicksUpGeneratedIpSources() {
        val xpr = writeXpr()
        write("src/counter.vhd")
        write("src/pkg_util.vhd")
        write("tb/tb_counter.vhd")
        write("counter.gen/sources_1/ip/fifo/fifo_stub.vhd")

        val model = VivadoXprReader.read(xpr)
        val all = model.libraries.flatMap { it.files }.map { it.fileName.toString() }
        assertTrue("generated IP source included", "fifo_stub.vhd" in all)
        assertFalse(
            "no ungenerated-IP warning once sources exist",
            model.warnings.any { it.message.contains("IP core") },
        )
    }

    @Test
    fun vivadoReportsMissingFilesWithoutFailing() {
        val xpr = writeXpr()
        write("src/counter.vhd")
        // pkg_util.vhd and tb_counter.vhd deliberately absent.

        val model = VivadoXprReader.read(xpr)
        assertTrue(model.warnings.any { it.message.contains("do not exist") })
        assertTrue("still returns what it could read", model.fileCount >= 1)
    }

    @Test
    fun malformedXprYieldsWarningNotException() {
        val xpr = write("broken.xpr", "<Project><FileSets>")
        val model = VivadoXprReader.read(xpr)
        assertTrue(model.libraries.isEmpty())
        assertTrue(model.warnings.isNotEmpty())
    }

    /**
     * Trimmed from a real Vivado 2022.2 project. Two details matter and are
     * easy to get wrong from memory: files carry no Library attribute at all
     * (only UsedIn), so the library comes from the project-level DefaultLib
     * option; and paths use $PSRCDIR, which resolves to <project>.srcs.
     */
    private fun writeRealWorldXpr(): Path = write(
        "counter4_ud.xpr",
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <!-- Product Version: Vivado v2022.2 (64-bit) -->
        <Project Version="7" Minor="61" Path="${'$'}PPRDIR/counter4_ud.xpr">
          <DefaultLaunch Dir="${'$'}PRUNDIR"/>
          <Configuration>
            <Option Name="Part" Val="xc7a35tcpg236-1"/>
            <Option Name="TargetLanguage" Val="VHDL"/>
            <Option Name="DefaultLib" Val="xil_defaultlib"/>
            <Option Name="IPOutputRepo" Val="${'$'}PCACHEDIR/ip"/>
            <Option Name="IPUserFilesDir" Val="${'$'}PIPUSERFILESDIR"/>
          </Configuration>
          <FileSets Version="1" Minor="31">
            <FileSet Name="sources_1" Type="DesignSrcs" RelSrcDir="${'$'}PSRCDIR/sources_1">
              <Filter Type="Srcs"/>
              <File Path="${'$'}PSRCDIR/sources_1/new/counter4_ud.vhd">
                <FileInfo>
                  <Attr Name="UsedIn" Val="synthesis"/>
                  <Attr Name="UsedIn" Val="simulation"/>
                </FileInfo>
              </File>
              <Config>
                <Option Name="TopModule" Val="counter4_ud"/>
              </Config>
            </FileSet>
            <FileSet Name="constrs_1" Type="Constrs" RelSrcDir="${'$'}PSRCDIR/constrs_1">
              <File Path="${'$'}PSRCDIR/constrs_1/new/counter4_ud.xdc"/>
            </FileSet>
            <FileSet Name="sim_1" Type="SimulationSrcs" RelSrcDir="${'$'}PSRCDIR/sim_1">
              <File Path="${'$'}PSRCDIR/sim_1/new/counter4_ud_tb.vhd">
                <FileInfo>
                  <Attr Name="UsedIn" Val="simulation"/>
                </FileInfo>
              </File>
              <Config>
                <Option Name="TopModule" Val="counter4_ud_tb"/>
                <Option Name="TopLib" Val="xil_defaultlib"/>
              </Config>
            </FileSet>
            <FileSet Name="utils_1" Type="Utils" RelSrcDir="${'$'}PSRCDIR/utils_1"/>
          </FileSets>
        </Project>
        """.trimIndent(),
    )

    @Test
    fun realVivadoProjectUsesDefaultLibWhenFilesCarryNoLibraryAttribute() {
        val xpr = writeRealWorldXpr()
        write("counter4_ud.srcs/sources_1/new/counter4_ud.vhd")
        write("counter4_ud.srcs/sim_1/new/counter4_ud_tb.vhd")
        write("counter4_ud.srcs/constrs_1/new/counter4_ud.xdc")

        val model = VivadoXprReader.read(xpr)

        assertEquals(
            "library comes from the Configuration DefaultLib option",
            listOf("xil_defaultlib"),
            model.libraries.map { it.name },
        )
        assertEquals(
            setOf("counter4_ud.vhd", "counter4_ud_tb.vhd"),
            model.libraries.single().files.map { it.fileName.toString() }.toSet(),
        )
        assertTrue("no spurious warnings on a clean project", model.warnings.isEmpty())
    }

    @Test
    fun realVivadoProjectRendersUsableToml() {
        val xpr = writeRealWorldXpr()
        write("counter4_ud.srcs/sources_1/new/counter4_ud.vhd")
        write("counter4_ud.srcs/sim_1/new/counter4_ud_tb.vhd")

        val toml = VhdlLsTomlWriter.render(VivadoXprReader.read(xpr), tmp.root.toPath())

        assertTrue(toml.contains("xil_defaultlib.files = ["))
        assertTrue(toml.contains("'counter4_ud.srcs/sources_1/new/counter4_ud.vhd'"))
        assertTrue(toml.contains("'counter4_ud.srcs/sim_1/new/counter4_ud_tb.vhd'"))
        assertFalse("library name must never be 'work'", toml.contains("work.files"))
    }

    // ---------------------------------------------------------------- Sigasi

    /** The real mapping file from a Sigasi project. */
    private fun writeSigasiMapping(): Path = write(
        SigasiMappingReader.FILE_NAME,
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <com.sigasi.hdt.shared.librarymapping.model:LibraryMappings
            xmlns:com.sigasi.hdt.shared.librarymapping.model="com.sigasi.hdt.vhdl.scoping.librarymapping" Version="2">
          <Mappings Location="Common Libraries/IEEE" Library="ieee"/>
          <Mappings Location="Common Libraries/IEEE Synopsys" Library="ieee"/>
          <Mappings Location="Common Libraries" Library="not mapped"/>
          <Mappings Location="Common Libraries/STD" Library="std"/>
          <Mappings Location="" Library="work"/>
        </com.sigasi.hdt.shared.librarymapping.model:LibraryMappings
        >
        """.trimIndent(),
    )

    @Test
    fun sigasiRootMappingCollectsEverythingAndRenamesWork() {
        val mapping = writeSigasiMapping()
        write("src/counter.vhd")
        write("tb/tb_counter.vhd")
        write("readme.md", "# not vhdl")

        val model = SigasiMappingReader.read(mapping)
        assertEquals(listOf("work"), model.libraries.map { it.name })
        assertEquals(2, model.fileCount)
        assertTrue(model.warnings.any { it.message.contains("renamed") })

        // The rename happens on write, so the emitted toml must not say "work".
        val toml = VhdlLsTomlWriter.render(model, tmp.root.toPath())
        assertFalse("'work' is rejected by VHDL-LS", toml.contains("work.files"))
        assertTrue(toml.contains("${LibraryNames.DEFAULT_REPLACEMENT}.files"))
    }

    @Test
    fun sigasiIgnoresBundledCommonLibraries() {
        val mapping = writeSigasiMapping()
        write("src/counter.vhd")
        val model = SigasiMappingReader.read(mapping)
        assertFalse("VHDL-LS ships its own ieee", model.libraries.any { it.name == "ieee" })
        assertFalse("VHDL-LS ships its own std", model.libraries.any { it.name == "std" })
    }

    @Test
    fun sigasiDeeperMappingWinsAndNotMappedExcludes() {
        val mapping = write(
            SigasiMappingReader.FILE_NAME,
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <LibraryMappings Version="2">
              <Mappings Location="" Library="work"/>
              <Mappings Location="src/vendor" Library="vendor_lib"/>
              <Mappings Location="src/legacy" Library="not mapped"/>
            </LibraryMappings>
            """.trimIndent(),
        )
        write("src/a.vhd")
        write("src/vendor/v.vhd")
        write("src/legacy/old.vhd")

        val model = SigasiMappingReader.read(mapping)
        val byName = model.libraries.associateBy { it.name }
        assertEquals(listOf("v.vhd"), byName.getValue("vendor_lib").files.map { it.fileName.toString() })
        assertEquals(listOf("a.vhd"), byName.getValue("work").files.map { it.fileName.toString() })
        assertFalse(
            "'not mapped' folders are excluded",
            model.libraries.flatMap { it.files }.any { it.fileName.toString() == "old.vhd" },
        )
    }

    // ----------------------------------------------------------- Toml writer

    @Test
    fun tomlUsesRelativePathsAndIsRegenerationSafe() {
        val root = tmp.root.toPath()
        val model = LibraryMappingModel(
            libraries = listOf(SourceLibrary("defaultlib", listOf(write("src/a.vhd")))),
            standard = "2008",
            source = "test",
        )
        val toml = VhdlLsTomlWriter.render(model, root)
        assertTrue(toml.startsWith(VhdlLsTomlWriter.GENERATED_MARKER))
        assertTrue("paths relative to the toml", toml.contains("'src/a.vhd'"))
        assertFalse("no absolute path leaked", toml.contains(root.toString()))
        assertTrue(toml.contains("standard = '2008'"))
    }

    @Test
    fun thirdPartyLibrariesAreMarked() {
        val root = tmp.root.toPath()
        val model = LibraryMappingModel(
            libraries = listOf(
                SourceLibrary("unisim", listOf(write("vendor/unisim_VCOMP.vhd")), isThirdParty = true),
            ),
            source = "test",
        )
        val toml = VhdlLsTomlWriter.render(model, root)
        assertTrue(toml.contains("unisim.is_third_party = true"))
    }

    @Test
    fun reservedAndInvalidLibraryNamesAreSanitized() {
        assertEquals(LibraryNames.DEFAULT_REPLACEMENT, LibraryNames.sanitize("work"))
        assertEquals(LibraryNames.DEFAULT_REPLACEMENT, LibraryNames.sanitize("WORK"))
        assertEquals("my_lib", LibraryNames.sanitize("my-lib"))
        assertEquals("lib_2fast", LibraryNames.sanitize("2fast"))
        assertEquals("xil_defaultlib", LibraryNames.sanitize("xil_defaultlib"))
    }

    @Test
    fun duplicateLibrariesMergeAndEmptiesDrop() {
        val a = write("src/a.vhd")
        val b = write("src/b.vhd")
        val model = LibraryMappingModel(
            libraries = listOf(
                SourceLibrary("lib", listOf(a)),
                SourceLibrary("lib", listOf(b, a)),
                SourceLibrary("empty", emptyList()),
            ),
            source = "test",
        ).normalized()

        assertEquals(listOf("lib"), model.libraries.map { it.name })
        assertEquals(2, model.fileCount)
    }
}
