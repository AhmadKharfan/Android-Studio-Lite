plugins {
    id("asl.android.library")
    alias(libs.plugins.kotlin.serialization)
}
android {
    namespace = "com.ahmadkharfan.androidstudiolite.data.build"
    buildFeatures { buildConfig = true }
    defaultConfig {
        buildConfigField("String", "DEFAULT_BUILD_SERVER_URL", "\"http://129.212.152.5\"")
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
