package com.ahmadkharfan.androidstudiolite.tooling.proto

/**
 * Plain, transport-level mirrors of the app's `domain.buildsystem` model, plus the request/result
 * payloads for each method. These live in `:tooling:proto` (no Android, no `java.io.File`) so the
 * server can build them from Tooling-API models and the app can map them back onto its domain types.
 * Every DTO knows how to (de)serialize itself to [JsonValue].
 */

// --------------------------------------------------------------------------- sync

data class SyncParams(
    val projectDir: String,
    /** Absolute path to a Gradle installation dir; when null the project's wrapper is used. */
    val gradleInstallation: String? = null,
    /** Explicit Gradle version (downloaded by the Tooling API); ignored when [gradleInstallation] is set. */
    val gradleVersion: String? = null,
    val gradleUserHome: String? = null,
    val javaHome: String? = null,
    /** Extra `-P`/`-D` style build arguments passed through to Gradle. */
    val arguments: List<String> = emptyList(),
) {
    fun toJson(): JsonValue.Obj = JsonValue.obj(
        "projectDir" to JsonValue.of(projectDir),
        "gradleInstallation" to JsonValue.of(gradleInstallation),
        "gradleVersion" to JsonValue.of(gradleVersion),
        "gradleUserHome" to JsonValue.of(gradleUserHome),
        "javaHome" to JsonValue.of(javaHome),
        "arguments" to JsonValue.arr(arguments.map { JsonValue.of(it) }),
    )

    companion object {
        fun fromJson(o: JsonValue.Obj) = SyncParams(
            projectDir = o.string("projectDir") ?: error("sync: missing projectDir"),
            gradleInstallation = o.string("gradleInstallation"),
            gradleVersion = o.string("gradleVersion"),
            gradleUserHome = o.string("gradleUserHome"),
            javaHome = o.string("javaHome"),
            arguments = o.array("arguments")?.items?.mapNotNull { (it as? JsonValue.Str)?.value }.orEmpty(),
        )
    }
}

data class SyncResult(val project: ProjectDto) {
    fun toJson(): JsonValue.Obj = JsonValue.obj("project" to project.toJson())

    companion object {
        fun fromJson(o: JsonValue.Obj) =
            SyncResult(ProjectDto.fromJson(o.obj("project") ?: error("sync result: missing project")))
    }
}

// --------------------------------------------------------------------------- build

data class BuildParams(
    val projectDir: String,
    val tasks: List<String>,
    val gradleInstallation: String? = null,
    val gradleVersion: String? = null,
    val gradleUserHome: String? = null,
    val javaHome: String? = null,
    val arguments: List<String> = emptyList(),
) {
    fun toJson(): JsonValue.Obj = JsonValue.obj(
        "projectDir" to JsonValue.of(projectDir),
        "tasks" to JsonValue.arr(tasks.map { JsonValue.of(it) }),
        "gradleInstallation" to JsonValue.of(gradleInstallation),
        "gradleVersion" to JsonValue.of(gradleVersion),
        "gradleUserHome" to JsonValue.of(gradleUserHome),
        "javaHome" to JsonValue.of(javaHome),
        "arguments" to JsonValue.arr(arguments.map { JsonValue.of(it) }),
    )

    companion object {
        fun fromJson(o: JsonValue.Obj) = BuildParams(
            projectDir = o.string("projectDir") ?: error("build: missing projectDir"),
            tasks = o.array("tasks")?.items?.mapNotNull { (it as? JsonValue.Str)?.value }.orEmpty(),
            gradleInstallation = o.string("gradleInstallation"),
            gradleVersion = o.string("gradleVersion"),
            gradleUserHome = o.string("gradleUserHome"),
            javaHome = o.string("javaHome"),
            arguments = o.array("arguments")?.items?.mapNotNull { (it as? JsonValue.Str)?.value }.orEmpty(),
        )
    }
}

data class BuildResultDto(val success: Boolean, val durationMillis: Long) {
    fun toJson(): JsonValue.Obj = JsonValue.obj(
        "success" to JsonValue.of(success),
        "durationMillis" to JsonValue.of(durationMillis),
    )

    companion object {
        fun fromJson(o: JsonValue.Obj) =
            BuildResultDto(o.bool("success") ?: false, o.long("durationMillis") ?: 0L)
    }
}

// --------------------------------------------------------------------------- project model DTOs

data class ProjectDto(val name: String, val rootDir: String, val modules: List<ModuleDto>) {
    fun toJson(): JsonValue.Obj = JsonValue.obj(
        "name" to JsonValue.of(name),
        "rootDir" to JsonValue.of(rootDir),
        "modules" to JsonValue.arr(modules.map { it.toJson() }),
    )

    companion object {
        fun fromJson(o: JsonValue.Obj) = ProjectDto(
            name = o.string("name").orEmpty(),
            rootDir = o.string("rootDir").orEmpty(),
            modules = o.array("modules")?.items?.mapNotNull { (it as? JsonValue.Obj)?.let(ModuleDto::fromJson) }.orEmpty(),
        )
    }
}

data class ModuleDto(
    val path: String,
    val name: String,
    val type: String,
    val moduleDir: String,
    val variants: List<VariantDto> = emptyList(),
    val sourceSets: List<SourceSetDto> = emptyList(),
    val dependencies: List<DependencyDto> = emptyList(),
) {
    fun toJson(): JsonValue.Obj = JsonValue.obj(
        "path" to JsonValue.of(path),
        "name" to JsonValue.of(name),
        "type" to JsonValue.of(type),
        "moduleDir" to JsonValue.of(moduleDir),
        "variants" to JsonValue.arr(variants.map { it.toJson() }),
        "sourceSets" to JsonValue.arr(sourceSets.map { it.toJson() }),
        "dependencies" to JsonValue.arr(dependencies.map { it.toJson() }),
    )

    companion object {
        fun fromJson(o: JsonValue.Obj) = ModuleDto(
            path = o.string("path").orEmpty(),
            name = o.string("name").orEmpty(),
            type = o.string("type") ?: "UNKNOWN",
            moduleDir = o.string("moduleDir").orEmpty(),
            variants = o.array("variants")?.items?.mapNotNull { (it as? JsonValue.Obj)?.let(VariantDto::fromJson) }.orEmpty(),
            sourceSets = o.array("sourceSets")?.items?.mapNotNull { (it as? JsonValue.Obj)?.let(SourceSetDto::fromJson) }.orEmpty(),
            dependencies = o.array("dependencies")?.items?.mapNotNull { (it as? JsonValue.Obj)?.let(DependencyDto::fromJson) }.orEmpty(),
        )
    }
}

data class VariantDto(val name: String, val buildType: String, val flavors: List<String> = emptyList()) {
    fun toJson(): JsonValue.Obj = JsonValue.obj(
        "name" to JsonValue.of(name),
        "buildType" to JsonValue.of(buildType),
        "flavors" to JsonValue.arr(flavors.map { JsonValue.of(it) }),
    )

    companion object {
        fun fromJson(o: JsonValue.Obj) = VariantDto(
            name = o.string("name").orEmpty(),
            buildType = o.string("buildType").orEmpty(),
            flavors = o.array("flavors")?.items?.mapNotNull { (it as? JsonValue.Str)?.value }.orEmpty(),
        )
    }
}

data class SourceSetDto(
    val name: String,
    val javaDirs: List<String> = emptyList(),
    val kotlinDirs: List<String> = emptyList(),
    val resDirs: List<String> = emptyList(),
    val assetsDirs: List<String> = emptyList(),
    val manifestFile: String? = null,
) {
    fun toJson(): JsonValue.Obj = JsonValue.obj(
        "name" to JsonValue.of(name),
        "javaDirs" to JsonValue.arr(javaDirs.map { JsonValue.of(it) }),
        "kotlinDirs" to JsonValue.arr(kotlinDirs.map { JsonValue.of(it) }),
        "resDirs" to JsonValue.arr(resDirs.map { JsonValue.of(it) }),
        "assetsDirs" to JsonValue.arr(assetsDirs.map { JsonValue.of(it) }),
        "manifestFile" to JsonValue.of(manifestFile),
    )

    companion object {
        fun fromJson(o: JsonValue.Obj) = SourceSetDto(
            name = o.string("name").orEmpty(),
            javaDirs = strings(o, "javaDirs"),
            kotlinDirs = strings(o, "kotlinDirs"),
            resDirs = strings(o, "resDirs"),
            assetsDirs = strings(o, "assetsDirs"),
            manifestFile = o.string("manifestFile"),
        )

        private fun strings(o: JsonValue.Obj, key: String): List<String> =
            o.array(key)?.items?.mapNotNull { (it as? JsonValue.Str)?.value }.orEmpty()
    }
}

data class DependencyDto(val coordinate: String, val scope: String, val resolvedArtifact: String? = null) {
    fun toJson(): JsonValue.Obj = JsonValue.obj(
        "coordinate" to JsonValue.of(coordinate),
        "scope" to JsonValue.of(scope),
        "resolvedArtifact" to JsonValue.of(resolvedArtifact),
    )

    companion object {
        fun fromJson(o: JsonValue.Obj) = DependencyDto(
            coordinate = o.string("coordinate").orEmpty(),
            scope = o.string("scope") ?: "UNKNOWN",
            resolvedArtifact = o.string("resolvedArtifact"),
        )
    }
}

// --------------------------------------------------------------------------- event payloads

/**
 * Typed constructors/readers for the streaming `event` notifications. The `type` discriminator is set
 * by [Notification.encode]; these helpers build/read only the remaining fields.
 */
object Events {

    fun progress(message: String) = Notification(
        ToolingProto.EventType.PROGRESS,
        JsonValue.obj("message" to JsonValue.of(message)),
    )

    fun log(stream: String, line: String) = Notification(
        ToolingProto.EventType.LOG,
        JsonValue.obj("stream" to JsonValue.of(stream), "line" to JsonValue.of(line)),
    )

    fun taskStarted(path: String) = Notification(
        ToolingProto.EventType.TASK_STARTED,
        JsonValue.obj("path" to JsonValue.of(path)),
    )

    fun taskFinished(path: String, result: String) = Notification(
        ToolingProto.EventType.TASK_FINISHED,
        JsonValue.obj("path" to JsonValue.of(path), "result" to JsonValue.of(result)),
    )

    fun problem(severity: String, message: String, file: String?, line: Int?, column: Int?) = Notification(
        ToolingProto.EventType.PROBLEM,
        JsonValue.obj(
            "severity" to JsonValue.of(severity),
            "message" to JsonValue.of(message),
            "file" to JsonValue.of(file),
            "line" to (line?.let { JsonValue.of(it) } ?: JsonValue.Null),
            "column" to (column?.let { JsonValue.of(it) } ?: JsonValue.Null),
        ),
    )
}
