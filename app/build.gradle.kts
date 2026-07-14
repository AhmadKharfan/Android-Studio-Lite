plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ahmadkharfan.androidstudiolite"
    compileSdk {
        version = release(37)
    }
    ndkVersion = "27.1.12297006"

    defaultConfig {
        applicationId = "com.ahmadkharfan.androidstudiolite"
        minSdk = 24
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Hosts the JSON manifest describing which JDK/Android-SDK/Gradle archives to fetch, per ABI,
        // for the on-device full-Gradle build (docs/build-run/06-full-build-production-study.md §5.4 /
        // §7 Phase A-B). Point this at real hosted release assets before shipping — until then the
        // environment install surfaces a clear "not configured" failure instead of crashing.
        buildConfigField("String", "IDE_ENVIRONMENT_MANIFEST_URL", "\"\"")

        externalNativeBuild {
            cmake {
                // Plain C executable; no STL needed.
                arguments += "-DANDROID_STL=none"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Two distributions with mutually exclusive constraints (docs/build-run/06 §4):
    //  - play: Play-Store-compliant, high targetSdk, in-process build engine (no downloaded executables).
    //  - full: self-distributed, targetSdk 28 so downloaded toolchain binaries (JDK/Gradle) may be exec()'d;
    //    ships the tooling-server fat jar in its assets.
    flavorDimensions += "distribution"
    productFlavors {
        create("play") {
            dimension = "distribution"
            targetSdk = 35
        }
        create("full") {
            dimension = "distribution"
            targetSdk = 28
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // JGit leans on java.nio.file / java.time APIs that only exist natively from API 26; desugaring
        // backfills them so the git integration works down to minSdk 24.
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            // Physically extract native binaries to nativeLibraryDir at install so the on-device
            // toolchain probe (and later the real toolchain binaries) can be exec()'d — libraries
            // left compressed inside the APK cannot be executed.
            useLegacyPackaging = true
        }
    }
}

// The full flavor embeds the Gradle tooling server as an asset; rebuild and copy the fat jar
// whenever a full-flavor variant merges assets so the app never ships a stale server.
val copyToolingServerJar = tasks.register<Copy>("copyToolingServerJar") {
    from(project(":tooling:server").tasks.named("fatJar"))
    into(layout.projectDirectory.dir("src/full/assets"))
    rename { "tooling-server.jar" }
}

tasks.matching { it.name.matches(Regex("merge(Full\\w+)Assets")) }.configureEach {
    dependsOn(copyToolingServerJar)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.commons.compress)
    implementation(libs.xz)
    implementation(libs.jgit)
    implementation(libs.androidx.security.crypto)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    // Full flavor only: the JSON-RPC protocol types shared with the on-device tooling server. The
    // server fat jar itself ships as an asset (copyToolingServerJar); this is just the wire model.
    "fullImplementation"(project(":tooling:proto"))
    // Play-only: the in-process build engine (aapt2/ECJ/kotlinc/D8/apksig pipeline). The full flavor
    // uses the out-of-process Gradle tooling server instead, so this stays a per-flavor dependency.
    "playImplementation"(project(":build:engine"))
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}