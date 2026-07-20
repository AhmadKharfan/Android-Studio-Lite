plugins { id("asl.android.library") }
android { namespace = "com.ahmadkharfan.androidstudiolite.data.git" }
dependencies {
    implementation(projects.domain)
    implementation(projects.data.local)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.jgit)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
