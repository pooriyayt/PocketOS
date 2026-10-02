package app.pocketos.ui.screens.common

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.pocketos.AppContainer
import app.pocketos.R
import app.pocketos.domain.model.Reminder
import app.pocketos.domain.model.Subscription
import app.pocketos.domain.model.SubscriptionStatus
import app.pocketos.ui.AppUiController
import app.pocketos.ui.LocalAppContainer
import app.pocketos.ui.LocalAppUi
import app.pocketos.ui.format.LocalFormatter
import app.pocketos.ui.format.UiFormatter
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * Shared, undoable item actions used by every screen (swipes, long-press
 * menus, detail screens). Writes run on the app scope so they complete even
 * if the user navigates away.
 */
class ItemActions(
    private val c: AppContainer,
    private val ui: AppUiController,
    private val res: Resources,
    private val f: UiFormatter,
) {
    private val scope get() = c.scope

    fun toggleComplete(reminder: Reminder) {
        if (reminder.isCompleted) {
            scope.launch { c.reminders.uncomplete(reminder.id) }
            return
        }
        scope.launch {
            val result = c.reminders.complete(reminder.id) ?: return@launch
            val text = when {
                result.nextDue != null -> res.getString(R.string.completed_next, f.dayLabel(result.nextDue, c.clock.today()))
                else -> res.getString(R.string.completed)
            }
            ui.undo(text, res.getString(R.string.undo)) { c.reminders.restore(result.previous) }
        }
    }

    fun snooze(reminder: Reminder, minutes: Int = c.latestSettings.snoozeMinutes) {
        scope.launch {
            val previous = c.reminders.snooze(reminder.id, minutes.toLong()) ?: return@launch
            ui.undo(res.getQuantityString(R.plurals.snoozed_for_minutes, minutes, minutes), res.getString(R.string.undo)) { c.reminders.restore(previous) }
        }
    }

    fun reschedule(reminder: Reminder, date: LocalDate?, time: LocalTime?) {
        scope.launch {
            val previous = c.reminders.reschedule(reminder.id, date, time) ?: return@launch
            ui.undo(res.getString(R.string.rescheduled_to, f.dateTime(date, time, c.clock.today())), res.getString(R.string.undo)) { c.reminders.restore(previous) }
        }
    }

    fun setCategory(reminder: Reminder, category: String) {
        scope.launch { c.reminders.setCategory(reminder.id, category) }
    }

    fun duplicate(reminder: Reminder) {
        scope.launch {
            c.reminders.duplicate(reminder.id)
            ui.message(res.getString(R.string.duplicated))
        }
    }

    fun delete(reminder: Reminder) {
        scope.launch {
            val previous = c.reminders.delete(reminder.id) ?: return@launch
            ui.undo(res.getString(R.string.deleted_item, reminder.title), res.getString(R.string.undo)) { c.reminders.restore(previous) }
        }
    }

    fun setStatus(subscription: Subscription, status: SubscriptionStatus) {
        scope.launch {
            val previous = c.subscriptions.setStatus(subscription.id, status) ?: return@launch
            val text = when (status) {
                SubscriptionStatus.PAUSED -> R.string.subscription_paused
                SubscriptionStatus.CANCELLED -> R.string.subscription_cancelled
                SubscriptionStatus.ACTIVE -> R.string.subscription_resumed
                SubscriptionStatus.EXPIRED -> R.string.subscription_expired
            }
            ui.undo(res.getString(text, subscription.name), res.getString(R.string.undo)) { c.subscriptions.restore(previous) }
        }
    }

    fun duplicate(subscription: Subscription) {
        scope.launch {
            c.subscriptions.duplicate(subscription.id)
            ui.message(res.getString(R.string.duplicated))
        }
    }

    fun delete(subscription: Subscription) {
        scope.launch {
            val previous = c.subscriptions.delete(subscription.id) ?: return@launch
            ui.undo(res.getString(R.string.deleted_item, subscription.name), res.getString(R.string.undo)) { c.subscriptions.restore(previous) }
        }
    }
}

@Composable
fun rememberItemActions(): ItemActions {
    val c = LocalAppContainer.current
    val ui = LocalAppUi.current
    val res = LocalContext.current.resources
    val f = LocalFormatter.current
    return remember(c, ui, res, f) { ItemActions(c, ui, res, f) }
}
