package com.ahmadkharfan.androidstudiolite.data.build

import android.content.Context
import com.ahmadkharfan.androidstudiolite.build.engine.tools.Toolchain
import com.ahmadkharfan.androidstudiolite.core.environment.IdeEnvironmentPaths
import java.io.File

/**
 * Resolves the play-flavor toolchain from the installed on-device environment:
 *  - `android.jar` from the downloaded Android platform under [IdeEnvironmentPaths.androidSdkHome].
 *  - `aapt2` from the app's `nativeLibraryDir` (shipped in the play APK's `jniLibs`, exec-safe).
 *  - the embedded Kotlin compiler jars from the downloaded `kotlinc/` data directory.
 *
 * Until the platform data + kotlinc are hosted/installed (T2), [toolchain] reports [ToolchainStatus.NotReady]
 * with a clear reason rather than failing mid-build.
 */
class AndroidToolchainProvider(private val context: Context) : ToolchainProvider {

    override fun toolchain(compileSdk: Int): ToolchainStatus {
        val androidJar = androidJar(compileSdk)
        if (androidJar == null || !androidJar.isFile) {
            return ToolchainStatus.NotReady(
                "Android platform android-$compileSdk is not installed. Install the SDK platform data " +
                    "from Settings → Environment before building.",
            )
        }

        val aapt2 = aapt2Binary()?.takeIf { it.exists() }
        val kotlinc = kotlinCompilerJars()

        return ToolchainStatus.Ready(
            Toolchain(
                aapt2Binary = aapt2,
                androidJar = androidJar,
                kotlinCompilerClasspath = kotlinc,
            ),
        )
    }

    private fun androidJar(compileSdk: Int): File? {
        val platforms = File(IdeEnvironmentPaths.androidSdkHome(context), "platforms")
        val exact = File(platforms, "android-$compileSdk/android.jar")
        if (exact.isFile) return exact
        // Fall back to the newest installed platform so a build isn't blocked on an exact match.
        return platforms.listFiles { f -> f.isDirectory && f.name.startsWith("android-") }
            ?.map { File(it, "android.jar") }
            ?.filter { it.isFile }
            ?.maxByOrNull { it.parentFile.name }
    }

    private fun aapt2Binary(): File? {
        val nativeDir = context.applicationInfo.nativeLibraryDir ?: return null
        // jniLibs binaries are shipped as lib<name>.so; aapt2 is packaged as libaapt2.so.
        return File(nativeDir, "libaapt2.so")
    }

    private fun kotlinCompilerJars(): List<File> {
        val dir = File(IdeEnvironmentPaths.home(context), ".androidstudiolite/kotlinc/lib")
        return dir.listFiles { f -> f.isFile && f.extension == "jar" }?.sorted() ?: emptyList()
    }
}
