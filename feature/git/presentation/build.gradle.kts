plugins { id("asl.android.feature") }

android { namespace = "com.ahmadkharfan.androidstudiolite.feature.git" }

dependencies {
    implementation(projects.core.gitauth)
    implementation(projects.feature.git.api)
    implementation(platform(libs.androidx.compose.bom))
    implementation(projects.core.common)
    implementation(projects.designsystem)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
