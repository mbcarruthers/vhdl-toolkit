import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "dev.vhdltoolkit"
version = "0.9.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        clion("2024.2.4")
    }
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
