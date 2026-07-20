plugins { id("asl.android.library.compose") }
android { namespace = "com.ahmadkharfan.androidstudiolite.feature.terminal" }
dependencies {
    implementation(platform(libs.koin.bom))
    implementation(platform(libs.androidx.compose.bom))
    implementation(projects.domain)
    implementation(projects.core.common)
    implementation(projects.designsystem)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.commons.compress)
    implementation(libs.xz)
    implementation(libs.okhttp)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}
