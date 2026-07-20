plugins { id("asl.android.library.compose") }
android {
    namespace = "com.ahmadkharfan.androidstudiolite.feature.settings"
    buildFeatures { buildConfig = true }
    defaultConfig {
        buildConfigField("String", "VERSION_NAME", "\"1.0\"")
        buildConfigField("int", "VERSION_CODE", "1")
    }
}
dependencies {
    implementation(platform(libs.koin.bom))
    implementation(platform(libs.androidx.compose.bom))
    implementation(projects.domain)
    implementation(projects.core.common)
    implementation(projects.designsystem)
    implementation(projects.data.local)
    implementation(projects.data.git)
    implementation(projects.data.build)
    implementation(projects.data.ai)
    implementation(projects.feature.projects)
    implementation(projects.feature.git)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
}
