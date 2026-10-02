package app.pocketos.ui.screens.subscriptions

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.pocketos.R
import app.pocketos.domain.model.CategoryKind
import app.pocketos.domain.model.Subscription
import app.pocketos.domain.model.SubscriptionStatus
import app.pocketos.domain.recurrence.BillingCalculator
import app.pocketos.ui.LocalAppContainer
import app.pocketos.ui.components.EmptyState
import app.pocketos.ui.components.ServiceIcon
import app.pocketos.ui.components.SkeletonList
import app.pocketos.ui.components.StatusPill
import app.pocketos.ui.components.parseHex
import app.pocketos.ui.design.ButtonStyle
import app.pocketos.ui.design.GlassCard
import app.pocketos.ui.design.GlassDialog
import app.pocketos.ui.design.GlassLevel
import app.pocketos.ui.design.GlassToolbar
import app.pocketos.ui.design.HapticType
import app.pocketos.ui.design.PocketButton
import app.pocketos.ui.format.LocalFormatter
import app.pocketos.ui.navigation.Routes
import app.pocketos.ui.screens.common.categoryLabel
import app.pocketos.ui.screens.common.rememberItemActions
import app.pocketos.ui.screens.reminders.InfoRow
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.Spacing
import kotlin.math.roundToLong

private object Loading

@Composable
fun SubscriptionDetailScreen(nav: NavController, id: String) {
    val container = LocalAppContainer.current
    val loaded by remember(id) { container.subscriptions.observe(id) }.collectAsState(initial = Loading)
    val catalog by container.catalog.catalog.collectAsState()
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val actions = rememberItemActions()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val today = container.clock.today()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(top = 64.dp).padding(horizontal = Spacing.gutter).navigationBarsPadding()) {
            when (val value = loaded) {
                Loading -> SkeletonList(3)
                null -> EmptyState(Icons.Rounded.AccountBalanceWallet, stringResource(R.string.not_found_title), stringResource(R.string.not_found_subscription), actionLabel = stringResource(R.string.back), onAction = { nav.popBackStack() })
                is Subscription -> {
                    val s = value
                    val service = catalog.find(s.serviceId)
                    val brand = parseHex(s.color) ?: parseHex(service?.color) ?: c.accent
                    // Hero: a card painted in the service's own brand colours.
                    val brandEnd = androidx.compose.ui.graphics.lerp(brand, Color(0xFF0B0C1A), 0.45f)
                    app.pocketos.ui.components.GradientCard(
                        Modifier.fillMaxWidth().padding(bottom = Spacing.md),
                        colors = listOf(brand, brandEnd),
                    ) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(26.dp))
                                    .background(Color.White.copy(alpha = 0.18f)).padding(6.dp),
                            ) {
                                ServiceIcon(service, s.name, s.category, size = 76.dp, customColor = s.color)
                            }
                            Spacer(Modifier.height(Spacing.md))
                            Text(s.name, style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                s.amount?.let { f.money(it.amountMinor, it.currency) } ?: stringResource(R.string.amount_unknown),
                                style = MaterialTheme.typography.displaySmall,
                                color = Color.White,
                            )
                            Text(f.billing(s.billing), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                            Spacer(Modifier.height(Spacing.md))
                            val badge = app.pocketos.ui.components.subscriptionBadge(s, today)
                            Row(
                                Modifier.clip(androidx.compose.foundation.shape.CircleShape).background(Color.White.copy(alpha = 0.18f))
                                    .padding(horizontal = Spacing.md, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(badge.color))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (badge.detail != badge.label) "${badge.label} · ${badge.detail}" else badge.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                    GlassCard(Modifier.fillMaxWidth(), level = GlassLevel.L2) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            app.pocketos.ui.components.ToneIcon(Icons.Rounded.Event, c.tones.violet, size = 44.dp)
                            Spacer(Modifier.width(Spacing.md))
                            if (s.isActive) {
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.renews), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                                    Text(
                                        f.relative(s.nextRenewal, today),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = if (s.nextRenewal.isBefore(today)) c.danger else if (c.isDark) c.accentHighlight else c.accent,
                                    )
                                    Text(f.date(s.nextRenewal), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                                }
                            } else {
                                Text(f.billing(s.billing), style = MaterialTheme.typography.titleMedium, color = c.textSecondary, modifier = Modifier.weight(1f))
                            }
                        }
                        s.amount?.let { amount ->
                            Spacer(Modifier.height(Spacing.md))
                            val monthly = BillingCalculator.monthlyEstimate(amount.amountMinor, s.billing).roundToLong()
                            val annual = BillingCalculator.annualEstimate(amount.amountMinor, s.billing).roundToLong()
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                EstimateTile(stringResource(R.string.per_month_estimate), f.money(monthly, amount.currency, compact = true), Modifier.weight(1f))
                                EstimateTile(stringResource(R.string.per_year_estimate), f.money(annual, amount.currency, compact = true), Modifier.weight(1f))
                            }
                            Text(stringResource(R.string.estimate_note), style = MaterialTheme.typography.bodySmall, color = c.textTertiary, modifier = Modifier.padding(top = Spacing.xs))
                        }
                    }
                    Spacer(Modifier.height(Spacing.md))
                    GlassCard(Modifier.fillMaxWidth(), level = GlassLevel.L1) {
                        if (s.isActive) {
                            val upcoming = BillingCalculator.renewalsBetween(s, today, today.plusYears(2), 3)
                            InfoRow(Icons.Rounded.Event, stringResource(R.string.next_renewals), upcoming.joinToString(" · ") { f.date(it) })
                        }
                        InfoRow(Icons.Rounded.NotificationsActive, stringResource(R.string.remind_me), f.offsets(s.reminderOffsets))
                        s.trialEnd?.let { InfoRow(Icons.Rounded.HourglassBottom, stringResource(R.string.free_trial), stringResource(R.string.trial_ends_on, f.date(it))) }
                        s.startDate?.takeIf { it.isBefore(today) }?.let { InfoRow(Icons.Rounded.Repeat, stringResource(R.string.start_date), f.date(it)) }
                        InfoRow(Icons.Rounded.Category, stringResource(R.string.category), categoryLabel(CategoryKind.SUBSCRIPTION, s.category))
                        s.notes?.let { InfoRow(Icons.Rounded.Notes, stringResource(R.string.notes), it) }
                    }
                    s.cancellationUrl?.let { url ->
                        Spacer(Modifier.height(Spacing.md))
                        PocketButton(stringResource(R.string.open_cancellation_page), {
                            container.appLock.allowExternalActivity()
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        }, modifier = Modifier.fillMaxWidth(), style = ButtonStyle.Glass, icon = Icons.Rounded.OpenInNew)
                    }
                    Spacer(Modifier.height(Spacing.lg))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        PocketButton(stringResource(R.string.edit), { nav.navigate(Routes.SubscriptionEditor(s.id)) }, Modifier.weight(1f), icon = Icons.Rounded.Edit)
                        if (s.isActive) {
                            PocketButton(stringResource(R.string.pause), { actions.setStatus(s, SubscriptionStatus.PAUSED) }, Modifier.weight(1f), style = ButtonStyle.Glass, icon = Icons.Rounded.Pause)
                        } else {
                            PocketButton(stringResource(R.string.resume), { actions.setStatus(s, SubscriptionStatus.ACTIVE) }, Modifier.weight(1f), style = ButtonStyle.Glass, icon = Icons.Rounded.PlayArrow)
                        }
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        if (s.isActive) PocketButton(stringResource(R.string.mark_cancelled), { actions.setStatus(s, SubscriptionStatus.CANCELLED) }, Modifier.weight(1f), style = ButtonStyle.Text, icon = Icons.Rounded.Block)
                        PocketButton(stringResource(R.string.duplicate), { actions.duplicate(s) }, Modifier.weight(1f), style = ButtonStyle.Text, icon = Icons.Rounded.ContentCopy)
                        PocketButton(stringResource(R.string.delete), { confirmDelete = true }, Modifier.weight(1f), style = ButtonStyle.Text, icon = Icons.Rounded.DeleteOutline, haptic = HapticType.Warning)
                    }
                    Spacer(Modifier.height(Spacing.huge))
                    if (confirmDelete) {
                        GlassDialog(
                            onDismiss = { confirmDelete = false },
                            title = stringResource(R.string.delete_subscription_title),
                            message = stringResource(R.string.delete_subscription_message, s.name),
                            confirmText = stringResource(R.string.delete),
                            onConfirm = { confirmDelete = false; actions.delete(s); nav.popBackStack() },
                            dismissText = stringResource(R.string.cancel),
                            destructive = true,
                        )
                    }
                }
            }
        }
        GlassToolbar(title = "", onBack = { nav.popBackStack() }, backLabel = stringResource(R.string.back), elevated = false)
    }
}

@Composable
private fun EstimateTile(label: String, value: String, modifier: Modifier) {
    val c = LocalPocketColors.current
    GlassCard(modifier, level = GlassLevel.L1, contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = c.textPrimary)
    }
}
