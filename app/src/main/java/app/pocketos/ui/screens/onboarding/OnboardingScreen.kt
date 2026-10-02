package app.pocketos.ui.screens.onboarding

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import app.pocketos.R
import app.pocketos.data.prefs.AppLanguage
import app.pocketos.data.prefs.OnboardingFocus
import app.pocketos.data.prefs.ThemeMode
import app.pocketos.ui.LocalAppContainer
import app.pocketos.ui.design.ButtonStyle
import app.pocketos.ui.design.GlassCard
import app.pocketos.ui.design.GlassLevel
import app.pocketos.ui.design.HapticType
import app.pocketos.ui.design.LocalHaptics
import app.pocketos.ui.design.LocalMotion
import app.pocketos.ui.design.PocketButton
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.Shapes
import app.pocketos.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Smart, 3-step Privacy-First Onboarding:
 * 1. Language preference (English / فارسی).
 * 2. Theme & appearance preference (Dark / Light / System).
 * 3. Primary focus / purpose (Reminders, Tasks, Subscriptions, Organization, Expenses, All).
 *
 * Transparently conveys: "Your data stays on your device." No account, no cloud servers.
 */
@Composable
fun OnboardingScreen(onCompleted: () -> Unit = {}) {
    val container = LocalAppContainer.current
    val c = LocalPocketColors.current
    val haptics = LocalHaptics.current
    val motion = LocalMotion.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableIntStateOf(0) }
    var selectedLanguage by remember { mutableStateOf(AppLanguage.SYSTEM) }
    var selectedTheme by remember { mutableStateOf(ThemeMode.SYSTEM) }
    var selectedFocus by remember { mutableStateOf(OnboardingFocus.ALL) }

    fun applyLanguage(lang: AppLanguage) {
        selectedLanguage = lang
        val tag = lang.tag ?: ""
        val locales = if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun finish() {
        scope.launch {
            haptics.perform(HapticType.Success)
            container.settings.update {
                it.copy(
                    language = selectedLanguage,
                    themeMode = selectedTheme,
                    primaryFocus = selectedFocus,
                    onboardingCompleted = true,
                    guidedTourDismissed = false,
                )
            }
            onCompleted()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = Spacing.gutter)
    ) {
        // Top Brand Header & Skip
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(listOf(c.accent, c.accentDeep))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Shield, null, tint = c.onAccent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(Spacing.sm))
                Column {
                    Text("PocketOS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.textPrimary)
                    Text(stringResource(R.string.privacy_badge), style = MaterialTheme.typography.labelSmall, color = c.accent)
                }
            }

            if (step < 2) {
                PocketButton(
                    stringResource(R.string.skip),
                    { finish() },
                    style = ButtonStyle.Text
                )
            }
        }

        // Stepper Progress Indicators
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(3) { i ->
                val active = i == step
                val past = i < step
                val width by animateDpAsState(if (active) 32.dp else 12.dp, motion.standard(), label = "stepBar")
                Box(
                    Modifier
                        .height(4.dp)
                        .width(width)
                        .clip(Shapes.pill)
                        .background(
                            when {
                                active -> c.accent
                                past -> c.accentSoft.copy(alpha = 0.6f)
                                else -> c.textTertiary.copy(alpha = 0.25f)
                            }
                        )
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))

        // Step Content Carousel
        Box(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = step,
                transitionSpec = { motion.cardEnter() togetherWith motion.tabExit() },
                label = "onboardingStep",
            ) { currentStep ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Top
                ) {
                    when (currentStep) {
                        0 -> LanguageStep(
                            selected = selectedLanguage,
                            onSelect = {
                                haptics.perform(HapticType.Selection)
                                applyLanguage(it)
                            }
                        )
                        1 -> ThemeStep(
                            selected = selectedTheme,
                            onSelect = {
                                haptics.perform(HapticType.Selection)
                                selectedTheme = it
                            }
                        )
                        2 -> FocusStep(
                            selected = selectedFocus,
                            onSelect = {
                                haptics.perform(HapticType.Selection)
                                selectedFocus = it
                            }
                        )
                    }
                }
            }
        }

        // Bottom Navigation Buttons
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            if (step > 0) {
                PocketButton(
                    stringResource(R.string.back),
                    { step-- },
                    style = ButtonStyle.Glass,
                    modifier = Modifier.weight(1f)
                )
            }
            PocketButton(
                stringResource(if (step == 2) R.string.get_started else R.string.next),
                {
                    if (step < 2) step++ else finish()
                },
                modifier = Modifier.weight(if (step > 0) 1.5f else 1f),
                haptic = HapticType.Confirm
            )
        }
    }
}

@Composable
private fun LanguageStep(selected: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    val c = LocalPocketColors.current

    StepHeader(
        title = stringResource(R.string.onboarding_lang_title),
        subtitle = stringResource(R.string.onboarding_lang_subtitle)
    )

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        LanguageOption(
            title = "English",
            nativeTitle = "English",
            selected = selected == AppLanguage.ENGLISH,
            onClick = { onSelect(AppLanguage.ENGLISH) }
        )
        LanguageOption(
            title = "فارسی",
            nativeTitle = "Persian",
            selected = selected == AppLanguage.PERSIAN,
            onClick = { onSelect(AppLanguage.PERSIAN) }
        )
        LanguageOption(
            title = stringResource(R.string.language_system),
            nativeTitle = stringResource(R.string.follow_system_lang),
            selected = selected == AppLanguage.SYSTEM,
            onClick = { onSelect(AppLanguage.SYSTEM) }
        )
    }

    Spacer(Modifier.height(Spacing.xl))
    PrivacyBadge()
}

@Composable
private fun ThemeStep(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    StepHeader(
        title = stringResource(R.string.onboarding_theme_title),
        subtitle = stringResource(R.string.onboarding_theme_subtitle)
    )

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        ThemeOption(
            icon = Icons.Rounded.DarkMode,
            title = stringResource(R.string.theme_dark),
            subtitle = stringResource(R.string.theme_dark_subtitle),
            selected = selected == ThemeMode.DARK,
            onClick = { onSelect(ThemeMode.DARK) }
        )
        ThemeOption(
            icon = Icons.Rounded.LightMode,
            title = stringResource(R.string.theme_light),
            subtitle = stringResource(R.string.theme_light_subtitle),
            selected = selected == ThemeMode.LIGHT,
            onClick = { onSelect(ThemeMode.LIGHT) }
        )
        ThemeOption(
            icon = Icons.Rounded.SettingsBrightness,
            title = stringResource(R.string.theme_system),
            subtitle = stringResource(R.string.theme_system_subtitle),
            selected = selected == ThemeMode.SYSTEM,
            onClick = { onSelect(ThemeMode.SYSTEM) }
        )
    }
}

@Composable
private fun FocusStep(selected: OnboardingFocus, onSelect: (OnboardingFocus) -> Unit) {
    StepHeader(
        title = stringResource(R.string.onboarding_focus_title),
        subtitle = stringResource(R.string.onboarding_focus_subtitle)
    )

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FocusOption(
            icon = Icons.Rounded.NotificationsActive,
            title = stringResource(R.string.focus_reminders),
            subtitle = stringResource(R.string.focus_reminders_sub),
            selected = selected == OnboardingFocus.REMINDERS,
            onClick = { onSelect(OnboardingFocus.REMINDERS) }
        )
        FocusOption(
            icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
            title = stringResource(R.string.focus_tasks),
            subtitle = stringResource(R.string.focus_tasks_sub),
            selected = selected == OnboardingFocus.TASKS,
            onClick = { onSelect(OnboardingFocus.TASKS) }
        )
        FocusOption(
            icon = Icons.Rounded.AccountBalanceWallet,
            title = stringResource(R.string.focus_subs),
            subtitle = stringResource(R.string.focus_subs_sub),
            selected = selected == OnboardingFocus.SUBSCRIPTIONS,
            onClick = { onSelect(OnboardingFocus.SUBSCRIPTIONS) }
        )
        FocusOption(
            icon = Icons.Rounded.Payments,
            title = stringResource(R.string.focus_expenses),
            subtitle = stringResource(R.string.focus_expenses_sub),
            selected = selected == OnboardingFocus.EXPENSES,
            onClick = { onSelect(OnboardingFocus.EXPENSES) }
        )
        FocusOption(
            icon = Icons.Rounded.Spa,
            title = stringResource(R.string.focus_organization),
            subtitle = stringResource(R.string.focus_organization_sub),
            selected = selected == OnboardingFocus.ORGANIZATION,
            onClick = { onSelect(OnboardingFocus.ORGANIZATION) }
        )
        FocusOption(
            icon = Icons.Rounded.CheckCircle,
            title = stringResource(R.string.focus_all),
            subtitle = stringResource(R.string.focus_all_sub),
            selected = selected == OnboardingFocus.ALL,
            onClick = { onSelect(OnboardingFocus.ALL) }
        )
    }
}

@Composable
private fun StepHeader(title: String, subtitle: String) {
    val c = LocalPocketColors.current
    Spacer(Modifier.height(Spacing.md))
    Text(
        title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = c.textPrimary,
        modifier = Modifier.semantics { heading() }
    )
    Spacer(Modifier.height(Spacing.xs))
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
    Spacer(Modifier.height(Spacing.xl))
}

@Composable
private fun LanguageOption(title: String, nativeTitle: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalPocketColors.current
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) Modifier.border(1.5.dp, c.accent, RoundedCornerShape(16.dp)) else Modifier
            ),
        level = if (selected) GlassLevel.L2 else GlassLevel.L1,
        tint = if (selected) c.accent.copy(alpha = 0.12f) else null,
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
                Text(nativeTitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            if (selected) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, null, tint = c.onAccent, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ThemeOption(icon: ImageVector, title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalPocketColors.current
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) Modifier.border(1.5.dp, c.accent, RoundedCornerShape(16.dp)) else Modifier
            ),
        level = if (selected) GlassLevel.L2 else GlassLevel.L1,
        tint = if (selected) c.accent.copy(alpha = 0.12f) else null,
        onClick = onClick,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) c.accent.copy(alpha = 0.2f) else c.surfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = if (selected) c.accent else c.textSecondary, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            if (selected) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, null, tint = c.onAccent, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun FocusOption(icon: ImageVector, title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalPocketColors.current
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) Modifier.border(1.5.dp, c.accent, RoundedCornerShape(16.dp)) else Modifier
            ),
        level = if (selected) GlassLevel.L2 else GlassLevel.L1,
        tint = if (selected) c.accent.copy(alpha = 0.12f) else null,
        onClick = onClick,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) c.accent.copy(alpha = 0.2f) else c.surfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = if (selected) c.accent else c.textSecondary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            if (selected) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, null, tint = c.onAccent, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun PrivacyBadge() {
    val c = LocalPocketColors.current
    GlassCard(Modifier.fillMaxWidth(), level = GlassLevel.L1) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, tint = c.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.md))
            Text(
                stringResource(R.string.onboarding_privacy_promise),
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary
            )
        }
    }
}
