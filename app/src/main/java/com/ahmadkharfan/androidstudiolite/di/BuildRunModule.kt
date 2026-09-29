package com.ahmadkharfan.androidstudiolite.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.ahmadkharfan.androidstudiolite.BuildConfig
import com.ahmadkharfan.androidstudiolite.feature.buildrun.install.ApkInstaller
import com.ahmadkharfan.androidstudiolite.data.buildsystem.signing.AndroidKeystoreManager
import com.ahmadkharfan.androidstudiolite.data.buildsystem.signing.ApksigApkSigner
import com.ahmadkharfan.androidstudiolite.data.githubactions.build.GitHubActionsBuildSystem
import com.ahmadkharfan.androidstudiolite.data.remote.ActiveBuildStore
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ActiveBuildRepository
import com.ahmadkharfan.androidstudiolite.data.remote.RemoteBuildSystem
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildProviderCatalog
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildProviderIds
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildSystem
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.RoutingBuildSystem
import com.ahmadkharfan.androidstudiolite.domain.repository.PreferencesRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.GitRepository
import com.ahmadkharfan.androidstudiolite.domain.signing.ApkSigner
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

private val BUILD_PROVIDERS = listOf(BuildProviderIds.REMOTE, BuildProviderIds.GITHUB_ACTIONS)

val buildRunModule = module {
    single<ActiveBuildRepository> { ActiveBuildStore(androidContext().activeBuildDataStore) }
    single {
        BuildProviderCatalog(
            available = BUILD_PROVIDERS,
            defaultProviderId = BuildConfig.DEFAULT_BUILD_PROVIDER.takeIf { it in BUILD_PROVIDERS }
                ?: BuildProviderIds.REMOTE,
        )
    }
    single<BuildSystem> {
        val gitRepository = get<GitRepository>()
        val keystoreManager = get<KeystoreManager>()
        val preferences = get<PreferencesRepository>()
        val catalog = get<BuildProviderCatalog>()
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
        RoutingBuildSystem(
            providers = mapOf(
                BuildProviderIds.REMOTE to remote,
                BuildProviderIds.GITHUB_ACTIONS to get<GitHubActionsBuildSystem>(),
            ),
            defaultProviderId = catalog.defaultProviderId,
            legacyProviderId = BuildProviderIds.REMOTE,
            selectedProviderId = { preferences.observePreferences().first().buildProviderId },
        )
    }
    single<KeystoreManager> { AndroidKeystoreManager(androidContext()) }
    single<ApkSigner> { ApksigApkSigner() }
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
