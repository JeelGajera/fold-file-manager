plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/** The repository this build came from. Used by About and by the issue link. */
val SOURCE_URL = "https://github.com/JeelGajera/fold-file-manager"

/**
 * The short commit hash, or "unknown" outside a git checkout.
 *
 * Baked into BuildConfig so the About screen can name the exact commit a build
 * came from, and so a filed issue carries it. Failing softly matters: a source
 * tarball with no .git directory must still build.
 */
fun gitSha(): String = try {
    providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().ifEmpty { "unknown" }
} catch (e: Exception) {
    "unknown"
}

android {
    namespace = "com.jeelgajera.fold"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jeelgajera.fold"
        // MANAGE_EXTERNAL_STORAGE is API 30+. Below that the permission does not
        // exist and the whole premise of the app -- reading the filesystem rather
        // than MediaStore -- cannot be honoured.
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.4.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The About screen shows the exact commit a build came from, and the
        // "report an issue" link carries it into the issue body. A bug report
        // that names the commit is worth several that describe a version.
        buildConfigField("String", "GIT_SHA", "\"" + gitSha() + "\"")
        buildConfigField("String", "SOURCE_URL", "\"" + SOURCE_URL + "\"")
        buildConfigField("String", "ISSUES_URL", "\"" + SOURCE_URL + "/issues\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources.excludes += setOf(
            "META-INF/INDEX.LIST",
            "META-INF/io.netty.versions.properties",
            "META-INF/{AL2.0,LGPL2.1}",
        )
    }
}

dependencies {
    implementation(project(":core:design"))
    implementation(project(":core:storage"))
    implementation(project(":core:crypto"))
    implementation(project(":feature:browser"))
    implementation(project(":feature:search"))
    implementation(project(":feature:transfer"))
    implementation(project(":feature:vault"))
    implementation(project(":feature:glyph"))
    implementation(project(":widget"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
