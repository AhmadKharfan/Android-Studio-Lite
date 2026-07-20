plugins { id("asl.android.library.compose") }
android { namespace = "com.ahmadkharfan.androidstudiolite.feature.onboarding" }
dependencies {
    implementation(platform(libs.koin.bom))
    implementation(platform(libs.androidx.compose.bom))
    implementation(projects.domain)
    implementation(projects.core.common)
    implementation(projects.designsystem)
    implementation(projects.data.local)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
}
