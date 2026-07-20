plugins {
    id("asl.android.library")
}

android {
    namespace = "com.ahmadkharfan.androidstudiolite.data.templates"
}

dependencies {
    implementation(projects.domain)
    testImplementation(libs.junit)
}
