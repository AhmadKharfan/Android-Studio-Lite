package com.ahmadkharfan.androidstudiolite.data.templates

import android.content.res.AssetManager
import java.io.InputStream

/**
 * Supplies the Gradle wrapper binaries copied into every generated project.
 *
 * Generated projects only ever carried `gradle/wrapper/gradle-wrapper.properties`, which pins a
 * distribution but can't fetch it — without `gradlew` and `gradle-wrapper.jar` the project isn't
 * self-contained and the remote worker has nothing to invoke. The binaries ship as app assets under
 * `assets/wrapper/` (see [AssetGradleWrapperSource]) and [ProjectRecipe.writeTo] copies them out; the
 * indirection keeps the recipe unit-testable off-device.
 */
fun interface GradleWrapperSource {

    /** Opens [relativePath] (one of [PATHS]), relative to the wrapper root. Caller closes the stream. */
    fun open(relativePath: String): InputStream

    companion object {
        const val GRADLEW = "gradlew"
        const val GRADLEW_BAT = "gradlew.bat"
        const val WRAPPER_JAR = "gradle/wrapper/gradle-wrapper.jar"

        /** Every file copied into a generated project, in wrapper-root-relative form. */
        val PATHS = listOf(GRADLEW, GRADLEW_BAT, WRAPPER_JAR)
    }
}

/** Reads the wrapper binaries bundled in the APK under `assets/wrapper/`. */
class AssetGradleWrapperSource(
    private val assets: AssetManager,
    private val assetRoot: String = "wrapper",
) : GradleWrapperSource {
    override fun open(relativePath: String): InputStream = assets.open("$assetRoot/$relativePath")
}
