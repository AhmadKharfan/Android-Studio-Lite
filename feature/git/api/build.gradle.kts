plugins { id("asl.android.library.compose") }

android { namespace = "com.ahmadkharfan.androidstudiolite.feature.git.api" }

dependencies {
    // A contract module: it may expose domain types in its signatures, so :domain is `api`, not
    // `implementation`. Nothing else is allowed in here.
    api(projects.domain)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
}
