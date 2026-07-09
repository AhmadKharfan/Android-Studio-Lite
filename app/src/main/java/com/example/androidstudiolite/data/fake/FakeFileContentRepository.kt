package com.example.androidstudiolite.data.fake
import com.example.androidstudiolite.domain.repository.FileContentRepository
import kotlinx.coroutines.delay
class FakeFileContentRepository : FileContentRepository {
    private val store = linkedMapOf(
        "MainActivity.kt" to MAIN_ACTIVITY,
        "MainViewModel.kt" to MAIN_VIEW_MODEL,
        "build.gradle.kts" to BUILD_GRADLE,
        "settings.gradle.kts" to SETTINGS_GRADLE,
        "AndroidManifest.xml" to ANDROID_MANIFEST,
        "local.properties" to LOCAL_PROPERTIES,
    )
    override suspend fun readText(path: String): String {
        delay(60)
        return store[path] ?: "// $path\n"
    }
    override suspend fun writeText(path: String, text: String) {
        delay(40)
        store[path] = text
    }
    private companion object {
        val MAIN_ACTIVITY = """
            package com.example.myapplication
            import android.os.Bundle
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    val greeting = "Hello, Lite!"
                    setContent {
                        Greeting(greeting)
                    }
                }
            }
        """.trimIndent() + "\n"
        val MAIN_VIEW_MODEL = """
            package com.example.myapplication
            import androidx.lifecycle.ViewModel
            import kotlinx.coroutines.flow.MutableStateFlow
            import kotlinx.coroutines.flow.asStateFlow
            class MainViewModel : ViewModel() {
                private val _count = MutableStateFlow(0)
                val count = _count.asStateFlow()
                fun increment() {
                    _count.value += 1
                }
            }
        """.trimIndent() + "\n"
        val BUILD_GRADLE = """
            plugins {
                id("com.android.application")
                kotlin("android")
            }
            android {
                namespace = "com.example.myapplication"
                compileSdk = 34
                defaultConfig {
                    applicationId = "com.example.myapplication"
                    minSdk = 24
                    targetSdk = 34
                    versionCode = 1
                    versionName = "1.0"
                }
            }
            dependencies {
                implementation("androidx.core:core-ktx:1.13.1")
            }
        """.trimIndent() + "\n"
        val SETTINGS_GRADLE = """
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            rootProject.name = "My Application"
            include(":app")
        """.trimIndent() + "\n"
        val ANDROID_MANIFEST = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application
                    android:allowBackup="true"
                    android:label="My Application"
                    android:theme="@style/Theme.MyApplication">
                    <!-- Launcher activity -->
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
        """.trimIndent() + "\n"
        val LOCAL_PROPERTIES = """
            sdk.dir=/opt/android-sdk
        """.trimIndent() + "\n"
    }
}
