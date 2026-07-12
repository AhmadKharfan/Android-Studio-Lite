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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AndroidStudioLite"
include(":app")

// Build-system abstraction modules. The ":build" path would default to the root build/ output
// directory, so remap the whole subtree under buildsystem/.
include(":build:common")
include(":build:engine")
project(":build").projectDir = file("buildsystem")
project(":build:common").projectDir = file("buildsystem/common")
project(":build:engine").projectDir = file("buildsystem/engine")

// Full-flavor Gradle tooling server (plain JVM; fat jar shipped in app/src/full/assets).
include(":tooling:proto")
include(":tooling:server")
 