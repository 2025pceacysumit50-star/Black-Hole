// Merge these into the build.gradle.kts of a new Android Studio project —
// see the top-level README for why this isn't a full standalone project.
// Verify these versions are still current before building; they shift
// over time and this file wasn't checked against a live Gradle sync.

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.yourcompany.shieldcheck"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.yourcompany.shieldcheck"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // For the periodic background scan (ScanWorker) — new dependency,
    // added when malware scanning became automatic instead of manual-only.
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
