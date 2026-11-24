// Everything here is plain Kotlin: tokenizing, block structure, diagnostics
// analysis, project file readers. None of it may reach into the IDE.
//
// That rule used to be a convention held up by review. Here it is the
// classpath: the platform is not a dependency of this module, so an import
// of com.intellij does not resolve and the build fails where it is written,
// rather than at runtime in whichever test happens to touch the line.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    // The platform ships the Kotlin standard library, so it is compiled
    // against but not bundled — see kotlin.stdlib.default.dependency in
    // gradle.properties.
    compileOnly(kotlin("stdlib"))
    testImplementation(kotlin("stdlib"))
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(21)
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
