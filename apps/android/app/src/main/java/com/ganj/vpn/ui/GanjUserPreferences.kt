package com.ganj.vpn.ui

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

enum class GanjThemePreference {
    SYSTEM,
    LIGHT,
    DARK,
}

@Immutable
data class GanjUserPreferences(
    val theme: GanjThemePreference = GanjThemePreference.SYSTEM,
    val reduceMotion: Boolean = false,
    val reduceTransparency: Boolean = false,
    val onboardingCompleted: Boolean = false,
)

internal class AndroidGanjUserPreferencesStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun restore(): GanjUserPreferences = GanjUserPreferences(
        theme = GanjThemePreference.entries.firstOrNull {
            it.name == preferences.getString(KEY_THEME, GanjThemePreference.SYSTEM.name)
        } ?: GanjThemePreference.SYSTEM,
        reduceMotion = preferences.getBoolean(KEY_REDUCE_MOTION, false),
        reduceTransparency = preferences.getBoolean(KEY_REDUCE_TRANSPARENCY, false),
        onboardingCompleted = preferences.getBoolean(KEY_ONBOARDING_COMPLETED, false),
    )

    fun save(value: GanjUserPreferences): Boolean = preferences.edit()
        .putString(KEY_THEME, value.theme.name)
        .putBoolean(KEY_REDUCE_MOTION, value.reduceMotion)
        .putBoolean(KEY_REDUCE_TRANSPARENCY, value.reduceTransparency)
        .putBoolean(KEY_ONBOARDING_COMPLETED, value.onboardingCompleted)
        .commit()

    private companion object {
        const val PREFS_NAME = "ganj_user_preferences_v1"
        const val KEY_THEME = "theme"
        const val KEY_REDUCE_MOTION = "reduce_motion"
        const val KEY_REDUCE_TRANSPARENCY = "reduce_transparency"
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    }
}

internal object GanjUserPreferencesPolicy {
    fun resolveDarkTheme(
        preference: GanjThemePreference,
        systemDark: Boolean,
    ): Boolean = when (preference) {
        GanjThemePreference.SYSTEM -> systemDark
        GanjThemePreference.LIGHT -> false
        GanjThemePreference.DARK -> true
    }

    fun applyVisualOverrides(
        base: GanjVisualEffectsPolicy,
        preferences: GanjUserPreferences,
    ): GanjVisualEffectsPolicy {
        val reduceMotion = base.reduceMotion || preferences.reduceMotion
        val reduceTransparency = base.reduceTransparency || preferences.reduceTransparency
        return base.copy(
            reduceMotion = reduceMotion,
            reduceTransparency = reduceTransparency,
            ambientBackgroundEffects = base.ambientBackgroundEffects && !reduceTransparency,
        )
    }
}

@Composable
internal fun resolvedGanjDarkTheme(preferences: GanjUserPreferences): Boolean =
    GanjUserPreferencesPolicy.resolveDarkTheme(
        preference = preferences.theme,
        systemDark = isSystemInDarkTheme(),
    )

@Composable
internal fun resolvedGanjVisualEffectsPolicy(
    preferences: GanjUserPreferences,
): GanjVisualEffectsPolicy = GanjUserPreferencesPolicy.applyVisualOverrides(
    base = currentGanjVisualEffectsPolicy(),
    preferences = preferences,
)
