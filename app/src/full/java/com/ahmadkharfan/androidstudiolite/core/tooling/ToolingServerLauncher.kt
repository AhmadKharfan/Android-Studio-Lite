package com.ahmadkharfan.androidstudiolite.core.tooling

import android.content.Context
import android.util.Log
import com.ahmadkharfan.androidstudiolite.core.environment.IdeEnvironment
import com.ahmadkharfan.androidstudiolite.core.environment.IdeEnvironmentPaths
import java.io.File
import java.security.MessageDigest

/**
 * Prepares and spawns the tooling server. It extracts the fat jar shipped in the full-flavor assets to
 * a private on-device location, then builds the `java -jar …` command and the toolchain environment
 * (JAVA_HOME/PATH/…) from [IdeEnvironment], returning a ready-to-start [ProcessTransport].
 *
 * Extraction is content-addressed: the asset is copied only when the on-device copy is missing or its
 * bytes differ, so an app update that changes the server jar re-extracts it, but ordinary launches are
 * cheap.
 */
class ToolingServerLauncher(private val context: Context) {

    /** Ensures the server jar is on disk (extracting from assets if needed) and returns it. */
    fun ensureServerJar(): File {
        val target = File(IdeEnvironmentPaths.home(context), ".androidstudiolite/tooling-server.jar")
        target.parentFile?.mkdirs()

        val assetBytes = context.assets.open(ASSET_NAME).use { it.readBytes() }
        if (!target.isFile || !sameContent(target, assetBytes)) {
            target.writeBytes(assetBytes)
            Log.i(TAG, "Extracted tooling server jar (${assetBytes.size} bytes) to $target")
        }
        return target
    }

    /** Creates a transport that runs the server with the installed JDK and toolchain environment. */
    fun createTransport(): ProcessTransport {
        val jar = ensureServerJar()
        val java = File(IdeEnvironmentPaths.javaHome(context), "bin/java")
        val command = listOf(java.absolutePath, "-jar", jar.absolutePath)
        return ProcessTransport(
            command = command,
            environment = IdeEnvironment.environment(context),
            workingDir = IdeEnvironmentPaths.home(context),
        )
    }

    /** Default sync/build parameters wiring the server to the on-device toolchain locations. */
    fun gradleUserHome(): String = IdeEnvironmentPaths.gradleUserHome(context).absolutePath

    fun javaHome(): String = IdeEnvironmentPaths.javaHome(context).absolutePath

    private fun sameContent(file: File, bytes: ByteArray): Boolean {
        if (file.length() != bytes.size.toLong()) return false
        val onDisk = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        val asset = MessageDigest.getInstance("SHA-256").digest(bytes)
        return onDisk.contentEquals(asset)
    }

    private companion object {
        const val ASSET_NAME = "tooling-server.jar"
        const val TAG = "tooling-server"
    }
}
