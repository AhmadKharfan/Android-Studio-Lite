package com.ahmadkharfan.androidstudiolite.feature.buildrun

import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildKind
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest
import com.ahmadkharfan.androidstudiolite.feature.buildrun.RemoteBuildKeepAliveService.Companion.EXTRA_BUILD_KIND
import com.ahmadkharfan.androidstudiolite.feature.buildrun.RemoteBuildKeepAliveService.Companion.EXTRA_BUILD_TYPE
import com.ahmadkharfan.androidstudiolite.feature.buildrun.RemoteBuildKeepAliveService.Companion.EXTRA_MODULE_PATH
import com.ahmadkharfan.androidstudiolite.feature.buildrun.RemoteBuildKeepAliveService.Companion.EXTRA_PROJECT_ROOT
import com.ahmadkharfan.androidstudiolite.feature.buildrun.RemoteBuildKeepAliveService.Companion.EXTRA_TASK_PATH
import com.ahmadkharfan.androidstudiolite.feature.buildrun.RemoteBuildKeepAliveService.Companion.EXTRA_VARIANT
import java.io.File

/**
 * Carries a [BuildRequest] across the intent that starts [RemoteBuildKeepAliveService].
 *
 * Kept free of `Intent` so the round trip can be unit tested: every field the caller resolved has to
 * survive, otherwise the service builds something other than what was asked for (the exact task and
 * the authoritative build type were once dropped here, silently falling back to name heuristics).
 */
internal object BuildRequestExtras {

    fun write(request: BuildRequest, put: (key: String, value: String) -> Unit) {
        put(EXTRA_PROJECT_ROOT, request.projectRoot.absolutePath)
        put(EXTRA_MODULE_PATH, request.modulePath)
        put(EXTRA_VARIANT, request.variantName)
        put(EXTRA_BUILD_KIND, request.kind.name)
        request.taskPath?.takeIf { it.isNotBlank() }?.let { put(EXTRA_TASK_PATH, it) }
        request.buildType?.takeIf { it.isNotBlank() }?.let { put(EXTRA_BUILD_TYPE, it) }
    }

    fun read(operationId: String, get: (key: String) -> String?): BuildRequest? {
        val root = get(EXTRA_PROJECT_ROOT)?.let(::File) ?: return null
        val kind = runCatching { BuildKind.valueOf(get(EXTRA_BUILD_KIND).orEmpty()) }
            .getOrDefault(BuildKind.ASSEMBLE)
        return BuildRequest(
            projectRoot = root,
            modulePath = get(EXTRA_MODULE_PATH).orEmpty().ifBlank { ":app" },
            variantName = get(EXTRA_VARIANT).orEmpty().ifBlank { "debug" },
            kind = kind,
            operationId = operationId,
            taskPath = get(EXTRA_TASK_PATH)?.takeIf { it.isNotBlank() },
            buildType = get(EXTRA_BUILD_TYPE)?.takeIf { it.isNotBlank() },
        )
    }
}
