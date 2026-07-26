plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.financemate.ai"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // The Anthropic Java SDK targets java.time and other desugared APIs.
        isCoreLibraryDesugaringEnabled = true
    }
}

// ---------------------------------------------------------------------------
// THE EGRESS CHOKE POINT.
//
// This is the only module in FinanceMate permitted to touch the network. Every
// byte that leaves the device passes through the redaction gate here. If you
// find yourself wanting an HTTP client in another module, that is the signal
// that something is about to bypass the gate — put it behind an interface in
// this module instead.
//
// `:app` enforces this structurally via a dependency-inspection test rather
// than relying on anyone remembering the rule.
// ---------------------------------------------------------------------------
dependencies {
    api(project(":core:model"))
    implementation(project(":core:crypto"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.anthropic.java)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
