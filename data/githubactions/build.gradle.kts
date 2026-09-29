plugins {
    id("asl.android.library")
}
android { namespace = "com.ahmadkharfan.androidstudiolite.data.githubactions" }
dependencies {
    implementation(projects.domain)
    testImplementation(libs.junit)
}
