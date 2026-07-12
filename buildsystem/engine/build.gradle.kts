// Play-flavor in-process build engine (cleanroom): dependency resolution, aapt2/ECJ/kotlinc/D8
// pipeline, incremental task graph. Android library because it execs aapt2 from nativeLibraryDir
// and runs on ART.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.ahmadkharfan.androidstudiolite.build.engine"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":build:common"))
    testImplementation(libs.junit)
}
