package com.ahmadkharfan.androidstudiolite.domain.repository

import com.ahmadkharfan.androidstudiolite.domain.model.ProjectTemplate

interface TemplateRepository {
    suspend fun getTemplates(): List<ProjectTemplate>
}
