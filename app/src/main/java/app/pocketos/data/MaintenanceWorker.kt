package app.pocketos.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.pocketos.PocketOsApp
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Daily housekeeping shortly after midnight: rolls passed renewal dates
 * forward, reconciles alarms (safety net), refreshes widgets for the new
 * day, prunes old local history, and cleans deleted tombstones.
 */
class MaintenanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as PocketOsApp).container
        c.subscriptions.rollForwardRenewals()
        c.notificationScheduler.reconcileAll()
        c.widgetUpdater.updateNow()
        val db = c.databases.current
        db.reminders().pruneEvents(c.clock.now().minus(Duration.ofDays(400)).toEpochMilli())

        // In local-first mode, deleted items are cleanly purged
        db.reminders().purgeSyncedTombstones()
        db.subscriptions().purgeSyncedTombstones()
        db.categories().purgeSyncedTombstones()

        if (c.latestSettings.autoCheckUpdates) {
            c.updateManager.checkForUpdates(isManual = false)
        }
        return Result.success()
    }
}

class MaintenanceScheduler(private val context: Context) {
    fun schedule() {
        val now = LocalDateTime.now()
        val nextRun = now.toLocalDate().plusDays(1).atTime(LocalTime.of(0, 15))
        val delayMinutes = Duration.between(now, nextRun).toMinutes().coerceAtLeast(1)
        val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("pocketos-maintenance", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
