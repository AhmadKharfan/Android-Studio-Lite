package com.example.androidstudiolite.data.fake

import com.example.androidstudiolite.domain.model.IdeComponent
import com.example.androidstudiolite.domain.model.IdeComponentStatus
import com.example.androidstudiolite.domain.model.IdeConfigState
import com.example.androidstudiolite.domain.repository.IdeConfigRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeIdeConfigRepository : IdeConfigRepository {

    private val state = MutableStateFlow(
        IdeConfigState(
            components = listOf(
                IdeComponent(
                    id = "acs",
                    icon = "server-cog",
                    title = "ACS build system",
                    subtitle = "core 2.4.1 · up to date",
                    status = IdeComponentStatus.Ready,
                ),
                IdeComponent(
                    id = "ndk",
                    icon = "cpu",
                    title = "NDK",
                    subtitle = "Not installed · 720 MB",
                    status = IdeComponentStatus.NotInstalled,
                ),
                IdeComponent(
                    id = "cmake",
                    icon = "blocks",
                    title = "CMake",
                    subtitle = "3.22.1 installed",
                    status = IdeComponentStatus.Ready,
                ),
            ),
            offlineMode = false,
        ),
    )

    override fun observeState(): StateFlow<IdeConfigState> = state

    override suspend fun installComponent(id: String) {
        updateComponent(id) { it.copy(status = IdeComponentStatus.Installing) }
        delay(1200)
        updateComponent(id) { it.copy(status = IdeComponentStatus.Ready, subtitle = "Installed") }
    }

    override suspend fun setOfflineMode(enabled: Boolean) {
        state.value = state.value.copy(offlineMode = enabled)
    }

    override suspend fun setNetworkAvailable(available: Boolean) {
        state.value = state.value.copy(networkAvailable = available)
    }

    private fun updateComponent(id: String, transform: (IdeComponent) -> IdeComponent) {
        state.value = state.value.copy(
            components = state.value.components.map { if (it.id == id) transform(it) else it },
        )
    }
}
