package app.pocketos.widget

import android.content.Context
import app.pocketos.PocketOsApp
import app.pocketos.R
import app.pocketos.core.money.MoneyFormatter
import app.pocketos.data.local.toDomain
import app.pocketos.domain.model.SubscriptionStatus
import app.pocketos.domain.recurrence.BillingCalculator
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

data class WidgetReminder(val id: String, val title: String, val whenText: String, val overdue: Boolean)
data class WidgetRenewal(val id: String, val name: String, val daysUntil: Long, val dateText: String, val amountText: String?)

data class WidgetSnapshot(
    val dateText: String,
    val remainingToday: Int,
    val overdue: Int,
    val next: WidgetReminder?,
    val upcoming: List<WidgetReminder>,
    val renewal: WidgetRenewal?,
    val failed: Boolean = false,
)

/**
 * Reads real data for widgets from the active profile's database, applying
 * the widget privacy settings (titles / amounts can be hidden).
 */
object WidgetDataLoader {

    suspend fun load(context: Context): WidgetSnapshot {
        val locale = Locale.getDefault()
        val application = context.applicationContext as PocketOsApp
        val container = application.container
        val clock = container.clock
        val today = clock.today()
        val dateText = DateTimeFormatter.ofPattern("EEE d MMM", locale).format(today)
        return try {
            val settings = container.settings.current()
            val db = container.databases.current
            val reminders = db.reminders().schedulable().map { it.toDomain() }
            val subs = db.subscriptions().allActive().map { it.toDomain() }.filter { it.status == SubscriptionStatus.ACTIVE }
            val hiddenTitle = context.getString(R.string.widget_hidden_title)

            fun label(date: LocalDate, time: LocalTime?): String {
                val day = when (date) {
                    today -> context.getString(R.string.today)
                    today.plusDays(1) -> context.getString(R.string.tomorrow)
                    else -> DateTimeFormatter.ofPattern("EEE d MMM", locale).format(date)
                }
                return time?.let { "$day · ${DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(it)}" } ?: day
            }

            val sorted = reminders.sortedWith(compareBy({ it.dueDate }, { it.dueTime ?: LocalTime.MAX }))
            val upcoming = sorted.filter { !it.dueDate!!.isBefore(today) }.take(6).map {
                WidgetReminder(it.id, if (settings.widgetHideTitles) hiddenTitle else it.title, label(it.dueDate!!, it.dueTime), overdue = false)
            }
            val nowTime = clock.localNow().toLocalTime()
            val next = sorted.firstOrNull { it.dueDate!! > today || (it.dueDate == today && (it.dueTime ?: LocalTime.MAX) >= nowTime) }
            val renewal = subs.map { it to BillingCalculator.rolledForward(it, today) }.minByOrNull { it.second }?.let { (sub, date) ->
                WidgetRenewal(
                    id = sub.id,
                    name = if (settings.widgetHideTitles) hiddenTitle else sub.name,
                    daysUntil = ChronoUnit.DAYS.between(today, date),
                    dateText = DateTimeFormatter.ofPattern("d MMM", locale).format(date),
                    amountText = if (settings.widgetHideAmounts) null else sub.amount?.let { MoneyFormatter.format(it.amountMinor, it.currency, locale, compact = true) },
                )
            }
            WidgetSnapshot(
                dateText = dateText,
                remainingToday = sorted.count { it.dueDate == today },
                overdue = sorted.count { it.dueDate!!.isBefore(today) },
                next = next?.let { WidgetReminder(it.id, if (settings.widgetHideTitles) hiddenTitle else it.title, label(it.dueDate!!, it.dueTime), false) },
                upcoming = upcoming,
                renewal = renewal,
            )
        } catch (_: Exception) {
            WidgetSnapshot(dateText, 0, 0, null, emptyList(), null, failed = true)
        }
    }
}
