package com.ahmadkharfan.androidstudiolite.core.tooling

import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.DependencyModel
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.DependencyScope
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ModuleModel
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ModuleType
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ProjectModel
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.SourceSetModel
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.VariantModel
import com.ahmadkharfan.androidstudiolite.tooling.proto.JsonValue
import com.ahmadkharfan.androidstudiolite.tooling.proto.Notification
import com.ahmadkharfan.androidstudiolite.tooling.proto.ProjectDto
import com.ahmadkharfan.androidstudiolite.tooling.proto.ToolingProto
import java.io.File

/**
 * Translates between the transport-level `:tooling:proto` DTOs and the app's `domain.buildsystem`
 * model. The proto layer is deliberately `File`-free (it's shared with the plain-JVM server); this is
 * where paths become `File`s and enum names become the domain enums.
 */
object ToolingProtoMapper {

    fun toProjectModel(dto: ProjectDto): ProjectModel = ProjectModel(
        name = dto.name,
        rootDir = File(dto.rootDir),
        modules = dto.modules.map { module ->
            ModuleModel(
                path = module.path,
                name = module.name,
                type = moduleType(module.type),
                moduleDir = File(module.moduleDir),
                variants = module.variants.map { VariantModel(it.name, it.buildType, it.flavors) },
                sourceSets = module.sourceSets.map { ss ->
                    SourceSetModel(
                        name = ss.name,
                        javaDirs = ss.javaDirs.map(::File),
                        kotlinDirs = ss.kotlinDirs.map(::File),
                        resDirs = ss.resDirs.map(::File),
                        assetsDirs = ss.assetsDirs.map(::File),
                        manifestFile = ss.manifestFile?.let(::File),
                    )
                },
                dependencies = module.dependencies.map { dep ->
                    DependencyModel(
                        coordinate = dep.coordinate,
                        scope = dependencyScope(dep.scope),
                        resolvedArtifact = dep.resolvedArtifact?.let(::File),
                    )
                },
            )
        },
    )

    /** Maps a streamed `event` [Notification] onto a [BuildEvent], or null for events we don't surface. */
    fun toBuildEvent(notification: Notification): BuildEvent? {
        val p = notification.params
        return when (notification.type) {
            ToolingProto.EventType.PROGRESS ->
                BuildEvent.Progress(p.string("message").orEmpty())
            ToolingProto.EventType.LOG ->
                BuildEvent.Output(p.string("line").orEmpty(), outputStream(p.string("stream")))
            ToolingProto.EventType.TASK_STARTED ->
                BuildEvent.TaskStarted(p.string("path").orEmpty())
            ToolingProto.EventType.TASK_FINISHED ->
                BuildEvent.TaskFinished(p.string("path").orEmpty(), taskResult(p.string("result")))
            ToolingProto.EventType.PROBLEM ->
                BuildEvent.Problem(
                    severity = problemSeverity(p.string("severity")),
                    message = p.string("message").orEmpty(),
                    file = p.string("file")?.let(::File),
                    line = (p["line"] as? JsonValue.Num)?.value?.toInt(),
                    column = (p["column"] as? JsonValue.Num)?.value?.toInt(),
                )
            else -> null
        }
    }

    private fun moduleType(name: String): ModuleType =
        runCatching { ModuleType.valueOf(name) }.getOrDefault(ModuleType.UNKNOWN)

    private fun dependencyScope(name: String): DependencyScope =
        runCatching { DependencyScope.valueOf(name) }.getOrDefault(DependencyScope.UNKNOWN)

    private fun outputStream(name: String?): BuildEvent.OutputStream =
        if (name == "stderr") BuildEvent.OutputStream.STDERR else BuildEvent.OutputStream.STDOUT

    private fun taskResult(name: String?): BuildEvent.TaskResult =
        runCatching { BuildEvent.TaskResult.valueOf(name ?: "") }.getOrDefault(BuildEvent.TaskResult.SUCCESS)

    private fun problemSeverity(name: String?): BuildEvent.ProblemSeverity =
        runCatching { BuildEvent.ProblemSeverity.valueOf(name ?: "") }.getOrDefault(BuildEvent.ProblemSeverity.ERROR)
}
