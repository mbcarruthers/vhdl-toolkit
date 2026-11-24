package dev.vhdltoolkit.project

import dev.vhdltoolkit.project.TclProjectScript.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class TclAndFileListTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun write(relative: String, content: String): Path {
        val p = tmp.root.toPath().resolve(relative)
        Files.createDirectories(p.parent)
        Files.writeString(p, content)
        return p.normalize()
    }

    // ------------------------------------------------------ classification

    @Test
    fun projectModeScriptIsImportable() {
        val a = TclProjectScript.classify(
            """
            create_project myproj ./build -part xc7a35tcpg236-1
            add_files [glob src/*.vhd]
            launch_runs synth_1
            """.trimIndent(),
        )
        assertEquals(Kind.PROJECT_MODE, a.kind)
        assertTrue(a.isImportable)
        assertEquals("myproj", a.projectName)
    }

    @Test
    fun nonProjectModeNeverProducesAProject() {
        val a = TclProjectScript.classify(
            """
            read_vhdl -library work [glob rtl/*.vhd]
            read_xdc constraints/pins.xdc
            synth_design -top my_top -part xc7a35tcpg236-1
            opt_design
            place_design
            route_design
            write_bitstream -force out.bit
            """.trimIndent(),
        )
        assertEquals(Kind.NON_PROJECT_MODE, a.kind)
        assertFalse("there is no .xpr to import", a.isImportable)
    }

    /** The real NEORV32 IP packaging script: creates a project on the way. */
    @Test
    fun ipPackagingIsRecognisedAndStillCreatesAProject() {
        val a = TclProjectScript.classify(
            """
            set outputdir ${'$'}script_dir/neorv32_vivado_ip_work
            create_project "neorv32-ip" ${'$'}outputdir
            add_files ${'$'}file_list
            set_property library neorv32 [get_files ${'$'}file_list]
            ipx::package_project -root_dir ${'$'}outputdir/packaged_ip -vendor NEORV32
            ipx::save_core [ipx::current_core]
            close_project
            """.trimIndent(),
        )
        assertEquals(Kind.IP_PACKAGING, a.kind)
        assertTrue("it still leaves an .xpr behind", a.isImportable)
        assertEquals("neorv32-ip", a.projectName)
    }

    @Test
    fun blockDesignScriptIsRecognised() {
        val a = TclProjectScript.classify("create_bd_design \"design_1\"\ncreate_bd_cell -type ip zynq")
        assertEquals(Kind.BLOCK_DESIGN, a.kind)
    }

    @Test
    fun commentedOutCommandsDoNotCount() {
        val a = TclProjectScript.classify(
            """
            # create_project disabled_example ./build
            puts "nothing to see"
            """.trimIndent(),
        )
        assertEquals(Kind.UNKNOWN, a.kind)
        assertFalse(a.isImportable)
    }

    @Test
    fun projectNameMayBeAVariableWeCannotResolve() {
        val a = TclProjectScript.classify("create_project ${'$'}proj_name ${'$'}outputdir")
        assertEquals(Kind.PROJECT_MODE, a.kind)
        assertTrue("still worth running", a.isImportable)
        assertNull("but the name is unknown", a.projectName)
    }

    @Test
    fun createProjectWithOptionsBeforeTheName() {
        val a = TclProjectScript.classify("create_project -force -part xc7a35t mydesign ./build")
        assertEquals("mydesign", a.projectName)
    }

    // --------------------------------------------------- generated project

    @Test
    fun findsTheProjectAScriptJustWrote() {
        val before = System.currentTimeMillis()
        Thread.sleep(5)
        val generated = write("work/neorv32-ip.xpr", "<Project/>")

        val found = TclProjectScript.findGeneratedProject(tmp.root.toPath(), before, "neorv32-ip")
        assertEquals(generated, found)
    }

    @Test
    fun prefersTheNamedProjectOverOthers() {
        val before = System.currentTimeMillis() - 1000
        write("a/other.xpr", "<Project/>")
        val wanted = write("b/wanted.xpr", "<Project/>")
        assertEquals(wanted, TclProjectScript.findGeneratedProject(tmp.root.toPath(), before, "wanted"))
    }

    @Test
    fun noProjectMeansNull() {
        assertNull(TclProjectScript.findGeneratedProject(tmp.root.toPath(), 0, null))
    }

    // ------------------------------------------------------- file lists

    @Test
    fun readsASimpleList() {
        write("rtl/a.vhd", "-- vhdl")
        write("rtl/b.vhd", "-- vhdl")
        val list = write(
            "rtl/files.f",
            """
            # core sources
            a.vhd
            b.vhd
            """.trimIndent(),
        )

        val model = FileListReader.read(list)
        assertEquals(2, model.fileCount)
        assertEquals(
            setOf("a.vhd", "b.vhd"),
            model.libraries.single().files.map { it.fileName.toString() }.toSet(),
        )
    }

    @Test
    fun neverNamesTheLibraryWork() {
        write("rtl/a.vhd", "-- vhdl")
        val list = write("rtl/files.f", "a.vhd")
        val model = FileListReader.read(list)
        assertEquals(LibraryNames.DEFAULT_REPLACEMENT, model.libraries.single().name)
    }

    /**
     * NEORV32's list refers to `${'$'}NEORV32_HOME`, which its build system sets
     * and we do not. The value is inferred from where the list file sits.
     */
    @Test
    fun infersAnUnsetRootVariableFromTheListLocation() {
        write("rtl/core/neorv32_cpu.vhd", "-- vhdl")
        write("rtl/core/neorv32_top.vhd", "-- vhdl")
        val list = write(
            "rtl/file_list_core.f",
            """
            ${'$'}NEORV32_HOME/rtl/core/neorv32_cpu.vhd
            ${'$'}NEORV32_HOME/rtl/core/neorv32_top.vhd
            """.trimIndent(),
        )

        val model = FileListReader.read(list)
        assertEquals("the project root is an ancestor of the list", 2, model.fileCount)
    }

    @Test
    fun explicitVariablesWinOverInference() {
        write("elsewhere/x.vhd", "-- vhdl")
        val list = write("rtl/files.f", "${'$'}ROOT/x.vhd")
        val model = FileListReader.read(
            list,
            mapOf("ROOT" to tmp.root.toPath().resolve("elsewhere").toString()),
        )
        assertEquals(1, model.fileCount)
    }

    @Test
    fun followsIncludedLists() {
        write("rtl/a.vhd", "-- vhdl")
        write("rtl/sub/b.vhd", "-- vhdl")
        write("rtl/sub/more.f", "b.vhd")
        val list = write("rtl/files.f", "a.vhd\n-f sub/more.f")

        val model = FileListReader.read(list)
        assertEquals(2, model.fileCount)
    }

    @Test
    fun selfIncludingListTerminates() {
        write("rtl/a.vhd", "-- vhdl")
        val list = write("rtl/files.f", "a.vhd\n-f files.f")
        assertEquals(1, FileListReader.read(list).fileCount)
    }

    @Test
    fun ignoresSwitchesAndNonVhdlSources() {
        write("rtl/a.vhd", "-- vhdl")
        write("rtl/b.v", "// verilog")
        val list = write(
            "rtl/files.f",
            """
            +incdir+./include
            -sv
            +define+DEBUG=1
            b.v
            a.vhd
            """.trimIndent(),
        )
        val model = FileListReader.read(list)
        assertEquals(listOf("a.vhd"), model.libraries.single().files.map { it.fileName.toString() })
    }

    @Test
    fun missingFilesAreReportedNotFatal() {
        write("rtl/a.vhd", "-- vhdl")
        val list = write("rtl/files.f", "a.vhd\nmissing.vhd")
        val model = FileListReader.read(list)
        assertEquals(1, model.fileCount)
        assertTrue(model.warnings.any { it.message.contains("does not exist") })
    }

    @Test
    fun unresolvableVariableIsReportedOnce() {
        val list = write("rtl/files.f", "${'$'}MYSTERY/a.vhd\n${'$'}MYSTERY/b.vhd")
        val model = FileListReader.read(list)
        assertEquals(1, model.warnings.count { it.message.contains("MYSTERY") })
    }

    @Test
    fun rendersToUsableToml() {
        write("rtl/a.vhd", "-- vhdl")
        val list = write("rtl/files.f", "a.vhd")
        val toml = VhdlLsTomlWriter.render(FileListReader.read(list), tmp.root.toPath())
        assertTrue(toml.contains("${LibraryNames.DEFAULT_REPLACEMENT}.files"))
        assertTrue(toml.contains("rtl/a.vhd"))
    }
}
