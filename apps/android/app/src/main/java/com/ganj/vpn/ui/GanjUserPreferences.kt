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
    val theme: GanjThemePreference = GanjThemePreference.DARK,
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
        theme = GanjUserPreferencesPolicy.restoreReferenceTheme(
            preferences.getString(KEY_THEME, null),
            preferences.getBoolean(KEY_REFERENCE_APPEARANCE, false),
        ),
        reduceMotion = preferences.getBoolean(KEY_REDUCE_MOTION, false),
        reduceTransparency = preferences.getBoolean(KEY_REDUCE_TRANSPARENCY, false),
        onboardingCompleted = preferences.getBoolean(KEY_ONBOARDING_COMPLETED, false),
    )

    fun save(value: GanjUserPreferences): Boolean = preferences.edit()
        .putBoolean(KEY_REFERENCE_APPEARANCE, true)
        .putString(KEY_THEME, value.theme.name)
        .putBoolean(KEY_REDUCE_MOTION, value.reduceMotion)
        .putBoolean(KEY_REDUCE_TRANSPARENCY, value.reduceTransparency)
        .putBoolean(KEY_ONBOARDING_COMPLETED, value.onboardingCompleted)
        .commit()

    private companion object {
        const val PREFS_NAME = "ganj_user_preferences_v1"
        const val KEY_THEME = "theme"
        const val KEY_REFERENCE_APPEARANCE = "reference_appearance_0321"
        const val KEY_REDUCE_MOTION = "reduce_motion"
        const val KEY_REDUCE_TRANSPARENCY = "reduce_transparency"
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    }
}

internal object GanjUserPreferencesPolicy {
    // Product-directed appearance refresh. Existing installs enter the approved dark UI once;
    // subsequent explicit Light/System choices remain supported. No other preference is reset.
    fun restoreReferenceTheme(stored: String?, referenceApplied: Boolean): GanjThemePreference =
        if (!referenceApplied) GanjThemePreference.DARK else
            GanjThemePreference.entries.firstOrNull { it.name == stored } ?: GanjThemePreference.DARK

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