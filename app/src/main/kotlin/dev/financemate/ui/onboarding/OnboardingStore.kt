package dev.financemate.ui.onboarding

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Remembers whether the welcome screens have been seen.
 *
 * Deliberately **not** in the encrypted ledger. Opening that database means
 * unsealing a Keystore key, and this flag is read on the very first frame to
 * decide which screen to show — gating startup on the Keystore to answer "has
 * this person been here before" would be slow for no benefit. There is also
 * nothing private about the answer.
 */
public class OnboardingStore(private val context: Context) {

    public val hasCompleted: Flow<Boolean> =
        context.onboardingDataStore.data.map { it[COMPLETED] == true }

    public suspend fun markCompleted() {
        context.onboardingDataStore.edit { it[COMPLETED] = true }
    }

    /** Lets the user run the introduction again from settings, later. */
    public suspend fun reset() {
        context.onboardingDataStore.edit { it.remove(COMPLETED) }
    }

    private companion object {
        val COMPLETED = booleanPreferencesKey("onboarding_completed")
    }
}

private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding")
