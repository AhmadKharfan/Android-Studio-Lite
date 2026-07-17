package com.ahmadkharfan.androidstudiolite.feature.settings.server

/** UI state for the build-server settings screen. */
data class ServerSettingsUiState(
    /** The base-URL draft shown in the text field (persisted on Save). */
    val baseUrl: String = "",
    /** True once the persisted base URL differs from the draft (enables Save). */
    val dirty: Boolean = false,
    /** The minted device token, or null before registration. */
    val deviceToken: String? = null,
    val isRegistered: Boolean = false,
    val isRegistering: Boolean = false,
    /** One-shot feedback shown under the actions (registration result / save confirmation / error). */
    val statusMessage: String? = null,
    val isError: Boolean = false,
    val gitAuthorName: String = "",
    val gitAuthorEmail: String = "",
    val gitAuthorDirty: Boolean = false,
) {
    /** Masked token for display — never show the full secret. */
    val tokenPreview: String?
        get() = deviceToken?.let { if (it.length <= 10) it else "${it.take(6)}…${it.takeLast(4)}" }
}
