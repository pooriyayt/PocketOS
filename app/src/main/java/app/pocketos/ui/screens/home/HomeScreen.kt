package app.pocketos.ui.screens.home

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.widthIn
import app.pocketos.ui.design.GlassProgressRing
import app.pocketos.ui.components.ToneIcon
import app.pocketos.ui.components.GradientCard
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.pocketos.R
import app.pocketos.data.prefs.DashboardSection
import app.pocketos.data.prefs.OnboardingFocus
import app.pocketos.domain.insights.AgendaItem
import app.pocketos.domain.insights.DayPeriod
import app.pocketos.domain.insights.Suggestion
import app.pocketos.domain.model.Reminder
import app.pocketos.domain.parser.QuickAddType
import app.pocketos.ui.LocalAppContainer
import app.pocketos.ui.LocalAppUi
import app.pocketos.ui.components.BottomClearance
import app.pocketos.ui.components.EmptyState
import app.pocketos.ui.components.RenewalCard
import app.pocketos.ui.components.ReminderRow
import app.pocketos.ui.components.SectionHeader
import app.pocketos.ui.components.ServiceIcon
import app.pocketos.ui.components.SkeletonBlock
import app.pocketos.ui.components.SkeletonList
import app.pocketos.ui.design.ButtonStyle
import app.pocketos.ui.design.GlassBottomSheet
import app.pocketos.ui.design.GlassCard
import app.pocketos.ui.design.GlassChip
import app.pocketos.ui.design.GlassIconButton
import app.pocketos.ui.design.GlassLevel
import app.pocketos.ui.design.GlassSwitchRow
import app.pocketos.ui.design.HapticType
import app.pocketos.ui.design.LocalHaptics
import app.pocketos.ui.design.LocalMotion
import app.pocketos.ui.design.PocketButton
import app.pocketos.ui.design.glass
import app.pocketos.ui.design.pressFeedback
import androidx.compose.foundation.selection.selectable
import app.pocketos.ui.format.LocalFormatter
import app.pocketos.ui.navigation.Routes
import app.pocketos.ui.pocketViewModel
import app.pocketos.ui.screens.common.ReminderMenu
import app.pocketos.ui.screens.common.rememberItemActions
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.Spacing
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavController) {
    val vm = pocketViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsState()
    val ui = LocalAppUi.current
    val actions = rememberItemActions()
    var menuFor by remember { mutableStateOf<Reminder?>(null) }
    var customizing by remember { mutableStateOf(false) }

    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Spacing.gutter),
        ) {
            item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
            item(key = "header") { HomeHeader(state, onSearch = { nav.navigate(Routes.Search) }, onProfile = { nav.navigate(Routes.Settings) }) }

            if (!state.settings.guidedTourDismissed) {
                item(key = "guidedTour") {
                    GuidedTourCard(
                        focus = state.settings.primaryFocus,
                        onDismiss = vm::dismissGuidedTour,
                        nav = nav,
                        ui = ui,
                    )
                }
            }

            val model = state.model
            if (state.loading || model == null) {
                item(key = "loading") {
                    Column(Modifier.padding(top = Spacing.xl)) {
                        SkeletonBlock(height = 26.dp, widthFraction = 0.7f)
                        Spacer(Modifier.height(Spacing.xl))
                        SkeletonList(4)
                    }
                }
                return@LazyColumn
            }

            item(key = "summary") { Summary(state) }

            if (model.isEmpty && !state.settings.firstRunHintDismissed) {
                item(key = "firstRun") { FirstRunCard(onPick = { ui.openQuickAdd(it) }, onDismiss = vm::dismissFirstRunHint) }
            }

            val order = state.settings.dashboardOrder.filterNot { it in state.settings.dashboardHidden }
            order.forEach { section ->
                when (section) {
                    DashboardSection.ATTENTION -> if (!model.isEmpty) {
                        item(key = "attention_h") { SectionHeader(stringResource(R.string.section_attention)) }
                        if (model.attention.isEmpty()) {
                            item(key = "attention_empty") {
                                GlassCard(Modifier.fillMaxWidth(), level = GlassLevel.L1) {
                                    EmptyState(
                                        Icons.Rounded.WbSunny,
                                        stringResource(R.string.empty_attention_title),
                                        stringResource(R.string.empty_attention_message),
                                        compact = true,
                                    )
                                }
                            }
                        }
                        items(model.attention, key = { "att_" + agendaKey(it) }) { item ->
                            AgendaRow(item, state, onReminderToggle = actions::toggleComplete, onReminderLongPress = { menuFor = it }, nav = nav,
                                modifier = Modifier.padding(bottom = Spacing.sm).animateItem(fadeInSpec = LocalMotion.current.fade(), placementSpec = LocalMotion.current.placement(), fadeOutSpec = LocalMotion.current.fade()))
                        }
                    }
                    DashboardSection.OVERVIEW -> if (!model.isEmpty) item(key = "overview") { Overview(state) }
                    DashboardSection.TIMELINE -> if (model.timeline.isNotEmpty()) {
                        item(key = "timeline_h") { SectionHeader(stringResource(R.string.section_coming_up), action = stringResource(R.string.view_schedule), onAction = { nav.navigate(Routes.Calendar) }) }
                        var lastDate: java.time.LocalDate? = null
                        model.timeline.forEach { entry ->
                            if (entry.date != lastDate) {
                                lastDate = entry.date
                                item(key = "tl_d_${entry.date}") {
                                    Text(
                                        LocalFormatter.current.dayLabel(entry.date, state.today),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = LocalPocketColors.current.textSecondary,
                                        modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs, start = Spacing.xs).semantics { heading() },
                                    )
                                }
                            }
                            item(key = "tl_" + agendaKey(entry)) {
                                AgendaRow(entry, state, actions::toggleComplete, { menuFor = it }, nav, Modifier.padding(bottom = Spacing.sm).animateItem())
                            }
                        }
                    }
                    DashboardSection.RENEWALS -> if (model.renewals.isNotEmpty()) {
                        item(key = "renewals_h") { SectionHeader(stringResource(R.string.section_renewals), action = stringResource(R.string.see_all), onAction = { nav.navigate(Routes.Subscriptions) }) }
                        item(key = "renewals") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md), contentPadding = PaddingValues(end = Spacing.gutter)) {
                                items(model.renewals, key = { it.subscription.id + it.date }) { p ->
                                    RenewalCard(p.subscription, state.catalog.find(p.subscription.serviceId), p.date, state.today, onClick = { nav.navigate(Routes.SubscriptionDetail(p.subscription.id)) })
                                }
                            }
                        }
                    }
                    DashboardSection.SUGGESTIONS -> if (state.suggestions.isNotEmpty()) {
                        item(key = "sugg_h") { SectionHeader(stringResource(R.string.section_suggestions)) }
                        items(state.suggestions, key = { "sg_" + it.id }) { s ->
                            SuggestionCard(s, onDismiss = { vm.dismissSuggestion(s.id) }, nav = nav, modifier = Modifier.padding(bottom = Spacing.sm).animateItem())
                        }
                    }
                    DashboardSection.QUICK_ACTIONS -> item(key = "qa") { QuickActions(nav) }
                }
            }

            item(key = "customize") {
                Row(Modifier.fillMaxWidth().padding(top = Spacing.xl), horizontalArrangement = Arrangement.Center) {
                    PocketButton(stringResource(R.string.customize_home), { customizing = true }, style = ButtonStyle.Tonal, icon = Icons.Rounded.Tune)
                }
            }
            item { BottomClearance() }
        }
    }

    ReminderMenu(menuFor, nav) { menuFor = null }
    if (customizing) CustomizeSheet(state.settings.dashboardOrder, state.settings.dashboardHidden, onDismiss = { customizing = false }, onSave = vm::saveDashboard)
}

private fun agendaKey(item: AgendaItem): String = when (item) {
    is AgendaItem.ReminderItem -> "r_" + item.reminder.id
    is AgendaItem.RenewalItem -> "s_" + item.subscription.id + "_" + item.date + if (item.trialEnd) "_t" else ""
}

@Composable
private fun HomeHeader(state: HomeUiState, onSearch: () -> Unit, onProfile: () -> Unit) {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val name = state.settings.displayName.takeIf { it.isNotBlank() }
    val greeting = stringResource(
        when (state.model?.period ?: DayPeriod.MORNING) {
            DayPeriod.MORNING -> R.string.greeting_morning
            DayPeriod.AFTERNOON -> R.string.greeting_afternoon
            DayPeriod.EVENING -> R.string.greeting_evening
            DayPeriod.NIGHT -> R.string.greeting_night
        }
    )
    Row(Modifier.fillMaxWidth().padding(top = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                f.date(state.today, withYear = false, withWeekday = true).uppercase(f.locale),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (c.isDark) c.accentSoft else c.accent,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                if (name != null) "$greeting, $name" else greeting,
                style = MaterialTheme.typography.headlineMedium,
                color = c.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        }
        GlassIconButton(Icons.Rounded.Search, stringResource(R.string.search), onSearch)
        Spacer(Modifier.width(Spacing.xs))
        val initials = (name ?: "").trim().split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }
        ProfileButton(initials, stringResource(R.string.account_and_settings), onProfile)
    }
}

@Composable
private fun ProfileButton(initials: String, label: String, onClick: () -> Unit) {
    val c = LocalPocketColors.current
    val haptics = LocalHaptics.current
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        Modifier
            .size(48.dp)
            .padding(2.dp)
            .pressFeedback(interaction, CircleShape)
            .clip(CircleShape)
            .background(c.brandGradient)
            .selectable(false, role = androidx.compose.ui.semantics.Role.Button, interactionSource = interaction, indication = null) {
                haptics.perform(HapticType.LightTap)
                onClick()
            }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (initials.isEmpty()) {
            Icon(Icons.Rounded.Person, null, tint = c.onAccent, modifier = Modifier.size(22.dp))
        } else {
            Text(initials, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = c.onAccent)
        }
    }
}

/** Gradient hero: the one-line status of the day plus today's progress. */
@Composable
private fun Summary(state: HomeUiState) {
    val f = LocalFormatter.current
    val model = state.model ?: return
    val count = model.attention.size
    val text = when {
        model.isEmpty -> stringResource(R.string.summary_empty)
        count == 0 -> stringResource(R.string.summary_clear)
        else -> pluralStringResource(R.plurals.summary_things, count, count)
    }
    val total = model.completedToday + model.dueToday + model.overdue
    val progress = if (total == 0) 0f else model.completedToday.toFloat() / total
    val motion = LocalMotion.current
    GradientCard(Modifier.fillMaxWidth().padding(top = Spacing.xl)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                AnimatedContent(text, transitionSpec = { motion.tabEnter() togetherWith motion.tabExit() }, label = "summary") { t ->
                    Text(t, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    if (total > 0) stringResource(R.string.hero_progress, f.localizeDigits(model.completedToday.toString()), f.localizeDigits(total.toString()))
                    else stringResource(R.string.hero_nothing_planned),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.82f),
                )
            }
            Spacer(Modifier.width(Spacing.lg))
            Box(contentAlignment = Alignment.Center) {
                GlassProgressRing(progress, size = 64.dp, strokeWidth = 7.dp, color = Color.White)
                Text(
                    f.localizeDigits("${(progress * 100).toInt()}%"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
        if (model.paymentsNext7Days > 0 || model.overdue > 0) {
            Spacer(Modifier.height(Spacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (model.overdue > 0) HeroPill(Icons.Rounded.Schedule, "${f.localizeDigits(model.overdue.toString())} ${stringResource(R.string.stat_overdue)}")
                if (model.paymentsNext7Days > 0) HeroPill(Icons.Rounded.Payments, "${f.localizeDigits(model.paymentsNext7Days.toString())} · ${stringResource(R.string.stat_payments_7d)}")
            }
        }
    }
}

@Composable
private fun HeroPill(icon: ImageVector, text: String) {
    Row(
        Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = Spacing.md, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}


@Composable
private fun AgendaRow(
    item: AgendaItem,
    state: HomeUiState,
    onReminderToggle: (Reminder) -> Unit,
    onReminderLongPress: (Reminder) -> Unit,
    nav: NavController,
    modifier: Modifier = Modifier,
) {
    when (item) {
        is AgendaItem.ReminderItem -> ReminderRow(
            reminder = item.reminder,
            today = state.today,
            onToggle = { onReminderToggle(item.reminder) },
            onClick = { nav.navigate(Routes.ReminderDetail(item.reminder.id)) },
            onLongClick = { onReminderLongPress(item.reminder) },
            modifier = modifier,
        )
        is AgendaItem.RenewalItem -> {
            val c = LocalPocketColors.current
            val f = LocalFormatter.current
            val sub = item.subscription
            GlassCard(modifier.fillMaxWidth(), onClick = { nav.navigate(Routes.SubscriptionDetail(sub.id)) }, contentPadding = PaddingValues(Spacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ServiceIcon(state.catalog.find(sub.serviceId), sub.name, sub.category, size = 40.dp, customColor = sub.color)
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(sub.name, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val label = if (item.trialEnd) stringResource(R.string.trial_ends_relative, f.relative(item.date, state.today))
                        else stringResource(R.string.renews_relative, f.relative(item.date, state.today))
                        Text(label, style = MaterialTheme.typography.bodySmall, color = if (ChronoUnit.DAYS.between(state.today, item.date) <= 1) c.warning else c.textSecondary)
                    }
                    sub.amount?.let { Text(f.money(it.amountMinor, it.currency, compact = true), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = c.textPrimary) }
                }
            }
        }
    }
}

@Composable
private fun Overview(state: HomeUiState) {
    val model = state.model ?: return
    val t = LocalPocketColors.current.tones
    val tiles = listOf(
        Triple(model.dueToday, R.string.stat_due_today, Icons.Rounded.Today to t.blue),
        Triple(model.overdue, R.string.stat_overdue, Icons.Rounded.Schedule to t.orange),
        Triple(model.completedToday, R.string.stat_done, Icons.Rounded.TaskAlt to t.green),
        Triple(model.paymentsNext7Days, R.string.stat_payments_7d, Icons.Rounded.Payments to t.violet),
    )
    Column {
        SectionHeader(stringResource(R.string.section_today))
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Two columns on phones, one row of four once there is room.
            val columns = if (maxWidth >= 560.dp) 4 else 2
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                tiles.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        row.forEach { (value, label, visual) ->
                            StatTile(value, stringResource(label), visual.first, visual.second, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(value: Int, label: String, icon: ImageVector, tone: Color, modifier: Modifier) {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val shown by app.pocketos.ui.design.animatedInt(value)
    GlassCard(
        modifier.clearAndSetSemantics { contentDescription = "$label: $value" },
        contentPadding = PaddingValues(Spacing.lg),
        decoration = {
            // Soft glow of the tile's own colour from its top corner.
            drawRect(
                Brush.radialGradient(
                    listOf(tone.copy(alpha = if (c.isDark) 0.22f else 0.12f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr) 0f else size.width, 0f),
                    radius = size.width * 0.9f,
                )
            )
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToneIcon(icon, tone, size = 38.dp, filled = value > 0)
            Spacer(Modifier.weight(1f))
            Text(
                f.localizeDigits(shown.toString()),
                style = MaterialTheme.typography.headlineMedium,
                color = if (value > 0) c.textPrimary else c.textTertiary,
            )
        }
        Spacer(Modifier.height(Spacing.md))
        Text(label, style = MaterialTheme.typography.labelLarge, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}


@Composable
private fun SuggestionCard(s: Suggestion, onDismiss: () -> Unit, nav: NavController, modifier: Modifier) {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val text = when (s) {
        is Suggestion.RenewalsThisMonth -> pluralStringResource(R.plurals.suggestion_renewals_month, s.count, s.count)
        is Suggestion.AnnualRenewalsSoon -> pluralStringResource(R.plurals.suggestion_annual_soon, s.count, s.count, f.date(s.firstDate))
        is Suggestion.TrialEnding -> stringResource(R.string.suggestion_trial_ending, s.subscription.name, f.relative(s.subscription.trialEnd!!, java.time.LocalDate.now()))
        is Suggestion.NewSubscriptions -> pluralStringResource(R.plurals.suggestion_new_subs, s.count, s.count, f.money(s.addedMonthlyMinor, s.currency, compact = true))
        is Suggestion.StaleReminder -> stringResource(R.string.suggestion_stale, s.reminder.title, f.localizeDigits(s.days.toString()))
        is Suggestion.OverdueBacklog -> pluralStringResource(R.plurals.suggestion_overdue, s.count, s.count)
    }
    val target: (() -> Unit) = when (s) {
        is Suggestion.TrialEnding -> { { nav.navigate(Routes.SubscriptionDetail(s.subscription.id)) } }
        is Suggestion.StaleReminder -> { { nav.navigate(Routes.ReminderDetail(s.reminder.id)) } }
        is Suggestion.OverdueBacklog -> { { nav.navigate(Routes.Reminders) } }
        else -> { { nav.navigate(Routes.Insights) } }
    }
    GlassCard(modifier.fillMaxWidth(), level = GlassLevel.L1, onClick = target, contentPadding = PaddingValues(start = Spacing.lg, top = Spacing.sm, bottom = Spacing.sm, end = Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToneIcon(Icons.Rounded.AutoAwesome, c.tones.violet, size = 34.dp)
            Spacer(Modifier.width(Spacing.md))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
            GlassIconButton(Icons.Rounded.Close, stringResource(R.string.dismiss), onDismiss, level = GlassLevel.L1, size = 32.dp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FirstRunCard(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val c = LocalPocketColors.current
    val examples = listOf(
        stringResource(R.string.example_call_mom),
        stringResource(R.string.example_netflix),
        stringResource(R.string.example_domain),
        stringResource(R.string.example_internet),
    )
    GlassCard(Modifier.fillMaxWidth().padding(top = Spacing.xl), level = GlassLevel.L2, tint = c.accent) {
        Text(stringResource(R.string.first_run_title), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
        Spacer(Modifier.height(Spacing.xs))
        Text(stringResource(R.string.first_run_message), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        Spacer(Modifier.height(Spacing.lg))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            examples.forEach { GlassChip("“$it”", selected = false, onClick = { onPick(it) }) }
        }
        Spacer(Modifier.height(Spacing.md))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PocketButton(stringResource(R.string.not_now), onDismiss, style = ButtonStyle.Text)
            PocketButton(stringResource(R.string.add_first_item), { onPick("") }, icon = Icons.Rounded.Done)
        }
    }
}

private data class QuickAction(val icon: ImageVector, val label: Int, val tone: Color, val go: (NavController) -> Unit)

@Composable
private fun QuickActions(nav: NavController) {
    val c = LocalPocketColors.current
    val t = c.tones
    val actions = listOf(
        QuickAction(Icons.Rounded.NotificationsActive, R.string.qa_reminder, t.amber) { it.navigate(Routes.ReminderEditor()) },
        QuickAction(Icons.Rounded.AccountBalanceWallet, R.string.qa_subscription, t.violet) { it.navigate(Routes.SubscriptionEditor()) },
        QuickAction(Icons.Rounded.Payments, R.string.qa_payment, t.green) { it.navigate(Routes.SubscriptionEditor(serviceId = "")) },
        QuickAction(Icons.Rounded.TaskAlt, R.string.qa_task, t.blue) { it.navigate(Routes.ReminderEditor(kind = "task")) },
        QuickAction(Icons.Rounded.CalendarMonth, R.string.qa_schedule, t.pink) { it.navigate(Routes.Calendar) },
    )
    Column {
        SectionHeader(stringResource(R.string.section_quick_actions))
        // A swipeable rail: every label gets the width it needs and the
        // partially visible last tile hints that there is more.
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md), contentPadding = PaddingValues(end = Spacing.gutter)) {
            items(actions, key = { it.label }) { a ->
                GlassCard(
                    Modifier.width(112.dp),
                    onClick = { a.go(nav) },
                    contentPadding = PaddingValues(start = Spacing.sm, end = Spacing.sm, top = Spacing.lg, bottom = Spacing.md),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        ToneIcon(a.icon, a.tone, size = 52.dp, filled = true)
                        Spacer(Modifier.height(Spacing.md))
                        Text(
                            stringResource(a.label),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = c.textPrimary,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomizeSheet(order: List<DashboardSection>, hidden: Set<DashboardSection>, onDismiss: () -> Unit, onSave: (List<DashboardSection>, Set<DashboardSection>) -> Unit) {
    var current by remember { mutableStateOf(order) }
    var hiddenNow by remember { mutableStateOf(hidden) }
    val haptics = LocalHaptics.current
    val labels = mapOf(
        DashboardSection.ATTENTION to R.string.section_attention,
        DashboardSection.OVERVIEW to R.string.section_today,
        DashboardSection.TIMELINE to R.string.section_coming_up,
        DashboardSection.RENEWALS to R.string.section_renewals,
        DashboardSection.SUGGESTIONS to R.string.section_suggestions,
        DashboardSection.QUICK_ACTIONS to R.string.section_quick_actions,
    )
    GlassBottomSheet(onDismiss = { onSave(current, hiddenNow); onDismiss() }, title = stringResource(R.string.customize_home)) {
        current.forEachIndexed { index, section ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassSwitchRow(
                    title = stringResource(labels.getValue(section)),
                    checked = section !in hiddenNow,
                    onCheckedChange = { show -> hiddenNow = if (show) hiddenNow - section else hiddenNow + section },
                    modifier = Modifier.weight(1f),
                )
                GlassIconButton(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.move_up), {
                    if (index > 0) {
                        current = current.toMutableList().also { java.util.Collections.swap(it, index, index - 1) }
                        haptics.perform(HapticType.DragTick)
                    }
                }, level = GlassLevel.L1, size = 36.dp)
                GlassIconButton(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.move_down), {
                    if (index < current.lastIndex) {
                        current = current.toMutableList().also { java.util.Collections.swap(it, index, index + 1) }
                        haptics.perform(HapticType.DragTick)
                    }
                }, level = GlassLevel.L1, size = 36.dp)
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        PocketButton(stringResource(R.string.done), { onSave(current, hiddenNow); onDismiss() }, modifier = Modifier.fillMaxWidth(), haptic = HapticType.Confirm)
    }
}

/** Kept for API symmetry with other screens. */
@Suppress("unused")
private val QuickAddTypes = QuickAddType.entries

@Composable
private fun GuidedTourCard(
    focus: OnboardingFocus,
    onDismiss: () -> Unit,
    nav: NavController,
    ui: app.pocketos.ui.AppUiController,
) {
    val c = LocalPocketColors.current
    val (icon, title, desc, actionLabel, action) = when (focus) {
        OnboardingFocus.SUBSCRIPTIONS -> Quintuple(
            Icons.Rounded.AccountBalanceWallet,
            stringResource(R.string.tour_subs_title),
            stringResource(R.string.tour_subs_body),
            stringResource(R.string.tour_subs_action),
            { nav.navigate(Routes.SubscriptionEditor()) },
        )
        OnboardingFocus.REMINDERS -> Quintuple(
            Icons.Rounded.NotificationsActive,
            stringResource(R.string.tour_rem_title),
            stringResource(R.string.tour_rem_body),
            stringResource(R.string.tour_rem_action),
            { nav.navigate(Routes.ReminderEditor()) },
        )
        OnboardingFocus.TASKS -> Quintuple(
            Icons.Rounded.TaskAlt,
            stringResource(R.string.tour_task_title),
            stringResource(R.string.tour_task_body),
            stringResource(R.string.tour_task_action),
            { nav.navigate(Routes.ReminderEditor(kind = "task")) },
        )
        OnboardingFocus.EXPENSES -> Quintuple(
            Icons.Rounded.Payments,
            stringResource(R.string.tour_exp_title),
            stringResource(R.string.tour_exp_body),
            stringResource(R.string.tour_exp_action),
            { nav.navigate(Routes.SubscriptionEditor()) },
        )
        OnboardingFocus.ORGANIZATION, OnboardingFocus.ALL -> Quintuple(
            Icons.Rounded.AutoAwesome,
            stringResource(R.string.tour_all_title),
            stringResource(R.string.tour_all_body),
            stringResource(R.string.quick_add),
            { ui.openQuickAdd() },
        )
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
        level = GlassLevel.L2,
        tint = c.accent,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            ToneIcon(icon, c.accent, size = 44.dp, filled = true)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(desc, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                Spacer(Modifier.height(Spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    PocketButton(
                        text = actionLabel,
                        onClick = action,
                        style = ButtonStyle.Primary,
                        icon = Icons.Rounded.Done,
                    )
                    PocketButton(
                        text = stringResource(R.string.dismiss),
                        onClick = onDismiss,
                        style = ButtonStyle.Text,
                    )
                }
            }
            GlassIconButton(
                icon = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.dismiss),
                onClick = onDismiss,
                level = GlassLevel.L1,
                size = 32.dp,
            )
        }
    }
}

private data class Quintuple<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)

