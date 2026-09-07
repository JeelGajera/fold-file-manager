import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.jeelgajera.fold.core.crypto"
    compileSdk = 37

    defaultConfig {
        minSdk = 30
        testOptions.targetSdk = 36
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
}

dependencies {
    // api, not implementation: VaultRepository's signatures speak in FsPath and
    // FileSystemProvider, so callers need those types on their compile classpath.
    api(project(":core:storage"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)
    api(libs.androidx.biometric)
    api(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
