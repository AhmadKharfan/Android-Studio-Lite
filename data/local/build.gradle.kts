plugins { id("asl.android.library") }
android { namespace = "com.ahmadkharfan.androidstudiolite.data.local" }
dependencies {
    implementation(projects.domain)
    implementation(projects.core.common)
    implementation(projects.data.templates)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
}
