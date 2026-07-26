package com.ahmadkharfan.androidstudiolite.data.templates

import com.ahmadkharfan.androidstudiolite.domain.model.NewProjectSpec
import com.ahmadkharfan.androidstudiolite.domain.model.ProjectBuildDsl
import com.ahmadkharfan.androidstudiolite.domain.model.TemplateLanguage

internal data class RecipeBuildFeatures(
    val enableCompose: Boolean,
    val enableViewBinding: Boolean,
    val useAndroidX: Boolean,
    val cmakeListsRelPath: String?,
)

internal class RecipeGradleScripts(
    private val spec: NewProjectSpec,
    private val appPlugins: List<PluginSpec>,
    private val appDependencies: List<DependencyRef>,
    private val features: RecipeBuildFeatures,
) {
    private val kts: Boolean = spec.buildDsl == ProjectBuildDsl.KTS
    private val kotlin: Boolean = spec.language == TemplateLanguage.KOTLIN

    fun render(): LinkedHashMap<String, String> {
        val renderedFiles = LinkedHashMap<String, String>()
        val settingsExt = if (kts) "settings.gradle.kts" else "settings.gradle"
        val buildExt = if (kts) "build.gradle.kts" else "build.gradle"

        renderedFiles[settingsExt] = renderSettings()
        renderedFiles[buildExt] = renderRootBuild()
        renderedFiles["gradle.properties"] = renderGradleProperties()
        renderedFiles["gradle/wrapper/gradle-wrapper.properties"] = renderWrapper()
        renderedFiles["gradle/libs.versions.toml"] = renderCatalog()
        renderedFiles[".gitignore"] = renderRootGitignore()
        renderedFiles["app/$buildExt"] = renderAppBuild()
        renderedFiles["app/proguard-rules.pro"] = PROGUARD
        renderedFiles["app/.gitignore"] = "/build\n"
        return renderedFiles
    }

    private fun effectiveAppPlugins(): List<PluginSpec> = buildList {
        addAll(appPlugins)
        if (features.enableCompose && kotlin) add(Catalog.composeCompiler)
    }

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
        appendLine("org.gradle.parallel=true")


        appendLine("android.useAndroidX=${features.useAndroidX}")
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

    private fun renderAppBuild(): String = if (kts) renderAppBuildKts() else renderAppBuildGroovy()

    private fun renderAppBuildKts(): String = buildString {
        appendLine("plugins {")
        for (p in effectiveAppPlugins()) appendLine("    alias(${p.accessor})")
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
        features.cmakeListsRelPath?.let {
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
        if (features.enableCompose || features.enableViewBinding) {
            appendLine("    buildFeatures {")
            if (features.enableCompose) appendLine("        compose = true")
            if (features.enableViewBinding) appendLine("        viewBinding = true")
            appendLine("    }")
        }


        features.cmakeListsRelPath?.let {
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
        for (p in effectiveAppPlugins()) appendLine("    alias ${p.accessor}")
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
        features.cmakeListsRelPath?.let {
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
        if (features.enableCompose || features.enableViewBinding) {
            appendLine("    buildFeatures {")
            if (features.enableCompose) appendLine("        compose true")
            if (features.enableViewBinding) appendLine("        viewBinding true")
            appendLine("    }")
        }

        features.cmakeListsRelPath?.let {
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

    private fun renderCatalog(): String {
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

    private fun rootPlugins(): List<PluginSpec> = effectiveAppPlugins()

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
