plugins { id("asl.android.library.compose") }

android { namespace = "com.ahmadkharfan.androidstudiolite.feature.projects.api" }

dependencies {
    // Contract module: domain types may appear in public signatures, so :domain is `api`.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
}

// The contract is this module's whole purpose; every declaration states its visibility.
kotlin { explicitApi() }
