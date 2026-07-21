import java.util.Properties

plugins {
    id("asl.android.application")
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun configValue(localKey: String, environment: String, default: String): String =
    sequenceOf(
        localProperties.getProperty(localKey),
        providers.environmentVariable(environment).orNull,
    ).firstOrNull { !it.isNullOrBlank() } ?: default

fun releaseCredential(property: String, environment: String): String? =
    providers.gradleProperty(property).orElse(providers.environmentVariable(environment)).orNull

val releaseStorePath = releaseCredential("asl.release.storeFile", "ASL_RELEASE_STORE_FILE")
val releaseStorePassword = releaseCredential("asl.release.storePassword", "ASL_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseCredential("asl.release.keyAlias", "ASL_RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseCredential("asl.release.keyPassword", "ASL_RELEASE_KEY_PASSWORD")
val releaseValues = listOf(releaseStorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
val releaseSigningComplete = releaseValues.all { !it.isNullOrBlank() }
val releaseSigningPartial = releaseValues.any { !it.isNullOrBlank() } && !releaseSigningComplete
if (releaseSigningPartial) {
    throw GradleException("Release signing is incomplete; provide storeFile, storePassword, keyAlias, and keyPassword")
}
val releaseTaskRequested = gradle.startParameter.taskNames.any {
    it.contains("release", ignoreCase = true) &&
        (it.contains("assemble", ignoreCase = true) || it.contains("bundle", ignoreCase = true) ||
            it.contains("package", ignoreCase = true))
}
if (releaseTaskRequested && !releaseSigningComplete) {
    throw GradleException(
        "Release signing is required. Set asl.release.* Gradle properties or ASL_RELEASE_* environment variables.",
    )
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
        targetSdk = 35
        versionCode = System.getenv("CI_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("CI_VERSION_NAME") ?: "1.0"

        buildConfigField("boolean", "PLAY_INTEGRITY_ENABLED", "false")
        buildConfigField("Long", "PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER", "0L")

        buildConfigField(
            "String",
            "GITHUB_OAUTH_CLIENT_ID",
            "\"${configValue("asl.githubOauthClientId", "ASL_GITHUB_OAUTH_CLIENT_ID", "Ov23liwZZUUv9fXJksGe")}\"",
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
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

    signingConfigs {
        if (releaseSigningComplete) {
            create("release") {
                storeFile = file(requireNotNull(releaseStorePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs {
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
    implementation(libs.androidx.security.crypto)
    implementation(libs.play.integrity)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.jgit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

tasks.withType<Test>().configureEach {
    System.getProperty("asl.templateCheck.out")?.let { systemProperty("asl.templateCheck.out", it) }
}
