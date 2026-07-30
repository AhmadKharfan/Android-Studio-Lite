plugins { id("asl.android.library.compose") }

android { namespace = "com.ahmadkharfan.androidstudiolite.core.gitauth" }

dependencies {
    // A capability shared by :feature:git and :feature:settings rather than something either owns.
    // It carries UI, so unlike a contract module it legitimately depends on the design system.
    api(projects.domain)
    implementation(projects.core.common)
    implementation(projects.designsystem)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
