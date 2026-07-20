plugins { id("asl.android.library.compose") }
android { namespace = "com.ahmadkharfan.androidstudiolite.feature.projects" }
dependencies {
    implementation(platform(libs.koin.bom))
    implementation(platform(libs.androidx.compose.bom))
    implementation(projects.domain)
    implementation(projects.core.common)
    implementation(projects.designsystem)
    implementation(projects.data.local)
    implementation(projects.data.git)
    implementation(projects.feature.git)
    implementation(projects.data.templates)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    testImplementation(libs.junit)
}
