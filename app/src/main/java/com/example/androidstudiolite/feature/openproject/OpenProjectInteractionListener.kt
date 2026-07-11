package com.example.androidstudiolite.feature.openproject

interface OpenProjectInteractionListener {
    fun onQueryChanged(query: String)
    fun onSelectProject(id: String)
}
