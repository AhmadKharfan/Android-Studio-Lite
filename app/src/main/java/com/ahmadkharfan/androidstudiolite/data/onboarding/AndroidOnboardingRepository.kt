package com.ahmadkharfan.androidstudiolite.data.onboarding

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.ahmadkharfan.androidstudiolite.core.permissions.AslPermissions
import com.ahmadkharfan.androidstudiolite.domain.model.OnboardingPermission
import com.ahmadkharfan.androidstudiolite.domain.model.OnboardingState
import com.ahmadkharfan.androidstudiolite.domain.repository.OnboardingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding")

private val KEY_SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
private val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")

/**
 * Real [OnboardingRepository]: permission state is read live from the OS on every [refreshPermissions]
 * call (never cached — the user can flip a permission off from Settings mid-session), and the
 * setup/onboarding-complete flags persist across process death via DataStore so onboarding doesn't
 * re-run every launch once finished.
 */
class AndroidOnboardingRepository(private val context: Context) : OnboardingRepository {

    /** Bumped by [refreshPermissions] to force a re-read of live OS permission state. */
    private val refreshTrigger = MutableStateFlow(0)

    override fun observeState(): Flow<OnboardingState> =
        combine(refreshTrigger, context.onboardingDataStore.data) { _, prefs ->
            OnboardingState(
                permissions = currentPermissions(),
                setupComplete = prefs[KEY_SETUP_COMPLETE] ?: false,
                onboardingComplete = prefs[KEY_ONBOARDING_COMPLETE] ?: false,
            )
        }

    override suspend fun refreshPermissions() {
        refreshTrigger.value = refreshTrigger.value + 1
    }

    override suspend fun markSetupComplete() {
        context.onboardingDataStore.edit { it[KEY_SETUP_COMPLETE] = true }
    }

    override suspend fun completeOnboarding() {
        context.onboardingDataStore.edit { it[KEY_ONBOARDING_COMPLETE] = true }
    }

    private fun currentPermissions(): List<OnboardingPermission> =
        AslPermissions.descriptors().map { descriptor ->
            OnboardingPermission(
                id = descriptor.id.domainId,
                title = descriptor.title,
                reason = descriptor.reason,
                granted = AslPermissions.isGranted(context, descriptor.id),
                optional = descriptor.optional,
            )
        }
}
