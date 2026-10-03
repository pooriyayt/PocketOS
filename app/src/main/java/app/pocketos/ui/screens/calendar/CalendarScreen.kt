package app.pocketos.ui.screens.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.EventAvailable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.pocketos.R
import app.pocketos.core.time.JalaliCalendar
import app.pocketos.domain.model.Reminder
import app.pocketos.domain.model.ReminderKind
import app.pocketos.domain.model.Subscription
import app.pocketos.domain.model.SubscriptionStatus
import app.pocketos.domain.recurrence.BillingCalculator
import app.pocketos.domain.recurrence.RecurrenceEngine
import app.pocketos.ui.LocalAppContainer
import app.pocketos.ui.components.EmptyState
import app.pocketos.ui.components.ReminderRow
import app.pocketos.ui.components.ServiceIcon
import app.pocketos.ui.design.GlassCard
import app.pocketos.ui.design.GlassIconButton
import app.pocketos.ui.design.GlassLevel
import app.pocketos.ui.design.GlassToolbar
import app.pocketos.ui.design.HapticType
import app.pocketos.ui.design.LocalHaptics
import app.pocketos.ui.design.LocalMotion
import app.pocketos.ui.design.PocketButton
import app.pocketos.ui.design.ButtonStyle
import app.pocketos.ui.format.LocalFormatter
import app.pocketos.ui.navigation.Routes
import app.pocketos.ui.screens.common.ReminderMenu
import app.pocketos.ui.screens.common.rememberItemActions
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Locale

/** A month in the user's calendar, expressed as its Gregorian dates. */
data class CalendarMonthKey(val year: Int, val month: Int, val kind: app.pocketos.core.time.CalendarKind) {
    fun days(): List<LocalDate> {
        val first = app.pocketos.core.time.CalendarMath.toDate(kind, year, month, 1)
        return (0 until app.pocketos.core.time.CalendarMath.monthLength(kind, year, month)).map { first.plusDays(it.toLong()) }
    }

    fun plus(months: Int): CalendarMonthKey {
        val index = year * 12 + (month - 1) + months
        return copy(year = Math.floorDiv(index, 12), month = Math.floorMod(index, 12) + 1)
    }

    companion object {
        fun of(date: LocalDate, kind: app.pocketos.core.time.CalendarKind): CalendarMonthKey =
            app.pocketos.core.time.CalendarMath.fromDate(date, kind).let { CalendarMonthKey(it.year, it.month, kind) }
    }
}

private sealed interface DayEntry {
    data class R(val reminder: Reminder, val occurrence: LocalDate) : DayEntry
    data class S(val subscription: Subscription) : DayEntry
}

@Composable
fun CalendarScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val reminders by container.reminders.reminders.collectAsState(initial = emptyList())
    val subscriptions by container.subscriptions.subscriptions.collectAsState(initial = emptyList())
    val catalog by container.catalog.catalog.collectAsState()
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val haptics = LocalHaptics.current
    val motion = LocalMotion.current
    val actions = rememberItemActions()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val today = container.clock.today()
    val calendarKind = f.calendar
    var monthOffset by rememberSaveable { mutableStateOf(0) }
    var selected by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var menuFor by remember { mutableStateOf<Reminder?>(null) }
    val month = CalendarMonthKey.of(today, calendarKind).plus(monthOffset)
    val days = remember(month) { month.days() }

    // Everything happening in the visible month, including repeating occurrences.
    val entries: Map<LocalDate, List<DayEntry>> = remember(reminders, subscriptions, month) {
        val from = days.first()
        val to = days.last()
        val map = HashMap<LocalDate, MutableList<DayEntry>>()
        reminders.filter { !it.isCompleted && it.dueDate != null }.forEach { r ->
            val dates = r.recurrence?.let { RecurrenceEngine.occurrencesBetween(it, r.dueDate!!, maxOf(from, r.dueDate), to, 62) }
                ?: listOf(r.dueDate!!).filter { it in from..to }
            dates.forEach { d -> map.getOrPut(d) { mutableListOf() }.add(DayEntry.R(r, d)) }
        }
        subscriptions.filter { it.status == SubscriptionStatus.ACTIVE }.forEach { s ->
            BillingCalculator.renewalsBetween(s, from, to, 62).forEach { d -> map.getOrPut(d) { mutableListOf() }.add(DayEntry.S(s)) }
        }
        map
    }
    val weekStart = if (f.locale.language == "fa") DayOfWeek.SATURDAY else WeekFields.of(f.locale).firstDayOfWeek
    val selectedDate = LocalDate.ofEpochDay(selected)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Spacing.gutter)) {
            item { Spacer(Modifier.statusBarsPadding().height(64.dp)) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedContent(month, transitionSpec = { motion.tabEnter() togetherWith motion.tabExit() }, label = "monthTitle", modifier = Modifier.weight(1f)) { m ->
                        Text(f.monthTitle(m.year, m.month, m.kind), style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
                    }
                    if (monthOffset != 0 || selected != today.toEpochDay()) {
                        PocketButton(stringResource(R.string.today), { monthOffset = 0; selected = today.toEpochDay() }, style = ButtonStyle.Text)
                    }
                    GlassIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.previous_month), { monthOffset-- }, size = 40.dp, haptic = HapticType.Selection)
                    GlassIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.next_month), { monthOffset++ }, size = 40.dp, haptic = HapticType.Selection)
                }
            }
            item {
                GlassCard(
                    Modifier.fillMaxWidth().padding(top = Spacing.md).pointerInput(rtl) {
                        var total = 0f
                        detectHorizontalDragGestures(onDragStart = { total = 0f }, onDragEnd = {
                            val logical = if (rtl) -total else total
                            if (logical < -80) monthOffset++ else if (logical > 80) monthOffset--
                            if (kotlin.math.abs(total) > 80) haptics.perform(HapticType.Selection)
                        }) { _, delta -> total += delta }
                    },
                    level = GlassLevel.L2,
                    contentPadding = PaddingValues(Spacing.sm),
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        (0 until 7).forEach { i ->
                            val dow = weekStart.plus(i.toLong())
                            Text(f.weekdayShort(dow), style = MaterialTheme.typography.labelSmall, color = c.textTertiary, modifier = Modifier.weight(1f).padding(vertical = Spacing.xs),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                    val leading = (days.first().dayOfWeek.value - weekStart.value + 7) % 7
                    val cells = List(leading) { null } + days
                    cells.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            week.forEach { date ->
                                Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                                    if (date != null) {
                                        DayCell(date, today, date.toEpochDay() == selected, entries[date].orEmpty()) {
                                            haptics.perform(HapticType.Selection)
                                            selected = date.toEpochDay()
                                        }
                                    }
                                }
                            }
                            repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            item { Legend() }
            item {
                Text(
                    f.date(selectedDate, withYear = selectedDate.year != today.year, withWeekday = true),
                    style = MaterialTheme.typography.titleMedium, color = c.textPrimary,
                    modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.sm),
                )
            }
            val dayEntries = entries[selectedDate].orEmpty()
            if (dayEntries.isEmpty()) {
                item { EmptyState(Icons.Rounded.EventAvailable, stringResource(R.string.empty_day_title), stringResource(R.string.empty_day_message), compact = true) }
            }
            items(dayEntries, key = { e -> when (e) { is DayEntry.R -> "r" + e.reminder.id; is DayEntry.S -> "s" + e.subscription.id } }) { e ->
                when (e) {
                    is DayEntry.R -> ReminderRow(
                        e.reminder, today,
                        onToggle = { actions.toggleComplete(e.reminder) },
                        onClick = { nav.navigate(Routes.ReminderDetail(e.reminder.id)) },
                        onLongClick = { menuFor = e.reminder },
                        modifier = Modifier.padding(bottom = Spacing.sm),
                    )
                    is DayEntry.S -> GlassCard(Modifier.fillMaxWidth().padding(bottom = Spacing.sm), onClick = { nav.navigate(Routes.SubscriptionDetail(e.subscription.id)) }, contentPadding = PaddingValues(Spacing.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ServiceIcon(catalog.find(e.subscription.serviceId), e.subscription.name, e.subscription.category, size = 36.dp, customColor = e.subscription.color)
                            Spacer(Modifier.width(Spacing.md))
                            Column(Modifier.weight(1f)) {
                                Text(e.subscription.name, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                                Text(stringResource(R.string.renewal), style = MaterialTheme.typography.bodySmall, color = c.warning)
                            }
                            e.subscription.amount?.let { Text(f.money(it.amountMinor, it.currency, compact = true), style = MaterialTheme.typography.titleSmall, color = c.textPrimary) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(Spacing.huge)) }
        }
        GlassToolbar(title = stringResource(R.string.calendar), onBack = { nav.popBackStack() }, backLabel = stringResource(R.string.back), elevated = true)
    }
    ReminderMenu(menuFor, nav) { menuFor = null }
}

@Composable
private fun DayCell(date: LocalDate, today: LocalDate, selected: Boolean, entries: List<Any>, onClick: () -> Unit) {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val hasReminder = entries.any { it is DayEntry.R && it.reminder.kind == ReminderKind.REMINDER }
    val hasTask = entries.any { it is DayEntry.R && it.reminder.kind == ReminderKind.TASK }
    val hasRenewal = entries.any { it is DayEntry.S }
    val isToday = date == today
    val description = buildString {
        append(f.date(date, withWeekday = true))
        if (entries.isNotEmpty()) append(", ").append(pluralStringResource(R.plurals.items_count, entries.size, entries.size))
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(2.dp)
            .clip(CircleShape)
            .then(if (selected) Modifier.background(c.brandGradient) else if (isToday) Modifier.border(1.5.dp, c.brandGradient, CircleShape) else Modifier)
            .selectable(selected, role = Role.Button, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(f.dayOfMonth(date), style = MaterialTheme.typography.bodyMedium, color = if (selected) c.onAccent else if (date.isBefore(today)) c.textTertiary else c.textPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(6.dp)) {
            if (hasReminder) Dot(if (selected) Color.White else c.accentSoft)
            if (hasTask) Dot(if (selected) Color.White else c.info)
            if (hasRenewal) Dot(if (selected) Color.White else c.warning)
        }
    }
}

@Composable
private fun Dot(color: Color) = Canvas(Modifier.size(5.dp)) { drawCircle(color) }

@Composable
private fun Legend() {
    val c = LocalPocketColors.current
    Row(Modifier.fillMaxWidth().padding(top = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        listOf(c.accentSoft to R.string.legend_reminders, c.info to R.string.legend_tasks, c.warning to R.string.legend_renewals).forEach { (color, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(color)
                Spacer(Modifier.width(Spacing.xs))
                Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            }
        }
    }
}

@Suppress("unused")
private val defaultLocale: Locale = Locale.getDefault()
