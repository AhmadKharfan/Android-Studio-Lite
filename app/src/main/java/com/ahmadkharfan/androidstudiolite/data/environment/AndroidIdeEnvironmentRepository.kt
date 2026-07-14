package com.ahmadkharfan.androidstudiolite.data.environment

import android.content.Context
import android.os.Build
import com.ahmadkharfan.androidstudiolite.domain.model.IdeEnvironmentComponentState
import com.ahmadkharfan.androidstudiolite.domain.model.IdeEnvironmentComponentStatus
import com.ahmadkharfan.androidstudiolite.domain.model.IdeEnvironmentState
import com.ahmadkharfan.androidstudiolite.domain.repository.IdeEnvironmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Server-side builds need no on-device toolchain (JDK / Android SDK / Gradle) — that whole stack moved
 * to the remote build backend. This is now a trivial, no-network implementation that reports a single
 * always-ready "cloud build" component so onboarding's Setup step and the IDE-config screen have
 * something coherent to show and can complete immediately. The heavy downloader/extractor that used to
 * live here (and the [IdeEnvironmentRepository] contract itself) are kept only until A2 replaces this
 * surface with the remote build-server status/quota screen.
 */
class AndroidIdeEnvironmentRepository(
    @Suppress("UNUSED_PARAMETER") context: Context,
) : IdeEnvironmentRepository {

    private val readyComponent = IdeEnvironmentComponentState(
        id = "cloud-build",
        displayName = "Cloud build service",
        version = "remote",
        sizeBytes = 0,
        status = IdeEnvironmentComponentStatus.Installed,
    )

    private val _state = MutableStateFlow(
        IdeEnvironmentState(
            // Server builds work on any CPU, so never flag the device as unsupported.
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            components = listOf(readyComponent),
        ),
    )

    override fun observeState(): Flow<IdeEnvironmentState> = _state

    override suspend fun refresh() = Unit

    override suspend fun installAll() = Unit

    override fun cancelInstall() = Unit
}
