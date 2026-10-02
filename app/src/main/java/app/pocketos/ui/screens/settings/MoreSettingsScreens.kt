package app.pocketos.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import app.pocketos.BuildConfig
import app.pocketos.R
import app.pocketos.data.backup.BackupType
import app.pocketos.data.prefs.AppSettings
import app.pocketos.ui.LocalAppContainer
import app.pocketos.ui.LocalAppUi
import app.pocketos.ui.design.ButtonStyle
import app.pocketos.ui.design.GlassCard
import app.pocketos.ui.design.GlassChip
import app.pocketos.ui.design.GlassDialog
import app.pocketos.ui.design.GlassLevel
import app.pocketos.ui.design.GlassSwitchRow
import app.pocketos.ui.design.GlassTextField
import app.pocketos.ui.design.PocketButton
import app.pocketos.ui.format.LocalFormatter
import app.pocketos.ui.navigation.Routes
import app.pocketos.ui.screens.common.PocketTimePickerDialog
import app.pocketos.ui.screens.reminders.snoozeLabel
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationSettingsScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val settings by container.settings.settings.collectAsState(initial = container.latestSettings)
    val f = LocalFormatter.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var notificationsAllowed by remember { mutableStateOf(container.notifier.canPost()) }
    var exact by remember { mutableStateOf(container.alarms.canScheduleExact()) }
    var pickTime by remember { mutableStateOf<String?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsAllowed = container.notifier.canPost()
        exact = container.alarms.canScheduleExact()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsAllowed = container.notifier.canPost() }
    fun set(t: (AppSettings) -> AppSettings) { scope.launch { container.settings.update(t) } }
    fun openSystem(intent: Intent) {
        container.appLock.allowExternalActivity()
        runCatching { context.startActivity(intent) }
    }

    SettingsPage(stringResource(R.string.settings_notifications), nav) {
        SettingsGroup(stringResource(R.string.permissions), footer = stringResource(R.string.exact_alarm_footer)) {
            SettingsRow(
                Icons.Rounded.Notifications, stringResource(R.string.notifications),
                stringResource(if (notificationsAllowed) R.string.allowed else R.string.not_allowed),
                trailing = if (notificationsAllowed) ({}) else null,
            ) {
                if (!notificationsAllowed && Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else openSystem(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }
            SettingsRow(
                Icons.Rounded.AlarmOn, stringResource(R.string.precise_timing),
                stringResource(if (exact) R.string.precise_timing_on else R.string.precise_timing_off),
            ) {
                if (Build.VERSION.SDK_INT >= 31) openSystem(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)))
            }
        }
        SettingsGroup(stringResource(R.string.defaults)) {
            SettingsRow(Icons.Rounded.Schedule, stringResource(R.string.default_reminder_time), f.time(settings.reminderDefaultTime)) { pickTime = "reminder" }
            SettingsRow(Icons.Rounded.Schedule, stringResource(R.string.renewal_reminder_time), f.time(settings.renewalReminderTime)) { pickTime = "renewal" }
        }
        Text(
            stringResource(R.string.snooze_duration),
            style = MaterialTheme.typography.labelLarge,
            color = LocalPocketColors.current.textSecondary,
            modifier = Modifier.padding(start = Spacing.xs, top = Spacing.xl, bottom = Spacing.sm),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            listOf(5, 10, 15, 30, 60).forEach { m -> GlassChip(snoozeLabel(m), settings.snoozeMinutes == m, { set { it.copy(snoozeMinutes = m) } }, leading = null) }
        }
        SettingsGroup(stringResource(R.string.privacy), footer = stringResource(R.string.notification_privacy_footer)) {
            GlassSwitchRow(
                stringResource(R.string.show_sensitive_in_notifications), settings.notificationsShowSensitive,
                { v -> set { it.copy(notificationsShowSensitive = v) } }, subtitle = stringResource(R.string.show_sensitive_subtitle), icon = Icons.Rounded.Visibility,
            )
        }
        if (Build.VERSION.SDK_INT >= 26) {
            Spacer(Modifier.height(Spacing.lg))
            PocketButton(
                stringResource(R.string.system_notification_settings),
                { openSystem(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) },
                style = ButtonStyle.Glass,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    pickTime?.let { which ->
        val initial = if (which == "reminder") settings.reminderDefaultTime else settings.renewalReminderTime
        PocketTimePickerDialog(initial, android.text.format.DateFormat.is24HourFormat(context), { pickTime = null }) { t ->
            set { if (which == "reminder") it.copy(reminderDefaultTime = t) else it.copy(renewalReminderTime = t) }
            pickTime = null
            scope.launch { container.notificationScheduler.reconcileAll() }
        }
    }
}

@Composable
fun PrivacyScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val settings by container.settings.settings.collectAsState(initial = container.latestSettings)
    val c = LocalPocketColors.current
    val scope = rememberCoroutineScope()

    fun set(t: (AppSettings) -> AppSettings) {
        scope.launch {
            container.settings.update(t)
            container.widgetUpdater.updateNow()
        }
    }

    SettingsPage(stringResource(R.string.settings_widgets_privacy), nav) {
        GlassCard(
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.lg),
            level = GlassLevel.L2,
            tint = c.accent,
        ) {
            Text("Privacy-First by Design", style = MaterialTheme.typography.titleMedium, color = c.textPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Your data stays on your device. PocketOS does not send personal details, reminders, or financial amounts to cloud servers. All information is secured with AES-256 encryption in a local database protected by Android Keystore.",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
        }

        SettingsGroup(stringResource(R.string.widgets), footer = stringResource(R.string.widgets_footer)) {
            GlassSwitchRow(stringResource(R.string.widget_hide_amounts), settings.widgetHideAmounts, { v -> set { it.copy(widgetHideAmounts = v) } }, icon = Icons.Rounded.Widgets)
            GlassSwitchRow(stringResource(R.string.widget_hide_titles), settings.widgetHideTitles, { v -> set { it.copy(widgetHideTitles = v) } }, icon = Icons.Rounded.Visibility)
        }

        SettingsGroup {
            SettingsRow(Icons.Rounded.Policy, stringResource(R.string.privacy_notice)) { nav.navigate(Routes.PrivacyNotice) }
        }
    }
}

@Composable
fun DataScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val ui = LocalAppUi.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val c = LocalPocketColors.current

    var pendingBytesToSave by remember { mutableStateOf<ByteArray?>(null) }
    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf("") }
    var exportPasswordError by remember { mutableStateOf<String?>(null) }

    var pendingImportBytes by remember { mutableStateOf<ByteArray?>(null) }
    var showImportPasswordDialog by remember { mutableStateOf(false) }
    var importPassword by remember { mutableStateOf("") }
    var importPasswordError by remember { mutableStateOf<String?>(null) }

    var confirmErase by remember { mutableStateOf(false) }

    val dateStr = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
    val backupSuccessMsg = stringResource(R.string.backup_success)
    val genericErrorMsg = stringResource(R.string.error_generic_title)

    // Saver launcher for encrypted binary (.pocketos) or JSON (.json)
    val fileSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val bytes = pendingBytesToSave
        if (uri != null && bytes != null) {
            scope.launch {
                runCatching {
                    container.backupManager.writeBytesTo(context.contentResolver, uri, bytes)
                }.onSuccess {
                    ui.message(backupSuccessMsg)
                }.onFailure {
                    ui.message(genericErrorMsg)
                }
            }
        }
        pendingBytesToSave = null
    }

    // Picker launcher for restoring backup
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    container.backupManager.readBytesFrom(context.contentResolver, uri)
                }.onSuccess { bytes ->
                    val type = container.backupManager.detectType(bytes)
                    when (type) {
                        is BackupType.Encrypted -> {
                            pendingImportBytes = bytes
                            importPassword = ""
                            importPasswordError = null
                            showImportPasswordDialog = true
                        }
                        is BackupType.PlainJson -> {
                            runCatching {
                                val json = String(bytes, Charsets.UTF_8)
                                container.backupManager.restoreFromJson(json)
                            }.onSuccess { stats ->
                                container.notificationScheduler.reconcileAll()
                                container.widgetUpdater.updateNow()
                                ui.message("Restored ${stats.remindersCount} reminders & ${stats.subscriptionsCount} subscriptions")
                            }.onFailure {
                                ui.message(context.getString(R.string.restore_failed))
                            }
                        }
                        is BackupType.Invalid -> {
                            ui.message(context.getString(R.string.restore_failed))
                        }
                    }
                }.onFailure {
                    ui.message(genericErrorMsg)
                }
            }
        }
    }

    SettingsPage(stringResource(R.string.settings_backup_title), nav) {
        SettingsGroup(
            stringResource(R.string.export),
            footer = "Backups contain your active reminders, subscriptions, and custom categories.",
        ) {
            SettingsRow(
                icon = Icons.Rounded.Lock,
                title = stringResource(R.string.backup_encrypted_action),
                subtitle = stringResource(R.string.backup_encrypted_desc),
                onClick = {
                    exportPassword = ""
                    exportPasswordError = null
                    showExportPasswordDialog = true
                },
            )
            SettingsRow(
                icon = Icons.Rounded.Download,
                title = stringResource(R.string.backup_plain_action),
                subtitle = stringResource(R.string.backup_plain_desc),
                onClick = {
                    scope.launch {
                        val json = container.backupManager.buildJsonPayload()
                        pendingBytesToSave = json.toByteArray(Charsets.UTF_8)
                        container.appLock.allowExternalActivity()
                        fileSaver.launch("pocketos-export-$dateStr.json")
                    }
                },
            )
        }

        SettingsGroup(
            "Restore",
            footer = "Importing a backup will merge records into your local encrypted database.",
        ) {
            SettingsRow(
                icon = Icons.Rounded.Upload,
                title = stringResource(R.string.restore_action),
                subtitle = stringResource(R.string.restore_desc),
                onClick = {
                    container.appLock.allowExternalActivity()
                    filePicker.launch(arrayOf("*/*"))
                },
            )
        }

        SettingsGroup(
            stringResource(R.string.delete),
            footer = stringResource(R.string.erase_local_footer),
        ) {
            SettingsRow(
                icon = Icons.Rounded.DeleteForever,
                title = stringResource(R.string.erase_device_data),
                tint = c.danger,
                onClick = { confirmErase = true },
            )
        }
    }

    // Dialog for creating an encrypted backup
    if (showExportPasswordDialog) {
        GlassDialog(
            onDismiss = { showExportPasswordDialog = false },
            title = stringResource(R.string.password_prompt_title),
            message = stringResource(R.string.password_prompt_message),
            confirmText = stringResource(R.string.save),
            onConfirm = {
                if (exportPassword.length < 4) {
                    exportPasswordError = "Password must be at least 4 characters"
                } else {
                    showExportPasswordDialog = false
                    scope.launch {
                        runCatching {
                            container.backupManager.createEncryptedBackup(exportPassword)
                        }.onSuccess { encryptedBytes ->
                            pendingBytesToSave = encryptedBytes
                            container.appLock.allowExternalActivity()
                            fileSaver.launch("pocketos-backup-$dateStr.pocketos")
                        }.onFailure {
                            ui.message(genericErrorMsg)
                        }
                    }
                }
            },
            dismissText = stringResource(R.string.cancel),
        ) {
            Spacer(Modifier.height(Spacing.md))
            GlassTextField(
                value = exportPassword,
                onValueChange = { exportPassword = it; exportPasswordError = null },
                placeholder = stringResource(R.string.password_hint),
                password = true,
                error = exportPasswordError,
            )
        }
    }

    // Dialog for decrypting an encrypted backup
    if (showImportPasswordDialog) {
        GlassDialog(
            onDismiss = {
                showImportPasswordDialog = false
                pendingImportBytes = null
            },
            title = stringResource(R.string.password_prompt_title),
            message = stringResource(R.string.password_restore_message),
            confirmText = stringResource(R.string.continue_label),
            onConfirm = {
                val bytes = pendingImportBytes
                if (bytes != null) {
                    showImportPasswordDialog = false
                    scope.launch {
                        runCatching {
                            val json = container.backupManager.decryptBackup(bytes, importPassword)
                            container.backupManager.restoreFromJson(json)
                        }.onSuccess { stats ->
                            container.notificationScheduler.reconcileAll()
                            container.widgetUpdater.updateNow()
                            ui.message("Restored ${stats.remindersCount} reminders & ${stats.subscriptionsCount} subscriptions")
                        }.onFailure {
                            ui.message(context.getString(R.string.restore_failed))
                        }
                        pendingImportBytes = null
                    }
                }
            },
            dismissText = stringResource(R.string.cancel),
        ) {
            Spacer(Modifier.height(Spacing.md))
            GlassTextField(
                value = importPassword,
                onValueChange = { importPassword = it; importPasswordError = null },
                placeholder = stringResource(R.string.password_hint),
                password = true,
                error = importPasswordError,
            )
        }
    }

    // Wipe all data confirmation
    if (confirmErase) {
        GlassDialog(
            onDismiss = { confirmErase = false },
            title = stringResource(R.string.erase_confirm_title),
            message = stringResource(R.string.erase_confirm_message),
            confirmText = stringResource(R.string.erase),
            onConfirm = {
                confirmErase = false
                scope.launch {
                    container.backupManager.clearAllData()
                    container.notificationScheduler.reconcileAll()
                    container.widgetUpdater.updateNow()
                    ui.message("All device data has been erased.")
                }
            },
            dismissText = stringResource(R.string.cancel),
            destructive = true,
        )
    }
}

@Composable
fun AboutScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val catalog by container.catalog.catalog.collectAsState()
    val c = LocalPocketColors.current
    val context = LocalContext.current

    SettingsPage(stringResource(R.string.about_pocketos), nav) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = c.textPrimary,
            modifier = Modifier.padding(top = Spacing.lg),
        )
        Text(
            stringResource(R.string.version_value, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            stringResource(R.string.about_body),
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
        )

        SettingsGroup("Updates & Source") {
            SettingsRow(
                icon = Icons.Rounded.SystemUpdate,
                title = stringResource(R.string.check_for_updates),
                subtitle = stringResource(R.string.check_for_updates_desc),
                onClick = { container.updateManager.checkForUpdates(isManual = true) },
            )
            SettingsRow(
                icon = Icons.Rounded.Code,
                title = stringResource(R.string.github_repository),
                subtitle = "github.com/pooriyayt/PocketOS",
                onClick = {
                    container.appLock.allowExternalActivity()
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/pooriyayt/PocketOS"))
                    runCatching { context.startActivity(intent) }
                },
            )
        }

        SettingsGroup(stringResource(R.string.legal)) {
            SettingsRow(Icons.Rounded.Policy, stringResource(R.string.privacy_notice)) { nav.navigate(Routes.PrivacyNotice) }
            SettingsRow(Icons.Rounded.Gavel, stringResource(R.string.brand_icons), stringResource(R.string.brand_icons_notice, catalog.iconSource?.version ?: "—"))
        }

        Text(
            stringResource(R.string.open_source_notice),
            style = MaterialTheme.typography.bodySmall,
            color = c.textTertiary,
            modifier = Modifier.padding(top = Spacing.lg),
        )
    }
}

@Composable
fun PrivacyNoticeScreen(nav: NavController) {
    val c = LocalPocketColors.current
    val sections = listOf(
        R.string.pn_local_title to R.string.pn_local_body,
        R.string.pn_export_title to R.string.pn_export_body,
        R.string.pn_notifications_title to R.string.pn_notifications_body,
        R.string.pn_disable_title to R.string.pn_disable_body,
        R.string.pn_security_title to R.string.pn_security_body,
    )
    SettingsPage(stringResource(R.string.privacy_notice), nav) {
        Text(
            "PocketOS is an offline-first, local-first application designed with total respect for your privacy.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
            modifier = Modifier.padding(top = Spacing.md),
        )
        sections.forEach { (title, body) ->
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.xs))
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        }
    }
}
