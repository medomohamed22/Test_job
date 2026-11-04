package com.tripmate.ai.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tripmate.ai.domain.model.Interest
import com.tripmate.ai.domain.model.Pace
import com.tripmate.ai.domain.model.WalkingLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserPreferences(
    val onboardingDone: Boolean = false,
    val language: String = "system",
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val currency: String = "EGP",
    val interests: Set<Interest> = setOf(Interest.HISTORY, Interest.FOOD),
    val pace: Pace = Pace.BALANCED,
    val walking: WalkingLevel = WalkingLevel.MODERATE,
    val dailyBudget: Double = 2500.0,
    val notifications: Boolean = false,
    val reducedMotion: Boolean = false,
    val cloudAiConsent: Boolean = false,
    val diagnosticsEnabled: Boolean = true,
    val sampleSeeded: Boolean = false,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tripmate_prefs")

@Singleton
class UserPreferencesRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private object K {
        val onboarding = booleanPreferencesKey("onboarding_done")
        val language = stringPreferencesKey("language")
        val theme = stringPreferencesKey("theme")
        val currency = stringPreferencesKey("currency")
        val interests = stringPreferencesKey("interests")
        val pace = stringPreferencesKey("pace")
        val walking = stringPreferencesKey("walking")
        val dailyBudget = doublePreferencesKey("daily_budget")
        val notifications = booleanPreferencesKey("notifications")
        val reducedMotion = booleanPreferencesKey("reduced_motion")
        val cloudConsent = booleanPreferencesKey("cloud_ai_consent")
        val diagnostics = booleanPreferencesKey("diagnostics")
        val sampleSeeded = booleanPreferencesKey("sample_seeded")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            UserPreferences(
                onboardingDone = p[K.onboarding] ?: false,
                language = p[K.language] ?: "system",
                theme = runCatching { ThemeMode.valueOf(p[K.theme] ?: "") }.getOrDefault(ThemeMode.SYSTEM),
                currency = p[K.currency] ?: "EGP",
                interests = p[K.interests]?.split(',')?.mapNotNull { runCatching { Interest.valueOf(it) }.getOrNull() }?.toSet()
                    ?: setOf(Interest.HISTORY, Interest.FOOD),
                pace = runCatching { Pace.valueOf(p[K.pace] ?: "") }.getOrDefault(Pace.BALANCED),
                walking = runCatching { WalkingLevel.valueOf(p[K.walking] ?: "") }.getOrDefault(WalkingLevel.MODERATE),
                dailyBudget = p[K.dailyBudget] ?: 2500.0,
                notifications = p[K.notifications] ?: false,
                reducedMotion = p[K.reducedMotion] ?: false,
                cloudAiConsent = p[K.cloudConsent] ?: false,
                diagnosticsEnabled = p[K.diagnostics] ?: true,
                sampleSeeded = p[K.sampleSeeded] ?: false,
            )
        }

    suspend fun current(): UserPreferences = preferences.first()

    suspend fun completeOnboarding(interests: Set<Interest>, pace: Pace, walking: WalkingLevel, dailyBudget: Double, currency: String) {
        context.dataStore.edit {
            it[K.onboarding] = true
            it[K.interests] = interests.joinToString(",") { i -> i.name }
            it[K.pace] = pace.name
            it[K.walking] = walking.name
            it[K.dailyBudget] = dailyBudget.coerceAtLeast(0.0)
            it[K.currency] = currency
        }
    }
    suspend fun setLanguage(tag: String) = context.dataStore.edit { it[K.language] = tag }
    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[K.theme] = mode.name }
    suspend fun setCurrency(code: String) = context.dataStore.edit { it[K.currency] = code }
    suspend fun setNotifications(on: Boolean) = context.dataStore.edit { it[K.notifications] = on }
    suspend fun setReducedMotion(on: Boolean) = context.dataStore.edit { it[K.reducedMotion] = on }
    suspend fun setCloudConsent(on: Boolean) = context.dataStore.edit { it[K.cloudConsent] = on }
    suspend fun setDiagnostics(on: Boolean) = context.dataStore.edit { it[K.diagnostics] = on }
    suspend fun setWalking(level: WalkingLevel) = context.dataStore.edit { it[K.walking] = level.name }
    suspend fun markSampleSeeded() = context.dataStore.edit { it[K.sampleSeeded] = true }
    suspend fun clearAll() = context.dataStore.edit { it.clear() }
}
