plugins {
    id("asl.android.application")
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

        // GitHub OAuth App client ID used for the "Sign in with GitHub" device flow (see
        // GitHubDeviceFlowAuthenticator). This is a PUBLIC identifier, not a secret. Create an OAuth
        // App at GitHub → Settings → Developer settings → OAuth Apps, enable "Device Flow", and paste
        // its "Ov23li…" Client ID here. While empty, the device flow is disabled and the auth UI only
        // offers manual personal-access-token entry.
        buildConfigField("String", "GITHUB_OAUTH_CLIENT_ID", "\"Ov23liwZZUUv9fXJksGe\"")

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
    // testOptions.unitTests.isReturnDefaultValues is applied centrally by the asl.android.* convention
    // plugin so every module's JVM tests can touch stubbed android.* APIs without a device.
    packaging {
        jniLibs {
            // proot + its loader ship as native libs so they land in the exec-capable
            // nativeLibraryDir; legacy packaging forces them to be extracted to disk (compressed
            // libs loaded straight from the APK can't be executed). See ProotEnvironment.
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(projects.domain)
    implementation(projects.core.common)
    implementation(projects.data.templates)
    implementation(projects.designsystem)
    implementation(projects.data.local)
    implementation(projects.data.git)
    implementation(projects.data.build)
    implementation(projects.data.ai)
    implementation(projects.feature.onboarding)
    implementation(projects.feature.settings)
    implementation(projects.feature.projects)
    implementation(projects.feature.terminal)
    implementation(projects.feature.buildrun)
    implementation(projects.feature.git)
    implementation(projects.feature.editor)
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
// Forwards -Dasl.templateCheck.out to the test JVM, which Gradle doesn't do by default. It's what
// tools/template_build_check.sh uses to have TemplateBuildCheckGenerator emit every template for a
// real build on the server; unset, the generator skips itself.
tasks.withType<Test>().configureEach {
    System.getProperty("asl.templateCheck.out")?.let { systemProperty("asl.templateCheck.out", it) }
}
