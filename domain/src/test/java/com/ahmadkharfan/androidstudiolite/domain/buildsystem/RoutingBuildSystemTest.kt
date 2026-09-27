package com.ahmadkharfan.androidstudiolite.domain.buildsystem

import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingBuildSystemTest {

    private val root = File("/projects/demo")
    private val request = BuildRequest(root, ":app", "debug")

    private class FakeProvider(
        private val boundId: String,
        private val readiness: BuildReadiness = BuildReadiness.Ready,
    ) : BuildSystem {
        val built = mutableListOf<BuildRequest>()
        val attached = mutableListOf<String>()
        var cancels = 0
        var onCollect: () -> Unit = {}

        override suspend fun readiness() = readiness
        override suspend fun sync(projectRoot: File) = ProjectModel(boundId, projectRoot, emptyList())
        override fun build(request: BuildRequest): Flow<BuildEvent> {
            built += request
            return events()
        }
        override fun attach(buildId: String, projectRoot: File): Flow<BuildEvent> {
            attached += buildId
            return events()
        }
        override fun cancel() {
            cancels++
        }
        private fun events() = flowOf(
            BuildEvent.RemoteBuildBound(boundId),
            BuildEvent.Progress("working"),
            BuildEvent.Finished(success = true, durationMillis = 1),
        ).onEach { onCollect() }
    }

    private val remote = FakeProvider("r-1")
    private val gha = FakeProvider("run-7", BuildReadiness.NeedsSignIn("Sign in to GitHub"))

    private fun router(selected: String? = null) = RoutingBuildSystem(
        providers = mapOf("remote" to remote, "gha" to gha),
        defaultProviderId = "remote",
        legacyProviderId = "remote",
        selectedProviderId = { selected },
    )

    @Test
    fun `builds go to the default provider when nothing is selected`() = runTest {
        router().build(request).toList()

        assertEquals(listOf(request), remote.built)
        assertTrue(gha.built.isEmpty())
    }

    @Test
    fun `builds go to the selected provider`() = runTest {
        router(selected = "gha").build(request).toList()

        assertEquals(listOf(request), gha.built)
        assertTrue(remote.built.isEmpty())
    }

    @Test
    fun `an unknown selection falls back to the default provider`() = runTest {
        router(selected = "retired").build(request).toList()

        assertEquals(listOf(request), remote.built)
    }

    @Test
    fun `bound build ids are qualified with the issuing provider`() = runTest {
        val bound = router(selected = "gha").build(request).toList().filterIsInstance<BuildEvent.RemoteBuildBound>()

        assertEquals(listOf(BuildEvent.RemoteBuildBound("gha:run-7")), bound)
    }

    @Test
    fun `other events pass through untouched`() = runTest {
        val events = router().build(request).toList()

        assertEquals(BuildEvent.Progress("working"), events[1])
        assertEquals(BuildEvent.Finished(success = true, durationMillis = 1), events[2])
    }

    @Test
    fun `attach goes to the provider that issued the id, whatever is selected now`() = runTest {
        router(selected = "remote").attach("gha:run-7", root).toList()

        assertEquals(listOf("run-7"), gha.attached)
        assertTrue(remote.attached.isEmpty())
    }

    @Test
    fun `ids from before routing attach to the legacy provider unchanged`() = runTest {
        val events = router(selected = "gha").attach("5f0c1e2a-uuid", root).toList()

        assertEquals(listOf("5f0c1e2a-uuid"), remote.attached)
        assertEquals(BuildEvent.RemoteBuildBound("remote:r-1"), events.first())
    }

    @Test
    fun `an id whose prefix is not a provider is treated as legacy`() = runTest {
        router().attach("weird:thing", root).toList()

        assertEquals(listOf("weird:thing"), remote.attached)
    }

    @Test
    fun `cancel reaches the provider running the build`() = runTest {
        val router = router(selected = "gha")
        gha.onCollect = { router.cancel() }

        router.build(request).toList()

        assertEquals(3, gha.cancels)
        assertEquals(0, remote.cancels)
    }

    @Test
    fun `cancel with nothing running reaches no provider`() {
        router().cancel()

        assertEquals(0, remote.cancels + gha.cancels)
    }

    @Test
    fun `readiness and sync come from the selected provider`() = runTest {
        val router = router(selected = "gha")

        assertEquals(BuildReadiness.NeedsSignIn("Sign in to GitHub"), router.readiness())
        assertEquals("run-7", router.sync(root).name)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an unregistered default provider is rejected`() {
        RoutingBuildSystem(mapOf("remote" to remote), defaultProviderId = "gha", selectedProviderId = { null })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `provider ids containing the separator are rejected`() {
        RoutingBuildSystem(mapOf("a:b" to remote), defaultProviderId = "a:b", selectedProviderId = { null })
    }
}
