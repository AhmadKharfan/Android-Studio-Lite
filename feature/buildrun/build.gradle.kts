plugins { id("asl.android.library.compose") }

android { namespace = "com.ahmadkharfan.androidstudiolite.feature.buildrun" }

dependencies {
    implementation(platform(libs.koin.bom))
    implementation(platform(libs.androidx.compose.bom))
    implementation(projects.domain)
    implementation(projects.core.common)
    implementation(projects.data.build)
    implementation(projects.data.local)
    implementation(projects.data.git)
    implementation(projects.designsystem)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
