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
                <background android:drawable="@drawable/ic_launcher_background" />
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
            "app/src/main/res/drawable/ic_launcher_background.xml" to LAUNCHER_BACKGROUND,
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
                xmlns:aapt="http://schemas.android.com/aapt"
                android:width="108dp"
                android:height="108dp"
                android:viewportWidth="108"
                android:viewportHeight="108">
                <path android:pathData="M31,63.928c0,0 6.4,-11 12.1,-13.1c7.2,-2.6 26,-1.4 26,-1.4l38.1,38.1L107,108.928l-32,-1L31,63.928z">
                    <aapt:attr name="android:fillColor">
                        <gradient
                            android:endX="85.84757"
                            android:endY="92.4963"
                            android:startX="42.9492"
                            android:startY="49.59793"
                            android:type="linear">
                            <item
                                android:color="#44000000"
                                android:offset="0.0" />
                            <item
                                android:color="#00000000"
                                android:offset="1.0" />
                        </gradient>
                    </aapt:attr>
                </path>
                <path
                    android:fillColor="#FFFFFF"
                    android:fillType="nonZero"
                    android:pathData="M65.3,45.828l3.8,-6.6c0.2,-0.4 0.1,-0.9 -0.3,-1.1c-0.4,-0.2 -0.9,-0.1 -1.1,0.3l-3.9,6.7c-6.3,-2.8 -13.4,-2.8 -19.7,0l-3.9,-6.7c-0.2,-0.4 -0.7,-0.5 -1.1,-0.3C38.8,38.328 38.7,38.828 38.9,39.228l3.8,6.6C36.2,49.428 31.7,56.028 31,63.928h46C76.3,56.028 71.8,49.428 65.3,45.828zM43.4,57.328c-0.8,0 -1.5,-0.5 -1.8,-1.2c-0.3,-0.7 -0.1,-1.5 0.4,-2.1c0.5,-0.5 1.4,-0.7 2.1,-0.4c0.7,0.3 1.2,1 1.2,1.8C45.3,56.528 44.5,57.328 43.4,57.328L43.4,57.328zM64.6,57.328c-0.8,0 -1.5,-0.5 -1.8,-1.2s-0.1,-1.5 0.4,-2.1c0.5,-0.5 1.4,-0.7 2.1,-0.4c0.7,0.3 1.2,1 1.2,1.8C66.5,56.528 65.6,57.328 64.6,57.328L64.6,57.328z"
                    android:strokeWidth="1"
                    android:strokeColor="#00000000" />
            </vector>
            """.trimIndent() + "\n"

        val LAUNCHER_BACKGROUND =
            """
            <?xml version="1.0" encoding="utf-8"?>
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:width="108dp"
                android:height="108dp"
                android:viewportWidth="108"
                android:viewportHeight="108">
                <path
                    android:fillColor="#3DDC84"
                    android:pathData="M0,0h108v108h-108z" />
                <path
                    android:fillColor="#00000000"
                    android:pathData="M9,0L9,108"
                    android:strokeWidth="0.8"
                    android:strokeColor="#33FFFFFF" />
                <path
                    android:fillColor="#00000000"
                    android:pathData="M19,0L19,108"
                    android:strokeWidth="0.8"
                    android:strokeColor="#33FFFFFF" />
                <path
                    android:fillColor="#00000000"
                    android:pathData="M29,0L29,108"
                    android:strokeWidth="0.8"
                    android:strokeColor="#33FFFFFF" />
                <path
                    android:fillColor="#00000000"
                    android:pathData="M39,0L39,108"
                    android:strokeWidth="0.8"
                    android:strokeColor="#33FFFFFF" />
                <path
                    android:fillColor="#00000000"
                    android:pathData="M49,0L49,108"
                    android:strokeWidth="0.8"
                    android:strokeColor="#33FFFFFF" />
                <path
                    android:fillColor="#00000000"
                    android:pathData="M59,0L59,108"
                    android:strokeWidth="0.8"
                    android:strokeColor="#33FFFFFF" />
                <path
                    android:fillColor="#00000000"
                    android:pathData="M69,0L69,108"
                    android:strokeWidth="0.8"
                    android:strokeColor="#33FFFFFF" />
                <path
                    android:fillColor="#00000000"
                    android:pathData="M79,0L79,108"
                    android:strokeWidth="0.8"
                    android:strokeColor="#33FFFFFF" />
                <path
                    android:fillColor="#00000000"
                    android:pathData="M89,0L89,108"
                    android:strokeWidth="0.8"
                    android:strokeColor="#33FFFFFF" />
                <path
                    android:fillColor="#00000000"
                    android:pathData="M99,0L99,108"
                    android:strokeWidth="0.8"
                    android:strokeColor="#33FFFFFF" />
                <path
                    android:fillColor="#00000000"
                    android:pathData="M0,9L108,9"
                    android:strokeWidth="0.8"
                    android:strokeColor="#33FFFFFF" />
                <path
                    android:fillColor="#00000000"
                    android:pathData="M0,19L108,19"
                    android:strokeWidth="0.8"
                    android:strokeColor="#33FFFFFF" />
                <path
                    android:fillColor="#00000000"
                    android:pathData="M0,29L108,29"
                    android:strokeWidth="0.8"
                    android:strokeColor="#33FFFFFF" />
                <path
                    android:fillColor="#00000000"
                    android:pathData="M0,39L108,39"
                    android:strokeWidth="0.8"
                    android:strokeColor="#33FFFFFF" />
                <path
                    android:fillColor="#00000000"
                    android:pathData="M0,49L108,49"
                    android:strokeWidth="0.8"
                    android:strokeColor="#33FFFFFF" />
                <path
                    android:fillColor="#00000000"
                    android:pathData="M0,59L108,59"
                    android:strokeWidth="0.8"
                    android:strokeColor="#33FFFFFF" />
                <path
                    android:fillColor="#00000000"
                    android:pathData="M0,69L108,69"
                    android:strokeWidth="0.8"
                    android:strokeColor="#33FFFFFF" />
                <path
                    android:fillColor="#00000000"
                    android:pathData="M0,79L108,79"
                    android:strokeWidth="0.8"
                    android:strokeColor="#33FFFFFF" />
                <path
                    android:fillColor="#00000000"
                    android:pathData="M0,89L108,89"
                    android:strokeWidth="0.8"
                    android:strokeColor="#33FFFFFF" />
                <path
                    android:fillColor="#00000000"
                    android:pathData="M0,99L108,99"
                    android:strokeWidth="0.8"
                    android:strokeColor="#33FFFFFF" />
                <path
                    android:fillColor="#00000000"
                    android:pathData="M19,29L89,29"
                    android:strokeWidth="0.8"
                    android:strokeColor="#33FFFFFF" />
                <path
                    android:fillColor="#00000000"
                    android:pathData="M19,39L89,39"
                    android:strokeWidth="0.8"
                    android:strokeColor="#33FFFFFF" />
                <path
                    android:fillColor="#00000000"
                    android:pathData="M19,49L89,49"
                    android:strokeWidth="0.8"
                    android:strokeColor="#33FFFFFF" />
                <path
                    android:fillColor="#00000000"
                    android:pathData="M19,59L89,59"
                    android:strokeWidth="0.8"
                    android:strokeColor="#33FFFFFF" />
                <path
                    android:fillColor="#00000000"
                    android:pathData="M19,69L89,69"
                    android:strokeWidth="0.8"
                    android:strokeColor="#33FFFFFF" />
                <path
                    android:fillColor="#00000000"
                    android:pathData="M19,79L89,79"
                    android:strokeWidth="0.8"
                    android:strokeColor="#33FFFFFF" />
                <path
                    android:fillColor="#00000000"
                    android:pathData="M29,19L29,89"
                    android:strokeWidth="0.8"
                    android:strokeColor="#33FFFFFF" />
                <path
                    android:fillColor="#00000000"
                    android:pathData="M39,19L39,89"
                    android:strokeWidth="0.8"
                    android:strokeColor="#33FFFFFF" />
                <path
                    android:fillColor="#00000000"
                    android:pathData="M49,19L49,89"
                    android:strokeWidth="0.8"
                    android:strokeColor="#33FFFFFF" />
                <path
                    android:fillColor="#00000000"
                    android:pathData="M59,19L59,89"
                    android:strokeWidth="0.8"
                    android:strokeColor="#33FFFFFF" />
                <path
                    android:fillColor="#00000000"
                    android:pathData="M69,19L69,89"
                    android:strokeWidth="0.8"
                    android:strokeColor="#33FFFFFF" />
                <path
                    android:fillColor="#00000000"
                    android:pathData="M79,19L79,89"
                    android:strokeWidth="0.8"
                    android:strokeColor="#33FFFFFF" />
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
                    android:fillType="nonZero"
                    android:pathData="M65.3,45.828l3.8,-6.6c0.2,-0.4 0.1,-0.9 -0.3,-1.1c-0.4,-0.2 -0.9,-0.1 -1.1,0.3l-3.9,6.7c-6.3,-2.8 -13.4,-2.8 -19.7,0l-3.9,-6.7c-0.2,-0.4 -0.7,-0.5 -1.1,-0.3C38.8,38.328 38.7,38.828 38.9,39.228l3.8,6.6C36.2,49.428 31.7,56.028 31,63.928h46C76.3,56.028 71.8,49.428 65.3,45.828zM43.4,57.328c-0.8,0 -1.5,-0.5 -1.8,-1.2c-0.3,-0.7 -0.1,-1.5 0.4,-2.1c0.5,-0.5 1.4,-0.7 2.1,-0.4c0.7,0.3 1.2,1 1.2,1.8C45.3,56.528 44.5,57.328 43.4,57.328L43.4,57.328zM64.6,57.328c-0.8,0 -1.5,-0.5 -1.8,-1.2s-0.1,-1.5 0.4,-2.1c0.5,-0.5 1.4,-0.7 2.1,-0.4c0.7,0.3 1.2,1 1.2,1.8C66.5,56.528 65.6,57.328 64.6,57.328L64.6,57.328z" />
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
