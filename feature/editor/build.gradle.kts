plugins { id("asl.android.feature") }

android { namespace = "com.ahmadkharfan.androidstudiolite.feature.editor" }

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(projects.core.common)
    implementation(projects.designsystem)
    implementation(projects.feature.buildrun)
    implementation(projects.feature.git.api)
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
    testImplementation(projects.data.build)
    testImplementation(projects.data.local)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
