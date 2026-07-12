// Shared, platform-neutral build-pipeline types used by both the play in-process engine
// (:build:engine) and the full-flavor tooling client. Plain JVM so it is desktop-testable.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}
