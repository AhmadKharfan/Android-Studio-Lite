package com.example.androidstudiolite.feature.createproject

interface CreateProjectInteractionListener {
    fun onSelectTemplate(id: String)
    fun onNextStep()
    fun onBackStep()
    fun onNameChanged(name: String)
    fun onPackageChanged(packageName: String)
    fun onLocationChanged(location: String)
    fun onMinSdkChanged(minSdk: String)
    fun onCreateProject()
}
