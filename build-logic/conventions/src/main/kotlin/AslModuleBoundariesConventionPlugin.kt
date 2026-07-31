import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register

/**
 * Fails the build when a module dependency crosses a boundary the architecture forbids.
 *
 * Applied to the root project only, because it has to see every subproject's declared dependencies.
 *
 * Two details matter for correctness:
 *
 * 1. Collection happens in `projectsEvaluated`, not in [apply]. A subproject's `dependencies {}`
 *    block has not run yet while the root project is being configured, so capturing earlier would
 *    silently observe an empty graph and pass no matter what.
 * 2. Only Strings are captured into the task's inputs. Holding a `Project`, `Configuration` or
 *    `Dependency` past configuration time is not allowed with the configuration cache, which this
 *    build has enabled.
 */
class AslModuleBoundariesConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        require(target == target.rootProject) {
            "asl.module.boundaries applies to the root project; it inspects the whole module graph."
        }

        target.tasks.register<VerifyModuleBoundariesTask>(TASK_NAME) {
            group = "verification"
            description = "Checks that module dependencies respect the architecture boundaries."
            baseline.set(MODULE_BOUNDARY_BASELINE)
            report.set(target.layout.buildDirectory.file("reports/module-boundaries/result.txt"))
            // Lazy on purpose: a subproject's dependencies { } block has not run while the root
            // project is configuring, so reading the graph eagerly would observe nothing and pass
            // regardless of what is declared. The provider is realised once every project is
            // evaluated, and only Strings are stored, which keeps it configuration-cache safe.
            edges.set(target.provider { target.collectProjectEdges() })
            contractSources.set(target.provider { target.collectContractSources() })
            externalEdges.set(target.provider { target.collectExternalEdges() })
        }
    }

    /** Kotlin sources of every contract module, keyed by a repo-relative path. */
    private fun Project.collectContractSources(): Map<String, String> = allprojects
        .filter { it.path.endsWith(":api") }
        .flatMap { project ->
            project.file("src/main").walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .map { it.relativeTo(rootDir).path to it.readText() }
                .toList()
        }
        .toMap()

    /** External dependencies as `owner|configuration|group|name`, Strings only for the cache. */
    private fun Project.collectExternalEdges(): List<String> = allprojects
        .flatMap { project ->
            project.configurations.flatMap { configuration ->
                configuration.dependencies
                    .filter { it !is ProjectDependency && it.group != null }
                    .map { "${project.path}|${configuration.name}|${it.group}|${it.name}" }
            }
        }
        .distinct()
        .sorted()

    private fun Project.collectProjectEdges(): List<String> = allprojects
        .flatMap { project ->
            project.configurations.flatMap { configuration ->
                configuration.dependencies
                    .withType(ProjectDependency::class.java)
                    // ProjectDependency.path is the Gradle 9 accessor; dependencyProject was removed.
                    .map { dependency -> "${project.path}|${configuration.name}|${dependency.path}" }
            }
        }
        .distinct()
        .sorted()

    private companion object {
        const val TASK_NAME = "verifyModuleBoundaries"
    }
}

abstract class VerifyModuleBoundariesTask : DefaultTask() {

    @get:Input
    abstract val edges: ListProperty<String>

    @get:Input
    abstract val baseline: SetProperty<String>

    @get:Input
    abstract val contractSources: MapProperty<String, String>

    @get:Input
    abstract val externalEdges: ListProperty<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val parsed = edges.get().map { encoded ->
            val (from, configuration, to) = encoded.split("|", limit = 3)
            ModuleEdge(from = from, configuration = configuration, to = to)
        }
        val allowed = baseline.get()
        val violations = findBoundaryViolations(parsed, allowed)

        val stillPresent = parsed
            .filter { isProductionConfiguration(it.configuration) }
            .map { "${it.from} -> ${it.to}" }
            .toSet()
        val staleBaseline = allowed - stillPresent

        val summary = buildString {
            appendLine("checked ${parsed.size} project dependencies")
            appendLine("baseline entries: ${allowed.size} (${staleBaseline.size} no longer present)")
            appendLine("violations: ${violations.size}")
        }
        report.get().asFile.apply { parentFile.mkdirs() }.writeText(summary)

        if (staleBaseline.isNotEmpty()) {
            logger.lifecycle(
                "Module boundary baseline has ${staleBaseline.size} stale entr" +
                    "${if (staleBaseline.size == 1) "y" else "ies"} that can now be deleted:",
            )
            staleBaseline.sorted().forEach { logger.lifecycle("  $it") }
        }

        val materialViolations = findMaterialViolations(
            externalEdges.get().map { encoded ->
                val (from, configuration, group, name) = encoded.split("|", limit = 4)
                ExternalEdge(from = from, configuration = configuration, group = group, name = name)
            },
        )
        if (materialViolations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Material used directly by a feature (${materialViolations.size}):")
                    appendLine()
                    materialViolations.forEach { appendLine(it.render()) }
                },
            )
        }

        val contractViolations = findApiContractViolations(contractSources.get())
        if (contractViolations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Contract module violations (${contractViolations.size}):")
                    appendLine()
                    contractViolations.forEach { appendLine(it.render()) }
                },
            )
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Module boundary violations (${violations.size}):")
                    appendLine()
                    violations.sortedBy { it.edge.toString() }.forEach { appendLine(it.render()) }
                    appendLine()
                    append(
                        "These edges are not in the baseline. Either route the dependency through a " +
                            "contract, or move it to a test configuration if only tests need it.",
                    )
                },
            )
        }
    }
}
