package app.pocketos.ui.screens.reminders

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import app.pocketos.AppContainer
import app.pocketos.R
import app.pocketos.domain.categories.Categories
import app.pocketos.domain.model.CategoryKind
import app.pocketos.domain.model.Frequency
import app.pocketos.domain.model.Priority
import app.pocketos.domain.model.RecurrenceRule
import app.pocketos.domain.model.Reminder
import app.pocketos.domain.model.ReminderKind
import app.pocketos.ui.LocalAppUi
import app.pocketos.ui.components.CategoryBadge
import app.pocketos.ui.design.ButtonStyle
import app.pocketos.ui.design.GlassBottomSheet
import app.pocketos.ui.design.GlassCard
import app.pocketos.ui.design.GlassChip
import app.pocketos.ui.design.GlassDialog
import app.pocketos.ui.design.GlassIconButton
import app.pocketos.ui.design.GlassLevel
import app.pocketos.ui.design.GlassSegmentedControl
import app.pocketos.ui.design.GlassSwitchRow
import app.pocketos.ui.design.GlassTextField
import app.pocketos.ui.design.GlassToolbar
import app.pocketos.ui.design.HapticType
import app.pocketos.ui.design.LocalHaptics
import app.pocketos.ui.design.LocalMotion
import app.pocketos.ui.design.PocketButton
import app.pocketos.ui.format.LocalFormatter
import app.pocketos.ui.pocketViewModel
import app.pocketos.ui.screens.common.CategoryPickerSheet
import app.pocketos.ui.screens.common.PocketDatePickerDialog
import app.pocketos.ui.screens.common.PocketTimePickerDialog
import app.pocketos.ui.screens.common.categoryLabel
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class ReminderForm(
    val id: String,
    val isNew: Boolean,
    val loading: Boolean = false,
    val kind: ReminderKind = ReminderKind.REMINDER,
    val title: String = "",
    val notes: String = "",
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val allDay: Boolean = false,
    val recurrence: RecurrenceRule? = null,
    val priority: Priority = Priority.NORMAL,
    val category: String = "personal",
    val categoryTouched: Boolean = false,
    val categorySuggested: Boolean = false,
    val leadMinutes: Int = 0,
    val soundUri: String? = null,
    val titleError: Boolean = false,
    val createdAt: Instant? = null,
    val completedAt: Instant? = null,
)

class ReminderEditorViewModel(private val c: AppContainer, id: String?, kind: String?) : ViewModel() {
    private val _form = MutableStateFlow(ReminderForm(id = id ?: c.reminders.newId(), isNew = id == null, loading = id != null))
    val form = _form.asStateFlow()

    init {
        if (id != null) {
            viewModelScope.launch {
                val r = c.reminders.get(id)
                if (r != null) {
                    _form.value = ReminderForm(
                        id = r.id, isNew = false, kind = r.kind, title = r.title, notes = r.notes.orEmpty(), date = r.dueDate, time = r.dueTime,
                        allDay = r.allDay, recurrence = r.recurrence, priority = r.priority, category = r.category, categoryTouched = true,
                        leadMinutes = r.leadMinutes, soundUri = r.soundUri, createdAt = r.createdAt, completedAt = r.completedAt,
                    )
                } else {
                    _form.update { it.copy(loading = false) }
                }
            }
        } else {
            val isTask = kind == "task"
            val now = c.clock.localNow()
            // Smart default: the next full hour today, or the user's usual time tomorrow if it is late.
            val nextHour = now.toLocalTime().withMinute(0).withSecond(0).withNano(0).plusHours(1)
            val lateEvening = now.hour >= 22
            _form.update {
                it.copy(
                    kind = if (isTask) ReminderKind.TASK else ReminderKind.REMINDER,
                    date = if (isTask) null else if (lateEvening) now.toLocalDate().plusDays(1) else now.toLocalDate(),
                    time = if (isTask) null else if (lateEvening) c.latestSettings.reminderDefaultTime else nextHour,
                )
            }
        }
    }

    fun update(transform: (ReminderForm) -> ReminderForm) = _form.update(transform)

    fun setTitle(title: String) {
        _form.update { f ->
            val suggestion = if (!f.categoryTouched) Categories.suggestReminderCategory(title) else null
            f.copy(
                title = title.take(200),
                titleError = false,
                category = suggestion ?: if (f.categorySuggested && !f.categoryTouched) "personal" else f.category,
                categorySuggested = suggestion != null,
            )
        }
    }

    /** Returns true when saved. */
    suspend fun save(): Boolean {
        val f = _form.value
        if (f.title.isBlank()) {
            _form.update { it.copy(titleError = true) }
            return false
        }
        val date = if (f.recurrence != null && f.date == null) c.clock.today() else f.date
        val now = c.clock.now()
        c.reminders.save(
            Reminder(
                id = f.id,
                kind = f.kind,
                title = f.title,
                notes = f.notes,
                dueDate = date,
                dueTime = if (f.allDay || date == null) null else f.time,
                allDay = f.allDay && date != null,
                recurrence = f.recurrence?.anchoredTo(date),
                priority = f.priority,
                category = f.category,
                leadMinutes = f.leadMinutes,
                completedAt = f.completedAt,
                soundUri = f.soundUri,
                createdAt = f.createdAt ?: now,
                updatedAt = now,
            )
        )
        return true
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderEditorScreen(nav: NavController, id: String?, kind: String?) {
    val vm = pocketViewModel(key = "reminder-editor-$id-$kind") { ReminderEditorViewModel(it, id, kind) }
    val form by vm.form.collectAsState()
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val haptics = LocalHaptics.current
    val ui = LocalAppUi.current
    val motion = LocalMotion.current
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val titleFocus = remember { FocusRequester() }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var pickRepeat by remember { mutableStateOf(false) }
    var pickCategory by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val savedText = stringResource(if (form.isNew) R.string.reminder_added else R.string.changes_saved)

    LaunchedEffect(form.isNew) { if (form.isNew) runCatching { titleFocus.requestFocus() } }

    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            vm.update { it.copy(soundUri = uri?.toString()) }
        }
    }

    fun save() {
        scope.launch {
            if (vm.save()) {
                haptics.perform(HapticType.Success)
                ui.message(savedText)
                nav.popBackStack()
            } else {
                haptics.perform(HapticType.Error)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(top = 64.dp).imePadding()
                .padding(horizontal = Spacing.gutter).navigationBarsPadding(),
        ) {
            GlassSegmentedControl(
                options = ReminderKind.entries,
                selected = form.kind,
                onSelect = { k ->
                    vm.update {
                        if (k == ReminderKind.TASK && it.isNew && it.recurrence == null) it.copy(kind = k)
                        else it.copy(kind = k, date = it.date ?: today)
                    }
                },
                label = { stringResource(if (it == ReminderKind.TASK) R.string.kind_task else R.string.kind_reminder) },
            )
            Spacer(Modifier.height(Spacing.xl))
            GlassTextField(
                value = form.title,
                onValueChange = vm::setTitle,
                placeholder = stringResource(if (form.kind == ReminderKind.TASK) R.string.task_title_hint else R.string.reminder_title_hint),
                error = if (form.titleError) stringResource(R.string.error_title_required) else null,
                textStyle = MaterialTheme.typography.titleLarge,
                modifier = Modifier.focusRequester(titleFocus),
            )
            Spacer(Modifier.height(Spacing.md))
            GlassTextField(
                value = form.notes,
                onValueChange = { v -> vm.update { it.copy(notes = v.take(5000)) } },
                placeholder = stringResource(R.string.notes_hint),
                singleLine = false,
                minLines = 2,
            )

            Spacer(Modifier.height(Spacing.xl))
            Text(stringResource(R.string.when_label), style = MaterialTheme.typography.titleSmall, color = c.textSecondary)
            Spacer(Modifier.height(Spacing.sm))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (form.kind == ReminderKind.TASK) {
                    GlassChip(stringResource(R.string.no_date), form.date == null, { vm.update { it.copy(date = null, recurrence = null) } })
                }
                GlassChip(stringResource(R.string.today), form.date == today, { vm.update { it.copy(date = today) } })
                GlassChip(stringResource(R.string.tomorrow), form.date == today.plusDays(1), { vm.update { it.copy(date = today.plusDays(1)) } })
                GlassChip(stringResource(R.string.next_week), form.date == today.plusWeeks(1), { vm.update { it.copy(date = today.plusWeeks(1)) } })
                val custom = form.date != null && form.date !in listOf(today, today.plusDays(1), today.plusWeeks(1))
                GlassChip(if (custom) f.date(form.date!!, withWeekday = true) else stringResource(R.string.pick_date), custom, { pickDate = true })
            }
            AnimatedVisibility(form.date != null, enter = motion.expandEnter(), exit = motion.expandExit()) {
                Column {
                    Spacer(Modifier.height(Spacing.sm))
                    GlassCard(Modifier.fillMaxWidth(), level = GlassLevel.L1, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        GlassSwitchRow(stringResource(R.string.all_day), form.allDay, { v -> vm.update { it.copy(allDay = v) } })
                        AnimatedVisibility(!form.allDay, enter = motion.expandEnter(), exit = motion.expandExit()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.time), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
                                PocketButton(form.time?.let(f::time) ?: stringResource(R.string.pick_time), { pickTime = true }, style = ButtonStyle.Tonal)
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Repeat, null, tint = c.textSecondary)
                            Spacer(Modifier.width(Spacing.md))
                            Text(stringResource(R.string.repeat), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
                            PocketButton(f.recurrence(form.recurrence), { pickRepeat = true }, style = ButtonStyle.Tonal)
                        }
                    }
                    Spacer(Modifier.height(Spacing.lg))
                    Text(stringResource(R.string.notify_me), style = MaterialTheme.typography.titleSmall, color = c.textSecondary)
                    Spacer(Modifier.height(Spacing.sm))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        listOf(0, 5, 15, 60, 1440).forEach { m ->
                            GlassChip(leadLabel(m), form.leadMinutes == m, { vm.update { it.copy(leadMinutes = m) } })
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.xl))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.category), style = MaterialTheme.typography.titleSmall, color = c.textSecondary, modifier = Modifier.weight(1f))
                if (form.categorySuggested && !form.categoryTouched) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = c.accentHighlight, modifier = Modifier.padding(end = 4.dp).width(14.dp))
                    Text(stringResource(R.string.suggested), style = MaterialTheme.typography.labelSmall, color = c.accentHighlight)
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            GlassChip(
                categoryLabel(CategoryKind.REMINDER, form.category),
                selected = true,
                onClick = { pickCategory = true },
                leading = { CategoryBadge(CategoryKind.REMINDER, form.category, null, size = 20.dp) },
            )

            Spacer(Modifier.height(Spacing.xl))
            Text(stringResource(R.string.priority), style = MaterialTheme.typography.titleSmall, color = c.textSecondary)
            Spacer(Modifier.height(Spacing.sm))
            GlassSegmentedControl(
                options = Priority.entries,
                selected = form.priority,
                onSelect = { p -> vm.update { it.copy(priority = p) } },
                label = { stringResource(when (it) { Priority.LOW -> R.string.priority_low; Priority.NORMAL -> R.string.priority_normal; Priority.HIGH -> R.string.priority_high }) },
            )

            Spacer(Modifier.height(Spacing.lg))
            PocketButton(
                stringResource(R.string.more_options),
                { advanced = !advanced },
                style = ButtonStyle.Text,
                icon = if (advanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                haptic = HapticType.LightTap,
            )
            AnimatedVisibility(advanced, enter = motion.expandEnter(), exit = motion.expandExit()) {
                GlassCard(Modifier.fillMaxWidth(), level = GlassLevel.L1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.MusicNote, null, tint = c.textSecondary)
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.notification_sound), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                            Text(
                                if (form.soundUri == null) stringResource(R.string.sound_default) else stringResource(R.string.sound_custom),
                                style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                            )
                        }
                        PocketButton(stringResource(R.string.change), {
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, form.soundUri?.let(Uri::parse))
                            (context.applicationContext as app.pocketos.PocketOsApp).container.appLock.allowExternalActivity()
                            soundPicker.launch(intent)
                        }, style = ButtonStyle.Tonal)
                    }
                    Text(stringResource(R.string.sound_device_only), style = MaterialTheme.typography.bodySmall, color = c.textTertiary, modifier = Modifier.padding(top = Spacing.sm))
                }
            }

            Spacer(Modifier.height(Spacing.xxl))
            PocketButton(stringResource(R.string.save), ::save, modifier = Modifier.fillMaxWidth(), haptic = HapticType.MediumTap)
            if (!form.isNew) {
                Spacer(Modifier.height(Spacing.sm))
                PocketButton(stringResource(R.string.delete), { confirmDelete = true }, modifier = Modifier.fillMaxWidth(), style = ButtonStyle.Text, icon = Icons.Rounded.DeleteOutline, haptic = HapticType.Warning)
            }
            Spacer(Modifier.height(Spacing.huge))
        }
        GlassToolbar(
            title = stringResource(if (form.isNew) (if (form.kind == ReminderKind.TASK) R.string.new_task else R.string.new_reminder) else R.string.edit_reminder),
            onBack = { nav.popBackStack() },
            backLabel = stringResource(R.string.back),
            elevated = true,
            actions = { PocketButton(stringResource(R.string.save), ::save, style = ButtonStyle.Text, haptic = HapticType.MediumTap) },
        )
    }

    if (pickDate) PocketDatePickerDialog(form.date ?: today, { pickDate = false }, { d -> vm.update { it.copy(date = d) }; pickDate = false })
    if (pickTime) PocketTimePickerDialog(
        form.time ?: LocalTime.of(9, 0),
        android.text.format.DateFormat.is24HourFormat(context),
        { pickTime = false },
        { t -> vm.update { it.copy(time = t) }; pickTime = false },
    )
    if (pickRepeat) RepeatSheet(form.recurrence, form.date ?: today, onDismiss = { pickRepeat = false }) { rule ->
        vm.update { it.copy(recurrence = rule, date = it.date ?: today) }
        pickRepeat = false
    }
    if (pickCategory) CategoryPickerSheet(CategoryKind.REMINDER, form.category, { pickCategory = false }) { cat ->
        vm.update { it.copy(category = cat, categoryTouched = true, categorySuggested = false) }
        pickCategory = false
    }
    if (confirmDelete) {
        GlassDialog(
            onDismiss = { confirmDelete = false },
            title = stringResource(R.string.delete_reminder_title),
            message = stringResource(R.string.delete_reminder_message, form.title),
            confirmText = stringResource(R.string.delete),
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    vm.form.value.let { fv -> (context.applicationContext as app.pocketos.PocketOsApp).container.reminders.delete(fv.id) }
                    nav.popBackStack()
                }
            },
            dismissText = stringResource(R.string.cancel),
            destructive = true,
        )
    }
}

@Composable
private fun leadLabel(minutes: Int): String = when (minutes) {
    0 -> stringResource(R.string.lead_at_time)
    in 1..59 -> androidx.compose.ui.res.pluralStringResource(R.plurals.lead_minutes, minutes, minutes)
    in 60..1439 -> androidx.compose.ui.res.pluralStringResource(R.plurals.lead_hours, minutes / 60, minutes / 60)
    else -> androidx.compose.ui.res.pluralStringResource(R.plurals.lead_days, minutes / 1440, minutes / 1440)
}

private enum class RepeatChoice { NONE, DAILY, WEEKDAYS, WEEKLY, MONTHLY, YEARLY, CUSTOM }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepeatSheet(current: RecurrenceRule?, start: LocalDate, onDismiss: () -> Unit, onPicked: (RecurrenceRule?) -> Unit) {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val haptics = LocalHaptics.current
    val initialChoice = when {
        current == null -> RepeatChoice.NONE
        current.interval != 1 -> RepeatChoice.CUSTOM
        current.frequency == Frequency.DAILY -> RepeatChoice.DAILY
        current.frequency == Frequency.WEEKLY && current.byDays == RecurrenceRule.WEEKDAYS -> RepeatChoice.WEEKDAYS
        current.frequency == Frequency.WEEKLY -> RepeatChoice.WEEKLY
        current.frequency == Frequency.MONTHLY -> RepeatChoice.MONTHLY
        else -> RepeatChoice.YEARLY
    }
    var choice by remember { mutableStateOf(initialChoice) }
    var days by remember { mutableStateOf(current?.byDays?.ifEmpty { null } ?: setOf(start.dayOfWeek)) }
    var interval by remember { mutableStateOf(current?.interval ?: 2) }
    var unit by remember { mutableStateOf(current?.frequency ?: Frequency.DAILY) }

    GlassBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.repeat)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            RepeatChoice.entries.forEach { option ->
                val label = when (option) {
                    RepeatChoice.NONE -> R.string.repeat_never
                    RepeatChoice.DAILY -> R.string.repeat_daily
                    RepeatChoice.WEEKDAYS -> R.string.repeat_weekdays
                    RepeatChoice.WEEKLY -> R.string.repeat_weekly
                    RepeatChoice.MONTHLY -> R.string.repeat_monthly
                    RepeatChoice.YEARLY -> R.string.repeat_yearly
                    RepeatChoice.CUSTOM -> R.string.repeat_custom
                }
                GlassChip(stringResource(label), choice == option, { choice = option })
            }
        }
        if (choice == RepeatChoice.WEEKLY) {
            Spacer(Modifier.height(Spacing.lg))
            Text(stringResource(R.string.on_days), style = MaterialTheme.typography.titleSmall, color = c.textSecondary)
            Spacer(Modifier.height(Spacing.sm))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                DayOfWeek.entries.forEach { d ->
                    GlassChip(f.weekdayShort(d), d in days, {
                        days = if (d in days && days.size > 1) days - d else days + d
                    })
                }
            }
        }
        if (choice == RepeatChoice.CUSTOM) {
            Spacer(Modifier.height(Spacing.lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.every), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                Spacer(Modifier.width(Spacing.md))
                GlassIconButton(Icons.Rounded.Remove, stringResource(R.string.decrease), { if (interval > 1) { interval--; haptics.perform(HapticType.DragTick) } }, size = 36.dp)
                Text(f.localizeDigits(interval.toString()), style = MaterialTheme.typography.titleLarge, color = c.textPrimary, modifier = Modifier.padding(horizontal = Spacing.md))
                GlassIconButton(Icons.Rounded.Add, stringResource(R.string.increase), { if (interval < 365) { interval++; haptics.perform(HapticType.DragTick) } }, size = 36.dp)
            }
            Spacer(Modifier.height(Spacing.sm))
            GlassSegmentedControl(
                options = Frequency.entries,
                selected = unit,
                onSelect = { unit = it },
                label = { stringResource(when (it) { Frequency.DAILY -> R.string.unit_days; Frequency.WEEKLY -> R.string.unit_weeks; Frequency.MONTHLY -> R.string.unit_months; Frequency.YEARLY -> R.string.unit_years }) },
            )
        }
        Spacer(Modifier.height(Spacing.xl))
        PocketButton(stringResource(R.string.done), {
            onPicked(
                when (choice) {
                    RepeatChoice.NONE -> null
                    RepeatChoice.DAILY -> RecurrenceRule.daily()
                    RepeatChoice.WEEKDAYS -> RecurrenceRule.weekly(1, RecurrenceRule.WEEKDAYS)
                    RepeatChoice.WEEKLY -> RecurrenceRule.weekly(1, days)
                    RepeatChoice.MONTHLY -> RecurrenceRule.monthly()
                    RepeatChoice.YEARLY -> RecurrenceRule.yearly()
                    RepeatChoice.CUSTOM -> RecurrenceRule(unit, interval)
                }
            )
        }, modifier = Modifier.fillMaxWidth(), haptic = HapticType.Confirm)
    }
}
