import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
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
