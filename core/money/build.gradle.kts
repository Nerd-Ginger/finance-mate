plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.kotest.property)
    // kotest's checkAll is a suspend function; property tests drive it via runBlocking.
    testImplementation(libs.kotlinx.coroutines.core)
}

tasks.test {
    useJUnit()
}
