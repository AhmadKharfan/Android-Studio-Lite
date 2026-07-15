package com.ahmadkharfan.androidstudiolite.data.templates.impl

import com.ahmadkharfan.androidstudiolite.data.templates.Catalog
import com.ahmadkharfan.androidstudiolite.data.templates.ProjectRecipe
import com.ahmadkharfan.androidstudiolite.data.templates.Template
import com.ahmadkharfan.androidstudiolite.data.templates.TemplateContent
import com.ahmadkharfan.androidstudiolite.data.templates.TemplateMetadata
import com.ahmadkharfan.androidstudiolite.domain.model.NewProjectSpec
import com.ahmadkharfan.androidstudiolite.domain.model.TemplateLanguage

/** An empty Android app module with no Activity — a starting point to add your own entry point. */
object NoActivityTemplate : Template {

    override val metadata = TemplateMetadata(
        id = "no-activity",
        name = "No Activity",
        description = "An empty app module — add your own entry point.",
        thumbnail = "template_no_activity",
        tags = listOf("Views"),
    )

    override fun assemble(spec: NewProjectSpec, recipe: ProjectRecipe) {
        recipe.plugin(Catalog.androidApplication)
        if (spec.language == TemplateLanguage.KOTLIN) {
            recipe.plugin(Catalog.kotlinAndroid)
            recipe.implementation(Catalog.coreKtx)
        }
        recipe.implementation(Catalog.appcompat)
        recipe.implementation(Catalog.material)
        TemplateContent.addStandardTestDeps(recipe)

        recipe.file(
            "app/src/main/AndroidManifest.xml",
            TemplateContent.manifest(spec, activityName = null),
        )
        recipe.file("app/src/main/res/values/strings.xml", TemplateContent.stringsXml(spec))
        recipe.file("app/src/main/res/values/themes.xml", TemplateContent.viewsThemesXml(spec))
    }
}

/**
 * A minimal Android app that does not depend on AndroidX at all — a plain framework `Activity` with a
 * framework theme. `android.useAndroidX` is disabled in `gradle.properties`.
 */
object NoAndroidXTemplate : Template {

    override val metadata = TemplateMetadata(
        id = "no-androidx",
        name = "No AndroidX",
        description = "A minimal app with no AndroidX libraries — framework APIs only.",
        thumbnail = "template_no_androidx",
        tags = listOf("Legacy"),
    )

    override fun assemble(spec: NewProjectSpec, recipe: ProjectRecipe) {
        recipe.useAndroidX = false
        recipe.plugin(Catalog.androidApplication)
        if (spec.language == TemplateLanguage.KOTLIN) recipe.plugin(Catalog.kotlinAndroid)
        recipe.testImplementation(Catalog.junit)

        recipe.file(
            "app/src/main/AndroidManifest.xml",
            TemplateContent.manifest(spec, "MainActivity", themeAttr = "@android:style/Theme.Material.Light"),
        )
        recipe.file("app/src/main/res/values/strings.xml", TemplateContent.stringsXml(spec))

        val pkg = spec.packageName
        if (spec.language == TemplateLanguage.KOTLIN) {
            recipe.sourceFile(
                "MainActivity.kt",
                """
                package $pkg

                import android.app.Activity
                import android.os.Bundle
                import android.widget.TextView

                class MainActivity : Activity() {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                        setContentView(TextView(this).apply { text = "Hello, no AndroidX!" })
                    }
                }
                """.trimIndent(),
            )
        } else {
            recipe.sourceFile(
                "MainActivity.java",
                """
                package $pkg;

                import android.app.Activity;
                import android.os.Bundle;
                import android.widget.TextView;

                public class MainActivity extends Activity {
                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        TextView view = new TextView(this);
                        view.setText("Hello, no AndroidX!");
                        setContentView(view);
                    }
                }
                """.trimIndent(),
            )
        }
    }
}

/** A Views app with a C++/CMake native library exposing a string over JNI ("Native C++"). */
object NativeCppTemplate : Template {

    override val metadata = TemplateMetadata(
        id = "native-cpp",
        name = "C++ project",
        description = "A Views activity backed by a C++ library built with CMake and the NDK.",
        thumbnail = "template_cpp",
        tags = listOf("Views", "C++"),
    )

    override fun assemble(spec: NewProjectSpec, recipe: ProjectRecipe) {
        ViewsSupport.applyBaseAndroidApp(spec, recipe)
        recipe.cmakeListsRelPath = "src/main/cpp/CMakeLists.txt"
        recipe.file("app/src/main/AndroidManifest.xml", TemplateContent.manifest(spec, "MainActivity"))
        ViewsSupport.commonResources(spec, recipe)

        val pkg = spec.packageName
        val jniName = "Java_" + pkg.replace('.', '_') + "_MainActivity_stringFromJNI"
        if (spec.language == TemplateLanguage.KOTLIN) {
            recipe.sourceFile(
                "MainActivity.kt",
                """
                package $pkg

                import android.os.Bundle
                import android.widget.TextView
                import androidx.appcompat.app.AppCompatActivity

                class MainActivity : AppCompatActivity() {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                        setContentView(R.layout.activity_main)
                        findViewById<TextView>(R.id.sample_text).text = stringFromJNI()
                    }

                    private external fun stringFromJNI(): String

                    companion object {
                        init {
                            System.loadLibrary("native-lib")
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
                import android.widget.TextView;
                import androidx.appcompat.app.AppCompatActivity;

                public class MainActivity extends AppCompatActivity {
                    static {
                        System.loadLibrary("native-lib");
                    }

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        setContentView(R.layout.activity_main);
                        TextView tv = findViewById(R.id.sample_text);
                        tv.setText(stringFromJNI());
                    }

                    public native String stringFromJNI();
                }
                """.trimIndent(),
            )
        }

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
                    android:id="@+id/sample_text"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    app:layout_constraintBottom_toBottomOf="parent"
                    app:layout_constraintEnd_toEndOf="parent"
                    app:layout_constraintStart_toStartOf="parent"
                    app:layout_constraintTop_toTopOf="parent" />

            </androidx.constraintlayout.widget.ConstraintLayout>
            """.trimIndent(),
        )

        recipe.file(
            "app/src/main/cpp/CMakeLists.txt",
            """
            cmake_minimum_required(VERSION 3.22.1)

            project("native-lib")

            add_library(native-lib SHARED native-lib.cpp)

            find_library(log-lib log)

            target_link_libraries(native-lib ${'$'}{log-lib})
            """.trimIndent(),
        )

        recipe.file(
            "app/src/main/cpp/native-lib.cpp",
            """
            #include <jni.h>
            #include <string>

            extern "C" JNIEXPORT jstring JNICALL
            $jniName(JNIEnv* env, jobject /* this */) {
                std::string hello = "Hello from C++";
                return env->NewStringUTF(hello.c_str());
            }
            """.trimIndent(),
        )
    }
}
