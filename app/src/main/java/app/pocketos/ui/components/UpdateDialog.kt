package app.pocketos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pocketos.R
import app.pocketos.ui.design.ButtonStyle
import app.pocketos.ui.design.GlassBottomSheet
import app.pocketos.ui.design.GlassCard
import app.pocketos.ui.design.GlassDialog
import app.pocketos.ui.design.GlassLevel
import app.pocketos.ui.design.GlassProgressBar
import app.pocketos.ui.design.HapticType
import app.pocketos.ui.design.PocketButton
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.Spacing
import app.pocketos.updater.AppUpdateManager
import app.pocketos.updater.UpdateState

@Composable
fun UpdateOverlay(updateManager: AppUpdateManager, state: UpdateState) {
    val context = LocalContext.current
    val c = LocalPocketColors.current

    when (state) {
        is UpdateState.Available -> {
            GlassBottomSheet(
                onDismiss = { updateManager.dismiss() },
                title = stringResource(R.string.update_available_title),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(c.accent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.SystemUpdate, null, tint = c.accent, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(Spacing.md))
                        Column {
                            Text(
                                state.release.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = c.textPrimary
                            )
                            val sizeMb = if (state.release.apkSize > 0) " · %.1f MB".format(state.release.apkSize / (1024f * 1024f)) else ""
                            Text(
                                "v${state.release.version}$sizeMb",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.accent
                            )
                        }
                    }

                    if (state.release.changelog.isNotBlank()) {
                        Spacer(Modifier.height(Spacing.md))
                        Text(
                            stringResource(R.string.update_changelog_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = c.textSecondary
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        GlassCard(
                            Modifier.fillMaxWidth().height(140.dp),
                            level = GlassLevel.L1
                        ) {
                            Column(Modifier.verticalScroll(rememberScrollState())) {
                                Text(
                                    state.release.changelog,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = c.textPrimary
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(Spacing.md))
                    GlassCard(
                        Modifier.fillMaxWidth(),
                        level = GlassLevel.L1
                    ) {
                        Row(
                            Modifier.padding(Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = c.accent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Text(
                                stringResource(R.string.update_install_tip),
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary
                            )
                        }
                    }

                    Spacer(Modifier.height(Spacing.lg))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        PocketButton(
                            stringResource(R.string.view_on_github),
                            { updateManager.openReleasePage(state.release) },
                            icon = Icons.AutoMirrored.Rounded.OpenInNew,
                            style = ButtonStyle.Glass,
                            modifier = Modifier.weight(1f)
                        )
                        PocketButton(
                            stringResource(R.string.download_apk),
                            { updateManager.openDownload(state.release) },
                            icon = Icons.Rounded.CloudDownload,
                            modifier = Modifier.weight(1.3f),
                            haptic = HapticType.Confirm
                        )
                    }
                    Spacer(Modifier.height(Spacing.xs))
                    PocketButton(
                        stringResource(R.string.update_later),
                        { updateManager.dismiss() },
                        style = ButtonStyle.Text,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        is UpdateState.Downloading -> {
            GlassBottomSheet(
                onDismiss = { updateManager.dismiss() },
                title = stringResource(R.string.downloading_update),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "PocketOS v${state.release.version}",
                            style = MaterialTheme.typography.titleMedium,
                            color = c.textPrimary
                        )
                        val percent = (state.progress * 100).toInt()
                        Text(
                            "$percent%",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = c.accent
                        )
                    }
                    Spacer(Modifier.height(Spacing.md))
                    GlassProgressBar(progress = state.progress)
                    Spacer(Modifier.height(Spacing.sm))
                    val readMb = state.bytesRead / (1024f * 1024f)
                    val totalMb = state.totalBytes / (1024f * 1024f)
                    Text(
                        "%.1f MB / %.1f MB".format(readMb, totalMb),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textTertiary
                    )
                    Spacer(Modifier.height(Spacing.lg))
                    PocketButton(
                        stringResource(R.string.cancel),
                        { updateManager.dismiss() },
                        style = ButtonStyle.Text,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        is UpdateState.ReadyToInstall -> {
            GlassBottomSheet(
                onDismiss = { updateManager.dismiss() },
                title = stringResource(R.string.update_ready_title),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(c.success.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.InstallMobile, null, tint = c.success, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(Spacing.md))
                        Column {
                            Text(
                                stringResource(R.string.update_ready_subtitle),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = c.textPrimary
                            )
                            Text(
                                "v${state.release.version}",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.success
                            )
                        }
                    }
                    Spacer(Modifier.height(Spacing.xl))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        PocketButton(
                            stringResource(R.string.cancel),
                            { updateManager.dismiss() },
                            style = ButtonStyle.Glass,
                            modifier = Modifier.weight(1f)
                        )
                        PocketButton(
                            stringResource(R.string.download_apk),
                            { updateManager.openDownload(state.release) },
                            icon = Icons.Rounded.CloudDownload,
                            modifier = Modifier.weight(1.5f),
                            haptic = HapticType.Confirm
                        )
                    }
                }
            }
        }

        is UpdateState.Error -> {
            if (state.isManual) {
                GlassDialog(
                    onDismiss = { updateManager.dismiss() },
                    title = stringResource(R.string.update_check_failed),
                    message = state.message,
                    confirmText = stringResource(R.string.dismiss),
                    onConfirm = { updateManager.dismiss() },
                    dismissText = ""
                )
            }
        }

        is UpdateState.UpToDate -> {
            GlassDialog(
                onDismiss = { updateManager.dismiss() },
                title = stringResource(R.string.up_to_date_title),
                message = stringResource(R.string.up_to_date_message),
                confirmText = stringResource(R.string.ok),
                onConfirm = { updateManager.dismiss() },
                dismissText = ""
            )
        }

        else -> Unit
    }
}
