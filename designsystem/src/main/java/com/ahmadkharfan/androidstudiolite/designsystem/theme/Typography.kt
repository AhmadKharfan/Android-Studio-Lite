package com.ahmadkharfan.androidstudiolite.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

object AslLetterSpacing {
    val label = 0.01f.em
    val overline = 0.08f.em
}

val AslTypography = Typography(
    displayLarge = TextStyle(fontFamily = AslSansFontFamily, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
    displayMedium = TextStyle(fontFamily = AslSansFontFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    displaySmall = TextStyle(fontFamily = AslSansFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),

    headlineLarge = TextStyle(fontFamily = AslSansFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    headlineMedium = TextStyle(fontFamily = AslSansFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    headlineSmall = TextStyle(fontFamily = AslSansFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),

    titleLarge = TextStyle(fontFamily = AslSansFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleMedium = TextStyle(fontFamily = AslSansFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontFamily = AslSansFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),

    bodyLarge = TextStyle(fontFamily = AslSansFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = AslSansFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = AslSansFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),

    labelLarge = TextStyle(fontFamily = AslSansFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = AslLetterSpacing.label),
    labelMedium = TextStyle(fontFamily = AslSansFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = AslLetterSpacing.label),
    labelSmall = TextStyle(fontFamily = AslSansFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = AslLetterSpacing.label),
)

data class AslCodeTypography(
    val codeBody: TextStyle,
    val codeSmall: TextStyle,
    val codeTiny: TextStyle,
)

val AslCode = AslCodeTypography(
    codeBody = TextStyle(fontFamily = AslMonoFontFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 20.sp),
    codeSmall = TextStyle(fontFamily = AslMonoFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    codeTiny = TextStyle(fontFamily = AslMonoFontFamily, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp),
)
