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
// When the checkout lives on a mapped network drive, SMB holds file handles long
// enough that Gradle intermittently fails with "Unable to delete directory" while
// clearing its own outputs. Pointing build directories at local disk removes the
// problem outright and is considerably faster besides.
//
// Opt in per machine by adding to local.properties (which is gitignored):
//     financemate.buildDir=C:/FinanceMateBuild
// Leave it unset and the build behaves exactly as normal.
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
