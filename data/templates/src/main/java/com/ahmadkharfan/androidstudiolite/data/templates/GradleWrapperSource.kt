package com.ahmadkharfan.androidstudiolite.data.templates

import android.content.res.AssetManager
import java.io.InputStream

fun interface GradleWrapperSource {

    fun open(relativePath: String): InputStream

    companion object {
        const val GRADLEW = "gradlew"
        const val GRADLEW_BAT = "gradlew.bat"
        const val WRAPPER_JAR = "gradle/wrapper/gradle-wrapper.jar"

        val PATHS = listOf(GRADLEW, GRADLEW_BAT, WRAPPER_JAR)
    }
}

class AssetGradleWrapperSource(
    private val assets: AssetManager,
    private val assetRoot: String = "wrapper",
) : GradleWrapperSource {
    override fun open(relativePath: String): InputStream = assets.open("$assetRoot/$relativePath")
}
