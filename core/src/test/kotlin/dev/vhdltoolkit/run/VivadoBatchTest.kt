package dev.vhdltoolkit.run

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class VivadoBatchTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun xpr(name: String = "counter4_ud.xpr"): Path =
        tmp.root.toPath().resolve(name).also { Files.writeString(it, "<Project/>") }

    // ------------------------------------------------------------- Tcl

    @Test
    fun synthesisRunsOnlySynthesis() {
        val tcl = VivadoTcl.script(xpr(), VivadoAction.SYNTHESIS, 4)
        assertTrue(tcl.contains("launch_runs synth_1 -jobs 4"))
        assertFalse("implementation is a separate action", tcl.contains("launch_runs impl_1"))
    }

    @Test
    fun implementationRunsSynthesisFirst() {
        val tcl = VivadoTcl.script(xpr(), VivadoAction.IMPLEMENTATION, 8)
        assertTrue(tcl.indexOf("launch_runs synth_1") < tcl.indexOf("launch_runs impl_1"))
    }

    @Test
    fun bitstreamGoesAllTheWay() {
        val tcl = VivadoTcl.script(xpr(), VivadoAction.BITSTREAM, 4)
        assertTrue(tcl.contains("launch_runs impl_1 -to_step write_bitstream -jobs 4"))
    }

    /**
     * Vivado exits zero after a failed run, so every stage must check its own
     * status. Without this the IDE reports a green build over a broken one.
     */
    @Test
    fun everyRunIsCheckedAndFailsLoudly() {
        for (action in listOf(VivadoAction.SYNTHESIS, VivadoAction.IMPLEMENTATION, VivadoAction.BITSTREAM)) {
            val tcl = VivadoTcl.script(xpr(), action, 4)
            val launches = Regex("launch_runs (\\w+)").findAll(tcl).count()
            val checks = Regex("get_property PROGRESS").findAll(tcl).count()
            assertEquals("$action: every launch is checked", launches, checks)
            assertTrue("$action: a failed run exits non-zero", tcl.contains("exit 1"))
        }
    }

    @Test
    fun simulationLaunchesXsim() {
        val tcl = VivadoTcl.script(xpr(), VivadoAction.SIMULATION, 4)
        assertTrue(tcl.contains("launch_simulation"))
        assertTrue(tcl.contains("run all"))
    }

    @Test
    fun pathsWithSpacesAreQuoted() {
        val spaced = tmp.root.toPath().resolve("my project").also { Files.createDirectories(it) }
            .resolve("top.xpr").also { Files.writeString(it, "<Project/>") }
        val tcl = VivadoTcl.script(spaced, VivadoAction.SYNTHESIS, 4)
        assertTrue("braces are Tcl literal quoting", tcl.contains("open_project {$spaced}"))
    }

    @Test
    fun jobCountIsClamped() {
        assertTrue(VivadoTcl.script(xpr(), VivadoAction.SYNTHESIS, 0).contains("-jobs 1"))
        assertTrue(VivadoTcl.script(xpr(), VivadoAction.SYNTHESIS, 9999).contains("-jobs 64"))
    }

    @Test
    fun scriptAlwaysTerminates() {
        for (action in VivadoAction.entries) {
            assertTrue("$action ends", VivadoTcl.script(xpr(), action, 4).trimEnd().endsWith("exit 0"))
        }
    }

    // -------------------------------------------------------- executable

    @Test
    fun findsTheLauncherInsideAnInstall() {
        val install = tmp.root.toPath().resolve("Vivado/2022.2")
        Files.createDirectories(install.resolve("bin"))
        val launcher = install.resolve("bin/vivado").also { Files.writeString(it, "#!/bin/sh") }
        assertEquals(launcher, VivadoExecutable.forInstall(install))
    }

    @Test
    fun noLauncherMeansNull() {
        val install = tmp.root.toPath().resolve("Vivado/2022.2")
        Files.createDirectories(install)
        assertNull(VivadoExecutable.forInstall(install))
    }

    // ------------------------------------------------------- log filter

    /** The filter is pure text matching; this checks the pattern it relies on. */
    private val locationPattern = Regex("""\[([^\[\]:]+\.(?:vhdl?|v|sv|xdc)):(\d+)]""", RegexOption.IGNORE_CASE)

    @Test
    fun recognisesVivadoErrorLocations() {
        val line = "ERROR: [Synth 8-1031] counter4_ud is not declared [/home/me/src/top.vhd:12]"
        val m = locationPattern.find(line)!!
        assertEquals("/home/me/src/top.vhd", m.groupValues[1])
        assertEquals("12", m.groupValues[2])
    }

    @Test
    fun recognisesWarningsAndOtherFileTypes() {
        val cases = mapOf(
            "WARNING: [Synth 8-327] inferring latch [/p/a.vhdl:45]" to ("/p/a.vhdl" to "45"),
            "CRITICAL WARNING: [Vivado 12-1008] bad constraint [/p/pins.xdc:3]" to ("/p/pins.xdc" to "3"),
            "ERROR: [Synth 8-2715] syntax error [/p/top.sv:100]" to ("/p/top.sv" to "100"),
        )
        for ((line, expected) in cases) {
            val m = locationPattern.find(line)!!
            assertEquals(expected.first, m.groupValues[1])
            assertEquals(expected.second, m.groupValues[2])
        }
    }

    @Test
    fun ordinaryOutputIsNotMistakenForALocation() {
        val noise = listOf(
            "INFO: [Synth 8-7075] Helper process launched with PID 12345",
            "Command: synth_design -top counter4_ud -part xc7a35tcpg236-1",
            "Finished Writing Synthesis Report : Time (s): cpu = 00:00:05",
        )
        for (line in noise) {
            assertNull("must not linkify: $line", locationPattern.find(line))
        }
    }
}
