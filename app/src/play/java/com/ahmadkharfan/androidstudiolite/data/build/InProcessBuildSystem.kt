package com.ahmadkharfan.androidstudiolite.data.build

import com.ahmadkharfan.androidstudiolite.build.common.BuildCancelledException
import com.ahmadkharfan.androidstudiolite.build.common.BuildFailedException
import com.ahmadkharfan.androidstudiolite.build.common.BuildReporter
import com.ahmadkharfan.androidstudiolite.build.common.CancellationToken
import com.ahmadkharfan.androidstudiolite.build.common.LogStream
import com.ahmadkharfan.androidstudiolite.build.engine.maven.MavenCoordinate
import com.ahmadkharfan.androidstudiolite.build.engine.maven.MavenRepositories
import com.ahmadkharfan.androidstudiolite.build.engine.maven.MavenResolver
import com.ahmadkharfan.androidstudiolite.build.engine.maven.ResolutionRequest
import com.ahmadkharfan.androidstudiolite.build.engine.pipeline.BuildPipeline
import com.ahmadkharfan.androidstudiolite.build.engine.pipeline.BuildSpec
import com.ahmadkharfan.androidstudiolite.build.engine.pipeline.DependencyClasspath
import com.ahmadkharfan.androidstudiolite.build.engine.tools.Apksigner
import com.ahmadkharfan.androidstudiolite.build.engine.tools.Aapt2Runner
import com.ahmadkharfan.androidstudiolite.build.engine.tools.D8Dexer
import com.ahmadkharfan.androidstudiolite.build.engine.tools.EcjJavaCompiler
import com.ahmadkharfan.androidstudiolite.build.engine.tools.EmbeddedKotlinCompiler
import com.ahmadkharfan.androidstudiolite.build.engine.tools.Toolchain
import com.ahmadkharfan.androidstudiolite.data.gradle.GradleProjectReader
import com.ahmadkharfan.androidstudiolite.data.gradle.model.GradleDsl
import com.ahmadkharfan.androidstudiolite.data.gradle.model.ParsedAndroidBlock
import com.ahmadkharfan.androidstudiolite.data.gradle.parse.BuildGradleParser
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildKind
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildSystem
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.DependencyScope
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ModuleModel
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ModuleType
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ProjectModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext

/**
 * Play-flavor [BuildSystem]: an on-ART, in-process build engine (cleanroom). [sync] reuses T8's static
 * [GradleProjectReader]; [build] maps the resolved [ProjectModel] into a [BuildSpec], resolves Maven
 * dependencies on device, and drives the [BuildPipeline] (aapt2 → kotlinc → ECJ → D8 → apksig),
 * bridging its structured reporting into the shared [BuildEvent] flow.
 *
 * The supported Gradle subset is documented in `docs/build-run/12-play-in-process-engine.md`.
 */
class InProcessBuildSystem(
    private val reader: GradleProjectReader,
    private val toolchainProvider: ToolchainProvider,
    /** Offline Maven cache directory (under the IDE environment's Gradle user home). */
    private val mavenCacheDir: File,
    /** Root for per-project build intermediates and outputs. */
    private val buildRootDir: File,
    private val repositories: MavenRepositories = MavenRepositories(mavenCacheDir),
) : BuildSystem {

    @Volatile
    private var activeCancellation: CancellationToken? = null

    override suspend fun sync(projectRoot: File): ProjectModel =
        withContext(Dispatchers.IO) { reader.read(projectRoot).model }

    override fun build(request: BuildRequest): Flow<BuildEvent> = channelFlow {
        val startedAt = System.currentTimeMillis()
        trySendBlocking(BuildEvent.Started(request))

        val cancellation = CancellationToken().also { activeCancellation = it }
        val reporter = channelReporter { trySendBlocking(it) }

        val success = try {
            withContext(Dispatchers.IO) { runBuild(request, reporter, cancellation) }
            true
        } catch (e: BuildCancelledException) {
            trySendBlocking(BuildEvent.Output("Build cancelled.", BuildEvent.OutputStream.STDERR))
            false
        } catch (e: BuildFailedException) {
            trySendBlocking(BuildEvent.Problem(BuildEvent.ProblemSeverity.ERROR, e.message ?: "Build failed"))
            false
        } catch (e: Exception) {
            trySendBlocking(BuildEvent.Problem(BuildEvent.ProblemSeverity.ERROR, e.message ?: e.toString()))
            false
        } finally {
            activeCancellation = null
        }

        trySendBlocking(BuildEvent.Finished(success, System.currentTimeMillis() - startedAt))
    }

    override fun cancel() {
        activeCancellation?.cancel()
    }

    // ---- build orchestration ----------------------------------------------------------------

    private fun runBuild(request: BuildRequest, reporter: BuildReporter, cancellation: CancellationToken) {
        if (request.kind == BuildKind.CLEAN) {
            reporter.progress("Cleaning build outputs")
            variantBuildDir(request).deleteRecursively()
            return
        }
        if (request.kind == BuildKind.BUNDLE) {
            throw BuildFailedException("AAB output is not yet supported by the in-process engine.")
        }

        reporter.progress("Reading project model")
        val model = reader.read(request.projectRoot).model
        val module = model.modules.firstOrNull { it.path == request.modulePath }
            ?: throw BuildFailedException("Module ${request.modulePath} not found in ${model.name}")
        if (module.type != ModuleType.ANDROID_APP) {
            throw BuildFailedException("Only application modules can be assembled; ${module.path} is ${module.type}")
        }

        val android = parseAndroidBlock(module)
        val compileSdk = android?.compileSdk?.toIntOrNull() ?: DEFAULT_COMPILE_SDK
        val toolchain = when (val status = toolchainProvider.toolchain(compileSdk)) {
            is ToolchainStatus.Ready -> status.toolchain
            is ToolchainStatus.NotReady -> throw BuildFailedException(status.reason)
        }

        reporter.progress("Resolving dependencies")
        val resolution = MavenResolver(repositories).resolve(resolutionRequests(module))
        resolution.warnings.forEach { reporter.log(it, LogStream.STDERR) }
        val classpath = DependencyClasspath.build(
            resolution,
            explodeRoot = File(mavenCacheDir, "exploded-aars"),
        )

        val spec = buildSpec(request, module, android, toolchain, classpath)
        reporter.progress("Assembling ${spec.applicationId} (${request.variantName})")
        BuildPipeline(
            aapt2 = Aapt2Runner(toolchain.requireAapt2()),
            kotlinc = EmbeddedKotlinCompiler(),
            javac = EcjJavaCompiler(),
            dexTool = D8Dexer(),
            signer = Apksigner(),
        ).run(spec, reporter, cancellation)

        reporter.progress("Build finished: ${spec.outputApk.name}")
        (reporter as? ChannelReporter)?.artifact(spec.outputApk)
    }

    private fun buildSpec(
        request: BuildRequest,
        module: ModuleModel,
        android: ParsedAndroidBlock?,
        toolchain: Toolchain,
        classpath: DependencyClasspath.Result,
    ): BuildSpec {
        val main = module.sourceSets.firstOrNull { it.name == "main" }
        val manifest = main?.manifestFile
            ?: File(module.moduleDir, "src/main/AndroidManifest.xml")
        val applicationId = android?.applicationId
            ?: android?.namespace
            ?: module.name
        val minSdk = android?.minSdk?.toIntOrNull() ?: DEFAULT_MIN_SDK
        val targetSdk = android?.targetSdk?.toIntOrNull() ?: (android?.compileSdk?.toIntOrNull() ?: DEFAULT_COMPILE_SDK)

        val variantDir = variantBuildDir(request)
        return BuildSpec(
            moduleName = module.name,
            applicationId = applicationId,
            minSdk = minSdk,
            targetSdk = targetSdk,
            kotlinSourceRoots = main?.kotlinDirs.orEmpty() + main?.javaDirs.orEmpty(),
            javaSourceRoots = main?.javaDirs.orEmpty(),
            resDirs = main?.resDirs.orEmpty() + classpath.aarResDirs,
            assetsDirs = main?.assetsDirs.orEmpty(),
            manifest = manifest,
            dependencyJars = classpath.compileJars,
            dependencyResApks = emptyList(),
            toolchain = toolchain,
            buildDir = variantDir,
            outputApk = File(variantDir, "outputs/${module.name}-${request.variantName}.apk"),
        )
    }

    /** Maven resolution inputs from a module's declared dependencies (skips project/test deps). */
    private fun resolutionRequests(module: ModuleModel): List<ResolutionRequest> =
        module.dependencies
            .filter { it.scope != DependencyScope.TEST && it.scope != DependencyScope.ANDROID_TEST }
            .filterNot { it.coordinate.startsWith(":") } // project deps are not Maven artifacts
            .mapNotNull { dep ->
                runCatching { MavenCoordinate.parse(dep.coordinate) }.getOrNull()?.let { coord ->
                    ResolutionRequest(coord, isPlatform = coord.artifact.endsWith("bom"))
                }
            }

    private fun parseAndroidBlock(module: ModuleModel): ParsedAndroidBlock? {
        val buildFile = listOf("build.gradle.kts", "build.gradle")
            .map { File(module.moduleDir, it) }
            .firstOrNull { it.isFile } ?: return null
        val dsl = if (buildFile.name.endsWith(".kts")) GradleDsl.KOTLIN else GradleDsl.GROOVY
        return runCatching { BuildGradleParser.parse(buildFile.readText(), dsl).android }.getOrNull()
    }

    private fun variantBuildDir(request: BuildRequest): File {
        val projectName = request.projectRoot.name
        val moduleTag = request.modulePath.trim(':').replace(':', '-').ifEmpty { "root" }
        return File(buildRootDir, "$projectName/$moduleTag/${request.variantName}")
    }

    private companion object {
        const val DEFAULT_MIN_SDK = 24
        const val DEFAULT_COMPILE_SDK = 34
    }
}
