package com.ahmadkharfan.androidstudiolite.data.githubactions.workflow

/**
 * The GitHub Actions workflow the app installs into the user's build repository, and the request it
 * accepts.
 *
 * The workflow text ships as a resource next to this class. [VERSION] must match the
 * `# asl-workflow-version:` marker in it; the app compares the marker in the repository with [VERSION]
 * to decide when to rewrite the file. Dispatch inputs are validated here with the same rules the
 * workflow enforces, so a bad request fails on the device instead of burning a runner.
 */
object BuildWorkflow {

    const val VERSION = 1

    /** Where the workflow lives in the build repository. */
    const val PATH = ".github/workflows/asl-build.yml"

    /** The workflow id the Actions API accepts in place of a numeric id. */
    const val FILE_NAME = "asl-build.yml"

    /** Name of the artifact holding `asl-result.json`, `build.log`, `events.ndjson` and `artifacts/`. */
    const val RESULT_ARTIFACT = "asl-result"

    /** Branches holding project snapshots; the workflow refuses any other source ref. */
    const val SOURCE_REF_PREFIX = "asl/src/"

    val SUPPORTED_JAVA_VERSIONS: Set<Int> = setOf(11, 17, 21)

    const val MAX_TASKS = 8

    private val CORRELATION_ID = Regex("^[A-Za-z0-9-]{8,64}$")
    private val SOURCE_REF = Regex("^asl/src/[A-Za-z0-9-]{1,64}$")
    private val SOURCE_SHA = Regex("^[0-9a-f]{40}$")
    private val TASK = Regex("^:?[A-Za-z0-9_-]+(:[A-Za-z0-9_-]+)*$")
    private val VERSION_MARKER = Regex("""#\s*asl-workflow-version:\s*(\d+)""")

    /** The workflow file as shipped with this version of the app, with LF line endings. */
    fun contents(): String =
        requireNotNull(BuildWorkflow::class.java.getResourceAsStream("asl-build.yml")) {
            "asl-build.yml is missing from the app's resources"
        }.use { it.readBytes().toString(Charsets.UTF_8) }.replace("\r\n", "\n")

    /** The workflow version recorded in [text], or null if it has no marker (not written by the app). */
    fun versionOf(text: String): Int? = VERSION_MARKER.find(text)?.groupValues?.get(1)?.toIntOrNull()

    /** The run name the workflow gives a dispatch, used to find the run the app just started. */
    fun runName(correlationId: String): String = "asl-$correlationId"

    fun isValidTask(task: String): Boolean = TASK.matches(task)

    /**
     * The `inputs` object for a `workflow_dispatch` request.
     *
     * @throws IllegalArgumentException when any value would be rejected by the workflow.
     */
    fun dispatchInputs(
        correlationId: String,
        sourceRef: String,
        sourceSha: String,
        tasks: List<String>,
        javaVersion: Int,
    ): Map<String, String> {
        require(CORRELATION_ID.matches(correlationId)) { "Invalid correlation id: $correlationId" }
        require(SOURCE_REF.matches(sourceRef)) { "Invalid source ref: $sourceRef" }
        require(SOURCE_SHA.matches(sourceSha)) { "Invalid source commit: $sourceSha" }
        require(javaVersion in SUPPORTED_JAVA_VERSIONS) { "Unsupported JDK version: $javaVersion" }
        require(tasks.size in 1..MAX_TASKS) { "Expected between 1 and $MAX_TASKS Gradle tasks, got ${tasks.size}" }
        tasks.firstOrNull { !isValidTask(it) }?.let { throw IllegalArgumentException("Invalid Gradle task: $it") }
        return mapOf(
            "correlation_id" to correlationId,
            "source_ref" to sourceRef,
            "source_sha" to sourceSha,
            "tasks" to tasks.joinToString(" "),
            "java_version" to javaVersion.toString(),
        )
    }
}
