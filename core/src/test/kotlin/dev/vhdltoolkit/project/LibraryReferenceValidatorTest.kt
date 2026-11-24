package dev.vhdltoolkit.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class LibraryReferenceValidatorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun source(relative: String, content: String): Path {
        val p = tmp.root.toPath().resolve(relative)
        Files.createDirectories(p.parent)
        Files.writeString(p, content)
        return p.normalize()
    }

    private fun model(libraryName: String, vararg files: Path) = LibraryMappingModel(
        libraries = listOf(SourceLibrary(libraryName, files.toList())),
        source = "test",
    )

    // ------------------------------------------------- reference extraction

    @Test
    fun findsLibrariesInContextClauses() {
        val refs = LibraryReferenceValidator.librariesReferencedIn(
            """
            library IEEE;
            use IEEE.std_logic_1164.all;
            library neorv32;
            use neorv32.neorv32_package.all;
            """.trimIndent(),
        )
        assertEquals(setOf("ieee", "neorv32"), refs)
    }

    @Test
    fun commentsAndStringsDoNotCount() {
        val refs = LibraryReferenceValidator.librariesReferencedIn(
            """
            -- library pretend;
            library IEEE;
            use IEEE.std_logic_1164.all;
            entity e is end entity;
            architecture a of e is
            begin
                report "library fake; use fake.thing.all;";
            end architecture;
            """.trimIndent(),
        )
        assertEquals("only the real context clause", setOf("ieee"), refs)
    }

    // -------------------------------------------------------- the .f case

    /**
     * The case that motivated this: a file list cannot name a library, so
     * everything lands in defaultlib while the sources expect `neorv32`.
     * Guessing would be brittle; saying so is not.
     */
    @Test
    fun reportsWhenSourcesExpectALibraryTheMappingDoesNotDefine() {
        val file = source(
            "rtl/core/neorv32_cpu.vhd",
            """
            library ieee;
            use ieee.std_logic_1164.all;
            library neorv32;
            use neorv32.neorv32_package.all;
            entity neorv32_cpu is end entity;
            """.trimIndent(),
        )

        val validated = LibraryReferenceValidator.validate(model(LibraryNames.DEFAULT_REPLACEMENT, file))
        val warning = validated.warnings.single { it.message.contains("neorv32") }
        assertTrue("names the missing library", warning.message.contains("neorv32"))
        assertTrue("names what is defined instead", warning.message.contains("defaultlib"))
    }

    @Test
    fun aCorrectlyNamedLibraryProducesNoWarning() {
        val file = source(
            "rtl/a.vhd",
            """
            library ieee;
            use ieee.std_logic_1164.all;
            library neorv32;
            use neorv32.neorv32_package.all;
            """.trimIndent(),
        )
        assertTrue(LibraryReferenceValidator.validate(model("neorv32", file)).warnings.isEmpty())
    }

    @Test
    fun standardLibrariesNeverCountAsMissing() {
        val file = source(
            "rtl/a.vhd",
            """
            library ieee;
            use ieee.std_logic_1164.all;
            use ieee.numeric_std.all;
            use std.textio.all;
            entity e is end entity;
            architecture a of e is
            begin
                u0 : entity work.other;
            end architecture;
            """.trimIndent(),
        )
        assertTrue(
            "ieee, std and work are always available",
            LibraryReferenceValidator.validate(model("defaultlib", file)).warnings.isEmpty(),
        )
    }

    @Test
    fun vendorLibrariesAreReportedSeparately() {
        val file = source(
            "rtl/top.vhd",
            """
            library ieee;
            use ieee.std_logic_1164.all;
            library unisim;
            use unisim.vcomponents.all;
            """.trimIndent(),
        )
        val findings = LibraryReferenceValidator.inspect(model("defaultlib", file))
        assertEquals(setOf("unisim"), findings.unmappedVendorLibraries)
        assertTrue("not confused with a project library", findings.unmappedProjectLibraries.isEmpty())

        val warning = LibraryReferenceValidator.validate(model("defaultlib", file))
            .warnings.single { it.message.contains("unisim") }
        assertTrue("explains the consequence", warning.message.contains("undefined"))
    }

    @Test
    fun mappedVendorLibraryIsNotReported() {
        val top = source("rtl/top.vhd", "library unisim;\nuse unisim.vcomponents.all;")
        val vcomp = source("vendor/unisim_VCOMP.vhd", "-- component declarations")
        val m = LibraryMappingModel(
            libraries = listOf(
                SourceLibrary("defaultlib", listOf(top)),
                SourceLibrary("unisim", listOf(vcomp), isThirdParty = true),
            ),
            source = "test",
        )
        assertTrue(LibraryReferenceValidator.validate(m).warnings.isEmpty())
    }

    @Test
    fun theWorkRenameDoesNotLookLikeAGap() {
        // An importer may map to 'work'; the writer renames it. Neither spelling
        // should be reported as missing.
        val file = source("rtl/a.vhd", "library ieee;\nuse ieee.std_logic_1164.all;")
        assertTrue(LibraryReferenceValidator.validate(model("work", file)).warnings.isEmpty())
    }

    @Test
    fun severalMissingLibrariesAreListedTogether() {
        val file = source(
            "rtl/a.vhd",
            """
            library alpha;
            use alpha.pkg.all;
            library beta;
            use beta.pkg.all;
            """.trimIndent(),
        )
        val warning = LibraryReferenceValidator.validate(model("defaultlib", file))
            .warnings.single { it.message.contains("alpha") }
        assertTrue(warning.message.contains("beta"))
        assertTrue("plural wording", warning.message.contains("libraries"))
    }

    @Test
    fun unreadableSourcesDoNotBreakValidation() {
        val missing = tmp.root.toPath().resolve("gone.vhd")
        val validated = LibraryReferenceValidator.validate(model("defaultlib", missing))
        assertEquals("no findings, no crash", 0, validated.warnings.size)
    }

    @Test
    fun emptyModelIsLeftAlone() {
        val empty = LibraryMappingModel(libraries = emptyList(), source = "test")
        assertEquals(empty, LibraryReferenceValidator.validate(empty))
    }

    @Test
    fun existingWarningsArePreserved() {
        val file = source("rtl/a.vhd", "library mystery;\nuse mystery.pkg.all;")
        val m = model("defaultlib", file).copy(warnings = listOf(ImportWarning("earlier note")))
        val validated = LibraryReferenceValidator.validate(m)
        assertTrue(validated.warnings.any { it.message == "earlier note" })
        assertTrue(validated.warnings.any { it.message.contains("mystery") })
    }
}
