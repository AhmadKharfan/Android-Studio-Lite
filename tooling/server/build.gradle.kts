// Full-flavor Gradle tooling server: a plain-JVM process the app spawns on-device with the
// installed JDK. Talks the Gradle Tooling API (Apache-2.0) to the real Gradle build and the
// :tooling:proto JSON-RPC protocol to the app. Shipped as a fat jar in app/src/full/assets/
// (the app's copyToolingServerJar task consumes this module's fatJar output).
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass = "com.ahmadkharfan.androidstudiolite.tooling.server.MainKt"
}

dependencies {
    implementation(project(":tooling:proto"))
    testImplementation(libs.junit)
}

// Single self-contained jar (server + all runtime deps) so the app can extract one asset and run
// `java -jar tooling-server.jar` on-device.
val fatJar = tasks.register<Jar>("fatJar") {
    archiveClassifier = "all"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath.map { classpath ->
        classpath.map { if (it.isDirectory) it else zipTree(it) }
    })
}
