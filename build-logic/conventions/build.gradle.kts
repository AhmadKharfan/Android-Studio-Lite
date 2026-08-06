import org.gradle.kotlin.dsl.implementation

plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.gradle)
    implementation(libs.kotlin.gradle.plugin)
    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
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
        register("aslAndroidFeature") {
            id = "asl.android.feature"
            implementationClass = "AslAndroidFeatureConventionPlugin"
        }
        register("aslModuleBoundaries") {
            id = "asl.module.boundaries"
            implementationClass = "AslModuleBoundariesConventionPlugin"
        }
        register("aslKotlinLibrary") {
            id = "asl.kotlin.library"
            implementationClass = "AslKotlinLibraryConventionPlugin"
        }
    }
}
