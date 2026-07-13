package com.ahmadkharfan.androidstudiolite.feature.createproject

interface CreateProjectInteractionListener {
    fun onSelectTemplate(id: String)
    fun onNextStep()
    fun onBackStep()
    fun onNameChanged(name: String)
    fun onPackageChanged(packageName: String)
    fun onLocationChanged(location: String)
    fun onMinSdkChanged(minSdk: String)
    fun onTargetSdkChanged(targetSdk: String)
    fun onLanguageChanged(language: String)
    fun onBuildDslChanged(dsl: String)
    fun onToggleCpp(enabled: Boolean)
    fun onCreateProject()
}
