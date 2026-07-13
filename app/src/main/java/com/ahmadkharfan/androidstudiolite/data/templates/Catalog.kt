package com.ahmadkharfan.androidstudiolite.data.templates

/**
 * The single source of truth for versions/libraries/plugins the templates emit. Everything is pinned
 * to the ASL compatibility matrix so generated projects are internally consistent and match the
 * on-device toolchain (JDK 17 / Gradle 8.7 / AGP 8.5.2 / compileSdk 34).
 *
 * Versions here target Kotlin 1.9.24, which pairs with AGP 8.5.2 and uses the Compose *compiler
 * extension* (`composeOptions`) rather than the Kotlin 2.0 Compose Gradle plugin — see [ProjectRecipe].
 */
object Catalog {

    // --- pinned toolchain (compat matrix) -------------------------------------------------------
    const val GRADLE_VERSION = "8.7"
    const val JDK_VERSION = "17"
    const val AGP_VERSION = "8.5.2"
    const val KOTLIN_VERSION = "1.9.24"
    const val COMPOSE_COMPILER_VERSION = "1.5.14"
    const val DEFAULT_COMPILE_SDK = 34

    // --- plugins --------------------------------------------------------------------------------
    val androidApplication = PluginSpec("android-application", "com.android.application", "agp", AGP_VERSION)
    val androidLibrary = PluginSpec("android-library", "com.android.library", "agp", AGP_VERSION)
    val kotlinAndroid = PluginSpec("kotlin-android", "org.jetbrains.kotlin.android", "kotlin", KOTLIN_VERSION)

    // --- libraries ------------------------------------------------------------------------------
    val coreKtx = LibrarySpec("androidx-core-ktx", "androidx.core", "core-ktx", "coreKtx", "1.13.1")
    val appcompat = LibrarySpec("androidx-appcompat", "androidx.appcompat", "appcompat", "appcompat", "1.7.0")
    val material = LibrarySpec("material", "com.google.android.material", "material", "material", "1.12.0")
    val constraintLayout =
        LibrarySpec("androidx-constraintlayout", "androidx.constraintlayout", "constraintlayout", "constraintlayout", "2.1.4")
    val activity = LibrarySpec("androidx-activity", "androidx.activity", "activity", "activity", "1.9.0")

    // Navigation (Views-based nav host for Bottom Nav / Nav Drawer)
    val navigationFragment =
        LibrarySpec("androidx-navigation-fragment-ktx", "androidx.navigation", "navigation-fragment-ktx", "navigation", "2.7.7")
    val navigationUi =
        LibrarySpec("androidx-navigation-ui-ktx", "androidx.navigation", "navigation-ui-ktx", "navigation", "2.7.7")
    val lifecycleViewModel =
        LibrarySpec("androidx-lifecycle-viewmodel-ktx", "androidx.lifecycle", "lifecycle-viewmodel-ktx", "lifecycle", "2.8.3")
    val lifecycleLiveData =
        LibrarySpec("androidx-lifecycle-livedata-ktx", "androidx.lifecycle", "lifecycle-livedata-ktx", "lifecycle", "2.8.3")

    // Compose
    val lifecycleRuntimeKtx =
        LibrarySpec("androidx-lifecycle-runtime-ktx", "androidx.lifecycle", "lifecycle-runtime-ktx", "lifecycle", "2.8.3")
    val activityCompose =
        LibrarySpec("androidx-activity-compose", "androidx.activity", "activity-compose", "activity", "1.9.0")
    val composeBom = LibrarySpec("androidx-compose-bom", "androidx.compose", "compose-bom", "composeBom", "2024.06.00")
    // The remaining Compose artifacts are governed by the BOM above → intentionally versionless.
    val composeUi = LibrarySpec("androidx-ui", "androidx.compose.ui", "ui")
    val composeUiGraphics = LibrarySpec("androidx-ui-graphics", "androidx.compose.ui", "ui-graphics")
    val composeUiToolingPreview = LibrarySpec("androidx-ui-tooling-preview", "androidx.compose.ui", "ui-tooling-preview")
    val composeUiTooling = LibrarySpec("androidx-ui-tooling", "androidx.compose.ui", "ui-tooling")
    val composeUiTestManifest = LibrarySpec("androidx-ui-test-manifest", "androidx.compose.ui", "ui-test-manifest")
    val composeUiTestJunit4 = LibrarySpec("androidx-ui-test-junit4", "androidx.compose.ui", "ui-test-junit4")
    val material3 = LibrarySpec("androidx-material3", "androidx.compose.material3", "material3")
    val navigationCompose =
        LibrarySpec("androidx-navigation-compose", "androidx.navigation", "navigation-compose", "navigation", "2.7.7")
    val viewpager2 = LibrarySpec("androidx-viewpager2", "androidx.viewpager2", "viewpager2", "viewpager2", "1.1.0")

    // Test
    val junit = LibrarySpec("junit", "junit", "junit", "junit", "4.13.2")
    val androidxJunit = LibrarySpec("androidx-junit", "androidx.test.ext", "junit", "junitVersion", "1.2.1")
    val espressoCore = LibrarySpec("androidx-espresso-core", "androidx.test.espresso", "espresso-core", "espressoCore", "3.6.1")
}
