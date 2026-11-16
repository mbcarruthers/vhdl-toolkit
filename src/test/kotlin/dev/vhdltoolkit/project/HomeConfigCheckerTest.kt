package dev.vhdltoolkit.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class HomeConfigCheckerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun home(): Path = tmp.root.toPath()

    private fun writeHomeConfig(content: String): Path {
        val p = home().resolve(HomeConfigChecker.FILE_NAME)
        Files.writeString(p, content)
        return p
    }

    @Test
    fun noConfigMeansNothingToReport() {
        assertNull(HomeConfigChecker.check(home()))
    }

    @Test
    fun detectsConfigWhoseFilesAllVanished() {
        // Exactly the shape TerosHDL leaves behind.
        writeHomeConfig(
            """
            [libraries]

            teroshdlDefault.files = [
              '/nowhere/adder3/src/adder3.vhdl',
              '/nowhere/adder3/tb/tb_adder3.vhdl',
            ]
            """.trimIndent(),
        )

        val finding = HomeConfigChecker.check(home())!!
        assertEquals(2, finding.totalFiles)
        assertEquals(2, finding.missingFiles.size)
        assertTrue("all files gone means stale", finding.isStale)
    }

    @Test
    fun configPointingAtRealFilesIsNotStale() {
        val real = home().resolve("real.vhd")
        Files.writeString(real, "-- vhdl")
        writeHomeConfig(
            """
            [libraries]
            mylib.files = ['${real.toAbsolutePath()}']
            """.trimIndent(),
        )

        val finding = HomeConfigChecker.check(home())!!
        assertTrue(finding.missingFiles.isEmpty())
        assertFalse(finding.isStale)
    }

    @Test
    fun partiallyValidConfigIsLeftAlone() {
        val real = home().resolve("real.vhd")
        Files.writeString(real, "-- vhdl")
        writeHomeConfig(
            """
            [libraries]
            mylib.files = ['${real.toAbsolutePath()}', '/nowhere/gone.vhd']
            """.trimIndent(),
        )

        val finding = HomeConfigChecker.check(home())!!
        assertEquals(1, finding.missingFiles.size)
        assertFalse("a config still partly in use must not be flagged", finding.isStale)
    }

    @Test
    fun globsAreNotTreatedAsMissing() {
        writeHomeConfig(
            """
            [libraries]
            mylib.files = ['src/**/*.vhd']
            """.trimIndent(),
        )
        val finding = HomeConfigChecker.check(home())!!
        assertTrue("a glob cannot be checked by existence", finding.missingFiles.isEmpty())
    }

    @Test
    fun disableRenamesAsideWithoutClobbering() {
        val config = writeHomeConfig("[libraries]\nx.files = ['/nowhere/a.vhd']\n")
        Files.writeString(home().resolve("${HomeConfigChecker.FILE_NAME}.bak"), "older backup")

        val moved = HomeConfigChecker.disable(config)!!
        assertFalse("original is gone", Files.exists(config))
        assertTrue("existing backup kept", Files.exists(home().resolve("${HomeConfigChecker.FILE_NAME}.bak")))
        assertEquals("${HomeConfigChecker.FILE_NAME}.bak2", moved.fileName.toString())
    }

    @Test
    fun malformedConfigIsIgnoredRatherThanThrowing() {
        writeHomeConfig("this is not toml at all [[[")
        assertNull(HomeConfigChecker.check(home()))
    }
}
