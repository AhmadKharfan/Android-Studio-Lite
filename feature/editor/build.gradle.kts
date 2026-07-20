plugins { id("asl.android.library.compose") }

android { namespace = "com.ahmadkharfan.androidstudiolite.feature.editor" }

dependencies {
    implementation(platform(libs.koin.bom))
    implementation(platform(libs.androidx.compose.bom))
    implementation(projects.domain)
    implementation(projects.core.common)
    implementation(projects.data.local)
    implementation(projects.data.build)
    implementation(projects.data.ai)
    implementation(projects.data.git)
    implementation(projects.designsystem)
    implementation(projects.feature.buildrun)
    implementation(projects.feature.git)
    implementation(projects.feature.terminal)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.junit)
}
