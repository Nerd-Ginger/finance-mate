plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.financemate.feature.imports"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    api(project(":core:model"))
    // Text-format parsing lives in a pure-JVM module so it can be tested without
    // an emulator; this module owns only the Android-specific surfaces (SAF,
    // PDF rendering, on-device OCR) and the review UI.
    implementation(project(":core:parsing"))
    implementation(project(":core:data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // Both run entirely on-device: no statement content reaches the network.
    implementation(libs.pdfbox.android)
    implementation(libs.mlkit.text.recognition)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    testImplementation(libs.kotest.assertions)
    androidTestImplementation(libs.androidx.test.junit)
}
