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

    // In-process, permissively-licensed toolchain (see THIRD-PARTY-NOTICES.md):
    //  - ECJ (EPL-2.0)               → in-process Java compilation
    //  - D8/R8 (AOSP, Apache-2.0)    → class → dex, with a per-jar content-hash cache
    //  - apksig (AOSP, Apache-2.0)   → APK signing (v1/v2/v3)
    // The embedded Kotlin compiler and aapt2 are NOT compile-time deps: kotlinc is loaded in-process
    // from a downloaded data jar (KotlinCompilerTool), and aapt2 ships as a native binary in the play
    // APK's jniLibs and is exec()'d from nativeLibraryDir.
    implementation(libs.ecj)
    implementation(libs.r8)
    implementation(libs.apksig)
    implementation(libs.bouncycastle.pkix)
    implementation(libs.bouncycastle.prov)

    testImplementation(libs.junit)
}
