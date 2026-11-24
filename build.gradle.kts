import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "dev.vhdltoolkit"
version = "0.13.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        clion("2024.2.4")
        // Spike question 3: does a real editor fixture start against a
        // CLion target? The logic is covered in :core; what is untested is
        // everything that only exists once the IDE is running.
        testFramework(TestFrameworkType.Platform)
    }
    // The logic half. Bundled into the plugin jar; see the verification in
    // the spike notes that its classes actually land in the distribution.
    implementation(project(":core"))
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    pluginConfiguration {
        id = "dev.vhdltoolkit.vhdl"
        name = "VHDL Toolkit"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "242"
            untilBuild = provider { null }
        }
    }
    buildSearchableOptions = false
}

// Bundle VHDL-LS binaries (staged into bundled/ by scripts/fetch-vhdl-ls.sh)
// into the plugin directory under <plugin>/vhdl_ls/<platform>/.
tasks.withType<PrepareSandboxTask> {
    from(layout.projectDirectory.dir("bundled")) {
        into("${intellijPlatform.pluginConfiguration.name.get()}/vhdl_ls")
    }
}

// Failure detail in the CI log. Without this a ComparisonFailure prints only
// its file and line, and the expected/actual values are reachable only by
// downloading the HTML report.
tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStackTraces = true
        showCauses = true
    }
}
