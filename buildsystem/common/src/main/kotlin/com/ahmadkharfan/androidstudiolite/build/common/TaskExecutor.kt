package com.ahmadkharfan.androidstudiolite.build.common

/**
 * Runs an ordered list of [BuildTask]s, applying content-based up-to-date checks, cancellation, and
 * structured reporting. Tasks are executed sequentially in the order given — the pipeline builder is
 * responsible for topological ordering, which is trivial for the linear Android app pipeline
 * (resources → R.jar → kotlinc → javac → dex → package → sign).
 *
 * A task is skipped as UP_TO_DATE when the combined fingerprint of its inputs and outputs matches the
 * value stored from its last successful run *and* all its declared outputs still exist.
 */
class TaskExecutor(
    private val store: FingerprintStore,
    private val reporter: BuildReporter,
    private val cancellation: CancellationToken,
) {
    /** @return true if every task succeeded or was up-to-date. Persists the store as it goes. */
    fun run(tasks: List<BuildTask>): Boolean {
        val context = TaskContext(reporter, cancellation)
        for (task in tasks) {
            cancellation.throwIfCancelled()
            reporter.taskStarted(task.path)

            if (isUpToDate(task)) {
                reporter.taskFinished(task.path, TaskOutcome.UP_TO_DATE)
                continue
            }

            try {
                task.run(context)
            } catch (e: BuildCancelledException) {
                throw e
            } catch (e: BuildFailedException) {
                reporter.taskFinished(task.path, TaskOutcome.FAILED)
                store.remove(task.path)
                store.save()
                throw e
            } catch (e: Throwable) {
                reporter.taskFinished(task.path, TaskOutcome.FAILED)
                store.remove(task.path)
                store.save()
                throw BuildFailedException("Task ${task.path} failed: ${e.message}", e)
            }

            store.put(task.path, fingerprintOf(task))
            store.save()
            reporter.taskFinished(task.path, TaskOutcome.SUCCESS)
        }
        return true
    }

    private fun isUpToDate(task: BuildTask): Boolean {
        val previous = store.get(task.path) ?: return false
        if (task.outputFiles.any { !it.exists() }) return false
        return previous == fingerprintOf(task)
    }

    private fun fingerprintOf(task: BuildTask): String {
        val parts = ArrayList<String>()
        for (f in task.inputFiles) parts += "in:${f.path}:${Fingerprint.ofFile(f)}"
        for (v in task.inputValues) parts += "val:$v"
        for (f in task.outputFiles) parts += "out:${f.path}:${Fingerprint.ofFile(f)}"
        return Fingerprint.combine(parts)
    }
}
