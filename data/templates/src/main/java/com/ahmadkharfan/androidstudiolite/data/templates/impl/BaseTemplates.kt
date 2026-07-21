package com.ahmadkharfan.androidstudiolite.data.templates.impl

import com.ahmadkharfan.androidstudiolite.data.templates.Catalog
import com.ahmadkharfan.androidstudiolite.data.templates.ProjectRecipe
import com.ahmadkharfan.androidstudiolite.data.templates.Template
import com.ahmadkharfan.androidstudiolite.data.templates.TemplateContent
import com.ahmadkharfan.androidstudiolite.data.templates.TemplateMetadata
import com.ahmadkharfan.androidstudiolite.domain.model.NewProjectSpec
import com.ahmadkharfan.androidstudiolite.domain.model.TemplateLanguage

object NoActivityTemplate : Template {

    override val metadata = TemplateMetadata(
        id = "no-activity",
        name = "No Activity",
        description = "An empty app module. Add your own entry point.",
        thumbnail = "template_no_activity",
        tags = listOf("Views"),
    )

    override fun assemble(spec: NewProjectSpec, recipe: ProjectRecipe) {
        recipe.plugin(Catalog.androidApplication)
        if (spec.language == TemplateLanguage.KOTLIN) {
            recipe.plugin(Catalog.kotlinAndroid)
            recipe.implementation(Catalog.coreKtx)
        }
        recipe.implementation(Catalog.appcompat)
        recipe.implementation(Catalog.material)
        TemplateContent.addStandardTestDeps(recipe)

        recipe.file(
            "app/src/main/AndroidManifest.xml",
            TemplateContent.manifest(spec, activityName = null),
        )
        recipe.file("app/src/main/res/values/strings.xml", TemplateContent.stringsXml(spec))
        recipe.file("app/src/main/res/values/themes.xml", TemplateContent.viewsThemesXml(spec))
    }
}
