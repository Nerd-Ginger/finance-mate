plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // Type-safe navigation routes are serialization-backed.
    alias(libs.plugins.kotlin.serialization)
    // Renders Compose to PNG on the JVM. See ScreenshotTest for why.
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "dev.financemate"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.financemate"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/INDEX.LIST"

            // PdfBox-Android bundles BouncyCastle so password-protected bank
            // statements can be opened - that part we need. What we do not need
            // is BouncyCastle's post-quantum suite, whose lookup tables alone
            // (picnic/lowmc, SIKE, Frodo, McEliece) add ~3 MB of resources for
            // algorithms no PDF has ever been encrypted with.
            excludes += "/org/bouncycastle/pqc/**"
        }
    }

    // Ship as an App Bundle. Without per-ABI delivery every device downloads all
    // four copies of ML Kit's ~11 MB OCR native library; with it, each device
    // gets only its own. This is the single biggest lever on download size.
    bundle {
        abi { enableSplit = true }
        density { enableSplit = true }
        language { enableSplit = true }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:money"))
    implementation(project(":core:data"))
    implementation(project(":core:crypto"))
    implementation(project(":feature:import"))
    implementation(project(":feature:budget"))
    implementation(project(":feature:insight"))
    implementation(project(":ai"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    // Holds the "has seen onboarding" flag. Deliberately outside the encrypted
    // ledger - see OnboardingStore.
    implementation(libs.androidx.datastore.preferences)
    // Runtime behind the type-safe navigation routes in ui/navigation/Routes.kt.
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    testImplementation(libs.kotest.assertions)
    // Screenshot testing. Renders Compose on the JVM via Robolectric, so every
    // screen can be reviewed without installing anything.
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.test.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.compose.ui.tooling)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
