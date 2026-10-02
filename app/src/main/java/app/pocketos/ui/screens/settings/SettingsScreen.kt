package app.pocketos.ui.screens.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.navigation.NavController
import app.pocketos.BuildConfig
import app.pocketos.R
import app.pocketos.data.prefs.AppLanguage
import app.pocketos.ui.LocalAppContainer
import app.pocketos.ui.components.BottomClearance
import app.pocketos.ui.navigation.Routes
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.Spacing

@Composable
fun SettingsScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val settings by container.settings.settings.collectAsState(initial = container.latestSettings)
    val c = LocalPocketColors.current

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Spacing.gutter)) {
        item { Spacer(Modifier.statusBarsPadding().height(Spacing.lg)) }
        item {
            Text(
                stringResource(R.string.nav_settings),
                style = MaterialTheme.typography.headlineMedium,
                color = c.textPrimary,
                modifier = Modifier.padding(top = Spacing.xl).semantics { heading() },
            )
        }
        item {
            SettingsGroup(stringResource(R.string.settings_security)) {
                SettingsRow(
                    Icons.Rounded.Security,
                    stringResource(R.string.privacy_notice),
                    stringResource(R.string.privacy_first_tagline),
                ) { nav.navigate(Routes.PrivacyNotice) }
                SettingsRow(
                    Icons.Rounded.Lock,
                    stringResource(R.string.settings_security),
                    stringResource(if (settings.appLockEnabled) R.string.app_lock_on else R.string.app_lock_off),
                ) { nav.navigate(Routes.Security) }
                SettingsRow(
                    Icons.Rounded.Widgets,
                    stringResource(R.string.settings_widgets_privacy),
                    stringResource(R.string.settings_widgets_privacy_subtitle),
                ) { nav.navigate(Routes.Privacy) }
            }
        }
        item {
            SettingsGroup(stringResource(R.string.settings_appearance)) {
                SettingsRow(
                    Icons.Rounded.Palette,
                    stringResource(R.string.settings_appearance),
                    stringResource(R.string.settings_appearance_subtitle),
                ) { nav.navigate(Routes.Appearance) }
                SettingsRow(
                    Icons.Rounded.Language,
                    stringResource(R.string.settings_language),
                    stringResource(
                        when (settings.language) {
                            AppLanguage.SYSTEM -> R.string.language_system
                            AppLanguage.ENGLISH -> R.string.language_english
                            AppLanguage.PERSIAN -> R.string.language_persian
                        }
                    ),
                ) { nav.navigate(Routes.Appearance) }
                SettingsRow(
                    Icons.Rounded.Notifications,
                    stringResource(R.string.settings_notifications),
                    stringResource(R.string.settings_notifications_subtitle),
                ) { nav.navigate(Routes.Notifications) }
            }
        }
        item {
            SettingsGroup(stringResource(R.string.settings_backup_title)) {
                SettingsRow(
                    Icons.Rounded.Storage,
                    stringResource(R.string.settings_export_delete),
                    stringResource(R.string.settings_export_delete_subtitle),
                ) { nav.navigate(Routes.Data) }
            }
        }
        item {
            SettingsGroup(stringResource(R.string.settings_about_group)) {
                SettingsRow(
                    Icons.Rounded.Info,
                    stringResource(R.string.about_pocketos),
                    stringResource(R.string.version_value, BuildConfig.VERSION_NAME),
                ) { nav.navigate(Routes.About) }
            }
        }
        item { BottomClearance() }
    }
}
