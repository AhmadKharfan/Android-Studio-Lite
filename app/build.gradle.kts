plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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
        // Builds run server-side, so nothing forces the legacy targetSdk 28 constraint any more:
        // target a normal high SDK for a Play-compatible single-flavor app.
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Default base URL of the server-side build backend (control plane). Overridable at runtime
        // from Settings → Build server; this is only the initial value shown there.
        // Points at the deployed DOKS control plane (LoadBalancer svc asl-control-public).
        // NOTE: plain HTTP — only reachable because src/debug ships a host-scoped cleartext
        // exception (network_security_config.xml). Release builds are HTTPS-only, so this MUST
        // become https://<domain> once TLS is terminated on the LB.
        buildConfigField("String", "DEFAULT_BUILD_SERVER_URL", "\"http://129.212.152.5\"")

        // Gates Play Integrity attestation on device registration (POST /v1/devices). Off by default
        // so dev builds without Play Services (emulators, sideloaded) still register; the server also
        // ignores the token while PLAY_INTEGRITY_REQUIRED=false. Flip to true for a Play-signed release.
        buildConfigField("boolean", "PLAY_INTEGRITY_ENABLED", "false")
        // Cloud project number linked to the app in Play Console (App integrity → Play Integrity API).
        // Placeholder until the app is linked; only read when PLAY_INTEGRITY_ENABLED is true.
        buildConfigField("Long", "PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER", "0L")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

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
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.commons.compress)
    implementation(libs.xz)
    implementation(libs.jgit)
    implementation(libs.androidx.security.crypto)
    implementation(libs.play.integrity)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}