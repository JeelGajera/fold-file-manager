plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.jeelgajera.fold.core.design"
    compileSdk = 35

    defaultConfig {
        minSdk = 30
        testOptions.targetSdk = 35
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    api(composeBom)

    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    // Material3 is used as a skeleton only (ripple, window insets, text field
    // plumbing). Its colour and type defaults are never surfaced -- see FoldTheme.
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui.tooling.preview)
    debugApi(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
}
