package com.ahmadkharfan.androidstudiolite.feature.projects.openproject

interface OpenProjectInteractionListener {
    fun onQueryChanged(query: String)
    fun onSelectProject(id: String)
}
