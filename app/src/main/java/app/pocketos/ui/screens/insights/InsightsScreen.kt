package app.pocketos.ui.screens.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import app.pocketos.AppContainer
import app.pocketos.R
import app.pocketos.domain.insights.InsightsCalculator
import app.pocketos.domain.insights.InsightsSnapshot
import app.pocketos.domain.model.CategoryKind
import app.pocketos.ui.LocalAppContainer
import app.pocketos.ui.components.BarChart
import app.pocketos.ui.components.BottomClearance
import app.pocketos.ui.components.ChartSlice
import app.pocketos.ui.components.DonutChart
import app.pocketos.ui.components.EmptyState
import app.pocketos.ui.components.SectionHeader
import app.pocketos.ui.components.ServiceIcon
import app.pocketos.ui.components.SkeletonList
import app.pocketos.ui.components.categoryColor
import app.pocketos.ui.components.categoryName
import app.pocketos.ui.design.GlassCard
import app.pocketos.ui.design.GlassChip
import app.pocketos.ui.design.GlassLevel
import app.pocketos.ui.format.LocalFormatter
import app.pocketos.ui.navigation.Routes
import app.pocketos.ui.pocketViewModel
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import kotlin.math.roundToInt

class InsightsViewModel(c: AppContainer) : ViewModel() {
    val snapshot: StateFlow<InsightsSnapshot?> = combine(
        c.subscriptions.subscriptions,
        c.reminders.reminders,
        c.reminders.eventsSince(c.clock.now().minus(Duration.ofDays(70))),
    ) { subs, reminders, events ->
        InsightsCalculator.compute(subs, reminders, events, c.clock.today(), c.clock.zone())
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun InsightsScreen(nav: NavController) {
    val vm = pocketViewModel { InsightsViewModel(it) }
    val snapshot by vm.snapshot.collectAsState()
    val container = LocalAppContainer.current
    val catalog by container.catalog.catalog.collectAsState()
    val settings by container.settings.settings.collectAsState(initial = container.latestSettings)
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val today = container.clock.today()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Spacing.gutter)) {
        item { Spacer(Modifier.statusBarsPadding().height(Spacing.lg)) }
        item {
            Text(stringResource(R.string.nav_insights), style = MaterialTheme.typography.headlineMedium, color = c.textPrimary,
                modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.xs).semantics { heading() })
            Text(stringResource(R.string.insights_subtitle), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        }
        val s = snapshot
        if (s == null) {
            item { Spacer(Modifier.height(Spacing.lg)); SkeletonList(4) }
            return@LazyColumn
        }
        if (s.activeSubscriptions == 0 && s.completionTrend.all { it.completed == 0 } && s.recentlyCompleted.isEmpty()) {
            item {
                EmptyState(Icons.Rounded.Insights, stringResource(R.string.empty_insights_title), stringResource(R.string.empty_insights_message),
                    actionLabel = stringResource(R.string.add_subscription), onAction = { nav.navigate(Routes.SubscriptionEditor()) })
            }
            item { BottomClearance() }
            return@LazyColumn
        }

        if (s.totals.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.recurring_costs)) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    items(s.totals, key = { it.currency }) { t ->
                        GlassCard(Modifier.width(220.dp), level = GlassLevel.L2, tint = c.accent) {
                            Text(t.currency, style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                            Text(f.money(t.monthlyMinor, t.currency), style = MaterialTheme.typography.headlineSmall, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(R.string.per_month_estimate), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                            Spacer(Modifier.height(Spacing.sm))
                            Text(f.money(t.annualMinor, t.currency), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1)
                            Text(stringResource(R.string.per_year_estimate), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        }
                    }
                }
            }
            item {
                Text(stringResource(R.string.estimates_disclaimer), style = MaterialTheme.typography.bodySmall, color = c.textTertiary, modifier = Modifier.padding(top = Spacing.sm))
            }
        }

        if (s.distribution.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.by_category)) }
            item { CategoryDistribution(s) }
        }

        if (s.upcoming.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.upcoming_payments_30)) }
            items(s.upcoming.take(10), key = { it.subscription.id + it.date }) { p ->
                GlassCard(Modifier.fillMaxWidth().padding(bottom = Spacing.sm), level = GlassLevel.L1, contentPadding = PaddingValues(Spacing.md),
                    onClick = { nav.navigate(Routes.SubscriptionDetail(p.subscription.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ServiceIcon(catalog.find(p.subscription.serviceId), p.subscription.name, p.subscription.category, size = 36.dp, customColor = p.subscription.color)
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(p.subscription.name, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, maxLines = 1)
                            Text(f.dayLabel(p.date, today), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        }
                        Text(p.amountMinor?.let { f.money(it, p.subscription.currency, compact = true) } ?: "—", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                    }
                }
            }
        }

        if (s.renewalDays.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.renewal_calendar), action = stringResource(R.string.view_schedule), onAction = { nav.navigate(Routes.Calendar) }) }
            item { RenewalStrip(s, today) }
        }

        item { SectionHeader(stringResource(R.string.completion_trend)) }
        item {
            GlassCard(Modifier.fillMaxWidth(), level = GlassLevel.L1) {
                val values = s.completionTrend.map { it.completed }
                val labels = s.completionTrend.map { f.localizeDigits(it.weekStart.dayOfMonth.toString()) }
                val description = stringResource(R.string.trend_description, s.completedThisWeek, s.completedLastWeek)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(f.localizeDigits(s.completedThisWeek.toString()), style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(stringResource(R.string.completed_this_week), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text(
                    when {
                        s.completedLastWeek == 0 && s.completedThisWeek == 0 -> stringResource(R.string.trend_none)
                        s.completedThisWeek >= s.completedLastWeek -> stringResource(R.string.trend_up, s.completedLastWeek)
                        else -> stringResource(R.string.trend_down, s.completedLastWeek)
                    },
                    style = MaterialTheme.typography.bodySmall, color = c.textTertiary,
                )
                Spacer(Modifier.height(Spacing.md))
                BarChart(values, labels, description)
            }
        }

        if (s.recentlyCompleted.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.recently_completed)) }
            items(s.recentlyCompleted, key = { "done_" + it.id }) { r ->
                GlassCard(Modifier.fillMaxWidth().padding(bottom = Spacing.sm), level = GlassLevel.L1, contentPadding = PaddingValues(Spacing.md),
                    onClick = { nav.navigate(Routes.ReminderDetail(r.id)) }) {
                    Text(r.title, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    r.completedAt?.let {
                        val d = java.time.LocalDate.ofInstant(it, container.clock.zone())
                        Text(stringResource(R.string.completed_on, f.dayLabel(d, today)), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }
            }
        }

        item { BottomClearance() }
    }
}

@Composable
private fun CategoryDistribution(s: InsightsSnapshot) {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val currencies = s.distribution.keys.toList()
    var currency by rememberSaveable { mutableStateOf(currencies.first()) }
    if (currency !in currencies) currency = currencies.first()
    val slices = s.distribution[currency].orEmpty()
    val total = s.totals.firstOrNull { it.currency == currency }?.monthlyMinor ?: 0
    val names = slices.map { categoryName(CategoryKind.SUBSCRIPTION, it.category) }
    val description = stringResource(
        R.string.donut_description, currency,
        slices.zip(names).joinToString(", ") { (slice, name) -> "$name ${(slice.share * 100).roundToInt()}%" },
    )
    GlassCard(Modifier.fillMaxWidth(), level = GlassLevel.L1) {
        if (currencies.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                currencies.forEach { cur -> GlassChip(cur, cur == currency, { currency = cur }) }
            }
            Spacer(Modifier.height(Spacing.md))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                slices = slices.map { ChartSlice(it.category, it.monthlyMinor.toFloat(), categoryColor(CategoryKind.SUBSCRIPTION, it.category)) },
                description = description,
                size = 140.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(f.money(total, currency, compact = true), style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 1)
                    Text(stringResource(R.string.per_month_short_label), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                }
            }
            Spacer(Modifier.width(Spacing.lg))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                slices.take(6).zip(names).forEach { (slice, name) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.size(10.dp)) { drawCircle(categoryColor(CategoryKind.SUBSCRIPTION, slice.category)) }
                        Spacer(Modifier.width(Spacing.sm))
                        Text(name, style = MaterialTheme.typography.bodySmall, color = c.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(f.localizeDigits("${(slice.share * 100).roundToInt()}%"), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun RenewalStrip(s: InsightsSnapshot, today: java.time.LocalDate) {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val days = (0 until 35).map { today.plusDays(it.toLong()) }
    GlassCard(Modifier.fillMaxWidth(), level = GlassLevel.L1) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(days) { d ->
                val count = s.renewalDays[d]?.size ?: 0
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(34.dp)) {
                    Text(f.weekdayShort(d.dayOfWeek).take(2), style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                    Spacer(Modifier.height(4.dp))
                    Canvas(Modifier.size(30.dp)) {
                        drawCircle(if (count > 0) c.warning.copy(alpha = 0.25f + 0.2f * count.coerceAtMost(3)) else c.textTertiary.copy(alpha = 0.08f))
                    }
                    Text(f.dayOfMonth(d), style = MaterialTheme.typography.labelSmall, color = if (count > 0) c.textPrimary else c.textSecondary)
                    if (count > 0) Text(pluralStringResource(R.plurals.count_short, count, count), style = MaterialTheme.typography.labelSmall, color = c.warning)
                }
            }
        }
    }
}
