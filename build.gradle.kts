plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ktlint) apply false
}

// One formatting standard for the whole repo rather than a per-module opt-in:
// a module added later is linted without anyone remembering to wire it up.
// Rules live in .editorconfig so the IDE and CI read the same source.
val ktlintVersion = libs.versions.ktlint

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set(ktlintVersion)
        // The Gradle scripts are Kotlin too, and they are where toolchain
        // mistakes hide, so they are linted alongside the sources.
        filter {
            exclude { it.file.path.contains("/build/") }
        }
    }
}
