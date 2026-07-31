plugins { id("asl.android.feature") }
android { namespace = "com.ahmadkharfan.androidstudiolite.feature.terminal" }
dependencies {
    implementation(projects.feature.terminal.api)
    implementation(platform(libs.androidx.compose.bom))
    implementation(projects.core.common)
    implementation(projects.designsystem)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.commons.compress)
    implementation(libs.xz)
    implementation(libs.okhttp)
    implementation(libs.androidx.compose.ui)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(libs.junit)
}
