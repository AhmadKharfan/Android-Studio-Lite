pluginManagement {
    includeBuild("build-logic")
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

// Type-safe project accessors: reference modules as `projects.feature.editor` instead of
// `project(":feature:editor")`, so a renamed or removed module fails at configuration time with a
// compile error (and gets IDE autocomplete) rather than a stringly-typed miss.
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "AndroidStudioLite"
include(":app")
include(":domain")
include(":core:common")
include(":data:templates")
include(":designsystem")
include(":data:local")
include(":data:git")
include(":data:build")
include(":data:ai")
include(":feature:onboarding")
include(":feature:settings")
include(":feature:projects")
include(":feature:terminal")
include(":feature:buildrun")
include(":feature:git")
include(":feature:editor")
 