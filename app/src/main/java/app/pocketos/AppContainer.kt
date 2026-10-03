package app.pocketos

import app.pocketos.ads.AdsManager
import android.app.Application
import app.pocketos.core.AppClock
import app.pocketos.core.ConnectivityMonitor
import app.pocketos.core.SystemAppClock
import app.pocketos.core.security.AppLockManager
import app.pocketos.core.security.BiometricUnlock
import app.pocketos.core.security.KeystorePinMac
import app.pocketos.core.security.KeystoreSecretBox
import app.pocketos.core.security.PinVault
import app.pocketos.data.MaintenanceScheduler
import app.pocketos.data.backup.BackupManager
import app.pocketos.data.local.DatabaseManager
import app.pocketos.data.prefs.AppSettings
import app.pocketos.data.prefs.SettingsRepository
import app.pocketos.data.repository.CatalogRepository
import app.pocketos.data.repository.CategoryRepository
import app.pocketos.data.repository.FinanceRepository
import app.pocketos.data.repository.ReminderRepository
import app.pocketos.data.repository.SideEffects
import app.pocketos.data.repository.SubscriptionRepository
import app.pocketos.notifications.AlarmScheduler
import app.pocketos.notifications.NotificationScheduler
import app.pocketos.notifications.Notifier
import app.pocketos.updater.AppUpdateManager
import app.pocketos.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Dependency container for PocketOS.
 *
 * Privacy-First & Local-First:
 * - 100% on-device storage with SQLCipher (AES-256) and Android Keystore.
 * - No server database, no user accounts, no telemetry or ads.
 * - Official In-App Updater for GitHub releases.
 * - Encrypted backups for moving data between devices.
 */
class AppContainer(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val clock: AppClock = SystemAppClock

    val settings = SettingsRepository(app)
    @Volatile var latestSettings: AppSettings = AppSettings()
        private set

    val databases = DatabaseManager(app, KeystoreSecretBox("pocketos_db_keys"))
    val connectivity = ConnectivityMonitor(app)

    val alarms = AlarmScheduler(app)
    val notifier = Notifier(app)
    val notificationScheduler = NotificationScheduler(databases, settings, alarms, notifier, clock)
    val widgetUpdater = WidgetUpdater(app, scope)

    private val effects = object : SideEffects {
        override suspend fun onReminderChanged(id: String, deleted: Boolean) = notificationScheduler.scheduleReminder(id)
        override suspend fun onSubscriptionChanged(id: String, deleted: Boolean) = notificationScheduler.scheduleRenewal(id)
        override suspend fun onInstallmentChanged(id: String) = notificationScheduler.scheduleInstallment(id)
        override suspend fun onDebtChanged(id: String) = notificationScheduler.scheduleDebt(id)
        override fun onDataChanged() {
            widgetUpdater.requestUpdate()
        }
    }

    val reminders = ReminderRepository(databases, clock, effects)
    val subscriptions = SubscriptionRepository(databases, clock, effects)
    val categories = CategoryRepository(databases, clock, effects)
    val finance = FinanceRepository(databases, clock, effects)
    val catalog = CatalogRepository(app)
    val backupManager = BackupManager(databases, clock)
    val updateManager = AppUpdateManager(app, scope)
    val ads = AdsManager(app)
    val maintenance = MaintenanceScheduler(app)

    val pinVault = PinVault(app, KeystorePinMac())
    val appLock = AppLockManager(pinVault)
    val biometric = BiometricUnlock()

    fun start() {
        notifier.ensureChannels()
        ads.init()
        maintenance.schedule()
        scope.launch { catalog.load() }
        scope.launch {
            settings.settings.collect { s ->
                latestSettings = s
                appLock.configure(s.appLockEnabled, s.relockAfterSeconds)
            }
        }
        scope.launch {
            subscriptions.rollForwardRenewals()
            notificationScheduler.reconcileAll()
        }
        // Non-blocking background check for official GitHub releases
        scope.launch {
            if (latestSettings.autoCheckUpdates) {
                updateManager.checkForUpdates(isManual = false)
            }
        }
    }
}
