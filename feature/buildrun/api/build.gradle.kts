plugins { id("asl.android.library.compose") }

android { namespace = "com.ahmadkharfan.androidstudiolite.feature.buildrun.api" }

dependencies {
    // Contract module: domain types appear in public signatures, so :domain is `api`.
    api(projects.domain)
    implementation(platform(libs.androidx.compose.bom))
    // @Immutable on the console models is part of their contract: it tells Compose the
    // state is stable, so consumers skip recomposition correctly.
    implementation(libs.androidx.compose.ui)
    implementation(libs.kotlinx.coroutines.android)
}

// A contract module's surface is its whole reason to exist: every declaration must state its
// visibility rather than inherit `public` by default.
kotlin { explicitApi() }
