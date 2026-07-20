package com.ahmadkharfan.androidstudiolite.data.templates

import com.ahmadkharfan.androidstudiolite.domain.model.NewProjectSpec
import com.ahmadkharfan.androidstudiolite.domain.model.ProjectBuildDsl
import com.ahmadkharfan.androidstudiolite.domain.model.TemplateLanguage
import java.io.File

class ProjectRecipe(val spec: NewProjectSpec) {

    private val kts: Boolean = spec.buildDsl == ProjectBuildDsl.KTS
    private val kotlin: Boolean = spec.language == TemplateLanguage.KOTLIN

    var enableCompose: Boolean = false
    var enableViewBinding: Boolean = false
    var useAndroidX: Boolean = true

    var cmakeListsRelPath: String? = null

    private val appPlugins = LinkedHashSet<PluginSpec>()
    private val appDependencies = ArrayList<DependencyRef>()
    private val files = LinkedHashMap<String, RecipeFile>()


    fun plugin(spec: PluginSpec) { appPlugins += spec }

    private fun effectiveAppPlugins(): List<PluginSpec> = buildList {
        addAll(appPlugins)
        if (enableCompose && kotlin) add(Catalog.composeCompiler)
    }

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
        out += launcherIconFiles()

        for ((path, f) in files) out[path] = f.content
        return out
    }

    private fun launcherIconFiles(): Map<String, String> {
        val adaptive = { round: Boolean ->
            """
            <?xml version="1.0" encoding="utf-8"?>
            <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                <background android:drawable="@color/ic_launcher_background" />
                <foreground android:drawable="@drawable/ic_launcher_foreground" />
                ${if (round) "<monochrome android:drawable=\"@drawable/ic_launcher_foreground\" />" else ""}
            </adaptive-icon>
            """.trimIndent().replace("\n\n", "\n") + "\n"
        }
        return mapOf(
            "app/src/main/res/values/ic_launcher_background.xml" to """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <color name="ic_launcher_background">#3DDC84</color>
                </resources>
            """.trimIndent() + "\n",
            "app/src/main/res/drawable/ic_launcher_foreground.xml" to LAUNCHER_FOREGROUND,

            "app/src/main/res/mipmap/ic_launcher.xml" to LAUNCHER_FALLBACK,
            "app/src/main/res/mipmap/ic_launcher_round.xml" to LAUNCHER_FALLBACK,
            "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml" to adaptive(false),
            "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml" to adaptive(true),
        )
    }

    fun writeTo(projectRoot: File, wrapperSource: GradleWrapperSource? = null) {
        for ((relPath, content) in render()) {
            val target = File(projectRoot, relPath)
            target.parentFile?.mkdirs()
            target.writeText(content)
        }
        wrapperSource?.let { writeWrapper(projectRoot, it) }
    }

    private fun writeWrapper(projectRoot: File, source: GradleWrapperSource) {
        for (relPath in GradleWrapperSource.PATHS) {
            val target = File(projectRoot, relPath)
            target.parentFile?.mkdirs()
            source.open(relPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        File(projectRoot, GradleWrapperSource.GRADLEW).setExecutable(true, false)
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
        val LAUNCHER_FOREGROUND =
            """
            <?xml version="1.0" encoding="utf-8"?>
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:width="108dp"
                android:height="108dp"
                android:viewportWidth="108"
                android:viewportHeight="108">
                <path
                    android:fillColor="#FFFFFF"
                    android:pathData="M54,30L70,66L62,66L54,48L46,66L38,66Z" />
            </vector>
            """.trimIndent() + "\n"

        val LAUNCHER_FALLBACK =
            """
            <?xml version="1.0" encoding="utf-8"?>
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:width="48dp"
                android:height="48dp"
                android:viewportWidth="108"
                android:viewportHeight="108">
                <path
                    android:fillColor="#3DDC84"
                    android:pathData="M0,0h108v108h-108z" />
                <path
                    android:fillColor="#FFFFFF"
                    android:pathData="M54,30L70,66L62,66L54,48L46,66L38,66Z" />
            </vector>
            """.trimIndent() + "\n"

        val PROGUARD =
            """
            # Add project specific ProGuard rules here.
            # You can control the set of applied configuration files using the
            # proguardFiles setting in build.gradle.
            """.trimIndent() + "\n"
    }
}
