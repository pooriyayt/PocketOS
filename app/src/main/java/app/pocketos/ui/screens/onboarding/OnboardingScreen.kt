package app.pocketos.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketos.R
import app.pocketos.data.prefs.AppLanguage
import app.pocketos.data.prefs.OnboardingFocus
import app.pocketos.data.prefs.ThemeMode
import app.pocketos.ui.LocalAppContainer
import app.pocketos.ui.components.ToneIcon
import app.pocketos.ui.design.ButtonStyle
import app.pocketos.ui.design.GlassIconButton
import app.pocketos.ui.design.GlassLevel
import app.pocketos.ui.design.HapticType
import app.pocketos.ui.design.LocalHaptics
import app.pocketos.ui.design.LocalMotion
import app.pocketos.ui.design.PocketButton
import app.pocketos.ui.design.appear
import app.pocketos.ui.design.glass
import app.pocketos.ui.design.pressFeedback
import app.pocketos.ui.design.rememberBreath
import app.pocketos.ui.format.LocalFormatter
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.PocketColors
import app.pocketos.ui.theme.Shapes
import app.pocketos.ui.theme.Spacing
import app.pocketos.ui.theme.accentPalette
import app.pocketos.ui.theme.darkPocketColors
import app.pocketos.ui.theme.lightPocketColors
import kotlinx.coroutines.launch

private const val STEPS = 4

/**
 * First-run setup: welcome, language, theme, focus.
 *
 * Language and theme are written to settings the moment they are picked, so
 * the whole app re-renders live in the new language / theme. The step is
 * saveable, so the activity recreation a language switch causes lands the
 * user back on the same step instead of the start.
 */
@Composable
fun OnboardingScreen(onCompleted: () -> Unit = {}) {
    val container = LocalAppContainer.current
    val settings by container.settings.settings.collectAsState(initial = container.latestSettings)
    val haptics = LocalHaptics.current
    val motion = LocalMotion.current
    val scope = rememberCoroutineScope()

    var step by rememberSaveable { mutableIntStateOf(0) }
    var selectedFocus by rememberSaveable { mutableStateOf(OnboardingFocus.ALL) }

    fun setLanguage(lang: AppLanguage) {
        haptics.perform(HapticType.Selection)
        // MainActivity applies the locale whenever settings.language changes.
        scope.launch { container.settings.update { it.copy(language = lang) } }
    }

    fun setTheme(mode: ThemeMode) {
        haptics.perform(HapticType.Selection)
        scope.launch { container.settings.update { it.copy(themeMode = mode) } }
    }

    fun finish() {
        scope.launch {
            haptics.perform(HapticType.Success)
            container.settings.update {
                it.copy(primaryFocus = selectedFocus, onboardingCompleted = true, guidedTourDismissed = false)
            }
            onCompleted()
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.gutter),
        ) {
            TopBar(step = step, onSkip = { finish() })
            Spacer(Modifier.height(Spacing.sm))

            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = { motion.cardEnter() togetherWith motion.tabExit() },
                    label = "onboardingStep",
                ) { current ->
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        when (current) {
                            0 -> WelcomeStep()
                            1 -> LanguageStep(settings.language, ::setLanguage)
                            2 -> ThemeStep(settings.themeMode, settings.accent, ::setTheme)
                            else -> FocusStep(selectedFocus) {
                                haptics.perform(HapticType.Selection)
                                selectedFocus = it
                            }
                        }
                        Spacer(Modifier.height(Spacing.xl))
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (step > 0) {
                    GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), { step-- }, size = 52.dp)
                }
                PocketButton(
                    stringResource(if (step == STEPS - 1) R.string.get_started else R.string.next),
                    { if (step < STEPS - 1) step++ else finish() },
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    icon = if (step == STEPS - 1) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
                    haptic = HapticType.Confirm,
                )
            }
        }
    }
}

@Composable
private fun TopBar(step: Int, onSkip: () -> Unit) {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val motion = LocalMotion.current
    Row(Modifier.fillMaxWidth().padding(top = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.step_of, f.localizeDigits((step + 1).toString()), f.localizeDigits(STEPS.toString())),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = c.textTertiary,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(STEPS) { i ->
                    val width by animateDpAsState(if (i == step) 36.dp else 14.dp, motion.standard(), label = "stepW")
                    Box(
                        Modifier.height(6.dp).width(width).clip(Shapes.pill).background(
                            if (i <= step) c.brandGradient else Brush.linearGradient(listOf(c.textTertiary.copy(alpha = 0.25f), c.textTertiary.copy(alpha = 0.25f)))
                        )
                    )
                }
            }
        }
        if (step < STEPS - 1) PocketButton(stringResource(R.string.skip), onSkip, style = ButtonStyle.Text)
    }
}

// ------------------------------------------------------------------ Steps

@Composable
private fun WelcomeStep() {
    val c = LocalPocketColors.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(Spacing.xxl))
        HeroOrb(Modifier.appear(0))
        Spacer(Modifier.height(Spacing.xxl))
        Text(
            stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.displaySmall,
            color = c.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.appear(1).semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            stringResource(R.string.onboarding_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = c.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.appear(2),
        )
        Spacer(Modifier.height(Spacing.xxl))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FeatureRow(Icons.Rounded.Shield, c.tones.green, stringResource(R.string.privacy_first_tagline), Modifier.appear(3))
            FeatureRow(Icons.Rounded.AccountBalanceWallet, c.tones.violet, stringResource(R.string.onboarding_2_title), Modifier.appear(4))
            FeatureRow(Icons.Rounded.NotificationsActive, c.tones.amber, stringResource(R.string.tour_rem_body), Modifier.appear(5))
        }
    }
}

@Composable
private fun HeroOrb(modifier: Modifier = Modifier) {
    val c = LocalPocketColors.current
    val breath = rememberBreath()
    Box(modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            drawCircle(Brush.radialGradient(listOf(c.accent.copy(alpha = 0.45f + 0.15f * breath), Color.Transparent), radius = r), radius = r)
            drawCircle(c.accent.copy(alpha = 0.18f), radius = r * (0.70f + 0.04f * breath), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            drawCircle(c.accentGradientEnd.copy(alpha = 0.14f), radius = r * (0.88f - 0.03f * breath), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            // Orbiting sparks.
            val a = breath * Math.PI.toFloat()
            drawCircle(c.tones.pink, radius = 4.dp.toPx(), center = Offset(center.x + r * 0.70f * kotlin.math.cos(a), center.y + r * 0.70f * kotlin.math.sin(a)))
            drawCircle(c.tones.cyan, radius = 3.dp.toPx(), center = Offset(center.x - r * 0.88f * kotlin.math.cos(a), center.y - r * 0.88f * kotlin.math.sin(a)))
        }
        Box(
            Modifier
                .size(104.dp)
                .scale(0.97f + 0.03f * breath)
                .clip(RoundedCornerShape(32.dp))
                .background(c.brandGradient)
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.05f))), RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center,
        ) {
            // The PocketOS mark: a pocket with a spark, same geometry as the launcher icon.
            Canvas(Modifier.size(56.dp)) {
                val u = size.width / 44f
                val pocket = androidx.compose.ui.graphics.Path().apply {
                    moveTo(10 * u, 15 * u)
                    lineTo(34 * u, 15 * u)
                    lineTo(34 * u, 23 * u)
                    cubicTo(34 * u, 36 * u, 10 * u, 36 * u, 10 * u, 23 * u)
                    close()
                }
                drawPath(pocket, Color.White)
                drawCircle(Color.White, radius = 3.2f * u, center = Offset(34 * u, 6 * u))
            }
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, tone: Color, text: String, modifier: Modifier = Modifier) {
    val c = LocalPocketColors.current
    Row(
        modifier.fillMaxWidth().glass(GlassLevel.L1, Shapes.card).padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToneIcon(icon, tone, size = 40.dp)
        Spacer(Modifier.width(Spacing.md))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StepHeader(title: String, subtitle: String) {
    val c = LocalPocketColors.current
    Spacer(Modifier.height(Spacing.xl))
    Text(title, style = MaterialTheme.typography.headlineLarge, color = c.textPrimary, modifier = Modifier.appear(0).semantics { heading() })
    Spacer(Modifier.height(Spacing.sm))
    Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = c.textSecondary, modifier = Modifier.appear(1))
    Spacer(Modifier.height(Spacing.xxl))
}

@Composable
private fun LanguageStep(selected: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    StepHeader(stringResource(R.string.onboarding_lang_title), stringResource(R.string.onboarding_lang_subtitle))
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        LanguageCard("🇮🇷", "فارسی", "Persian", selected == AppLanguage.PERSIAN, { onSelect(AppLanguage.PERSIAN) }, Modifier.appear(2))
        LanguageCard("🇬🇧", "English", "انگلیسی", selected == AppLanguage.ENGLISH, { onSelect(AppLanguage.ENGLISH) }, Modifier.appear(3))
        LanguageCard("🌐", stringResource(R.string.language_system), stringResource(R.string.follow_system_lang), selected == AppLanguage.SYSTEM, { onSelect(AppLanguage.SYSTEM) }, Modifier.appear(4))
    }
}

@Composable
private fun LanguageCard(flag: String, title: String, subtitle: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalPocketColors.current
    SelectableSurface(selected, onClick, modifier.fillMaxWidth()) {
        Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(c.textPrimary.copy(alpha = if (c.isDark) 0.08f else 0.05f)),
                contentAlignment = Alignment.Center,
            ) { Text(flag, fontSize = 28.sp) }
            Spacer(Modifier.width(Spacing.lg))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            SelectionMark(selected)
        }
    }
}

@Composable
private fun ThemeStep(selected: ThemeMode, accent: app.pocketos.data.prefs.AccentColor, onSelect: (ThemeMode) -> Unit) {
    StepHeader(stringResource(R.string.onboarding_theme_title), stringResource(R.string.onboarding_theme_subtitle))
    val palette = accentPalette(accent)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        ThemePreviewCard(
            darkPocketColors(palette), Icons.Rounded.DarkMode, stringResource(R.string.theme_dark), selected == ThemeMode.DARK,
            { onSelect(ThemeMode.DARK) }, Modifier.weight(1f).appear(2),
        )
        ThemePreviewCard(
            lightPocketColors(palette), Icons.Rounded.LightMode, stringResource(R.string.theme_light), selected == ThemeMode.LIGHT,
            { onSelect(ThemeMode.LIGHT) }, Modifier.weight(1f).appear(3),
        )
    }
    Spacer(Modifier.height(Spacing.md))
    val c = LocalPocketColors.current
    SelectableSurface(selected == ThemeMode.SYSTEM, { onSelect(ThemeMode.SYSTEM) }, Modifier.fillMaxWidth().appear(4)) {
        Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            ToneIcon(Icons.Rounded.SettingsBrightness, c.tones.blue, size = 44.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.theme_system), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Text(stringResource(R.string.theme_system_subtitle), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            SelectionMark(selected == ThemeMode.SYSTEM)
        }
    }
}

/** A miniature of the home screen painted in [palette], so the choice is visual, not verbal. */
@Composable
private fun ThemePreviewCard(palette: PocketColors, icon: ImageVector, title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalPocketColors.current
    SelectableSurface(selected, onClick, modifier) {
        Column(Modifier.padding(Spacing.sm)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.background)
                    .background(Brush.radialGradient(listOf(palette.ambientA, Color.Transparent), center = Offset(0f, 0f), radius = 500f))
                    .padding(10.dp),
            ) {
                Box(Modifier.width(54.dp).height(8.dp).clip(Shapes.pill).background(palette.textTertiary.copy(alpha = 0.6f)))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.width(86.dp).height(12.dp).clip(Shapes.pill).background(palette.textPrimary))
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(12.dp)).background(palette.brandGradient)) {
                    Box(Modifier.padding(8.dp).width(60.dp).height(8.dp).clip(Shapes.pill).background(Color.White.copy(alpha = 0.85f)))
                    Box(Modifier.align(Alignment.CenterEnd).padding(end = 10.dp).size(26.dp).border(4.dp, Color.White.copy(alpha = 0.9f), CircleShape))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(palette.tones.blue, palette.tones.orange).forEach { tone ->
                        Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp)).background(palette.surface).padding(6.dp)) {
                            Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(tone.copy(alpha = 0.8f)))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(8.dp)).background(palette.surface))
            }
            Row(Modifier.padding(horizontal = 6.dp, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = if (selected) c.accent else c.textSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                SelectionMark(selected, size = 22.dp)
            }
        }
    }
}

@Composable
private fun FocusStep(selected: OnboardingFocus, onSelect: (OnboardingFocus) -> Unit) {
    StepHeader(stringResource(R.string.onboarding_focus_title), stringResource(R.string.onboarding_focus_subtitle))
    val t = LocalPocketColors.current.tones
    val options = listOf(
        FocusOption(OnboardingFocus.REMINDERS, Icons.Rounded.NotificationsActive, t.amber, R.string.focus_reminders, R.string.focus_reminders_sub),
        FocusOption(OnboardingFocus.TASKS, Icons.AutoMirrored.Rounded.FormatListBulleted, t.blue, R.string.focus_tasks, R.string.focus_tasks_sub),
        FocusOption(OnboardingFocus.SUBSCRIPTIONS, Icons.Rounded.AccountBalanceWallet, t.violet, R.string.focus_subs, R.string.focus_subs_sub),
        FocusOption(OnboardingFocus.EXPENSES, Icons.Rounded.Payments, t.green, R.string.focus_expenses, R.string.focus_expenses_sub),
        FocusOption(OnboardingFocus.ORGANIZATION, Icons.Rounded.Spa, t.pink, R.string.focus_organization, R.string.focus_organization_sub),
        FocusOption(OnboardingFocus.ALL, Icons.Rounded.AutoAwesome, t.cyan, R.string.focus_all, R.string.focus_all_sub),
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = Spacing.md
        val tile = (maxWidth - gap) / 2
        FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
            options.forEachIndexed { i, o ->
                FocusTile(o, selected == o.focus, { onSelect(o.focus) }, Modifier.width(tile).appear(2 + i))
            }
        }
    }
    Spacer(Modifier.height(Spacing.lg))
    PrivacyNote()
}

private data class FocusOption(val focus: OnboardingFocus, val icon: ImageVector, val tone: Color, val title: Int, val subtitle: Int)

@Composable
private fun FocusTile(o: FocusOption, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = LocalPocketColors.current
    SelectableSurface(selected, onClick, modifier) {
        Column(Modifier.padding(Spacing.lg).heightIn(min = 120.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                ToneIcon(o.icon, o.tone, size = 44.dp, filled = selected)
                Spacer(Modifier.weight(1f))
                SelectionMark(selected, size = 22.dp)
            }
            Spacer(Modifier.height(Spacing.md))
            Text(stringResource(o.title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(o.subtitle), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PrivacyNote() {
    val c = LocalPocketColors.current
    Row(
        Modifier.fillMaxWidth().glass(GlassLevel.L1, Shapes.card).padding(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToneIcon(Icons.Rounded.Lock, c.tones.green, size = 36.dp)
        Spacer(Modifier.width(Spacing.md))
        Text(stringResource(R.string.onboarding_privacy_promise), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    }
}

// ---------------------------------------------------------------- Shared

/** Card that lifts and gains a gradient outline + accent wash when selected. */
@Composable
private fun SelectableSurface(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = LocalPocketColors.current
    val motion = LocalMotion.current
    val interaction = remember { MutableInteractionSource() }
    val shape = Shapes.cardLarge
    val ring by animateFloatAsState(if (selected) 1f else 0f, motion.standard(), label = "selRing")
    val wash by animateColorAsState(if (selected) c.accent.copy(alpha = if (c.isDark) 0.16f else 0.07f) else Color.Transparent, motion.standard(), label = "selWash")
    Box(
        modifier
            .pressFeedback(interaction, shape)
            .glass(GlassLevel.L2, shape)
            .background(wash, shape)
            .border(
                width = (1 + ring).dp,
                brush = if (selected) c.brandGradient else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
                shape = shape,
            )
            .selectable(selected, role = Role.RadioButton, interactionSource = interaction, indication = null, onClick = onClick),
    ) { content() }
}

@Composable
private fun SelectionMark(selected: Boolean, size: Dp = 26.dp) {
    val c = LocalPocketColors.current
    val motion = LocalMotion.current
    val scale by animateFloatAsState(if (selected) 1f else 0f, motion.emphasized(), label = "markScale")
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .border(1.5.dp, if (selected) Color.Transparent else c.textTertiary.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (scale > 0f) {
            Box(Modifier.fillMaxSize().scale(scale).clip(CircleShape).background(c.brandGradient), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, tint = c.onAccent, modifier = Modifier.size(size * 0.62f))
            }
        }
    }
}

