plugins { id("asl.android.feature") }

android { namespace = "com.ahmadkharfan.androidstudiolite.feature.buildrun" }

dependencies {
    api(projects.feature.buildrun.api)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(projects.data.build)
    testImplementation(libs.junit)
}
