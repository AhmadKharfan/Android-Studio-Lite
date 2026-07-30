plugins { id("asl.android.feature") }
android {
    namespace = "com.ahmadkharfan.androidstudiolite.feature.settings"
    buildFeatures { buildConfig = true }
    defaultConfig {
        buildConfigField("String", "VERSION_NAME", "\"1.0\"")
        buildConfigField("int", "VERSION_CODE", "1")
    }
}
dependencies {
    implementation(projects.feature.settings.api)
    implementation(platform(libs.androidx.compose.bom))
    implementation(projects.core.common)
    implementation(projects.designsystem)
    implementation(projects.core.gitauth)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.navigation.compose)
}
