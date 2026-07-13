package com.ahmadkharfan.androidstudiolite.data.templates

import com.ahmadkharfan.androidstudiolite.domain.model.NewProjectSpec
import com.ahmadkharfan.androidstudiolite.domain.model.TemplateLanguage
import java.io.File

/**
 * Turns a [NewProjectSpec] into a real Gradle project on disk by resolving the chosen [Template] from
 * the [TemplateRegistry], letting it populate a [ProjectRecipe], applying cross-cutting option toggles
 * (e.g. C++), and flushing the assembled project (KTS + version catalog by default, pinned to the
 * compat matrix) to [projectDir].
 *
 * Replaces the placeholder `ProjectScaffold`. Written fresh for ASL.
 */
class ProjectTemplateEngine(
    private val registry: TemplateRegistry = TemplateRegistry(),
) {

    /** Generates [spec]'s project into [projectDir], returning the resolved template's language. */
    fun generate(spec: NewProjectSpec, projectDir: File): GenerationResult {
        val template = registry.find(spec.templateId)
            ?: throw IllegalArgumentException("Unknown template: ${spec.templateId}")

        // A template that can't be authored in the requested language falls back to Kotlin.
        val effective = if (spec.language == TemplateLanguage.JAVA && !template.supportsJava) {
            spec.copy(language = TemplateLanguage.KOTLIN)
        } else {
            spec
        }.copy(packageName = spec.packageName.ifBlank { "com.example.app" })

        val recipe = ProjectRecipe(effective)
        template.assemble(effective, recipe)

        // Global C++ toggle: layer a native source set onto any template that didn't already add one.
        if (effective.useCpp && recipe.cmakeListsRelPath == null) {
            NativeCppScaffold.addTo(effective, recipe)
        }

        projectDir.mkdirs()
        recipe.writeTo(projectDir)
        return GenerationResult(templateId = template.metadata.id, language = effective.language)
    }
}

data class GenerationResult(
    val templateId: String,
    val language: TemplateLanguage,
)

/** Adds a minimal C++/CMake native library to an existing recipe (for the global C++ toggle). */
private object NativeCppScaffold {
    fun addTo(spec: NewProjectSpec, recipe: ProjectRecipe) {
        recipe.cmakeListsRelPath = "src/main/cpp/CMakeLists.txt"
        recipe.file(
            "app/src/main/cpp/CMakeLists.txt",
            """
            cmake_minimum_required(VERSION 3.22.1)
            project("native-lib")
            add_library(native-lib SHARED native-lib.cpp)
            find_library(log-lib log)
            target_link_libraries(native-lib ${'$'}{log-lib})
            """.trimIndent(),
        )
        recipe.file(
            "app/src/main/cpp/native-lib.cpp",
            """
            #include <jni.h>
            #include <string>
            // Load with System.loadLibrary("native-lib") and declare a matching external function.
            """.trimIndent(),
        )
    }
}
