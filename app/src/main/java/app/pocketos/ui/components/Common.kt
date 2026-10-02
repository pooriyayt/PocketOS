package app.pocketos.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pocketos.R
import app.pocketos.ui.design.ButtonStyle
import app.pocketos.ui.design.GlassCard
import app.pocketos.ui.design.GlassLevel
import app.pocketos.ui.design.LocalMotion
import app.pocketos.ui.design.PocketButton
import app.pocketos.ui.design.glass
import app.pocketos.ui.design.auroraBackdrop
import app.pocketos.ui.design.sheenSweep
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.Shapes
import app.pocketos.ui.theme.Spacing

/** The living aurora backdrop behind every screen (see [app.pocketos.ui.design.auroraBackdrop]). */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().auroraBackdrop(), content = content)
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    val c = LocalPocketColors.current
    Row(modifier.fillMaxWidth().padding(top = Spacing.xxl, bottom = Spacing.sm).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
            color = c.textPrimary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (action != null && onAction != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (c.isDark) c.accentSoft else c.accent,
                modifier = Modifier
                    .clip(Shapes.pill)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
        }
    }
}

/**
 * Contextual empty state: illustration glyph in a glowing glass orb, a
 * human headline, a supporting line and an optional call to action.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    val c = LocalPocketColors.current
    val motion = LocalMotion.current
    val transition = rememberInfiniteTransition(label = "emptyGlow")
    val glow by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = if (motion.reduced) 0.55f else 0.9f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "glow",
    )
    Column(
        modifier.fillMaxWidth().padding(vertical = if (compact) Spacing.xl else Spacing.huge, horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(if (compact) 72.dp else 96.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Brush.radialGradient(listOf(c.accent.copy(alpha = 0.30f * glow), Color.Transparent)))
            }
            Box(
                Modifier
                    .size(if (compact) 52.dp else 64.dp)
                    .clip(RoundedCornerShape(if (compact) 18.dp else 22.dp))
                    .background(c.brandGradient),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(if (compact) 26.dp else 32.dp))
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Spacing.xs))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.lg))
            PocketButton(actionLabel, onAction, style = ButtonStyle.Tonal)
        }
    }
}

/** Calm, human error state. Never shows raw HTTP or server messages. */
@Composable
fun ErrorState(offline: Boolean, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    EmptyState(
        icon = if (offline) Icons.Rounded.CloudOff else Icons.Rounded.ErrorOutline,
        title = stringResource(if (offline) R.string.error_offline_title else R.string.error_generic_title),
        message = stringResource(if (offline) R.string.error_offline_message else R.string.error_generic_message),
        actionLabel = onRetry?.let { stringResource(R.string.retry) },
        onAction = onRetry,
        modifier = modifier,
        compact = true,
    )
}

/** Skeleton block with a gentle shimmer (static when reduced motion is on). */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, height: Dp = 16.dp, widthFraction: Float = 1f) {
    val c = LocalPocketColors.current
    val motion = LocalMotion.current
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shift by transition.animateFloat(0f, if (motion.reduced) 0f else 1f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "shift")
    Box(
        modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(Shapes.chip)
            .drawBehind {
                drawRect(c.skeleton)
                if (!motion.reduced) {
                    val x = size.width * (shift * 2f - 0.5f)
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, c.glassHighlight.copy(alpha = 0.18f), Color.Transparent), startX = x - size.width / 3, endX = x + size.width / 3))
                }
            },
    )
}

/** Skeleton matching a list row (icon + two lines + trailing value) to avoid layout jumps. */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    GlassCard(modifier.fillMaxWidth(), level = GlassLevel.L1) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(Shapes.icon).background(LocalPocketColors.current.skeleton))
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                SkeletonBlock(height = 14.dp, widthFraction = 0.6f)
                Spacer(Modifier.height(Spacing.sm))
                SkeletonBlock(height = 12.dp, widthFraction = 0.35f)
            }
            Spacer(Modifier.width(Spacing.md))
            SkeletonBlock(Modifier.width(56.dp), height = 14.dp)
        }
    }
}

@Composable
fun SkeletonList(count: Int = 4, modifier: Modifier = Modifier) {
    Column(modifier.clearAndSetSemantics { contentDescription = "Loading" }, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        repeat(count) { SkeletonRow() }
    }
}

/** Small status pill. Colour is always paired with text, never the only signal. */
@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null, dot: Boolean = false) {
    Row(
        modifier.clip(Shapes.pill).background(color.copy(alpha = 0.15f)).padding(horizontal = Spacing.sm, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot && icon == null) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(5.dp))
        }
        icon?.let {
            Icon(it, null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(3.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

/**
 * Rounded icon tile washed in a [tone] colour. The main way the app adds
 * colour: every kind of thing (reminder, payment, stat...) gets its own hue.
 */
@Composable
fun ToneIcon(icon: ImageVector, tone: Color, modifier: Modifier = Modifier, size: Dp = 40.dp, filled: Boolean = false) {
    val c = LocalPocketColors.current
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(
                if (filled) Brush.linearGradient(listOf(tone, tone.copy(alpha = 0.75f)))
                else Brush.linearGradient(listOf(tone.copy(alpha = if (c.isDark) 0.22f else 0.14f), tone.copy(alpha = if (c.isDark) 0.12f else 0.08f)))
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (filled) Color.White else tone, modifier = Modifier.size(size * 0.52f))
    }
}

/**
 * Hero surface: the signature gradient layered with coloured light blooms
 * (a "mesh" look), a hairline highlight, a soft coloured glow beneath and a
 * light sweep that glides across now and then. Content should be white.
 */
@Composable
fun GradientCard(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(Spacing.xl),
    onClick: (() -> Unit)? = null,
    colors: List<Color>? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val c = LocalPocketColors.current
    val shape = Shapes.cardLarge
    val base = colors ?: listOf(c.accent, c.accentGradientEnd)
    Column(
        modifier
            .shadow(22.dp, shape, clip = false, ambientColor = base.first().copy(alpha = 0.55f), spotColor = base.first().copy(alpha = 0.65f))
            .clip(shape)
            .background(Brush.linearGradient(base, start = Offset.Zero, end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)))
            .drawBehind {
                drawRect(Brush.radialGradient(listOf(c.tones.pink.copy(alpha = 0.45f), Color.Transparent), center = Offset(size.width * 1.0f, size.height * 0.0f), radius = size.width * 0.75f))
                drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), center = Offset(size.width * 0.15f, -size.height * 0.2f), radius = size.width * 0.6f))
                drawRect(Brush.radialGradient(listOf(Color(0xFF0B0C1A).copy(alpha = 0.30f), Color.Transparent), center = Offset(size.width * 0.1f, size.height * 1.2f), radius = size.width * 0.9f))
            }
            .sheenSweep()
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0.06f))), shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/** Inline, dismissible banner for helpful (non-alarming) notices. */
@Composable
fun InlineBanner(
    visible: Boolean,
    icon: ImageVector,
    title: String,
    message: String?,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    tone: Color? = null,
) {
    val c = LocalPocketColors.current
    val motion = LocalMotion.current
    AnimatedVisibility(visible, enter = motion.bannerEnter(), exit = motion.bannerExit(), modifier = modifier) {
        GlassCard(Modifier.fillMaxWidth(), level = GlassLevel.L2, tint = (tone ?: c.accent).copy(alpha = 0.6f)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(icon, null, tint = tone ?: c.accentHighlight, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                    message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.textSecondary) }
                }
            }
            if (actionLabel != null && onAction != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    PocketButton(actionLabel, onAction, style = ButtonStyle.Text)
                }
            }
        }
    }
}

@Composable
fun BottomClearance() = Spacer(Modifier.height(app.pocketos.ui.theme.Spacing.navBarClearance))

/** Stable per-icon colour, so rows in menus and settings each get their own hue without bookkeeping. */
fun autoTone(icon: ImageVector, tones: app.pocketos.ui.theme.PocketTones): Color {
    val palette = listOf(tones.violet, tones.blue, tones.green, tones.amber, tones.pink, tones.cyan, tones.orange)
    return palette[Math.floorMod(icon.name.hashCode(), palette.size)]
}
