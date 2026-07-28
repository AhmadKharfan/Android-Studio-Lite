// Top-level build file where you can add configuration options common to all sub-projects/modules.
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt) apply false
}

val composeRulesDetekt = libs.detekt.compose.rules

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        parallel = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        baseline = file("detekt-baseline.xml")
        source.setFrom(
            files("src/main/java", "src/main/kotlin", "src/test/java", "src/test/kotlin"),
        )
    }

    dependencies {
        add("detektPlugins", composeRulesDetekt)
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = "11"
        reports {
            html.required.set(true)
            xml.required.set(false)
            sarif.required.set(false)
            md.required.set(false)
            txt.required.set(false)
        }
    }
}
