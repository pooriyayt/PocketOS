package app.pocketos.ui.screens.home

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
                    PocketButton(stringResource(R.string.customize_home), { customizing = true }, style = ButtonStyle.Text, icon = Icons.Rounded.Tune)
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
            Text(f.date(state.today, withYear = false, withWeekday = true), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
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
            .glass(GlassLevel.L2, CircleShape, tint = c.accent)
            .selectable(false, role = androidx.compose.ui.semantics.Role.Button, interactionSource = interaction, indication = null) {
                haptics.perform(HapticType.LightTap)
                onClick()
            }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (initials.isEmpty()) {
            Icon(Icons.Rounded.Person, null, tint = c.accentHighlight, modifier = Modifier.size(22.dp))
        } else {
            Text(initials, style = MaterialTheme.typography.labelLarge, color = c.textPrimary)
        }
    }
}

@Composable
private fun Summary(state: HomeUiState) {
    val c = LocalPocketColors.current
    val model = state.model ?: return
    val count = model.attention.size
    val text = when {
        model.isEmpty -> stringResource(R.string.summary_empty)
        count == 0 -> stringResource(R.string.summary_clear)
        else -> pluralStringResource(R.plurals.summary_things, count, count)
    }
    val motion = LocalMotion.current
    AnimatedContent(text, transitionSpec = { motion.tabEnter() togetherWith motion.tabExit() }, label = "summary") { t ->
        Text(t, style = MaterialTheme.typography.bodyLarge, color = c.textSecondary, modifier = Modifier.padding(top = Spacing.sm))
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
                    sub.amount?.let { Text(f.money(it.amountMinor, it.currency, compact = true), style = MaterialTheme.typography.titleSmall, color = c.textPrimary) }
                }
            }
        }
    }
}

@Composable
private fun Overview(state: HomeUiState) {
    val model = state.model ?: return
    val c = LocalPocketColors.current
    Column {
        SectionHeader(stringResource(R.string.section_today))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatTile(model.dueToday, stringResource(R.string.stat_due_today), c.accent, Modifier.weight(1f))
            StatTile(model.overdue, stringResource(R.string.stat_overdue), c.warning, Modifier.weight(1f))
            StatTile(model.completedToday, stringResource(R.string.stat_done), c.success, Modifier.weight(1f))
            StatTile(model.paymentsNext7Days, stringResource(R.string.stat_payments_7d), c.info, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(value: Int, label: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    GlassCard(modifier.clearAndSetSemantics { contentDescription = "$label: $value" }, level = GlassLevel.L1, contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.md)) {
        Text(f.localizeDigits(value.toString()), style = MaterialTheme.typography.titleLarge, color = if (value > 0) color else c.textTertiary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.textSecondary, maxLines = 2)
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
            Icon(Icons.Rounded.AutoAwesome, null, tint = c.accentHighlight, modifier = Modifier.size(20.dp))
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

private data class QuickAction(val icon: ImageVector, val label: Int, val go: (NavController) -> Unit)

@Composable
private fun QuickActions(nav: NavController) {
    val c = LocalPocketColors.current
    val actions = listOf(
        QuickAction(Icons.Rounded.NotificationsActive, R.string.qa_reminder) { it.navigate(Routes.ReminderEditor()) },
        QuickAction(Icons.Rounded.AccountBalanceWallet, R.string.qa_subscription) { it.navigate(Routes.SubscriptionEditor()) },
        QuickAction(Icons.Rounded.Payments, R.string.qa_payment) { it.navigate(Routes.SubscriptionEditor(serviceId = "")) },
        QuickAction(Icons.Rounded.TaskAlt, R.string.qa_task) { it.navigate(Routes.ReminderEditor(kind = "task")) },
        QuickAction(Icons.Rounded.CalendarMonth, R.string.qa_schedule) { it.navigate(Routes.Calendar) },
    )
    Column {
        SectionHeader(stringResource(R.string.section_quick_actions))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            actions.forEach { a ->
                GlassCard(Modifier.weight(1f), level = GlassLevel.L1, onClick = { a.go(nav) }, contentPadding = PaddingValues(vertical = Spacing.md, horizontal = Spacing.xs)) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(a.icon, null, tint = c.accentSoft, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(Spacing.xs))
                        Text(stringResource(a.label), style = MaterialTheme.typography.labelSmall, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
            "Track Your Subscriptions",
            "Monitor recurring bills, trial end dates, and renewal notifications. PocketOS alerts you before charges occur.",
            "Add Subscription",
            { nav.navigate(Routes.SubscriptionEditor()) },
        )
        OnboardingFocus.REMINDERS -> Quintuple(
            Icons.Rounded.NotificationsActive,
            "Reliable Offline Reminders",
            "Schedule timely notifications with custom sounds and alarms. Completely local and private.",
            "Set Reminder",
            { nav.navigate(Routes.ReminderEditor()) },
        )
        OnboardingFocus.TASKS -> Quintuple(
            Icons.Rounded.TaskAlt,
            "Stay on Top of Tasks",
            "Keep organized checklists and daily todos. Complete tasks directly from Home or notifications.",
            "Add Task",
            { nav.navigate(Routes.ReminderEditor(kind = "task")) },
        )
        OnboardingFocus.EXPENSES -> Quintuple(
            Icons.Rounded.Payments,
            "Manage Recurring Expenses",
            "Get clear monthly spending summaries and upcoming payment forecasts without linking a bank account.",
            "Add Expense",
            { nav.navigate(Routes.SubscriptionEditor()) },
        )
        OnboardingFocus.ORGANIZATION, OnboardingFocus.ALL -> Quintuple(
            Icons.Rounded.AutoAwesome,
            "Welcome to PocketOS",
            "Your private daily command center. Reminders, tasks, and subscriptions stay safe on your device.",
            "Quick Add",
            { ui.openQuickAdd() },
        )
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
        level = GlassLevel.L2,
        tint = c.accent,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(c.accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = c.accentHighlight, modifier = Modifier.size(24.dp))
            }
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

