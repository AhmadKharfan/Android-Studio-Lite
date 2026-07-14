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

    // The Gradle Tooling API (Apache-2.0): GradleConnector/ProjectConnection/BuildLauncher plus the
    // built-in GradleProject/IdeaProject models we map into the proto model.
    implementation(libs.gradle.tooling.api)
    // TAPI logs through slf4j; slf4j-simple routes to System.err so it never corrupts the stdout
    // JSON-RPC stream.
    runtimeOnly(libs.slf4j.simple)

    testImplementation(libs.junit)
    testImplementation(project(":tooling:proto"))
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

// The desktop harness (ToolingServerHarnessTest) spawns the fat jar as a real subprocess, so make
// sure it is built and current before the server's own tests run.
tasks.test {
    dependsOn(fatJar)
    // Hand the freshly built jar to the test via a system property.
    systemProperty("asl.tooling.server.jar", fatJar.get().archiveFile.get().asFile.absolutePath)
    // A Gradle installation the harness can point the Tooling API at; overridable from the environment.
    (System.getenv("ASL_TEST_GRADLE_HOME") ?: defaultGradleHome())?.let {
        systemProperty("asl.test.gradle.home", it)
    }
}

/** Best-effort discovery of a local Gradle install so the harness runs without extra setup. */
fun defaultGradleHome(): String? {
    val dists = File(System.getProperty("user.home"), ".gradle/wrapper/dists")
    if (!dists.isDirectory) return null
    return dists.walkTopDown()
        .firstOrNull { it.isFile && it.name == "gradle" && it.parentFile?.name == "bin" }
        ?.parentFile?.parentFile?.absolutePath
}
