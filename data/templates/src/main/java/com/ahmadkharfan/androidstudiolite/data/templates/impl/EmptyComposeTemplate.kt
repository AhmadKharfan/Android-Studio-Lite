package com.ahmadkharfan.androidstudiolite.data.templates.impl

import com.ahmadkharfan.androidstudiolite.data.templates.Catalog
import com.ahmadkharfan.androidstudiolite.data.templates.ProjectRecipe
import com.ahmadkharfan.androidstudiolite.data.templates.Template
import com.ahmadkharfan.androidstudiolite.data.templates.TemplateContent
import com.ahmadkharfan.androidstudiolite.data.templates.TemplateMetadata
import com.ahmadkharfan.androidstudiolite.domain.model.NewProjectSpec

/** A single-screen Jetpack Compose app (Material 3), mirroring Android Studio's "Empty Activity". */
object EmptyComposeTemplate : Template {

    override val metadata = TemplateMetadata(
        id = "empty-compose",
        name = "Compose Activity",
        description = "A single Compose screen with a Material 3 theme.",
        thumbnail = "template_compose",
        tags = listOf("Kotlin", "Compose"),
    )

    override val supportsJava = false // Compose is Kotlin-only.

    override fun assemble(spec: NewProjectSpec, recipe: ProjectRecipe) {
        recipe.enableCompose = true
        recipe.plugin(Catalog.androidApplication)
        recipe.plugin(Catalog.kotlinAndroid)

        recipe.implementation(Catalog.coreKtx)
        recipe.implementation(Catalog.lifecycleRuntimeKtx)
        recipe.implementation(Catalog.activityCompose)
        recipe.implementation(Catalog.composeBom, isPlatform = true)
        recipe.implementation(Catalog.composeUi)
        recipe.implementation(Catalog.composeUiGraphics)
        recipe.implementation(Catalog.composeUiToolingPreview)
        recipe.implementation(Catalog.material3)
        recipe.testImplementation(Catalog.junit)
        recipe.androidTestImplementation(Catalog.androidxJunit)
        recipe.androidTestImplementation(Catalog.espressoCore)
        recipe.androidTestImplementation(Catalog.composeBom, isPlatform = true)
        recipe.androidTestImplementation(Catalog.composeUiTestJunit4)
        recipe.debugImplementation(Catalog.composeUiTooling)
        recipe.debugImplementation(Catalog.composeUiTestManifest)

        val pkg = spec.packageName
        val theme = TemplateContent.pascal(spec)
        recipe.file("app/src/main/AndroidManifest.xml", TemplateContent.manifest(spec, "MainActivity"))
        recipe.file("app/src/main/res/values/strings.xml", TemplateContent.stringsXml(spec))
        recipe.file("app/src/main/res/values/themes.xml", TemplateContent.composeThemesXml(spec))
        TemplateContent.composeThemeFiles(recipe, spec)

        recipe.sourceFile(
            "MainActivity.kt",
            """
            package $pkg

            import android.os.Bundle
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.activity.enableEdgeToEdge
            import androidx.compose.foundation.layout.fillMaxSize
            import androidx.compose.foundation.layout.padding
            import androidx.compose.material3.Scaffold
            import androidx.compose.material3.Text
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.tooling.preview.Preview
            import $pkg.ui.theme.${theme}Theme

            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    enableEdgeToEdge()
                    setContent {
                        ${theme}Theme {
                            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                                Greeting(
                                    name = "Android",
                                    modifier = Modifier.padding(innerPadding),
                                )
                            }
                        }
                    }
                }
            }

            @Composable
            fun Greeting(name: String, modifier: Modifier = Modifier) {
                Text(text = "Hello ${'$'}name!", modifier = modifier)
            }

            @Preview(showBackground = true)
            @Composable
            fun GreetingPreview() {
                ${theme}Theme {
                    Greeting("Android")
                }
            }
            """.trimIndent(),
        )
    }
}
