package com.ahmadkharfan.androidstudiolite.data.templates

import com.ahmadkharfan.androidstudiolite.domain.model.NewProjectSpec
import com.ahmadkharfan.androidstudiolite.domain.model.ProjectBuildDsl
import com.ahmadkharfan.androidstudiolite.domain.model.TemplateLanguage
import java.io.File

/**
 * The mutable accumulator a [Template] fills in. It captures the single-app-module shape common to
 * every ASL template — plugins, the `android {}` config, catalog dependencies, and arbitrary files —
 * then renders the whole project (shared scaffolding + assembled build script + version catalog) so
 * output is uniform and always parseable by the static Gradle reader.
 *
 * Written fresh for ASL (no GPL sources). KTS + version catalogs is the default; Groovy is fully
 * supported for the DSL option.
 */
class ProjectRecipe(val spec: NewProjectSpec) {

    private val kts: Boolean = spec.buildDsl == ProjectBuildDsl.KTS
    private val kotlin: Boolean = spec.language == TemplateLanguage.KOTLIN

    /** `app/build.gradle(.kts)` android {} config. */
    var enableCompose: Boolean = false
    var enableViewBinding: Boolean = false
    var useAndroidX: Boolean = true

    /** When set, an `externalNativeBuild { cmake { path = … } }` clause is emitted. */
    var cmakeListsRelPath: String? = null

    private val appPlugins = LinkedHashSet<PluginSpec>()
    private val appDependencies = ArrayList<DependencyRef>()
    private val files = LinkedHashMap<String, RecipeFile>()

    // --- template-facing API --------------------------------------------------------------------

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

    /** Add a file relative to the project root, e.g. `app/src/main/AndroidManifest.xml`. */
    fun file(relativePath: String, content: String) {
        files[relativePath] = RecipeFile(relativePath, content.trimEnd('\n') + "\n")
    }

    /** Add a file under the app module's main source package dir (java/kotlin), language-aware. */
    fun sourceFile(simpleFileName: String, content: String) {
        val pkgPath = spec.packageName.replace('.', '/')
        file("app/src/main/java/$pkgPath/$simpleFileName", content)
    }

    /** Add a file under a sub-package of the app module's main source dir. */
    fun sourceFileIn(subPackage: String, simpleFileName: String, content: String) {
        val pkgPath = (spec.packageName + "." + subPackage).replace('.', '/')
        file("app/src/main/java/$pkgPath/$simpleFileName", content)
    }

    val isKotlin: Boolean get() = kotlin
    val sourceExt: String get() = if (kotlin) "kt" else "java"

    // --- rendering ------------------------------------------------------------------------------

    /** Renders every project file into a path → content map (relative to the project root). */
    fun render(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val settingsExt = if (kts) "settings.gradle.kts" else "settings.gradle"
        val buildExt = if (kts) "build.gradle.kts" else "build.gradle"

        out[settingsExt] = renderSettings()
        out[buildExt] = renderRootBuild()
        out["gradle.properties"] = renderGradleProperties()
        out["gradle/wrapper/gradle-wrapper.properties"] = renderWrapper()
        out["gradle/libs.versions.toml"] = renderCatalog()
        out[".gitignore"] = renderRootGitignore()
        out["app/$buildExt"] = renderAppBuild()
        out["app/proguard-rules.pro"] = PROGUARD
        out["app/.gitignore"] = "/build\n"

        for ((path, f) in files) out[path] = f.content
        return out
    }

    fun writeTo(projectRoot: File) {
        for ((relPath, content) in render()) {
            val target = File(projectRoot, relPath)
            target.parentFile?.mkdirs()
            target.writeText(content)
        }
    }

    // --- gradle files ---------------------------------------------------------------------------

    private fun renderSettings(): String {
        val name = spec.name
        return if (kts) {
            """
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    google()
                    mavenCentral()
                }
            }

            rootProject.name = "$name"
            include(":app")
            """.trimIndent() + "\n"
        } else {
            """
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    google()
                    mavenCentral()
                }
            }

            rootProject.name = '$name'
            include ':app'
            """.trimIndent() + "\n"
        }
    }

    private fun renderRootBuild(): String {
        val lines = ArrayList<String>()
        lines += "plugins {"
        for (p in rootPlugins()) {
            lines += if (kts) "    alias(${p.accessor}) apply false" else "    alias ${p.accessor} apply false"
        }
        lines += "}"
        return lines.joinToString("\n") + "\n"
    }

    private fun renderGradleProperties(): String = buildString {
        appendLine("org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8")
        appendLine("org.gradle.caching=true")
        appendLine("android.useAndroidX=${useAndroidX}")
        appendLine("android.nonTransitiveRClass=true")
        if (kotlin) appendLine("kotlin.code.style=official")
    }

    private fun renderWrapper(): String =
        """
        distributionBase=GRADLE_USER_HOME
        distributionPath=wrapper/dists
        distributionUrl=https\://services.gradle.org/distributions/gradle-${Catalog.GRADLE_VERSION}-bin.zip
        networkTimeout=10000
        validateDistributionUrl=true
        zipStoreBase=GRADLE_USER_HOME
        zipStorePath=wrapper/dists
        """.trimIndent() + "\n"

    // --- app build script -----------------------------------------------------------------------

    private fun renderAppBuild(): String = if (kts) renderAppBuildKts() else renderAppBuildGroovy()

    private fun renderAppBuildKts(): String = buildString {
        appendLine("plugins {")
        for (p in appPlugins) appendLine("    alias(${p.accessor})")
        appendLine("}")
        appendLine()
        appendLine("android {")
        appendLine("    namespace = \"${spec.packageName}\"")
        appendLine("    compileSdk = ${spec.compileSdk}")
        appendLine()
        appendLine("    defaultConfig {")
        appendLine("        applicationId = \"${spec.packageName}\"")
        appendLine("        minSdk = ${spec.minSdk}")
        appendLine("        targetSdk = ${spec.targetSdk}")
        appendLine("        versionCode = 1")
        appendLine("        versionName = \"1.0\"")
        appendLine()
        appendLine("        testInstrumentationRunner = \"androidx.test.runner.AndroidJUnitRunner\"")
        cmakeListsRelPath?.let {
            appendLine("        externalNativeBuild {")
            appendLine("            cmake {")
            appendLine("                cppFlags += \"\"")
            appendLine("            }")
            appendLine("        }")
        }
        appendLine("    }")
        appendLine()
        appendLine("    buildTypes {")
        appendLine("        release {")
        appendLine("            isMinifyEnabled = false")
        appendLine("            proguardFiles(")
        appendLine("                getDefaultProguardFile(\"proguard-android-optimize.txt\"),")
        appendLine("                \"proguard-rules.pro\",")
        appendLine("            )")
        appendLine("        }")
        appendLine("    }")
        appendLine("    compileOptions {")
        appendLine("        sourceCompatibility = JavaVersion.VERSION_${Catalog.JDK_VERSION}")
        appendLine("        targetCompatibility = JavaVersion.VERSION_${Catalog.JDK_VERSION}")
        appendLine("    }")
        if (kotlin) {
            appendLine("    kotlinOptions {")
            appendLine("        jvmTarget = \"${Catalog.JDK_VERSION}\"")
            appendLine("    }")
        }
        if (enableCompose || enableViewBinding) {
            appendLine("    buildFeatures {")
            if (enableCompose) appendLine("        compose = true")
            if (enableViewBinding) appendLine("        viewBinding = true")
            appendLine("    }")
        }
        if (enableCompose) {
            appendLine("    composeOptions {")
            appendLine("        kotlinCompilerExtensionVersion = \"${Catalog.COMPOSE_COMPILER_VERSION}\"")
            appendLine("    }")
        }
        cmakeListsRelPath?.let {
            appendLine("    externalNativeBuild {")
            appendLine("        cmake {")
            appendLine("            path = file(\"$it\")")
            appendLine("            version = \"3.22.1\"")
            appendLine("        }")
            appendLine("    }")
        }
        appendLine("}")
        appendLine()
        appendLine("dependencies {")
        for (d in appDependencies) {
            val ref = if (d.isPlatform) "platform(${d.library.accessor})" else d.library.accessor
            appendLine("    ${d.configuration}($ref)")
        }
        appendLine("}")
    }

    private fun renderAppBuildGroovy(): String = buildString {
        appendLine("plugins {")
        for (p in appPlugins) appendLine("    alias ${p.accessor}")
        appendLine("}")
        appendLine()
        appendLine("android {")
        appendLine("    namespace '${spec.packageName}'")
        appendLine("    compileSdk ${spec.compileSdk}")
        appendLine()
        appendLine("    defaultConfig {")
        appendLine("        applicationId '${spec.packageName}'")
        appendLine("        minSdk ${spec.minSdk}")
        appendLine("        targetSdk ${spec.targetSdk}")
        appendLine("        versionCode 1")
        appendLine("        versionName '1.0'")
        appendLine()
        appendLine("        testInstrumentationRunner 'androidx.test.runner.AndroidJUnitRunner'")
        cmakeListsRelPath?.let {
            appendLine("        externalNativeBuild {")
            appendLine("            cmake {")
            appendLine("                cppFlags ''")
            appendLine("            }")
            appendLine("        }")
        }
        appendLine("    }")
        appendLine()
        appendLine("    buildTypes {")
        appendLine("        release {")
        appendLine("            minifyEnabled false")
        appendLine("            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'")
        appendLine("        }")
        appendLine("    }")
        appendLine("    compileOptions {")
        appendLine("        sourceCompatibility JavaVersion.VERSION_${Catalog.JDK_VERSION}")
        appendLine("        targetCompatibility JavaVersion.VERSION_${Catalog.JDK_VERSION}")
        appendLine("    }")
        if (kotlin) {
            appendLine("    kotlinOptions {")
            appendLine("        jvmTarget = '${Catalog.JDK_VERSION}'")
            appendLine("    }")
        }
        if (enableCompose || enableViewBinding) {
            appendLine("    buildFeatures {")
            if (enableCompose) appendLine("        compose true")
            if (enableViewBinding) appendLine("        viewBinding true")
            appendLine("    }")
        }
        if (enableCompose) {
            appendLine("    composeOptions {")
            appendLine("        kotlinCompilerExtensionVersion '${Catalog.COMPOSE_COMPILER_VERSION}'")
            appendLine("    }")
        }
        cmakeListsRelPath?.let {
            appendLine("    externalNativeBuild {")
            appendLine("        cmake {")
            appendLine("            path file('$it')")
            appendLine("            version '3.22.1'")
            appendLine("        }")
            appendLine("    }")
        }
        appendLine("}")
        appendLine()
        appendLine("dependencies {")
        for (d in appDependencies) {
            val ref = if (d.isPlatform) "platform(${d.library.accessor})" else d.library.accessor
            appendLine("    ${d.configuration} $ref")
        }
        appendLine("}")
    }

    // --- version catalog ------------------------------------------------------------------------

    private fun renderCatalog(): String {
        // Collect libraries + plugins actually referenced, and the versions they pin to.
        val libraries = LinkedHashMap<String, LibrarySpec>()
        for (d in appDependencies) libraries[d.library.alias] = d.library
        val plugins = LinkedHashMap<String, PluginSpec>()
        for (p in rootPlugins()) plugins[p.alias] = p

        val versions = LinkedHashMap<String, String>()
        for (l in libraries.values) if (l.versionKey != null && l.version != null) versions.putIfAbsent(l.versionKey, l.version)
        for (p in plugins.values) versions.putIfAbsent(p.versionKey, p.version)

        return buildString {
            appendLine("[versions]")
            for ((k, v) in versions) appendLine("$k = \"$v\"")
            appendLine()
            appendLine("[libraries]")
            for (l in libraries.values) {
                val version = l.versionKey?.let { ", version.ref = \"$it\"" } ?: ""
                appendLine("${l.alias} = { group = \"${l.group}\", name = \"${l.name}\"$version }")
            }
            appendLine()
            appendLine("[plugins]")
            for (p in plugins.values) {
                appendLine("${p.alias} = { id = \"${p.id}\", version.ref = \"${p.versionKey}\" }")
            }
        }
    }

    /** Plugins declared at the root (with `apply false`) = the distinct set the app module applies. */
    private fun rootPlugins(): List<PluginSpec> = appPlugins.toList()

    private fun renderRootGitignore(): String =
        """
        *.iml
        .gradle
        /local.properties
        /.idea
        .DS_Store
        /build
        /captures
        .externalNativeBuild
        .cxx
        """.trimIndent() + "\n"

    private companion object {
        val PROGUARD =
            """
            # Add project specific ProGuard rules here.
            # You can control the set of applied configuration files using the
            # proguardFiles setting in build.gradle.
            """.trimIndent() + "\n"
    }
}
