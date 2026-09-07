import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.jeelgajera.fold.widget"
    compileSdk = 37

    defaultConfig {
        minSdk = 30
        testOptions.targetSdk = 36
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
}

dependencies {
    implementation(project(":core:storage"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.glance.appwidget)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
