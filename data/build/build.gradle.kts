import java.util.Properties

plugins {
    id("asl.android.library")
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

val buildServerUrl: String = localProperties.getProperty("asl.buildServerUrl")
    ?: providers.environmentVariable("ASL_BUILD_SERVER_URL").orNull
    ?: "https://build.androidstudiolite.me"

android {
    namespace = "com.ahmadkharfan.androidstudiolite.data.build"
    buildFeatures { buildConfig = true }
    defaultConfig {
        buildConfigField("String", "DEFAULT_BUILD_SERVER_URL", "\"$buildServerUrl\"")
    }
}
dependencies {
    implementation(projects.domain)
    implementation(projects.core.common)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.integrity)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
