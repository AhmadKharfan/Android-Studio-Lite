plugins {
    id("asl.android.library")
    alias(libs.plugins.kotlin.serialization)
}
android { namespace = "com.ahmadkharfan.androidstudiolite.data.githubactions" }
dependencies {
    implementation(projects.domain)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
