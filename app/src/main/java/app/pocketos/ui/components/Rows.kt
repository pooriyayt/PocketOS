package app.pocketos.ui.components

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pocketos.R
import app.pocketos.domain.catalog.ServiceInfo
import app.pocketos.domain.categories.Categories
import app.pocketos.domain.model.CategoryKind
import app.pocketos.domain.model.Priority
import app.pocketos.domain.model.Reminder
import app.pocketos.domain.model.Subscription
import app.pocketos.domain.model.SubscriptionStatus
import app.pocketos.ui.design.GlassCard
import app.pocketos.ui.design.GlassLevel
import app.pocketos.ui.design.HapticType
import app.pocketos.ui.design.LocalHaptics
import app.pocketos.ui.design.LocalMotion
import app.pocketos.ui.format.LocalFormatter
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.Sizes
import app.pocketos.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Circular completion control. Checking animates a fill and draws the tick
 * along its path, with a success haptic. Exposed to accessibility as a
 * checkbox with its own state description.
 */
@Composable
fun CompletionCheck(checked: Boolean, onToggle: () -> Unit, label: String, modifier: Modifier = Modifier, color: Color? = null) {
    val c = LocalPocketColors.current
    val motion = LocalMotion.current
    val haptics = LocalHaptics.current
    val tone = color ?: c.accent
    val fill by animateFloatAsState(if (checked) 1f else 0f, motion.emphasized(), label = "checkFill")
    val draw by animateFloatAsState(if (checked) 1f else 0f, motion.standard(), label = "checkDraw")
    val ring by animateColorAsState(if (checked) tone else c.textTertiary, motion.standard(), label = "checkRing")
    val stateText = stringResource(if (checked) R.string.state_completed else R.string.state_not_completed)
    Box(
        modifier
            .size(Sizes.touch)
            .toggleable(value = checked, role = Role.Checkbox, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                haptics.perform(if (!checked) HapticType.Success else HapticType.ToggleOff)
                onToggle()
            }
            .semantics { stateDescription = "$label, $stateText" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp)) {
            val r = size.minDimension / 2
            drawCircle(ring, r - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
            if (fill > 0f) drawCircle(tone, (r - 1.dp.toPx()) * fill)
            if (draw > 0f) {
                val path = Path().apply {
                    moveTo(size.width * 0.28f, size.height * 0.52f)
                    lineTo(size.width * 0.44f, size.height * 0.67f)
                    lineTo(size.width * 0.73f, size.height * 0.36f)
                }
                val measure = PathMeasure().apply { setPath(path, false) }
                val partial = Path()
                measure.getSegment(0f, measure.length * draw, partial, true)
                drawPath(partial, Color.White, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
fun ReminderRow(
    reminder: Reminder,
    today: LocalDate,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    level: GlassLevel = GlassLevel.L2,
    now: Instant = Instant.now(),
) {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val overdue = !reminder.isCompleted && reminder.dueDate != null && reminder.dueDate.isBefore(today)
    val snoozed = reminder.snoozedUntil?.isAfter(now) == true
    val categoryColor = categoryColor(CategoryKind.REMINDER, reminder.category)
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        decoration = {
            val start = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr) 0f else size.width
            drawRect(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    listOf((if (overdue) c.warning else categoryColor).copy(alpha = if (reminder.isCompleted) 0f else if (c.isDark) 0.16f else 0.08f), Color.Transparent),
                    center = Offset(start, size.height / 2),
                    radius = size.height * 1.5f,
                )
            )
        },
        level = level,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = Spacing.xs, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.sm),
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CompletionCheck(reminder.isCompleted, onToggle, reminder.title, color = categoryColor)
            Column(Modifier.weight(1f).padding(vertical = Spacing.xs)) {
                Text(
                    reminder.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (reminder.isCompleted) c.textTertiary else c.textPrimary,
                    textDecoration = if (reminder.isCompleted) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val whenText = when {
                        snoozed -> stringResource(R.string.snoozed_until, f.time(java.time.LocalTime.ofInstant(reminder.snoozedUntil, java.time.ZoneId.systemDefault())))
                        reminder.dueDate != null -> f.dateTime(reminder.dueDate, reminder.dueTime, today, reminder.allDay)
                        else -> stringResource(R.string.no_date)
                    }
                    Text(
                        whenText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overdue) c.warning else c.textSecondary,
                        maxLines = 1,
                    )
                    if (overdue) StatusPill(stringResource(R.string.overdue), c.warning)
                    if (snoozed) Icon(Icons.Rounded.Snooze, null, tint = c.textTertiary, modifier = Modifier.size(14.dp))
                    if (reminder.recurrence != null) Icon(Icons.Rounded.Repeat, stringResource(R.string.repeats), tint = c.textTertiary, modifier = Modifier.size(14.dp))
                }
            }
            if (reminder.priority == Priority.HIGH) {
                Icon(Icons.Rounded.Flag, stringResource(R.string.priority_high), tint = c.danger, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.xs))
            }
            Box(Modifier.size(8.dp).padding(0.dp)) {
                Canvas(Modifier.size(8.dp)) { drawCircle(categoryColor, center = Offset(size.width / 2, size.height / 2)) }
            }
        }
    }
}

/** Visual status of a subscription: what to call it and which tone to paint it. */
data class SubscriptionBadge(val label: String, val color: Color, val detail: String)

/**
 * One source of truth for how a subscription's state is shown, so the list,
 * detail screen and widgets never disagree. Covers trials, renewals that are
 * due soon or already past, and paused / cancelled / expired plans.
 */
@Composable
fun subscriptionBadge(subscription: Subscription, today: LocalDate): SubscriptionBadge {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val days = ChronoUnit.DAYS.between(today, subscription.nextRenewal)
    val trialEnd = subscription.trialEnd
    return when (subscription.status) {
        SubscriptionStatus.PAUSED -> SubscriptionBadge(stringResource(R.string.status_paused), c.tones.amber, stringResource(R.string.status_paused))
        SubscriptionStatus.CANCELLED -> SubscriptionBadge(stringResource(R.string.status_cancelled), c.tones.red, stringResource(R.string.status_cancelled))
        SubscriptionStatus.EXPIRED -> SubscriptionBadge(stringResource(R.string.status_expired), c.textTertiary, stringResource(R.string.status_expired))
        SubscriptionStatus.ACTIVE -> when {
            trialEnd != null && !trialEnd.isBefore(today) ->
                SubscriptionBadge(stringResource(R.string.status_trial), c.tones.cyan, stringResource(R.string.trial_ends_relative, f.relative(trialEnd, today)))
            days < 0 ->
                SubscriptionBadge(stringResource(R.string.status_overdue_payment), c.danger, stringResource(R.string.renewal_was_due, f.date(subscription.nextRenewal)))
            days == 0L ->
                SubscriptionBadge(stringResource(R.string.status_renews_soon), c.warning, stringResource(R.string.renews_today))
            days <= 3 ->
                SubscriptionBadge(stringResource(R.string.status_renews_soon), c.warning, stringResource(R.string.renews_relative, f.relative(subscription.nextRenewal, today)))
            days <= 7 ->
                SubscriptionBadge(stringResource(R.string.status_active), c.success, stringResource(R.string.renews_relative, f.relative(subscription.nextRenewal, today)))
            else ->
                SubscriptionBadge(stringResource(R.string.status_active), c.success, stringResource(R.string.renews_on, f.date(subscription.nextRenewal)))
        }
    }
}

@Composable
fun SubscriptionRow(
    subscription: Subscription,
    service: ServiceInfo?,
    today: LocalDate,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    showAmounts: Boolean = true,
) {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val badge = subscriptionBadge(subscription, today)
    val dim = if (subscription.isActive) 1f else 0.55f
    val brand = parseHex(subscription.color) ?: parseHex(service?.color) ?: categoryColor(CategoryKind.SUBSCRIPTION, subscription.category)
    GlassCard(
        modifier.fillMaxWidth(),
        decoration = {
            // Brand-coloured light behind the icon ties each row to its service.
            val start = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr) 0f else size.width
            drawRect(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    listOf(brand.copy(alpha = if (c.isDark) 0.20f * dim else 0.10f * dim), Color.Transparent),
                    center = Offset(start, size.height / 2),
                    radius = size.height * 1.6f,
                )
            )
        },
        onClick = onClick,
        onLongClick = onLongClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ServiceIcon(service, subscription.name, subscription.category, size = 48.dp, customColor = subscription.color, modifier = Modifier.graphicsLayer { alpha = dim })
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    subscription.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (subscription.isActive) c.textPrimary else c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(badge.label, badge.color, dot = true)
                    if (badge.detail != badge.label) {
                        Spacer(Modifier.width(6.dp))
                        Text(badge.detail, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 140.dp)) {
                val amount = subscription.amount
                Text(
                    if (amount != null && showAmounts) f.money(amount.amountMinor, amount.currency, compact = true) else "\u2014",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (subscription.isActive) c.textPrimary else c.textTertiary,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(f.billing(subscription.billing), style = MaterialTheme.typography.labelSmall, color = c.textTertiary, maxLines = 1)
            }
        }
    }
}

/** Compact card used in the horizontal renewals strip. */
@Composable
fun RenewalCard(subscription: Subscription, service: ServiceInfo?, date: LocalDate, today: LocalDate, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val soon = ChronoUnit.DAYS.between(today, date) <= 2
    GlassCard(modifier.width(164.dp), onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.lg)) {
        Row(verticalAlignment = Alignment.Top) {
            ServiceIcon(service, subscription.name, subscription.category, size = 42.dp, customColor = subscription.color)
            Spacer(Modifier.weight(1f))
            if (soon) StatusPill(stringResource(R.string.status_renews_soon), c.warning)
        }
        Spacer(Modifier.height(Spacing.md))
        Text(subscription.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            f.relative(date, today),
            style = MaterialTheme.typography.bodySmall,
            color = if (soon) c.warning else c.textSecondary,
            maxLines = 1,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            subscription.amount?.let { f.money(it.amountMinor, it.currency, compact = true) } ?: "\u2014",
            style = MaterialTheme.typography.titleLarge,
            color = c.textPrimary,
            maxLines = 1,
        )
    }
}

/** Category name for built-in ids (custom categories pass their own name). */
@Composable
fun categoryName(kind: CategoryKind, id: String, customName: String? = null): String {
    if (customName != null) return customName
    val res = when (kind) {
        CategoryKind.REMINDER -> reminderCategoryNames[id]
        CategoryKind.SUBSCRIPTION -> subscriptionCategoryNames[id]
    }
    return res?.let { stringResource(it) } ?: id.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private val reminderCategoryNames = mapOf(
    "personal" to R.string.cat_personal, "work" to R.string.cat_work, "health" to R.string.cat_health,
    "finance" to R.string.cat_finance, "home" to R.string.cat_home, "shopping" to R.string.cat_shopping,
    "learning" to R.string.cat_learning, "other" to R.string.cat_other,
)

private val subscriptionCategoryNames = mapOf(
    "entertainment" to R.string.cat_entertainment, "music" to R.string.cat_music, "ai" to R.string.cat_ai,
    "productivity" to R.string.cat_productivity, "developer" to R.string.cat_developer, "hosting" to R.string.cat_hosting,
    "domains" to R.string.cat_domains, "cloud_storage" to R.string.cat_cloud_storage, "design" to R.string.cat_design,
    "communication" to R.string.cat_communication, "security" to R.string.cat_security, "shopping" to R.string.cat_shopping,
    "gaming" to R.string.cat_gaming, "news" to R.string.cat_news, "education" to R.string.cat_education,
    "health" to R.string.cat_health, "utilities" to R.string.cat_utilities, "finance" to R.string.cat_finance,
    "home" to R.string.cat_home, "transport" to R.string.cat_transport, "other" to R.string.cat_other,
)

/** Unused import guard for Categories (kept for future custom icon lookups). */
internal val reminderCategoryIds = Categories.reminder.map { it.id }
