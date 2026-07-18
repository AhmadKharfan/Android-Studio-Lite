package com.ahmadkharfan.androidstudiolite.feature.settings.general

sealed interface GeneralEffect {
    /** Activity must recreate so [com.ahmadkharfan.androidstudiolite.core.locale.AppLocale] takes effect. */
    data object RecreateForLocale : GeneralEffect
}
