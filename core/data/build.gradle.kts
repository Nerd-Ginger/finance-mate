plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.financemate.core.data"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

ksp {
    // Emit schema JSON so Room migrations can be tested against real history
    // rather than hand-written assumptions about what the old schema was.
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    api(project(":core:model"))
    // The import pipeline normalises merchants and computes dedup fingerprints,
    // both of which live in the pure-JVM parsing module.
    api(project(":core:parsing"))
    api(project(":core:crypto"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    api(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // SQLCipher: the ledger is encrypted at rest, keyed from the Keystore.
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Robolectric lets the DAO and import-pipeline tests run on the JVM against a
    // real in-memory SQLite database. Encryption is deliberately not exercised
    // here — SQLCipher needs its native library, so DatabaseFactory is covered by
    // instrumentation tests on a device. What these verify is the schema and the
    // pipeline's behaviour, which is where the logic lives.
    testImplementation(libs.junit)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.room.testing)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.room.testing)
}
