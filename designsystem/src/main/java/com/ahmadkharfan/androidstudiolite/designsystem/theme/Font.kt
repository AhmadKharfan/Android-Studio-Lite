package com.ahmadkharfan.androidstudiolite.designsystem.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.ahmadkharfan.androidstudiolite.designsystem.R

/**
 * JetBrains Sans is not freely redistributable; Inter is loaded as its metrically-compatible
 * stand-in (same approach the source design system uses). JetBrains Mono is OFL and used as-is.
 * Both are bundled as variable fonts (res/font); [FontVariation] selects the weight instance.
 */
@OptIn(ExperimentalTextApi::class)
private fun variableFont(resId: Int, weight: FontWeight, style: FontStyle = FontStyle.Normal) = Font(
    resId = resId,
    weight = weight,
    style = style,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val AslSansFontFamily = FontFamily(
    variableFont(R.font.inter, FontWeight.Normal),
    variableFont(R.font.inter, FontWeight.Medium),
    variableFont(R.font.inter, FontWeight.SemiBold),
    variableFont(R.font.inter, FontWeight.Bold),
)

val AslMonoFontFamily = FontFamily(
    variableFont(R.font.jetbrains_mono, FontWeight.Normal),
    variableFont(R.font.jetbrains_mono, FontWeight.Medium),
    variableFont(R.font.jetbrains_mono, FontWeight.SemiBold),
    variableFont(R.font.jetbrains_mono, FontWeight.Bold),
    variableFont(R.font.jetbrains_mono_italic, FontWeight.Normal, FontStyle.Italic),
)
