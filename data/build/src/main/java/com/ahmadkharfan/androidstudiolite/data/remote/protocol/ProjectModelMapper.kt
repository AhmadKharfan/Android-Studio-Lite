package com.ahmadkharfan.androidstudiolite.data.remote.protocol

import com.ahmadkharfan.androidstudiolite.domain.buildsystem.DependencyModel
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.DependencyScope
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ModuleModel
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ModuleType
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ProjectModel
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.SourceSetModel
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.VariantModel
import java.io.File

object ProjectModelMapper {

    fun toDomain(wire: WireProjectModel, projectRoot: File): ProjectModel = ProjectModel(
        name = wire.name,
        rootDir = resolve(projectRoot, wire.rootDir) ?: projectRoot,
        modules = wire.modules.map { module(it, projectRoot) },
    )

    private fun module(wire: WireModule, root: File): ModuleModel = ModuleModel(
        path = wire.path,
        name = wire.name,
        type = enumOrDefault(wire.type, ModuleType.UNKNOWN),
        moduleDir = resolve(root, wire.moduleDir) ?: File(root, wire.name),
        variants = wire.variants.map { VariantModel(it.name, it.buildType, it.flavors) },
        sourceSets = wire.sourceSets.map { sourceSet(it, root) },
        dependencies = wire.dependencies.map { dependency(it, root) },
    )

    private fun sourceSet(wire: WireSourceSet, root: File): SourceSetModel = SourceSetModel(
        name = wire.name,
        javaDirs = wire.javaDirs.map { File(root, it) },
        kotlinDirs = wire.kotlinDirs.map { File(root, it) },
        resDirs = wire.resDirs.map { File(root, it) },
        assetsDirs = wire.assetsDirs.map { File(root, it) },
        manifestFile = resolve(root, wire.manifestFile),
    )

    private fun dependency(wire: WireDependency, root: File): DependencyModel = DependencyModel(
        coordinate = wire.coordinate,
        scope = enumOrDefault(wire.scope, DependencyScope.UNKNOWN),
        resolvedArtifact = resolve(root, wire.resolvedArtifact),
    )

    private fun resolve(root: File, path: String?): File? = when {
        path == null -> null
        path == "." || path.isBlank() -> root
        else -> File(root, path)
    }

    private inline fun <reified E : Enum<E>> enumOrDefault(raw: String, default: E): E =
        enumValues<E>().firstOrNull { it.name == raw } ?: default
}
