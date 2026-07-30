package com.ahmadkharfan.androidstudiolite.feature.onboarding.di

import com.ahmadkharfan.androidstudiolite.domain.repository.OnboardingRepository
import com.ahmadkharfan.androidstudiolite.feature.onboarding.OnboardingFeatureApiImpl
import com.ahmadkharfan.androidstudiolite.feature.onboarding.api.OnboardingFeatureApi
import com.ahmadkharfan.androidstudiolite.feature.onboarding.complete.CompleteViewModel
import com.ahmadkharfan.androidstudiolite.feature.onboarding.data.AndroidOnboardingRepository
import com.ahmadkharfan.androidstudiolite.feature.onboarding.permissions.PermissionsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Everything :feature:onboarding contributes to the graph, declared by the feature that owns it. */
val onboardingModule = module {
    // The feature binds its own contract, so :app never names the implementation.
    single<OnboardingFeatureApi> { OnboardingFeatureApiImpl() }
    single<OnboardingRepository> { AndroidOnboardingRepository(androidContext()) }
    viewModelOf(::PermissionsViewModel)
    viewModelOf(::CompleteViewModel)
}
