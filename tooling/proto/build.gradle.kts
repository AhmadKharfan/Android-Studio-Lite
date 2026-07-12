// JSON-RPC protocol types shared between the app (client) and :tooling:server. Plain JVM with no
// Android dependencies so the server jar can depend on it.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}
