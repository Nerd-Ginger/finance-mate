plugins {
    // AGP 9 has built-in Kotlin support. Applying org.jetbrains.kotlin.android
    // here is an error — the Kotlin plugin is supplied by AGP itself.
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.financemate.core.crypto"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    // Publishes an in-memory SecretVault so other modules can test against the
    // vault contract without a device, and without a fake being shipped in the
    // production APK.
    testFixtures {
        enable = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.kotlinx.coroutines.android)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testFixturesImplementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(testFixtures(project(":core:crypto")))

    androidTestImplementation(libs.androidx.test.junit)
}
