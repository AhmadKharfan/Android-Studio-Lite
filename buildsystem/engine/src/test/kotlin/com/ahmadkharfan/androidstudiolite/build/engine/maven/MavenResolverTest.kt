package com.ahmadkharfan.androidstudiolite.build.engine.maven

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Resolver behaviour verified against a local fixture repo — no network. */
class MavenResolverTest {

    private lateinit var repoDir: File
    private lateinit var cacheDir: File

    @Before fun setup() {
        repoDir = Files.createTempDirectory("repo").toFile()
        cacheDir = Files.createTempDirectory("cache").toFile()
    }

    private fun resolver(): MavenResolver =
        MavenResolver(MavenRepositories(remotes = listOf(DirectoryFetcher(repoDir)), cacheDir = cacheDir))

    private fun modules(result: ResolutionResult): Set<String> =
        result.artifacts.map { "${it.coordinate.moduleId}:${it.coordinate.version}" }.toSet()

    // ---- fixtures ----

    private fun pom(g: String, a: String, v: String, packaging: String = "jar", body: String = "") {
        val gp = g.replace('.', '/')
        writeFile("$gp/$a/$v/$a-$v.pom", buildString {
            append("<project><modelVersion>4.0.0</modelVersion>")
            append("<groupId>$g</groupId><artifactId>$a</artifactId><version>$v</version>")
            append("<packaging>$packaging</packaging>")
            append(body)
            append("</project>")
        })
        if (packaging != "pom") writeFile("$gp/$a/$v/$a-$v.$packaging", "artifact:$g:$a:$v")
    }

    private fun dep(g: String, a: String, v: String? = null, scope: String? = null, optional: Boolean = false, extra: String = "") =
        buildString {
            append("<dependency><groupId>$g</groupId><artifactId>$a</artifactId>")
            if (v != null) append("<version>$v</version>")
            if (scope != null) append("<scope>$scope</scope>")
            if (optional) append("<optional>true</optional>")
            append(extra)
            append("</dependency>")
        }

    private fun writeFile(relative: String, content: String) {
        val f = File(repoDir, relative)
        f.parentFile.mkdirs()
        f.writeText(content)
    }

    // ---- tests ----

    @Test fun `resolves transitive dependencies`() {
        pom("com.example", "c", "1.0")
        pom("com.example", "b", "1.0", body = "<dependencies>${dep("com.example", "c", "1.0")}</dependencies>")
        pom("com.example", "a", "1.0", body = "<dependencies>${dep("com.example", "b", "1.0")}</dependencies>")

        val result = resolver().resolve(listOf(ResolutionRequest(MavenCoordinate.parse("com.example:a:1.0"))))
        assertEquals(setOf("com.example:a:1.0", "com.example:b:1.0", "com.example:c:1.0"), modules(result))
    }

    @Test fun `newest version wins on conflict`() {
        pom("com.example", "shared", "1.0")
        pom("com.example", "shared", "2.0")
        pom("com.example", "left", "1.0", body = "<dependencies>${dep("com.example", "shared", "1.0")}</dependencies>")
        pom("com.example", "right", "1.0", body = "<dependencies>${dep("com.example", "shared", "2.0")}</dependencies>")
        pom(
            "com.example", "app", "1.0",
            body = "<dependencies>${dep("com.example", "left", "1.0")}${dep("com.example", "right", "1.0")}</dependencies>",
        )

        val result = resolver().resolve(listOf(ResolutionRequest(MavenCoordinate.parse("com.example:app:1.0"))))
        assertTrue("shared:2.0 expected", modules(result).contains("com.example:shared:2.0"))
        assertFalse("shared:1.0 should be superseded", modules(result).contains("com.example:shared:1.0"))
    }

    @Test fun `BOM supplies versions for versionless dependencies`() {
        pom("com.example", "lib", "3.1.0")
        pom(
            "com.example", "bom", "1.0", packaging = "pom",
            body = "<dependencyManagement><dependencies>${dep("com.example", "lib", "3.1.0")}</dependencies></dependencyManagement>",
        )
        val result = resolver().resolve(
            listOf(
                ResolutionRequest(MavenCoordinate.parse("com.example:bom:1.0"), isPlatform = true),
                ResolutionRequest(MavenCoordinate("com.example", "lib", version = null)),
            ),
        )
        assertEquals(setOf("com.example:lib:3.1.0"), modules(result))
    }

    @Test fun `test and optional dependencies are skipped`() {
        pom("com.example", "prod", "1.0")
        pom("com.example", "onlytest", "1.0")
        pom("com.example", "opt", "1.0")
        pom(
            "com.example", "app", "1.0",
            body = "<dependencies>" +
                dep("com.example", "prod", "1.0") +
                dep("com.example", "onlytest", "1.0", scope = "test") +
                dep("com.example", "opt", "1.0", optional = true) +
                "</dependencies>",
        )
        val result = resolver().resolve(listOf(ResolutionRequest(MavenCoordinate.parse("com.example:app:1.0"))))
        assertEquals(setOf("com.example:app:1.0", "com.example:prod:1.0"), modules(result))
    }

    @Test fun `exclusions prune the transitive graph`() {
        pom("com.example", "unwanted", "1.0")
        pom("com.example", "mid", "1.0", body = "<dependencies>${dep("com.example", "unwanted", "1.0")}</dependencies>")
        val exclusion = "<exclusions><exclusion><groupId>com.example</groupId><artifactId>unwanted</artifactId></exclusion></exclusions>"
        pom(
            "com.example", "app", "1.0",
            body = "<dependencies>${dep("com.example", "mid", "1.0", extra = exclusion)}</dependencies>",
        )
        val result = resolver().resolve(listOf(ResolutionRequest(MavenCoordinate.parse("com.example:app:1.0"))))
        assertFalse(modules(result).contains("com.example:unwanted:1.0"))
        assertTrue(modules(result).contains("com.example:mid:1.0"))
    }

    @Test fun `parent properties interpolate dependency versions`() {
        pom("com.example", "child-lib", "9.9")
        pom(
            "com.example", "parent", "1.0", packaging = "pom",
            body = "<properties><lib.version>9.9</lib.version></properties>",
        )
        pom(
            "com.example", "app", "1.0",
            body = "<parent><groupId>com.example</groupId><artifactId>parent</artifactId><version>1.0</version></parent>" +
                "<dependencies>${dep("com.example", "child-lib", "\${lib.version}")}</dependencies>",
        )
        val result = resolver().resolve(listOf(ResolutionRequest(MavenCoordinate.parse("com.example:app:1.0"))))
        assertTrue(modules(result).contains("com.example:child-lib:9.9"))
    }

    @Test fun `missing artifact is a warning, not a crash`() {
        pom("com.example", "app", "1.0", body = "<dependencies>${dep("com.example", "ghost", "1.0")}</dependencies>")
        val result = resolver().resolve(listOf(ResolutionRequest(MavenCoordinate.parse("com.example:app:1.0"))))
        assertTrue(modules(result).contains("com.example:app:1.0"))
        assertTrue(result.warnings.any { it.contains("ghost") })
    }
}
