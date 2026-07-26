package com.ahmadkharfan.androidstudiolite.data.templates

import com.ahmadkharfan.androidstudiolite.domain.model.NewProjectSpec
import com.ahmadkharfan.androidstudiolite.domain.model.ProjectBuildDsl
import com.ahmadkharfan.androidstudiolite.domain.model.TemplateLanguage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectRecipeTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `render emits the complete project skeleton`() {
        val rendered = recipe().render()

        assertEquals(
            setOf(
                "settings.gradle.kts",
                "build.gradle.kts",
                "gradle.properties",
                "gradle/wrapper/gradle-wrapper.properties",
                "gradle/libs.versions.toml",
                ".gitignore",
                "app/build.gradle.kts",
                "app/proguard-rules.pro",
                "app/.gitignore",
                "app/src/main/res/values/ic_launcher_background.xml",
                "app/src/main/res/drawable/ic_launcher_background.xml",
                "app/src/main/res/drawable/ic_launcher_foreground.xml",
                "app/src/main/res/mipmap/ic_launcher.xml",
                "app/src/main/res/mipmap/ic_launcher_round.xml",
                "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
                "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml",
            ),
            rendered.keys,
        )
        assertTrue(
            rendered.getValue("app/src/main/res/drawable/ic_launcher_foreground.xml")
                .contains("M65.3,45.828"),
        )
        assertTrue(
            rendered.getValue("app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml")
                .contains("<monochrome android:drawable=\"@drawable/ic_launcher_foreground\" />"),
        )
    }

    @Test
    fun `build DSL selects the matching settings and build script filenames`() {
        val kotlinDsl = recipe(buildDsl = ProjectBuildDsl.KTS).render()
        val groovyDsl = recipe(buildDsl = ProjectBuildDsl.GROOVY).render()

        assertTrue(kotlinDsl.containsKey("settings.gradle.kts"))
        assertTrue(kotlinDsl.containsKey("build.gradle.kts"))
        assertTrue(kotlinDsl.containsKey("app/build.gradle.kts"))
        assertTrue(groovyDsl.containsKey("settings.gradle"))
        assertTrue(groovyDsl.containsKey("build.gradle"))
        assertTrue(groovyDsl.containsKey("app/build.gradle"))
        assertFalse(groovyDsl.containsKey("settings.gradle.kts"))
        assertFalse(groovyDsl.containsKey("build.gradle.kts"))
        assertFalse(groovyDsl.containsKey("app/build.gradle.kts"))
    }

    @Test
    fun `plugins and dependencies appear in the rendered app build script`() {
        val recipe = recipe()
        recipe.plugin(
            PluginSpec(
                alias = "sample-feature",
                id = "com.example.feature",
                versionKey = "samplePlugin",
                version = "1.2.3",
            ),
        )
        recipe.implementation(
            LibrarySpec(
                alias = "sample-runtime",
                group = "com.example",
                name = "runtime",
                versionKey = "sampleRuntime",
                version = "4.5.6",
            ),
        )
        recipe.testImplementation(
            LibrarySpec(
                alias = "sample-test",
                group = "com.example",
                name = "test",
                versionKey = "sampleTest",
                version = "7.8.9",
            ),
        )

        val appBuild = recipe.render().getValue("app/build.gradle.kts")

        assertTrue(appBuild.contains("alias(libs.plugins.sample.feature)"))
        assertTrue(appBuild.contains("implementation(libs.sample.runtime)"))
        assertTrue(appBuild.contains("testImplementation(libs.sample.test)"))
    }

    @Test
    fun `file content is rendered unchanged at its registered path`() {
        val recipe = recipe()
        val path = "docs/nested/sample.txt"
        val content = "first line\n  second line  \n"

        recipe.file(path, content)

        assertEquals(content, recipe.render().getValue(path))
    }

    @Test
    fun `source files use the package path and language extension under the java source directory`() {
        val kotlinRecipe = recipe(language = TemplateLanguage.KOTLIN)
        val javaRecipe = recipe(language = TemplateLanguage.JAVA)
        val kotlinContent = "package com.example.recipe\n\nclass KotlinSource\n"
        val javaContent = "package com.example.recipe;\n\nclass JavaSource {}\n"

        kotlinRecipe.sourceFile("KotlinSource.${kotlinRecipe.sourceExt}", kotlinContent)
        javaRecipe.sourceFile("JavaSource.${javaRecipe.sourceExt}", javaContent)

        assertEquals(
            kotlinContent,
            kotlinRecipe.render().getValue("app/src/main/java/com/example/recipe/KotlinSource.kt"),
        )
        assertEquals(
            javaContent,
            javaRecipe.render().getValue("app/src/main/java/com/example/recipe/JavaSource.java"),
        )
    }

    @Test
    fun `source file in a subpackage nests below the package path`() {
        val recipe = recipe()
        val content = "package com.example.recipe.ui.feature\n\nclass Screen\n"

        recipe.sourceFileIn("ui.feature", "Screen.kt", content)

        assertEquals(
            content,
            recipe.render().getValue("app/src/main/java/com/example/recipe/ui/feature/Screen.kt"),
        )
    }

    @Test
    fun `registered file replaces generated content at the same path`() {
        val recipe = recipe()
        val replacement = "replacement settings\n"

        recipe.file("settings.gradle.kts", replacement)

        assertEquals(replacement, recipe.render().getValue("settings.gradle.kts"))
    }

    @Test
    fun `writeTo materializes every rendered file with matching content`() {
        val recipe = recipe()
        recipe.file("custom/deep/nested.txt", "nested content\n")
        val rendered = recipe.render()
        val projectRoot = temporaryFolder.newFolder("project")

        recipe.writeTo(projectRoot)

        for ((relativePath, content) in rendered) {
            val writtenFile = File(projectRoot, relativePath)
            assertTrue(relativePath, writtenFile.isFile)
            assertEquals(relativePath, content, writtenFile.readText())
        }
    }

    private fun recipe(
        language: TemplateLanguage = TemplateLanguage.KOTLIN,
        buildDsl: ProjectBuildDsl = ProjectBuildDsl.KTS,
    ): ProjectRecipe =
        ProjectRecipe(
            NewProjectSpec(
                name = "Recipe",
                packageName = "com.example.recipe",
                templateId = "recipe-test",
                language = language,
                buildDsl = buildDsl,
            ),
        )
}
