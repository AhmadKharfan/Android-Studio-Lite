plugins {
    id("asl.android.library.compose")
}

android {
    namespace = "com.ahmadkharfan.androidstudiolite.designsystem"
}

dependencies {
    implementation(projects.domain)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
}
