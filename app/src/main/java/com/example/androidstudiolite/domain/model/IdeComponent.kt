package com.example.androidstudiolite.domain.model

enum class IdeComponentStatus { Ready, NotInstalled, Installing }

data class IdeComponent(
    val id: String,
    val icon: String,
    val title: String,
    val subtitle: String,
    val status: IdeComponentStatus,
)

data class IdeConfigState(
    val components: List<IdeComponent>,
    val offlineMode: Boolean,
    val networkAvailable: Boolean = true,
)
