package com.ahmadkharfan.androidstudiolite.domain.buildsystem

import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * A [BuildSystem] that hands each call to one of several providers, so the backend can be switched by
 * configuration without the rest of the app knowing which one runs.
 *
 * New builds go to the currently selected provider. Build ids are prefixed with the id of the provider
 * that issued them (`"<providerId>:<id>"`), so a build persisted before the selection changed is still
 * re-attached to the provider that owns it.
 *
 * @param providers every available provider, keyed by a stable id that ends up persisted in build ids.
 * @param defaultProviderId used when nothing is selected, or the selection names an unknown provider,
 *   so a stale setting can't make builds impossible.
 * @param legacyProviderId owner of ids without a known prefix: builds persisted before routing existed.
 * @param selectedProviderId the configured provider id, or null for the default.
 */
class RoutingBuildSystem(
    private val providers: Map<String, BuildSystem>,
    private val defaultProviderId: String,
    private val legacyProviderId: String = defaultProviderId,
    private val selectedProviderId: suspend () -> String?,
) : BuildSystem {

    init {
        require(defaultProviderId in providers) { "Default build provider '$defaultProviderId' is not registered" }
        require(legacyProviderId in providers) { "Legacy build provider '$legacyProviderId' is not registered" }
        require(providers.keys.none { SEPARATOR in it }) { "Build provider ids must not contain '$SEPARATOR'" }
    }

    @Volatile private var activeProvider: BuildSystem? = null

    override suspend fun readiness(): BuildReadiness = providers.getValue(selectedId()).readiness()

    override suspend fun sync(projectRoot: File): ProjectModel = providers.getValue(selectedId()).sync(projectRoot)

    override fun build(request: BuildRequest): Flow<BuildEvent> = flow {
        val providerId = selectedId()
        emitAll(routed(providerId) { it.build(request) })
    }

    override fun attach(buildId: String, projectRoot: File): Flow<BuildEvent> {
        val prefix = buildId.substringBefore(SEPARATOR, missingDelimiterValue = "")
        return if (prefix in providers) {
            routed(prefix) { it.attach(buildId.substringAfter(SEPARATOR), projectRoot) }
        } else {
            routed(legacyProviderId) { it.attach(buildId, projectRoot) }
        }
    }

    override fun cancel() {
        activeProvider?.cancel()
    }

    private suspend fun selectedId(): String =
        selectedProviderId()?.takeIf { it in providers } ?: defaultProviderId

    private fun routed(providerId: String, events: (BuildSystem) -> Flow<BuildEvent>): Flow<BuildEvent> = flow {
        val provider = providers.getValue(providerId)
        activeProvider = provider
        try {
            emitAll(
                events(provider).map { event ->
                    if (event is BuildEvent.RemoteBuildBound) {
                        BuildEvent.RemoteBuildBound(qualify(providerId, event.buildId))
                    } else {
                        event
                    }
                },
            )
        } finally {
            if (activeProvider === provider) activeProvider = null
        }
    }

    companion object {
        private const val SEPARATOR = ':'

        /** The routed form of [buildId] issued by [providerId]. */
        fun qualify(providerId: String, buildId: String): String = "$providerId$SEPARATOR$buildId"
    }
}
