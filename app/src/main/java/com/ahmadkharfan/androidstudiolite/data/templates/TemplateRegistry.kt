package com.ahmadkharfan.androidstudiolite.data.templates

import com.ahmadkharfan.androidstudiolite.data.templates.impl.BasicViewsTemplate
import com.ahmadkharfan.androidstudiolite.data.templates.impl.BottomNavigationTemplate
import com.ahmadkharfan.androidstudiolite.data.templates.impl.EmptyComposeTemplate
import com.ahmadkharfan.androidstudiolite.data.templates.impl.EmptyViewsTemplate
import com.ahmadkharfan.androidstudiolite.data.templates.impl.NativeCppTemplate
import com.ahmadkharfan.androidstudiolite.data.templates.impl.NavDrawerTemplate
import com.ahmadkharfan.androidstudiolite.data.templates.impl.NoActivityTemplate
import com.ahmadkharfan.androidstudiolite.data.templates.impl.NoAndroidXTemplate
import com.ahmadkharfan.androidstudiolite.data.templates.impl.TabbedTemplate

/**
 * The ordered set of templates ASL ships. This is the single place both the picker (via
 * [com.ahmadkharfan.androidstudiolite.data.templates.RealTemplateRepository]) and the generator
 * ([ProjectTemplateEngine]) look them up, so they can never drift apart.
 */
class TemplateRegistry(
    val templates: List<Template> = DEFAULT,
) {
    private val byId: Map<String, Template> = templates.associateBy { it.metadata.id }

    fun find(id: String): Template? = byId[id]

    companion object {
        val DEFAULT: List<Template> = listOf(
            NoActivityTemplate,
            EmptyViewsTemplate,
            BasicViewsTemplate,
            BottomNavigationTemplate,
            TabbedTemplate,
            NavDrawerTemplate,
            EmptyComposeTemplate,
            NoAndroidXTemplate,
            NativeCppTemplate,
        )
    }
}
