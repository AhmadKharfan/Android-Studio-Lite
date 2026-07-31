plugins { id("asl.android.feature") }
android { namespace = "com.ahmadkharfan.androidstudiolite.feature.onboarding" }
dependencies {
    implementation(projects.feature.onboarding.api)
    implementation(platform(libs.androidx.compose.bom))
    implementation(projects.core.common)
    implementation(projects.designsystem)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.navigation.compose)
}
