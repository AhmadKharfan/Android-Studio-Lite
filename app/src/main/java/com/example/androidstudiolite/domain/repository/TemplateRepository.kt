package com.example.androidstudiolite.domain.repository

import com.example.androidstudiolite.domain.model.ProjectTemplate

interface TemplateRepository {
    suspend fun getTemplates(): List<ProjectTemplate>
}
