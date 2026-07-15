package com.ahmadkharfan.androidstudiolite.feature.createproject.components

import com.ahmadkharfan.androidstudiolite.R

/**
 * Resolves a template's [com.ahmadkharfan.androidstudiolite.domain.model.ProjectTemplate.thumbnail]
 * key to its artwork. The mapping lives here, in the UI layer, so the template registry stays free of
 * Android resource ids.
 *
 * Returns null for an unknown key; the card then falls back to a generic glyph rather than crashing
 * or showing an empty tile.
 */
internal fun templateThumbnailRes(key: String): Int? = when (key) {
    "template_no_activity" -> R.drawable.template_no_activity
    "template_empty_activity" -> R.drawable.template_empty_activity
    "template_cpp" -> R.drawable.template_cpp
    "template_basic_activity" -> R.drawable.template_basic_activity
    "template_nav_drawer" -> R.drawable.template_nav_drawer
    "template_bottom_nav" -> R.drawable.template_bottom_nav
    "template_tabbed" -> R.drawable.template_tabbed
    "template_no_androidx" -> R.drawable.template_no_androidx
    "template_compose" -> R.drawable.template_compose
    else -> null
}
