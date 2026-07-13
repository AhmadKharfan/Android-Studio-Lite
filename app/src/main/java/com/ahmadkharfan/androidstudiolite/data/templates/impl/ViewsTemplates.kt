package com.ahmadkharfan.androidstudiolite.data.templates.impl

import com.ahmadkharfan.androidstudiolite.data.templates.Catalog
import com.ahmadkharfan.androidstudiolite.data.templates.ProjectRecipe
import com.ahmadkharfan.androidstudiolite.data.templates.Template
import com.ahmadkharfan.androidstudiolite.data.templates.TemplateContent
import com.ahmadkharfan.androidstudiolite.data.templates.TemplateMetadata
import com.ahmadkharfan.androidstudiolite.domain.model.NewProjectSpec
import com.ahmadkharfan.androidstudiolite.domain.model.TemplateLanguage

/** Shared setup for classic-Views single-activity templates (Kotlin or Java). */
internal object ViewsSupport {

    fun applyBaseAndroidApp(spec: NewProjectSpec, recipe: ProjectRecipe) {
        recipe.plugin(Catalog.androidApplication)
        if (spec.language == TemplateLanguage.KOTLIN) {
            recipe.plugin(Catalog.kotlinAndroid)
            recipe.implementation(Catalog.coreKtx)
        }
        recipe.implementation(Catalog.appcompat)
        recipe.implementation(Catalog.material)
        recipe.implementation(Catalog.constraintLayout)
        TemplateContent.addStandardTestDeps(recipe)
    }

    /** Emits `MainActivity` in the selected language that inflates [layoutRes]. */
    fun mainActivity(spec: NewProjectSpec, recipe: ProjectRecipe, layoutRes: String = "activity_main") {
        val pkg = spec.packageName
        if (spec.language == TemplateLanguage.KOTLIN) {
            recipe.sourceFile(
                "MainActivity.kt",
                """
                package $pkg

                import android.os.Bundle
                import androidx.appcompat.app.AppCompatActivity

                class MainActivity : AppCompatActivity() {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                        setContentView(R.layout.$layoutRes)
                    }
                }
                """.trimIndent(),
            )
        } else {
            recipe.sourceFile(
                "MainActivity.java",
                """
                package $pkg;

                import android.os.Bundle;
                import androidx.appcompat.app.AppCompatActivity;

                public class MainActivity extends AppCompatActivity {
                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        setContentView(R.layout.$layoutRes);
                    }
                }
                """.trimIndent(),
            )
        }
    }

    fun commonResources(spec: NewProjectSpec, recipe: ProjectRecipe) {
        recipe.file("app/src/main/res/values/strings.xml", TemplateContent.stringsXml(spec))
        recipe.file("app/src/main/res/values/themes.xml", TemplateContent.viewsThemesXml(spec))
    }
}

/** A single classic-Views activity with a centered greeting inside a ConstraintLayout. */
object EmptyViewsTemplate : Template {

    override val metadata = TemplateMetadata(
        id = "empty-views",
        name = "Empty Views Activity",
        description = "A single Activity using the classic Android View system.",
        icon = "layout",
        tags = listOf("Views"),
    )

    override fun assemble(spec: NewProjectSpec, recipe: ProjectRecipe) {
        ViewsSupport.applyBaseAndroidApp(spec, recipe)
        recipe.file("app/src/main/AndroidManifest.xml", TemplateContent.manifest(spec, "MainActivity"))
        ViewsSupport.commonResources(spec, recipe)
        ViewsSupport.mainActivity(spec, recipe)

        recipe.file(
            "app/src/main/res/layout/activity_main.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <androidx.constraintlayout.widget.ConstraintLayout
                xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:layout_width="match_parent"
                android:layout_height="match_parent">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Hello World!"
                    app:layout_constraintBottom_toBottomOf="parent"
                    app:layout_constraintEnd_toEndOf="parent"
                    app:layout_constraintStart_toStartOf="parent"
                    app:layout_constraintTop_toTopOf="parent" />

            </androidx.constraintlayout.widget.ConstraintLayout>
            """.trimIndent(),
        )
    }
}

/** A single-activity app with a Material toolbar and a floating action button ("Basic Activity"). */
object BasicViewsTemplate : Template {

    override val metadata = TemplateMetadata(
        id = "basic-views",
        name = "Basic Views Activity",
        description = "A Views activity with an app bar and a floating action button.",
        icon = "layout-dashboard",
        tags = listOf("Views", "Material"),
    )

    override fun assemble(spec: NewProjectSpec, recipe: ProjectRecipe) {
        ViewsSupport.applyBaseAndroidApp(spec, recipe)
        recipe.enableViewBinding = true
        recipe.file(
            "app/src/main/AndroidManifest.xml",
            TemplateContent.manifest(spec, "MainActivity", themeAttr = "@style/${TemplateContent.themeName(spec)}"),
        )
        ViewsSupport.commonResources(spec, recipe)

        val pkg = spec.packageName
        if (spec.language == TemplateLanguage.KOTLIN) {
            recipe.sourceFile(
                "MainActivity.kt",
                """
                package $pkg

                import android.os.Bundle
                import androidx.appcompat.app.AppCompatActivity
                import com.google.android.material.snackbar.Snackbar

                class MainActivity : AppCompatActivity() {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                        setContentView(R.layout.activity_main)
                        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab)
                            .setOnClickListener { view ->
                                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                                    .setAnchorView(R.id.fab)
                                    .show()
                            }
                    }
                }
                """.trimIndent(),
            )
        } else {
            recipe.sourceFile(
                "MainActivity.java",
                """
                package $pkg;

                import android.os.Bundle;
                import android.view.View;
                import androidx.appcompat.app.AppCompatActivity;
                import com.google.android.material.floatingactionbutton.FloatingActionButton;
                import com.google.android.material.snackbar.Snackbar;

                public class MainActivity extends AppCompatActivity {
                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        setContentView(R.layout.activity_main);
                        FloatingActionButton fab = findViewById(R.id.fab);
                        fab.setOnClickListener(view ->
                            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                                .setAnchorView(R.id.fab)
                                .show());
                    }
                }
                """.trimIndent(),
            )
        }

        recipe.file(
            "app/src/main/res/layout/activity_main.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <androidx.coordinatorlayout.widget.CoordinatorLayout
                xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:layout_width="match_parent"
                android:layout_height="match_parent">

                <com.google.android.material.appbar.AppBarLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content">

                    <com.google.android.material.appbar.MaterialToolbar
                        android:id="@+id/toolbar"
                        android:layout_width="match_parent"
                        android:layout_height="?attr/actionBarSize" />

                </com.google.android.material.appbar.AppBarLayout>

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_gravity="center"
                    android:text="Hello World!" />

                <com.google.android.material.floatingactionbutton.FloatingActionButton
                    android:id="@+id/fab"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_gravity="bottom|end"
                    android:layout_margin="16dp"
                    android:src="@android:drawable/ic_input_add" />

            </androidx.coordinatorlayout.widget.CoordinatorLayout>
            """.trimIndent(),
        )
    }
}
