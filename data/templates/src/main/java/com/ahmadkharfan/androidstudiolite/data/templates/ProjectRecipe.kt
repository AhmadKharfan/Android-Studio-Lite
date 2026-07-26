package com.ahmadkharfan.androidstudiolite.data.templates

import com.ahmadkharfan.androidstudiolite.domain.model.NewProjectSpec
import com.ahmadkharfan.androidstudiolite.domain.model.TemplateLanguage
import java.io.File

class ProjectRecipe(val spec: NewProjectSpec) {

    private val kotlin: Boolean = spec.language == TemplateLanguage.KOTLIN

    var enableCompose: Boolean = false
    var enableViewBinding: Boolean = false
    var useAndroidX: Boolean = true

    var cmakeListsRelPath: String? = null

    private val appPlugins = LinkedHashSet<PluginSpec>()
    private val appDependencies = ArrayList<DependencyRef>()
    private val files = LinkedHashMap<String, RecipeFile>()

    fun plugin(spec: PluginSpec) { appPlugins += spec }

    fun dependency(configuration: String, library: LibrarySpec, isPlatform: Boolean = false) {
        appDependencies += DependencyRef(configuration, library, isPlatform)
    }

    fun implementation(library: LibrarySpec, isPlatform: Boolean = false) =
        dependency("implementation", library, isPlatform)

    fun testImplementation(library: LibrarySpec) = dependency("testImplementation", library)
    fun androidTestImplementation(library: LibrarySpec, isPlatform: Boolean = false) =
        dependency("androidTestImplementation", library, isPlatform)

    fun debugImplementation(library: LibrarySpec) = dependency("debugImplementation", library)

    fun file(relativePath: String, content: String) {
        files[relativePath] = RecipeFile(relativePath, content.trimEnd('\n') + "\n")
    }

    fun sourceFile(simpleFileName: String, content: String) {
        val pkgPath = spec.packageName.replace('.', '/')
        file("app/src/main/java/$pkgPath/$simpleFileName", content)
    }

    fun sourceFileIn(subPackage: String, simpleFileName: String, content: String) {
        val pkgPath = (spec.packageName + "." + subPackage).replace('.', '/')
        file("app/src/main/java/$pkgPath/$simpleFileName", content)
    }

    val isKotlin: Boolean get() = kotlin
    val sourceExt: String get() = if (kotlin) "kt" else "java"

    fun render(): Map<String, String> {
        val renderedFiles = RecipeGradleScripts(
            spec = spec,
            appPlugins = appPlugins.toList(),
            appDependencies = appDependencies.toList(),
            features = RecipeBuildFeatures(
                enableCompose = enableCompose,
                enableViewBinding = enableViewBinding,
                useAndroidX = useAndroidX,
                cmakeListsRelPath = cmakeListsRelPath,
            ),
        ).render()
        renderedFiles += LauncherIconFiles.files()

        for ((path, f) in files) renderedFiles[path] = f.content
        return renderedFiles
    }

    fun writeTo(projectRoot: File, wrapperSource: GradleWrapperSource? = null) {
        RecipeFileWriter.writeTo(projectRoot, render(), wrapperSource)
    }
}
