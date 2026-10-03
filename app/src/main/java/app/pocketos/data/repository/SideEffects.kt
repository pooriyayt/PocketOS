package app.pocketos.data.repository

/**
 * Hooks fired after local writes. Implemented by the app container to keep
 * repositories independent of alarms, widgets and WorkManager.
 */
interface SideEffects {
    /** A reminder was created/changed (or deleted when [deleted]). */
    suspend fun onReminderChanged(id: String, deleted: Boolean) {}

    suspend fun onSubscriptionChanged(id: String, deleted: Boolean) {}

    /** An installment plan / debt changed: reschedule its due-date notification. */
    suspend fun onInstallmentChanged(id: String) {}

    suspend fun onDebtChanged(id: String) {}

    /** A check changed: reschedule its due-date reminder notification. */
    suspend fun onCheckChanged(id: String) {}

    /** Any user data changed: refresh widgets and schedule a sync. */
    fun onDataChanged() {}

    object None : SideEffects
}
