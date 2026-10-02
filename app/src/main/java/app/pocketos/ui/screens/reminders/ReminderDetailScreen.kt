package app.pocketos.ui.screens.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.pocketos.R
import app.pocketos.domain.model.CategoryKind
import app.pocketos.domain.model.Priority
import app.pocketos.domain.model.ReminderEventType
import app.pocketos.domain.model.ReminderKind
import app.pocketos.ui.LocalAppContainer
import app.pocketos.ui.components.CategoryBadge
import app.pocketos.ui.components.CompletionCheck
import app.pocketos.ui.components.EmptyState
import app.pocketos.ui.components.SkeletonList
import app.pocketos.ui.components.StatusPill
import app.pocketos.ui.design.ButtonStyle
import app.pocketos.ui.design.GlassBottomSheet
import app.pocketos.ui.design.GlassCard
import app.pocketos.ui.design.GlassDialog
import app.pocketos.ui.design.GlassLevel
import app.pocketos.ui.design.GlassToolbar
import app.pocketos.ui.design.HapticType
import app.pocketos.ui.design.PocketButton
import app.pocketos.ui.format.LocalFormatter
import app.pocketos.ui.navigation.Routes
import app.pocketos.ui.screens.common.MenuRow
import app.pocketos.ui.screens.common.PocketDatePickerDialog
import app.pocketos.ui.screens.common.PocketTimePickerDialog
import app.pocketos.ui.screens.common.categoryLabel
import app.pocketos.ui.screens.common.rememberItemActions
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.Spacing
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

private object NotLoaded

@Composable
fun ReminderDetailScreen(nav: NavController, id: String) {
    val container = LocalAppContainer.current
    val loaded by remember(id) { container.reminders.observe(id) }.collectAsState(initial = NotLoaded)
    val history by remember(id) { container.reminders.history(id) }.collectAsState(initial = emptyList())
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val actions = rememberItemActions()
    val context = LocalContext.current
    var snoozeSheet by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTimeFor by remember { mutableStateOf<LocalDate?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val today = container.clock.today()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(top = 64.dp).padding(horizontal = Spacing.gutter).navigationBarsPadding()) {
            when (val value = loaded) {
                NotLoaded -> SkeletonList(3)
                null -> EmptyState(Icons.Rounded.NotificationsActive, stringResource(R.string.not_found_title), stringResource(R.string.not_found_reminder), actionLabel = stringResource(R.string.back), onAction = { nav.popBackStack() })
                is app.pocketos.domain.model.Reminder -> {
                    val r = value
                    GlassCard(Modifier.fillMaxWidth(), level = GlassLevel.L2) {
                        Row(verticalAlignment = Alignment.Top) {
                            CompletionCheck(r.isCompleted, { actions.toggleComplete(r) }, r.title)
                            Spacer(Modifier.width(Spacing.xs))
                            Column(Modifier.weight(1f).padding(top = Spacing.sm)) {
                                Text(r.title, style = MaterialTheme.typography.headlineSmall, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                                Spacer(Modifier.height(Spacing.sm))
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                    StatusPill(stringResource(if (r.kind == ReminderKind.TASK) R.string.kind_task else R.string.kind_reminder), c.accentSoft)
                                    if (r.isCompleted) StatusPill(stringResource(R.string.state_completed), c.success, icon = Icons.Rounded.Check)
                                    else if (r.dueDate?.isBefore(today) == true) StatusPill(stringResource(R.string.overdue), c.warning)
                                }
                            }
                        }
                        r.notes?.let {
                            Spacer(Modifier.height(Spacing.md))
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                        }
                    }
                    Spacer(Modifier.height(Spacing.md))
                    GlassCard(Modifier.fillMaxWidth(), level = GlassLevel.L1) {
                        InfoRow(Icons.Rounded.Event, stringResource(R.string.when_label), f.dateTime(r.dueDate, r.dueTime, today, r.allDay))
                        InfoRow(Icons.Rounded.Repeat, stringResource(R.string.repeat), f.recurrence(r.recurrence))
                        if (r.leadMinutes > 0) InfoRow(Icons.Rounded.NotificationsActive, stringResource(R.string.notify_me), leadText(r.leadMinutes))
                        r.snoozedUntil?.takeIf { it.isAfter(container.clock.now()) }?.let {
                            InfoRow(Icons.Rounded.AlarmOn, stringResource(R.string.snooze), stringResource(R.string.snoozed_until, f.time(LocalTime.ofInstant(it, ZoneId.systemDefault()))))
                        }
                        InfoRow(Icons.Rounded.Flag, stringResource(R.string.priority), stringResource(when (r.priority) { Priority.LOW -> R.string.priority_low; Priority.NORMAL -> R.string.priority_normal; Priority.HIGH -> R.string.priority_high }))
                        Row(Modifier.padding(vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                            CategoryBadge(CategoryKind.REMINDER, r.category, null, size = 28.dp)
                            Spacer(Modifier.width(Spacing.md))
                            Text(categoryLabel(CategoryKind.REMINDER, r.category), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                        }
                    }
                    Spacer(Modifier.height(Spacing.lg))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        PocketButton(
                            stringResource(if (r.isCompleted) R.string.mark_not_done else R.string.complete),
                            { actions.toggleComplete(r) },
                            modifier = Modifier.weight(1f),
                            icon = if (r.isCompleted) Icons.Rounded.Undo else Icons.Rounded.Check,
                            haptic = if (r.isCompleted) HapticType.ToggleOff else HapticType.Success,
                        )
                        if (!r.isCompleted && r.dueDate != null) {
                            PocketButton(stringResource(R.string.snooze), { snoozeSheet = true }, modifier = Modifier.weight(1f), style = ButtonStyle.Glass, icon = Icons.Rounded.AlarmOn)
                        }
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        PocketButton(stringResource(R.string.reschedule), { pickDate = true }, modifier = Modifier.weight(1f), style = ButtonStyle.Glass, icon = Icons.Rounded.Event)
                        PocketButton(stringResource(R.string.edit), { nav.navigate(Routes.ReminderEditor(r.id)) }, modifier = Modifier.weight(1f), style = ButtonStyle.Glass, icon = Icons.Rounded.Edit)
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        PocketButton(stringResource(R.string.duplicate), { actions.duplicate(r) }, modifier = Modifier.weight(1f), style = ButtonStyle.Text, icon = Icons.Rounded.ContentCopy)
                        PocketButton(stringResource(R.string.delete), { confirmDelete = true }, modifier = Modifier.weight(1f), style = ButtonStyle.Text, icon = Icons.Rounded.DeleteOutline, haptic = HapticType.Warning)
                    }

                    if (history.isNotEmpty()) {
                        Spacer(Modifier.height(Spacing.xl))
                        Text(stringResource(R.string.history), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                        Spacer(Modifier.height(Spacing.sm))
                        GlassCard(Modifier.fillMaxWidth(), level = GlassLevel.L1) {
                            history.take(12).forEach { e ->
                                val label = stringResource(
                                    when (e.type) {
                                        ReminderEventType.CREATED -> R.string.event_created
                                        ReminderEventType.COMPLETED -> R.string.event_completed
                                        ReminderEventType.SNOOZED -> R.string.event_snoozed
                                        ReminderEventType.RESCHEDULED -> R.string.event_rescheduled
                                        ReminderEventType.UNCOMPLETED -> R.string.event_uncompleted
                                    }
                                )
                                val at = LocalDateTime.ofInstant(e.at, ZoneId.systemDefault())
                                InfoRow(Icons.Rounded.History, label, f.dateTime(at.toLocalDate(), at.toLocalTime(), today))
                            }
                        }
                    }
                    Spacer(Modifier.height(Spacing.huge))

                    if (snoozeSheet) {
                        GlassBottomSheet(onDismiss = { snoozeSheet = false }, title = stringResource(R.string.snooze)) {
                            listOf(10, 30, 60, 180, 24 * 60).forEach { m ->
                                MenuRow(Icons.Rounded.AlarmOn, snoozeLabel(m), { actions.snooze(r, m); snoozeSheet = false }, haptic = HapticType.Confirm)
                            }
                        }
                    }
                    if (pickDate) PocketDatePickerDialog(r.dueDate ?: today, { pickDate = false }) { d -> pickDate = false; pickTimeFor = d }
                    pickTimeFor?.let { d ->
                        PocketTimePickerDialog(r.dueTime ?: LocalTime.of(9, 0), android.text.format.DateFormat.is24HourFormat(context), { actions.reschedule(r, d, r.dueTime); pickTimeFor = null }) { t ->
                            actions.reschedule(r, d, t)
                            pickTimeFor = null
                        }
                    }
                    if (confirmDelete) {
                        GlassDialog(
                            onDismiss = { confirmDelete = false },
                            title = stringResource(R.string.delete_reminder_title),
                            message = stringResource(R.string.delete_reminder_message, r.title),
                            confirmText = stringResource(R.string.delete),
                            onConfirm = { confirmDelete = false; actions.delete(r); nav.popBackStack() },
                            dismissText = stringResource(R.string.cancel),
                            destructive = true,
                        )
                    }
                }
            }
        }
        GlassToolbar(title = stringResource(R.string.reminder_details), onBack = { nav.popBackStack() }, backLabel = stringResource(R.string.back), elevated = true)
    }
}

@Composable
fun InfoRow(icon: ImageVector, label: String, value: String) {
    val c = LocalPocketColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.md))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
    }
}

@Composable
private fun leadText(minutes: Int): String = when {
    minutes < 60 -> pluralStringResource(R.plurals.lead_minutes, minutes, minutes)
    minutes < 1440 -> pluralStringResource(R.plurals.lead_hours, minutes / 60, minutes / 60)
    else -> pluralStringResource(R.plurals.lead_days, minutes / 1440, minutes / 1440)
}
