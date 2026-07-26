pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FinanceMate"

// ---------------------------------------------------------------------------
// Optional build-output relocation.
//
// Some machines intermittently fail with "Unable to delete directory" while
// Gradle clears its own intermediates, because another process (typically
// real-time antivirus or search indexing) still holds handles on files Gradle
// just wrote. Moving build output out of the source tree avoids it, and is
// faster besides.
//
// Opt in per machine by adding to local.properties (which is gitignored):
//     financemate.buildDir=Z:/FinanceMateBuild
// Leave it unset and the build behaves exactly as normal.
//
// ⚠️ On Windows the target MUST be on the same drive as the checkout. Room's
// KSP processor relativises generated-source paths against the project
// directory, and Java cannot express a relative path between two drive roots —
// so a Z: checkout with a C: build directory fails with
// "this and base files have different roots". Same drive, different folder.
// ---------------------------------------------------------------------------
val localProperties = java.util.Properties().apply {
    val file = rootDir.resolve("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
localProperties.getProperty("financemate.buildDir")?.trim()?.takeIf { it.isNotEmpty() }?.let { root ->
    gradle.lifecycle.beforeProject {
        val slug = path.removePrefix(":").replace(':', '_').ifEmpty { "root" }
        layout.buildDirectory.set(java.io.File(root, slug))
    }
}

// Pure-JVM modules. Everything that carries real logic lives here so it can be
// unit-tested in milliseconds without an Android runtime.
include(":core:money")
include(":core:model")
include(":core:parsing")
include(":feature:insight")

// Android modules.
include(":core:crypto")
include(":core:data")
include(":feature:import")
include(":feature:budget")
include(":ai")
include(":app")
