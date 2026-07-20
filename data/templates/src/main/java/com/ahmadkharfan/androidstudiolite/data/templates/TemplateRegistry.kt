package com.ahmadkharfan.androidstudiolite.data.templates

import com.ahmadkharfan.androidstudiolite.data.templates.impl.BasicViewsTemplate
import com.ahmadkharfan.androidstudiolite.data.templates.impl.BottomNavigationTemplate
import com.ahmadkharfan.androidstudiolite.data.templates.impl.EmptyComposeTemplate
import com.ahmadkharfan.androidstudiolite.data.templates.impl.EmptyViewsTemplate
import com.ahmadkharfan.androidstudiolite.data.templates.impl.NavDrawerTemplate
import com.ahmadkharfan.androidstudiolite.data.templates.impl.NoActivityTemplate

class TemplateRegistry(
    val templates: List<Template> = DEFAULT,
) {
    private val byId: Map<String, Template> = templates.associateBy { it.metadata.id }

    fun find(id: String): Template? = byId[id]

    companion object {
        val DEFAULT: List<Template> = listOf(
            NoActivityTemplate,
            BasicViewsTemplate,
            EmptyViewsTemplate,
            EmptyComposeTemplate,
            BottomNavigationTemplate,
            NavDrawerTemplate,
        )
    }
}
