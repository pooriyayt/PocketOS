package app.pocketos.ui.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation destinations for Local-First PocketOS. */
object Routes {
    @Serializable data object Home
    @Serializable data object Reminders
    @Serializable data object Subscriptions
    @Serializable data object Wallet
    @Serializable data object Insights
    @Serializable data object Settings

    @Serializable data class ReminderDetail(val id: String)
    @Serializable data class ReminderEditor(val id: String? = null, val kind: String? = null)
    @Serializable data class SubscriptionDetail(val id: String)
    @Serializable data class SubscriptionEditor(val id: String? = null, val serviceId: String? = null)
    @Serializable data object Installments
    @Serializable data object Debts
    @Serializable data object Checks
    @Serializable data object Support
    @Serializable data object Calendar
    @Serializable data object Search

    @Serializable data object Security
    @Serializable data object Appearance
    @Serializable data object Notifications
    @Serializable data object Privacy
    @Serializable data object Data
    @Serializable data object About
    @Serializable data object PrivacyNotice
}
