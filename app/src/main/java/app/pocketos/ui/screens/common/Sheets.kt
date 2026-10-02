package app.pocketos.ui.screens.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.pocketos.R
import app.pocketos.domain.categories.Categories
import app.pocketos.domain.model.CategoryKind
import app.pocketos.domain.model.Reminder
import app.pocketos.domain.model.Subscription
import app.pocketos.domain.model.SubscriptionStatus
import app.pocketos.ui.LocalAppContainer
import app.pocketos.ui.components.CategoryBadge
import app.pocketos.ui.components.categoryName
import app.pocketos.ui.design.GlassBottomSheet
import app.pocketos.ui.design.GlassChip
import app.pocketos.ui.design.GlassDialog
import app.pocketos.ui.design.GlassTextField
import app.pocketos.ui.design.HapticType
import app.pocketos.ui.design.LocalHaptics
import app.pocketos.ui.design.PocketButton
import app.pocketos.ui.design.ButtonStyle
import app.pocketos.ui.navigation.Routes
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.Sizes
import app.pocketos.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@Composable
fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color? = null, haptic: HapticType = HapticType.LightTap) {
    val c = LocalPocketColors.current
    val haptics = LocalHaptics.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touch + 4.dp)
            .selectable(false, role = Role.Button, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                haptics.perform(haptic)
                onClick()
            }
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        app.pocketos.ui.components.ToneIcon(icon, tint ?: app.pocketos.ui.components.autoTone(icon, c.tones), size = 36.dp)
        Spacer(Modifier.width(Spacing.md))
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, color = tint ?: c.textPrimary)
    }
}

/** Long-press menu for a reminder: edit, duplicate, change date/category, snooze, delete. */
@Composable
fun ReminderMenu(reminder: Reminder?, nav: NavController, onDismiss: () -> Unit) {
    if (reminder == null) return
    val actions = rememberItemActions()
    val c = LocalPocketColors.current
    var pickDate by remember { mutableStateOf(false) }
    var pickCategory by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (!pickDate && !pickCategory && !confirmDelete) {
        GlassBottomSheet(onDismiss = onDismiss, title = reminder.title) {
            MenuRow(Icons.Rounded.Edit, stringResource(R.string.edit), { onDismiss(); nav.navigate(Routes.ReminderEditor(reminder.id)) })
            MenuRow(Icons.Rounded.ContentCopy, stringResource(R.string.duplicate), { actions.duplicate(reminder); onDismiss() })
            MenuRow(Icons.Rounded.Event, stringResource(R.string.change_date), { pickDate = true })
            MenuRow(Icons.Rounded.Category, stringResource(R.string.change_category), { pickCategory = true })
            if (!reminder.isCompleted && reminder.dueDate != null) {
                MenuRow(Icons.Rounded.Snooze, stringResource(R.string.snooze), { actions.snooze(reminder); onDismiss() })
            }
            MenuRow(Icons.Rounded.DeleteOutline, stringResource(R.string.delete), { confirmDelete = true }, tint = c.danger, haptic = HapticType.Warning)
        }
    }
    if (pickDate) {
        PocketDatePickerDialog(
            initial = reminder.dueDate ?: LocalDate.now(),
            onDismiss = { pickDate = false; onDismiss() },
            onPicked = { date ->
                actions.reschedule(reminder, date, reminder.dueTime)
                pickDate = false
                onDismiss()
            },
        )
    }
    if (pickCategory) {
        CategoryPickerSheet(
            kind = CategoryKind.REMINDER,
            selected = reminder.category,
            onDismiss = { pickCategory = false; onDismiss() },
            onPicked = { actions.setCategory(reminder, it); pickCategory = false; onDismiss() },
        )
    }
    if (confirmDelete) {
        GlassDialog(
            onDismiss = { confirmDelete = false; onDismiss() },
            title = stringResource(R.string.delete_reminder_title),
            message = stringResource(R.string.delete_reminder_message, reminder.title),
            confirmText = stringResource(R.string.delete),
            onConfirm = { actions.delete(reminder); confirmDelete = false; onDismiss() },
            dismissText = stringResource(R.string.cancel),
            destructive = true,
        )
    }
}

/** Long-press menu for a subscription: edit, duplicate, pause/resume, cancel, delete. */
@Composable
fun SubscriptionMenu(subscription: Subscription?, nav: NavController, onDismiss: () -> Unit) {
    if (subscription == null) return
    val actions = rememberItemActions()
    val c = LocalPocketColors.current
    var confirmDelete by remember { mutableStateOf(false) }
    if (!confirmDelete) {
        GlassBottomSheet(onDismiss = onDismiss, title = subscription.name) {
            MenuRow(Icons.Rounded.Edit, stringResource(R.string.edit), { onDismiss(); nav.navigate(Routes.SubscriptionEditor(subscription.id)) })
            MenuRow(Icons.Rounded.ContentCopy, stringResource(R.string.duplicate), { actions.duplicate(subscription); onDismiss() })
            if (subscription.status == SubscriptionStatus.ACTIVE) {
                MenuRow(Icons.Rounded.Pause, stringResource(R.string.pause), { actions.setStatus(subscription, SubscriptionStatus.PAUSED); onDismiss() })
                MenuRow(Icons.Rounded.Block, stringResource(R.string.mark_cancelled), { actions.setStatus(subscription, SubscriptionStatus.CANCELLED); onDismiss() })
            } else {
                MenuRow(Icons.Rounded.PlayArrow, stringResource(R.string.resume), { actions.setStatus(subscription, SubscriptionStatus.ACTIVE); onDismiss() })
            }
            MenuRow(Icons.Rounded.DeleteOutline, stringResource(R.string.delete), { confirmDelete = true }, tint = c.danger, haptic = HapticType.Warning)
        }
    } else {
        GlassDialog(
            onDismiss = { confirmDelete = false; onDismiss() },
            title = stringResource(R.string.delete_subscription_title),
            message = stringResource(R.string.delete_subscription_message, subscription.name),
            confirmText = stringResource(R.string.delete),
            onConfirm = { actions.delete(subscription); confirmDelete = false; onDismiss() },
            dismissText = stringResource(R.string.cancel),
            destructive = true,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PocketDatePickerDialog(initial: LocalDate, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    val c = LocalPocketColors.current
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            PocketButton(stringResource(R.string.done), {
                state.selectedDateMillis?.let { onPicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
            }, style = ButtonStyle.Text, haptic = HapticType.Confirm)
        },
        dismissButton = { PocketButton(stringResource(R.string.cancel), onDismiss, style = ButtonStyle.Text) },
        colors = DatePickerDefaults.colors(containerColor = c.surfaceElevated),
    ) {
        DatePicker(
            state = state,
            colors = DatePickerDefaults.colors(
                containerColor = c.surfaceElevated,
                selectedDayContainerColor = c.accent,
                todayDateBorderColor = c.accent,
                todayContentColor = c.accent,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PocketTimePickerDialog(initial: LocalTime, is24Hour: Boolean, onDismiss: () -> Unit, onPicked: (LocalTime) -> Unit) {
    val c = LocalPocketColors.current
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour)
    GlassDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.pick_time),
        confirmText = stringResource(R.string.done),
        onConfirm = { onPicked(LocalTime.of(state.hour, state.minute)) },
        dismissText = stringResource(R.string.cancel),
    ) {
        TimePicker(
            state = state,
            colors = TimePickerDefaults.colors(
                clockDialColor = c.textTertiary.copy(alpha = 0.12f),
                selectorColor = c.accent,
                timeSelectorSelectedContainerColor = c.accent.copy(alpha = 0.25f),
                timeSelectorSelectedContentColor = c.textPrimary,
                periodSelectorSelectedContainerColor = c.accent.copy(alpha = 0.25f),
            ),
        )
    }
}

/** Category selection: built-in categories plus the user's own; creating one inline. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPickerSheet(kind: CategoryKind, selected: String, onDismiss: () -> Unit, onPicked: (String) -> Unit) {
    val container = LocalAppContainer.current
    val custom by container.categories.custom(kind).collectAsState(initial = emptyList())
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val builtIns = if (kind == CategoryKind.REMINDER) Categories.reminder else Categories.subscription
    GlassBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.choose_category)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            builtIns.forEach { cat ->
                GlassChip(
                    text = categoryName(kind, cat.id),
                    selected = cat.id == selected,
                    onClick = { onPicked(cat.id) },
                    leading = { CategoryBadge(kind, cat.id, cat.iconKey, size = 22.dp) },
                )
            }
            custom.forEach { cat ->
                GlassChip(
                    text = cat.name,
                    selected = cat.id == selected,
                    onClick = { onPicked(cat.id) },
                    leading = { CategoryBadge(kind, cat.id, cat.icon, size = 22.dp, customColor = cat.color) },
                )
            }
        }
        Spacer(Modifier.padding(top = Spacing.lg))
        if (creating) {
            GlassTextField(newName, { newName = it.take(60) }, placeholder = stringResource(R.string.new_category_name))
            Spacer(Modifier.padding(top = Spacing.sm))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PocketButton(stringResource(R.string.cancel), { creating = false }, style = ButtonStyle.Text)
                PocketButton(stringResource(R.string.create), {
                    if (newName.isNotBlank()) {
                        container.scope.launch {
                            val created = container.categories.create(kind, newName, null, null)
                            onPicked(created.id)
                        }
                    }
                }, enabled = newName.isNotBlank(), haptic = HapticType.Success)
            }
        } else {
            PocketButton(stringResource(R.string.new_category), { creating = true }, style = ButtonStyle.Tonal)
        }
    }
}

/** Resolves a category id (built-in or custom) to a display name. */
@Composable
fun categoryLabel(kind: CategoryKind, id: String): String {
    val container = LocalAppContainer.current
    val custom by container.categories.custom(kind).collectAsState(initial = emptyList())
    return categoryName(kind, id, custom.firstOrNull { it.id == id }?.name)
}

/** Simple vertical list column helper for sheets. */
@Composable
fun SheetColumn(content: @Composable () -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) { content() }
