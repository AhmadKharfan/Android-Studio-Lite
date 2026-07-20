package com.ahmadkharfan.androidstudiolite.feature.createproject.components

import com.ahmadkharfan.androidstudiolite.feature.projects.R

internal fun templateThumbnailRes(key: String): Int? = when (key) {
    "template_no_activity" -> R.drawable.template_no_activity
    "template_empty_activity" -> R.drawable.template_empty_activity
    "template_basic_activity" -> R.drawable.template_basic_activity
    "template_nav_drawer" -> R.drawable.template_nav_drawer
    "template_bottom_nav" -> R.drawable.template_bottom_nav
    "template_compose" -> R.drawable.template_compose
    else -> null
}
