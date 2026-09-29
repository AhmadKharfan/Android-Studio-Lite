package com.ahmadkharfan.androidstudiolite.domain.buildsystem

/** Stable ids of the build providers. They are persisted in build ids and preferences, so never change them. */
object BuildProviderIds {
    /** The Android Studio Lite build server. */
    const val REMOTE = "remote"

    /** GitHub Actions in the user's own build repository. */
    const val GITHUB_ACTIONS = "gha"
}

/** The build providers this build of the app offers, and the one used when the user hasn't chosen. */
data class BuildProviderCatalog(
    val available: List<String>,
    val defaultProviderId: String,
) {
    init {
        require(defaultProviderId in available) { "Default build provider '$defaultProviderId' is not available" }
    }

    /** The provider actually in use for the user's [selected] id (null or unknown means the default). */
    fun effective(selected: String?): String = selected?.takeIf { it in available } ?: defaultProviderId
}
