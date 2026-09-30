package com.ahmadkharfan.androidstudiolite.core.gitauth.cloudbuild

import com.ahmadkharfan.androidstudiolite.core.gitauth.cloudbuild.CloudBuildAction.OpenGitHub
import com.ahmadkharfan.androidstudiolite.core.gitauth.cloudbuild.CloudBuildAction.Purpose
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.CloudBuildState
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.CloudBuildStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudBuildSetupScreenTest {

    private val manage = "https://github.com/settings/installations/42"
    private val install = "https://github.com/apps/android-studio-lite-builds/installations/new"

    private fun screen(state: CloudBuildState?) = cloudBuildSetupScreen(state, installUrl = install)

    @Test
    fun `not connected and revoked ask to connect in the app`() {
        assertEquals(CloudBuildSetupScreen(CloudBuildStep.Connect, CloudBuildAction.Connect), screen(CloudBuildState.NotConnected))
        assertEquals(CloudBuildSetupScreen(CloudBuildStep.Reconnect, CloudBuildAction.Connect), screen(CloudBuildState.Revoked))
    }

    @Test
    fun `missing access installs the app, with creating storage as the other way`() {
        val screen = screen(CloudBuildState.AccessMissing(installUrl = null))

        assertEquals(CloudBuildStep.AllowAccess, screen.step)
        assertEquals(OpenGitHub(install, Purpose.AllowAccess), screen.primary)
        assertEquals(OpenGitHub(CREATE_STORAGE_URL, Purpose.CreateStorage), screen.secondary)
    }

    @Test
    fun `unreachable storage is fixed through the installation's own link`() {
        val screen = screen(CloudBuildState.StorageNotReachable(manage, wasReachableBefore = true))

        assertEquals(CloudBuildStep.StorageNotReachable, screen.step)
        assertEquals(OpenGitHub(manage, Purpose.ManageAccess), screen.primary)
        assertEquals(OpenGitHub(CREATE_STORAGE_URL, Purpose.CreateStorage), screen.secondary)
    }

    @Test
    fun `without github's own link the installations page is used, never a built one`() {
        assertEquals(OpenGitHub(INSTALLATIONS_URL, Purpose.ManageAccess), screen(CloudBuildState.AccessPaused(null)).primary)
    }

    @Test
    fun `missing storage opens github's prefilled new-repository form`() {
        val screen = screen(CloudBuildState.StorageMissing(manage))

        assertEquals(CloudBuildStep.CreateStorage, screen.step)
        assertEquals(OpenGitHub(CREATE_STORAGE_URL, Purpose.CreateStorage), screen.primary)
        assertEquals(
            "https://github.com/new?name=asl-build&visibility=private&description=Private%20build%20storage%20for%20Android%20Studio%20Lite",
            CREATE_STORAGE_URL,
        )
    }

    @Test
    fun `public storage opens its own settings`() {
        val storage = CloudBuildStorage(7, "octo/asl-build", "https://github.com/octo/asl-build", isPrivate = false)

        assertEquals(
            OpenGitHub("https://github.com/octo/asl-build/settings", Purpose.StorageSettings),
            screen(CloudBuildState.StoragePublic(storage)).primary,
        )
    }

    @Test
    fun `a permission update is reviewed on github, a missing oauth scope reconnects`() {
        assertEquals(
            OpenGitHub(manage, Purpose.ReviewAccess),
            screen(CloudBuildState.PermissionUpdateRequired(manage, "actions=write")).primary,
        )
        assertEquals(CloudBuildAction.Connect, screen(CloudBuildState.PermissionUpdateRequired(null, null)).primary)
    }

    @Test
    fun `transient problems offer to check again`() {
        listOf(
            CloudBuildState.Offline to CloudBuildStep.Offline,
            CloudBuildState.GitHubUnavailable to CloudBuildStep.GitHubUnavailable,
            CloudBuildState.RateLimited(1_900_000_000) to CloudBuildStep.RateLimited,
            CloudBuildState.Unknown("teapot") to CloudBuildStep.Unknown,
        ).forEach { (state, step) ->
            val screen = screen(state)
            assertEquals(step, screen.step)
            assertEquals(CloudBuildAction.CheckAgain, screen.primary)
        }
        assertEquals(1_900_000_000L, screen(CloudBuildState.RateLimited(1_900_000_000)).resetAtEpochSeconds)
        assertEquals("teapot", screen(CloudBuildState.Unknown("teapot")).detail)
    }

    @Test
    fun `ready shows the account and needs nothing`() {
        val screen = screen(CloudBuildState.Ready("octo", storage = null))

        assertEquals(CloudBuildStep.Ready, screen.step)
        assertEquals("octo", screen.account)
        assertNull(screen.primary)
    }

    @Test
    fun `before the first answer the setup is checking`() {
        assertEquals(CloudBuildStep.Checking, screen(null).step)
        assertEquals(CloudBuildStep.Checking, screen(CloudBuildState.Checking).step)
    }
}
