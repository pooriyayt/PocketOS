package app.pocketos.data.repository

/**
 * Hooks fired after local writes. Implemented by the app container to keep
 * repositories independent of alarms, widgets and WorkManager.
 */
interface SideEffects {
    /** A reminder was created/changed (or deleted when [deleted]). */
    suspend fun onReminderChanged(id: String, deleted: Boolean) {}

    suspend fun onSubscriptionChanged(id: String, deleted: Boolean) {}

    /** Any user data changed: refresh widgets and schedule a sync. */
    fun onDataChanged() {}

    object None : SideEffects
}
