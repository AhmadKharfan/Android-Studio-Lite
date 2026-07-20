package com.ahmadkharfan.androidstudiolite.data.templates

import com.ahmadkharfan.androidstudiolite.data.templates.impl.BasicViewsTemplate
import com.ahmadkharfan.androidstudiolite.data.templates.impl.BottomNavigationTemplate
import com.ahmadkharfan.androidstudiolite.data.templates.impl.EmptyComposeTemplate
import com.ahmadkharfan.androidstudiolite.data.templates.impl.EmptyViewsTemplate
import com.ahmadkharfan.androidstudiolite.data.templates.impl.NavDrawerTemplate
import com.ahmadkharfan.androidstudiolite.data.templates.impl.NoActivityTemplate
import com.ahmadkharfan.androidstudiolite.data.templates.impl.ResponsiveTemplate

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
        /**
         * Picker order and names, matching android-code-studio's wizard, so anyone moving between the
         * two IDEs finds each template where they expect it.
         *
         * Its list also has Game Activity and Native C++, which ASL doesn't ship: both need the NDK
         * and CMake, and the build worker's image installs neither (`worker/Dockerfile` pulls
         * platform-tools, platforms and build-tools only), so every native build fails at
         * configuration with "Failed to install the following SDK components: ndk;…". Rather than
         * offer templates that can't build, they're left out until the worker grows a native
         * toolchain — see tools/template_build_check.py, which is what proves this either way.
         */
        val DEFAULT: List<Template> = listOf(
            NoActivityTemplate,
            BasicViewsTemplate,
            EmptyViewsTemplate,
            EmptyComposeTemplate,
            BottomNavigationTemplate,
            NavDrawerTemplate,
            ResponsiveTemplate,
        )
    }
}
