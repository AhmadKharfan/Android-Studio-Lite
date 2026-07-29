plugins { id("asl.android.feature") }
android { namespace = "com.ahmadkharfan.androidstudiolite.feature.projects" }
dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(projects.core.common)
    implementation(projects.designsystem)
    implementation(projects.feature.git)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.navigation.compose)
    testImplementation(projects.data.templates)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
