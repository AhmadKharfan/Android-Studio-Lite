plugins {
    id("asl.android.library")
    alias(libs.plugins.kotlin.serialization)
}
android { namespace = "com.ahmadkharfan.androidstudiolite.data.ai" }
dependencies {
    implementation(projects.domain)
    implementation(projects.data.build)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
