import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class AslAndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.configureAndroidDefaults()
    }
}

class AslAndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configureAndroidDefaults()
    }
}

class AslAndroidComposeLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("asl.android.library")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        configureComposePreviewTooling()
    }
}

private fun Project.configureComposePreviewTooling() {
    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
    val bom = libs.findLibrary("androidx-compose-bom").get()
    dependencies {
        add("implementation", platform(bom))
        add("implementation", libs.findLibrary("androidx-compose-ui-tooling-preview").get())
        add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
    }
}

class AslKotlinLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        extensions.configure(KotlinJvmProjectExtension::class.java) {
            jvmToolchain(11)
        }
    }
}

private fun org.gradle.api.plugins.ExtensionContainer.configureAndroidDefaults() {
    configure(CommonExtension::class.java) {
        compileSdk = 37
        defaultConfig.minSdk = 24
        // JVM unit tests routinely touch stubbed android.* APIs (e.g. android.util.Log). Returning
        // defaults instead of throwing "Method ... not mocked" keeps pure-logic tests device-free in
        // every module, matching what :app relied on before the split.
        testOptions.unitTests.isReturnDefaultValues = true
    }
}

/**
 * Shared setup for `:feature:*` modules.
 *
 * Deliberately limited to things every feature needs by definition — the Android/Compose defaults,
 * the dependency-version BOMs, and the domain contracts. Capabilities stay in each feature's own
 * build file: not every feature wants navigation, paging or a datastore, and centralising those
 * would quietly grant them to modules that never asked. `:feature:buildrun`, for instance, contains
 * no composables at all.
 */
class AslAndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("asl.android.library.compose")
        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        dependencies {
            add("implementation", platform(libs.findLibrary("koin-bom").get()))
            add("implementation", project(":domain"))
        }
    }
}
