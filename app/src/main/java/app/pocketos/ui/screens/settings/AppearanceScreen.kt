package app.pocketos.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.pocketos.R
import app.pocketos.data.prefs.AccentColor
import app.pocketos.data.prefs.AppLanguage
import app.pocketos.data.prefs.AppSettings
import app.pocketos.data.prefs.CalendarSystem
import app.pocketos.data.prefs.GlassIntensity
import app.pocketos.data.prefs.HapticsPreference
import app.pocketos.data.prefs.MotionPreference
import app.pocketos.data.prefs.ThemeMode
import app.pocketos.ui.LocalAppContainer
import app.pocketos.ui.design.GlassSegmentedControl
import app.pocketos.ui.design.GlassSwitchRow
import app.pocketos.ui.design.HapticType
import app.pocketos.ui.design.LocalHaptics
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.Spacing
import app.pocketos.ui.theme.accentPalette
import kotlinx.coroutines.launch

@Composable
fun AppearanceScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val settings by container.settings.settings.collectAsState(initial = container.latestSettings)
    val scope = rememberCoroutineScope()
    fun set(t: (AppSettings) -> AppSettings) { scope.launch { container.settings.update(t) } }

    SettingsPage(stringResource(R.string.settings_appearance), nav) {
        Label(stringResource(R.string.theme))
        GlassSegmentedControl(ThemeMode.entries, settings.themeMode, { m -> set { it.copy(themeMode = m) } },
            { stringResource(when (it) { ThemeMode.SYSTEM -> R.string.theme_system; ThemeMode.LIGHT -> R.string.theme_light; ThemeMode.DARK -> R.string.theme_dark }) })

        Label(stringResource(R.string.accent_color))
        AccentPicker(settings.accent) { a -> set { it.copy(accent = a) } }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Spacer(Modifier.height(Spacing.sm))
            SettingsGroup {
                GlassSwitchRow(stringResource(R.string.dynamic_color), settings.dynamicColor, { v -> set { it.copy(dynamicColor = v) } },
                    subtitle = stringResource(R.string.dynamic_color_subtitle), icon = Icons.Rounded.ColorLens)
            }
        }

        Label(stringResource(R.string.glass_effect))
        GlassSegmentedControl(GlassIntensity.entries, settings.glass, { g -> set { it.copy(glass = g) } },
            { stringResource(when (it) { GlassIntensity.SUBTLE -> R.string.glass_subtle; GlassIntensity.BALANCED -> R.string.glass_balanced; GlassIntensity.VIVID -> R.string.glass_vivid }) })
        Hint(stringResource(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) R.string.glass_hint else R.string.glass_hint_no_blur))

        Label(stringResource(R.string.motion))
        GlassSegmentedControl(MotionPreference.entries, settings.motion, { m -> set { it.copy(motion = m) } },
            { stringResource(when (it) { MotionPreference.SYSTEM -> R.string.motion_system; MotionPreference.REDUCED -> R.string.motion_reduced; MotionPreference.FULL -> R.string.motion_full }) })

        Label(stringResource(R.string.haptics))
        GlassSegmentedControl(HapticsPreference.entries, settings.haptics, { h -> set { it.copy(haptics = h) } },
            { stringResource(when (it) { HapticsPreference.ON -> R.string.haptics_on; HapticsPreference.REDUCED -> R.string.haptics_reduced; HapticsPreference.OFF -> R.string.haptics_off }) })
        Hint(stringResource(R.string.haptics_hint))

        Label(stringResource(R.string.settings_language))
        GlassSegmentedControl(AppLanguage.entries, settings.language, { l -> set { it.copy(language = l) } },
            { stringResource(when (it) { AppLanguage.SYSTEM -> R.string.language_system; AppLanguage.ENGLISH -> R.string.language_english; AppLanguage.PERSIAN -> R.string.language_persian }) })

        Label(stringResource(R.string.calendar_system))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            CalendarSystem.entries.forEach { cs ->
                app.pocketos.ui.design.GlassChip(
                    stringResource(when (cs) {
                        CalendarSystem.AUTO -> R.string.calendar_auto
                        CalendarSystem.GREGORIAN -> R.string.calendar_gregorian
                        CalendarSystem.SOLAR_HIJRI -> R.string.calendar_solar_hijri
                        CalendarSystem.LUNAR_HIJRI -> R.string.calendar_lunar_hijri
                    }),
                    settings.calendarSystem == cs,
                    { set { it.copy(calendarSystem = cs) } },
                )
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = LocalPocketColors.current.textSecondary, modifier = Modifier.padding(start = Spacing.xs, top = Spacing.xl, bottom = Spacing.sm))
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = LocalPocketColors.current.textTertiary, modifier = Modifier.padding(start = Spacing.xs, top = Spacing.sm))
}

@Composable
private fun AccentPicker(selected: AccentColor, onPick: (AccentColor) -> Unit) {
    val haptics = LocalHaptics.current
    val c = LocalPocketColors.current
    val names = mapOf(
        AccentColor.AURORA to R.string.accent_aurora, AccentColor.EMERALD to R.string.accent_emerald,
        AccentColor.VIOLET to R.string.accent_violet, AccentColor.INDIGO to R.string.accent_indigo, AccentColor.BLUE to R.string.accent_blue,
        AccentColor.TEAL to R.string.accent_teal, AccentColor.ROSE to R.string.accent_rose, AccentColor.AMBER to R.string.accent_amber,
    )
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        AccentColor.entries.forEach { accent ->
            val palette = accentPalette(accent)
            val isSelected = accent == selected
            val label = stringResource(names.getValue(accent))
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(palette.primary, palette.gradientEnd)))
                    .then(if (isSelected) Modifier.border(3.dp, c.textPrimary, CircleShape) else Modifier)
                    .selectable(isSelected, role = Role.RadioButton, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        haptics.perform(HapticType.Selection)
                        onPick(accent)
                    }
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) Icon(Icons.Rounded.Check, null, tint = Color.White)
            }
        }
    }
}
