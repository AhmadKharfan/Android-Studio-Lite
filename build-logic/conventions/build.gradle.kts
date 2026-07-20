plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.android.tools.build:gradle:9.2.1")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10")
}

gradlePlugin {
    plugins {
        register("aslAndroidApplication") {
            id = "asl.android.application"
            implementationClass = "AslAndroidApplicationConventionPlugin"
        }
        register("aslAndroidLibrary") {
            id = "asl.android.library"
            implementationClass = "AslAndroidLibraryConventionPlugin"
        }
        register("aslAndroidComposeLibrary") {
            id = "asl.android.library.compose"
            implementationClass = "AslAndroidComposeLibraryConventionPlugin"
        }
        register("aslKotlinLibrary") {
            id = "asl.kotlin.library"
            implementationClass = "AslKotlinLibraryConventionPlugin"
        }
    }
}
