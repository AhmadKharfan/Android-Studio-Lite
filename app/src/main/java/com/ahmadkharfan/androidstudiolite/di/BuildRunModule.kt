package com.ahmadkharfan.androidstudiolite.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.ahmadkharfan.androidstudiolite.BuildConfig
import com.ahmadkharfan.androidstudiolite.feature.buildrun.install.ApkInstaller
import com.ahmadkharfan.androidstudiolite.data.buildsystem.signing.AndroidKeystoreManager
import com.ahmadkharfan.androidstudiolite.data.remote.ActiveBuildStore
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ActiveBuildRepository
import com.ahmadkharfan.androidstudiolite.data.remote.RemoteBuildSystem
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildSystem
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.RoutingBuildSystem
import com.ahmadkharfan.androidstudiolite.domain.repository.PreferencesRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.GitRepository
import com.ahmadkharfan.androidstudiolite.domain.signing.KeystoreManager
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildNotifier
import com.ahmadkharfan.androidstudiolite.feature.buildrun.api.BuildRunApi
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildRunCoordinator
import java.io.File
import kotlinx.coroutines.flow.first
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private val Context.activeBuildDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "active_build",
)

/** Id of the Kubernetes-backed build server; persisted inside build ids, so it must not change. */
private const val REMOTE_BUILD_PROVIDER = "remote"

val buildRunModule = module {
    single<ActiveBuildRepository> { ActiveBuildStore(androidContext().activeBuildDataStore) }
    single<BuildSystem> {
        val gitRepository = get<GitRepository>()
        val keystoreManager = get<KeystoreManager>()
        val preferences = get<PreferencesRepository>()
        val remote = RemoteBuildSystem(
            client = get(),
            packager = get(),
            artifactDownloader = get(),
            gradleReader = get(),
            sourceDir = File(androidContext().cacheDir, "build-sources"),
            preferGitSource = { false },
            gitSourceResolver = { root -> gitRepository.remoteInfo(root) },
            releaseSigningResolver = { keystoreManager.releaseSigningConfig() },
        )
        val providers = mapOf(REMOTE_BUILD_PROVIDER to remote)
        RoutingBuildSystem(
            providers = providers,
            defaultProviderId = BuildConfig.DEFAULT_BUILD_PROVIDER.takeIf { it in providers } ?: REMOTE_BUILD_PROVIDER,
            legacyProviderId = REMOTE_BUILD_PROVIDER,
            selectedProviderId = { preferences.observePreferences().first().buildProviderId },
        )
    }
    single<KeystoreManager> { AndroidKeystoreManager(androidContext()) }
    single { ApkInstaller(androidContext()) }
    single { BuildNotifier(androidContext()) }
    single<BuildRunCoordinator> {
        BuildRunCoordinator(
            context = androidContext(),
            buildSystem = get(),
            keystoreManager = get(),
            apkInstaller = get(),
            gradleReader = get(),
            notifier = get(),
            activeBuildStore = get(),
        )
    }
    single<BuildRunApi> { get<BuildRunCoordinator>() }
}
