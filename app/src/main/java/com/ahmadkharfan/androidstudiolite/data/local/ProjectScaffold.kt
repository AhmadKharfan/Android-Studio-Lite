package com.ahmadkharfan.androidstudiolite.data.local

import java.io.File

/**
 * Writes a minimal but real Gradle project skeleton to disk so that a freshly created project can be
 * browsed, opened, edited and saved end-to-end (and passes `settings.gradle[.kts]` project detection).
 *
 * This is deliberately bare — the full template engine with recipe-based generation and 9+ templates is
 * task T4, which supersedes this. Everything here is written fresh (no GPL sources), KTS + a single
 * source file, enough to exercise the real file-system layer.
 */
internal object ProjectScaffold {

    fun create(projectDir: File, projectName: String, packageName: String) {
        projectDir.mkdirs()

        File(projectDir, "settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            rootProject.name = "$projectName"
            include(":app")
            """.trimIndent() + "\n",
        )

        File(projectDir, "build.gradle.kts").writeText(
            "// Top-level build file. Add project-wide configuration here.\n",
        )

        val appDir = File(projectDir, "app").apply { mkdirs() }
        File(appDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application")
                kotlin("android")
            }

            android {
                namespace = "$packageName"
                compileSdk = 35

                defaultConfig {
                    applicationId = "$packageName"
                    minSdk = 24
                    targetSdk = 35
                    versionCode = 1
                    versionName = "1.0"
                }
            }
            """.trimIndent() + "\n",
        )

        val mainDir = File(appDir, "src/main").apply { mkdirs() }
        File(mainDir, "AndroidManifest.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application
                    android:allowBackup="true"
                    android:label="$projectName">
                    <activity
                        android:name=".MainActivity"
                        android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>
            </manifest>
            """.trimIndent() + "\n",
        )

        val packageDir = File(mainDir, "java/${packageName.replace('.', '/')}").apply { mkdirs() }
        File(packageDir, "MainActivity.kt").writeText(
            """
            package $packageName

            import android.app.Activity
            import android.os.Bundle

            class MainActivity : Activity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                }
            }
            """.trimIndent() + "\n",
        )
    }
}
