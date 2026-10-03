package app.pocketos.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.pocketos.MainActivity
import app.pocketos.R
import app.pocketos.core.money.MoneyFormatter
import app.pocketos.domain.model.Reminder
import app.pocketos.domain.model.Subscription
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Builds and posts notifications. Privacy defaults are conservative:
 *  - on the lock screen only a generic public version is shown,
 *  - amounts are never included unless the user enables
 *    "Show sensitive information in notifications".
 */
class Notifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_reminders_desc)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
                enableVibration(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RENEWALS, context.getString(R.string.channel_renewals), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_renewals_desc)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            }
        )
    }

    /** A dedicated channel per custom sound (Android fixes sounds per channel). */
    private fun reminderChannelFor(soundUri: String?): String {
        if (soundUri.isNullOrBlank() || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return CHANNEL_REMINDERS
        val id = CHANNEL_REMINDERS + "_s" + Integer.toHexString(soundUri.hashCode())
        val nm = context.getSystemService(NotificationManager::class.java) ?: return CHANNEL_REMINDERS
        if (nm.getNotificationChannel(id) == null) {
            nm.createNotificationChannel(
                NotificationChannel(id, context.getString(R.string.channel_reminders_custom_sound), NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(Uri.parse(soundUri), AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build())
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
                }
            )
        }
        return id
    }

    fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return manager.areNotificationsEnabled()
    }

    fun showReminder(reminder: Reminder, snoozeMinutes: Int, requireAuthForActions: Boolean) {
        if (!canPost()) return
        val id = notificationId("r", reminder.id)
        val timeText = reminder.dueTime?.let { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()).format(it) }
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_pocketos)
            .setContentTitle(context.getString(R.string.notif_reminder_public_title))
            .setContentText(context.getString(R.string.notif_unlock_to_view))
            .build()
        val builder = NotificationCompat.Builder(context, reminderChannelFor(reminder.soundUri))
            .setSmallIcon(R.drawable.ic_stat_pocketos)
            .setColor(0xFF7B6CFF.toInt())
            .setContentTitle(reminder.title)
            .setContentText(reminder.notes?.take(200) ?: timeText ?: context.getString(R.string.notif_reminder_due))
            .setStyle(reminder.notes?.let { NotificationCompat.BigTextStyle().bigText(it.take(500)) })
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .setContentIntent(openIntent("pocketos://reminder/${reminder.id}", id))
            .addAction(action(R.drawable.ic_action_check, R.string.action_complete, NotificationActionReceiver.ACTION_COMPLETE, reminder.id, id, requireAuthForActions))
            .addAction(
                action(
                    R.drawable.ic_action_snooze,
                    context.getString(R.string.action_snooze_minutes, snoozeMinutes),
                    NotificationActionReceiver.ACTION_SNOOZE, reminder.id, id, requireAuthForActions,
                )
            )
        post(id, builder)
    }

    fun showRenewal(sub: Subscription, daysUntil: Long, trialEnding: Boolean, showSensitive: Boolean, requireAuthForActions: Boolean) {
        if (!canPost()) return
        val id = notificationId("s", sub.id)
        val title = when {
            trialEnding -> context.getString(R.string.notif_trial_ending_title, sub.name)
            daysUntil <= 0 -> context.getString(R.string.notif_renews_today_title, sub.name)
            daysUntil == 1L -> context.getString(R.string.notif_renews_tomorrow_title, sub.name)
            else -> context.resources.getQuantityString(R.plurals.notif_renews_in_days_title, daysUntil.toInt(), sub.name, daysUntil.toInt())
        }
        val text = if (showSensitive && sub.amount != null) {
            context.getString(R.string.notif_renewal_amount, MoneyFormatter.format(sub.amount.amountMinor, sub.amount.currency, Locale.getDefault()))
        } else {
            context.getString(R.string.notif_renewal_generic)
        }
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_RENEWALS)
            .setSmallIcon(R.drawable.ic_stat_pocketos)
            .setContentTitle(context.getString(R.string.notif_renewal_public_title))
            .setContentText(context.getString(R.string.notif_unlock_to_view))
            .build()
        val builder = NotificationCompat.Builder(context, CHANNEL_RENEWALS)
            .setSmallIcon(R.drawable.ic_stat_pocketos)
            .setColor(0xFF7B6CFF.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .setContentIntent(openIntent("pocketos://subscription/${sub.id}", id))
            .addAction(
                NotificationCompat.Action.Builder(R.drawable.ic_action_open, context.getString(R.string.action_view), openIntent("pocketos://subscription/${sub.id}", id + 1))
                    .setAuthenticationRequired(requireAuthForActions)
                    .build()
            )
            .addAction(action(R.drawable.ic_action_snooze, R.string.action_remind_later, NotificationActionReceiver.ACTION_SNOOZE_RENEWAL, sub.id, id, requireAuthForActions))
        post(id, builder)
    }

    fun showInstallment(plan: app.pocketos.domain.finance.InstallmentPlan, daysUntil: Long, showSensitive: Boolean) {
        if (!canPost()) return
        val id = notificationId("i", plan.id)
        val title = when {
            daysUntil <= 0 -> context.getString(R.string.notif_installment_today, plan.title)
            else -> context.resources.getQuantityString(R.plurals.notif_installment_in_days, daysUntil.toInt(), plan.title, daysUntil.toInt())
        }
        val number = context.getString(R.string.installment_n_of_m, plan.paidCount + 1, plan.totalCount)
        val text = if (showSensitive) "$number \u00b7 " + MoneyFormatter.format(plan.amountMinor, plan.currency, Locale.getDefault()) else number
        postDue(id, title, text, "pocketos://installments")
    }

    fun showDebt(debt: app.pocketos.domain.finance.Debt, daysUntil: Long, showSensitive: Boolean) {
        if (!canPost()) return
        val id = notificationId("d", debt.id)
        val owe = debt.direction == app.pocketos.domain.finance.DebtDirection.I_OWE
        val title = when {
            daysUntil <= 0 -> context.getString(if (owe) R.string.notif_debt_pay_today else R.string.notif_debt_collect_today, debt.person)
            else -> context.getString(if (owe) R.string.notif_debt_pay_soon else R.string.notif_debt_collect_soon, debt.person)
        }
        val text = if (showSensitive) MoneyFormatter.format(debt.remainingMinor, debt.currency, Locale.getDefault()) else context.getString(R.string.notif_renewal_generic)
        postDue(id, title, text, "pocketos://debts")
    }

    private fun postDue(id: Int, title: String, text: String, uri: String) {
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_RENEWALS)
            .setSmallIcon(R.drawable.ic_stat_pocketos)
            .setContentTitle(context.getString(R.string.notif_payment_public_title))
            .setContentText(context.getString(R.string.notif_unlock_to_view))
            .build()
        val builder = NotificationCompat.Builder(context, CHANNEL_RENEWALS)
            .setSmallIcon(R.drawable.ic_stat_pocketos)
            .setColor(0xFF7B6CFF.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .setContentIntent(openIntent(uri, id))
        post(id, builder)
    }

    fun cancel(prefix: String, itemId: String) = manager.cancel(notificationId(prefix, itemId))

    private fun post(id: Int, builder: NotificationCompat.Builder) {
        try {
            manager.notify(id, builder.build())
        } catch (_: SecurityException) {
            // Notification permission revoked: nothing to do; the app shows a hint instead.
        }
    }

    private fun openIntent(uri: String, requestCode: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun action(icon: Int, label: Int, action: String, itemId: String, notificationId: Int, auth: Boolean) =
        action(icon, context.getString(label), action, itemId, notificationId, auth)

    private fun action(icon: Int, label: String, action: String, itemId: String, notificationId: Int, auth: Boolean): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            data = Uri.parse("pocketos://action/$action/$itemId")
            putExtra(NotificationActionReceiver.EXTRA_ID, itemId)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val pi = PendingIntent.getBroadcast(context, notificationId, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Action.Builder(icon, label, pi).setAuthenticationRequired(auth).build()
    }

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_RENEWALS = "renewals"

        fun notificationId(prefix: String, id: String): Int = ("$prefix:$id").hashCode() and 0x3FFFFFFF
    }
}
