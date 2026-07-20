package com.ahmadkharfan.androidstudiolite.data.gradle.deps

import com.ahmadkharfan.androidstudiolite.data.gradle.model.GradleDsl
import java.io.File

class DependencyManager {

    enum class Strategy {
        VERSION_CATALOG,

        DIRECT,
    }

    data class AddRequest(
        val buildFile: File,
        val coordinate: String,
        val configuration: String = "implementation",
        val strategy: Strategy = Strategy.VERSION_CATALOG,
        val catalogFile: File? = null,
        val alias: String? = null,
    )

    sealed interface Outcome {
        data class Success(val changedFiles: List<File>, val message: String) : Outcome
        data class NoOp(val reason: String) : Outcome
        data class Failure(val reason: String) : Outcome
    }

    fun add(request: AddRequest): Outcome {
        if (!request.buildFile.isFile) return Outcome.Failure("Build file not found: ${request.buildFile}")
        val coord = Coordinate.parse(request.coordinate)
            ?: return Outcome.Failure("Malformed coordinate '${request.coordinate}' (expected group:name[:version])")
        val dsl = dslOf(request.buildFile)

        return when (request.strategy) {
            Strategy.DIRECT -> addDirect(request, coord, dsl)
            Strategy.VERSION_CATALOG -> addViaCatalog(request, coord, dsl)
        }
    }

    fun removeFromBuildFile(buildFile: File, notation: String): Outcome {
        if (!buildFile.isFile) return Outcome.Failure("Build file not found: $buildFile")
        return when (val r = DependenciesBlockEditor.remove(buildFile.readText(), notation)) {
            is DependenciesBlockEditor.Result.Changed -> {
                buildFile.writeText(r.text)
                Outcome.Success(listOf(buildFile), "Removed '$notation'")
            }
            is DependenciesBlockEditor.Result.Unchanged -> Outcome.NoOp(r.reason)
            is DependenciesBlockEditor.Result.Failure -> Outcome.Failure(r.reason)
        }
    }

    private fun addDirect(request: AddRequest, coord: Coordinate, dsl: GradleDsl): Outcome {
        val edit = DependenciesBlockEditor.add(
            text = request.buildFile.readText(),
            configuration = request.configuration,
            notation = coord.full,
            dsl = dsl,
            quoteNotation = true,
        )
        return when (edit) {
            is DependenciesBlockEditor.Result.Changed -> {
                request.buildFile.writeText(edit.text)
                Outcome.Success(listOf(request.buildFile), "Added ${coord.full} to ${request.buildFile.name}")
            }
            is DependenciesBlockEditor.Result.Unchanged -> Outcome.NoOp(edit.reason)
            is DependenciesBlockEditor.Result.Failure -> Outcome.Failure(edit.reason)
        }
    }

    private fun addViaCatalog(request: AddRequest, coord: Coordinate, dsl: GradleDsl): Outcome {
        val catalogFile = request.catalogFile
            ?: return Outcome.Failure("A catalog file is required for the version-catalog strategy")
        if (!catalogFile.isFile) return Outcome.Failure("Catalog file not found: $catalogFile")

        val alias = request.alias ?: defaultAlias(coord)
        val catalogText = catalogFile.readText()
        val catalogEdit = VersionCatalogEditor.addLibrary(catalogText, alias, coord.group, coord.name, coord.version)

        val newCatalogText = when (catalogEdit) {
            is VersionCatalogEditor.Result.Changed -> catalogEdit.text
            is VersionCatalogEditor.Result.Unchanged -> catalogText
            is VersionCatalogEditor.Result.Failure -> return Outcome.Failure(catalogEdit.reason)
        }

        val accessor = "libs.${alias.replace('-', '.').replace('_', '.')}"
        val buildEdit = DependenciesBlockEditor.add(
            text = request.buildFile.readText(),
            configuration = request.configuration,
            notation = accessor,
            dsl = dsl,
            quoteNotation = false,
        )

        val changed = ArrayList<File>()
        if (catalogEdit is VersionCatalogEditor.Result.Changed) {
            catalogFile.writeText(newCatalogText); changed += catalogFile
        }
        return when (buildEdit) {
            is DependenciesBlockEditor.Result.Changed -> {
                request.buildFile.writeText(buildEdit.text); changed += request.buildFile
                Outcome.Success(changed, "Added $accessor (${coord.full}) via version catalog")
            }
            is DependenciesBlockEditor.Result.Unchanged ->
                if (changed.isEmpty()) Outcome.NoOp(buildEdit.reason)
                else Outcome.Success(changed, "Catalog updated; build file already referenced $accessor")
            is DependenciesBlockEditor.Result.Failure -> Outcome.Failure(buildEdit.reason)
        }
    }

    private fun defaultAlias(coord: Coordinate): String =
        coord.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "lib" }

    private fun dslOf(buildFile: File): GradleDsl =
        if (buildFile.name.endsWith(".kts")) GradleDsl.KOTLIN else GradleDsl.GROOVY

    data class Coordinate(val group: String, val name: String, val version: String?) {
        val full: String get() = if (version.isNullOrBlank()) "$group:$name" else "$group:$name:$version"

        companion object {
            fun parse(text: String): Coordinate? {
                val parts = text.trim().split(':')
                if (parts.size < 2 || parts[0].isBlank() || parts[1].isBlank()) return null
                return Coordinate(parts[0], parts[1], parts.getOrNull(2)?.takeIf { it.isNotBlank() })
            }
        }
    }
}
