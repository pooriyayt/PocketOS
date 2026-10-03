package app.pocketos.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class AccentColor { AURORA, EMERALD, VIOLET, INDIGO, BLUE, TEAL, ROSE, AMBER }
enum class MotionPreference { SYSTEM, REDUCED, FULL }
enum class HapticsPreference { ON, REDUCED, OFF }
enum class GlassIntensity { SUBTLE, BALANCED, VIVID }
enum class AppLanguage(val tag: String?) { SYSTEM(null), ENGLISH("en"), PERSIAN("fa") }
enum class CalendarSystem {
    AUTO, GREGORIAN, SOLAR_HIJRI, LUNAR_HIJRI;

    /** The concrete calendar to use; AUTO follows the app language (Persian -> Solar Hijri). */
    fun resolve(language: String): app.pocketos.core.time.CalendarKind = when (this) {
        AUTO -> if (language == "fa") app.pocketos.core.time.CalendarKind.SOLAR_HIJRI else app.pocketos.core.time.CalendarKind.GREGORIAN
        GREGORIAN -> app.pocketos.core.time.CalendarKind.GREGORIAN
        SOLAR_HIJRI -> app.pocketos.core.time.CalendarKind.SOLAR_HIJRI
        LUNAR_HIJRI -> app.pocketos.core.time.CalendarKind.LUNAR_HIJRI
    }
}
enum class OnboardingFocus { REMINDERS, TASKS, SUBSCRIPTIONS, ORGANIZATION, EXPENSES, ALL }

enum class DashboardSection { ATTENTION, OVERVIEW, TIMELINE, RENEWALS, SUGGESTIONS, QUICK_ACTIONS }

/**
 * Device-level, non-sensitive preferences (DataStore).
 * PocketOS is completely Local-First and Privacy-First: No cloud accounts or tracking.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentColor = AccentColor.AURORA,
    val dynamicColor: Boolean = false,
    val motion: MotionPreference = MotionPreference.SYSTEM,
    val haptics: HapticsPreference = HapticsPreference.ON,
    val glass: GlassIntensity = GlassIntensity.BALANCED,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val calendarSystem: CalendarSystem = CalendarSystem.AUTO,
    val onboardingCompleted: Boolean = false,
    val primaryFocus: OnboardingFocus = OnboardingFocus.ALL,
    val guidedTourDismissed: Boolean = false,
    val firstRunHintDismissed: Boolean = false,
    val displayName: String = "",
    val defaultCurrency: String = "USD",
    val reminderDefaultTime: LocalTime = LocalTime.of(9, 0),
    val renewalReminderTime: LocalTime = LocalTime.of(9, 0),
    val snoozeMinutes: Int = 10,
    val notificationsShowSensitive: Boolean = false,
    val widgetHideAmounts: Boolean = false,
    val widgetHideTitles: Boolean = false,
    val appLockEnabled: Boolean = false,
    val biometricUnlock: Boolean = false,
    /** Seconds in background before the app relocks; 0 = immediately. */
    val relockAfterSeconds: Int = 0,
    val hideInRecents: Boolean = true,
    val autoCheckUpdates: Boolean = true,
    val dashboardOrder: List<DashboardSection> = DashboardSection.entries.toList(),
    val dashboardHidden: Set<DashboardSection> = emptySet(),
    val exactAlarmPromptDismissed: Boolean = false,
)

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.settingsStore)

    private object K {
        val theme = stringPreferencesKey("theme")
        // v2: the Aurora redesign resets everyone to the new signature accent once.
        val accent = stringPreferencesKey("accent_v2")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val motion = stringPreferencesKey("motion")
        val haptics = stringPreferencesKey("haptics")
        val glass = stringPreferencesKey("glass")
        val language = stringPreferencesKey("language")
        val calendar = stringPreferencesKey("calendar")
        val onboarding = booleanPreferencesKey("onboarding_done")
        val focus = stringPreferencesKey("onboarding_focus")
        val tourDismissed = booleanPreferencesKey("guided_tour_dismissed")
        val firstRunHint = booleanPreferencesKey("first_run_hint_dismissed")
        val displayName = stringPreferencesKey("display_name")
        val currency = stringPreferencesKey("default_currency")
        val reminderTime = stringPreferencesKey("reminder_default_time")
        val renewalTime = stringPreferencesKey("renewal_reminder_time")
        val snooze = intPreferencesKey("snooze_minutes")
        val notifSensitive = booleanPreferencesKey("notifications_sensitive")
        val widgetAmounts = booleanPreferencesKey("widget_hide_amounts")
        val widgetTitles = booleanPreferencesKey("widget_hide_titles")
        val lock = booleanPreferencesKey("app_lock")
        val biometric = booleanPreferencesKey("biometric_unlock")
        val relock = intPreferencesKey("relock_after")
        val hideRecents = booleanPreferencesKey("hide_in_recents")
        val autoUpdates = booleanPreferencesKey("auto_check_updates")
        val dashOrder = stringPreferencesKey("dashboard_order")
        val dashHidden = stringPreferencesKey("dashboard_hidden")
        val exactPrompt = booleanPreferencesKey("exact_alarm_prompt_dismissed")
    }

    val settings: Flow<AppSettings> = store.data.map { p -> p.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    private inline fun <reified E : Enum<E>> Preferences.enum(key: Preferences.Key<String>, default: E): E =
        this[key]?.let { v -> runCatching { enumValueOf<E>(v) }.getOrNull() } ?: default

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            themeMode = enum(K.theme, d.themeMode),
            accent = enum(K.accent, d.accent),
            dynamicColor = this[K.dynamic] ?: d.dynamicColor,
            motion = enum(K.motion, d.motion),
            haptics = enum(K.haptics, d.haptics),
            glass = enum(K.glass, d.glass),
            language = enum(K.language, d.language),
            calendarSystem = enum(K.calendar, d.calendarSystem),
            onboardingCompleted = this[K.onboarding] ?: d.onboardingCompleted,
            primaryFocus = enum(K.focus, d.primaryFocus),
            guidedTourDismissed = this[K.tourDismissed] ?: d.guidedTourDismissed,
            firstRunHintDismissed = this[K.firstRunHint] ?: d.firstRunHintDismissed,
            displayName = this[K.displayName] ?: d.displayName,
            defaultCurrency = this[K.currency] ?: d.defaultCurrency,
            reminderDefaultTime = this[K.reminderTime]?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: d.reminderDefaultTime,
            renewalReminderTime = this[K.renewalTime]?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: d.renewalReminderTime,
            snoozeMinutes = this[K.snooze] ?: d.snoozeMinutes,
            notificationsShowSensitive = this[K.notifSensitive] ?: d.notificationsShowSensitive,
            widgetHideAmounts = this[K.widgetAmounts] ?: d.widgetHideAmounts,
            widgetHideTitles = this[K.widgetTitles] ?: d.widgetHideTitles,
            appLockEnabled = this[K.lock] ?: d.appLockEnabled,
            biometricUnlock = this[K.biometric] ?: d.biometricUnlock,
            relockAfterSeconds = this[K.relock] ?: d.relockAfterSeconds,
            hideInRecents = this[K.hideRecents] ?: d.hideInRecents,
            autoCheckUpdates = this[K.autoUpdates] ?: d.autoCheckUpdates,
            dashboardOrder = this[K.dashOrder]?.let(::parseSections)?.let(::completeOrder) ?: d.dashboardOrder,
            dashboardHidden = this[K.dashHidden]?.let(::parseSections)?.toSet() ?: d.dashboardHidden,
            exactAlarmPromptDismissed = this[K.exactPrompt] ?: d.exactAlarmPromptDismissed,
        )
    }

    private fun parseSections(text: String): List<DashboardSection> =
        text.split(',').mapNotNull { runCatching { DashboardSection.valueOf(it) }.getOrNull() }

    private fun completeOrder(order: List<DashboardSection>): List<DashboardSection> =
        (order + DashboardSection.entries).distinct()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { p ->
            val old = p.toSettings()
            val n = transform(old)
            p[K.theme] = n.themeMode.name
            p[K.accent] = n.accent.name
            p[K.dynamic] = n.dynamicColor
            p[K.motion] = n.motion.name
            p[K.haptics] = n.haptics.name
            p[K.glass] = n.glass.name
            p[K.language] = n.language.name
            p[K.calendar] = n.calendarSystem.name
            p[K.onboarding] = n.onboardingCompleted
            p[K.focus] = n.primaryFocus.name
            p[K.tourDismissed] = n.guidedTourDismissed
            p[K.firstRunHint] = n.firstRunHintDismissed
            p[K.displayName] = n.displayName.take(80)
            p[K.currency] = n.defaultCurrency
            p[K.reminderTime] = n.reminderDefaultTime.toString()
            p[K.renewalTime] = n.renewalReminderTime.toString()
            p[K.snooze] = n.snoozeMinutes.coerceIn(1, 24 * 60)
            p[K.notifSensitive] = n.notificationsShowSensitive
            p[K.widgetAmounts] = n.widgetHideAmounts
            p[K.widgetTitles] = n.widgetHideTitles
            p[K.lock] = n.appLockEnabled
            p[K.biometric] = n.biometricUnlock
            p[K.relock] = n.relockAfterSeconds.coerceIn(0, 3600)
            p[K.hideRecents] = n.hideInRecents
            p[K.autoUpdates] = n.autoCheckUpdates
            p[K.dashOrder] = n.dashboardOrder.joinToString(",") { it.name }
            p[K.dashHidden] = n.dashboardHidden.joinToString(",") { it.name }
            p[K.exactPrompt] = n.exactAlarmPromptDismissed
        }
    }
}
