package app.pocketos.ui.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.Radii
import app.pocketos.ui.theme.Shapes
import app.pocketos.ui.theme.Sizes
import app.pocketos.ui.theme.Spacing

// ---------------------------------------------------------------- Card

/** A physically pressable glass card. Long-press triggers [onLongClick] with a haptic. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    level: GlassLevel = GlassLevel.L2,
    shape: Shape = Shapes.card,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    tint: Color? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val haptics = LocalHaptics.current
    val interaction = remember { MutableInteractionSource() }
    val clickable = onClick != null || onLongClick != null
    Column(
        modifier = modifier
            .then(if (clickable) Modifier.pressFeedback(interaction, shape) else Modifier)
            .glass(level, shape, tint = tint)
            .then(
                if (clickable) Modifier.combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClickLabel = onClickLabel,
                    onClick = {
                        haptics.perform(HapticType.LightTap)
                        onClick?.invoke()
                    },
                    onLongClick = onLongClick?.let { long ->
                        {
                            haptics.perform(HapticType.LongPress)
                            long()
                        }
                    },
                ) else Modifier
            )
            .padding(contentPadding),
        content = content,
    )
}

// ------------------------------------------------------------- Buttons

enum class ButtonStyle { Primary, Tonal, Glass, Danger, Text }

@Composable
fun PocketButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    haptic: HapticType = HapticType.MediumTap,
) {
    val colors = LocalPocketColors.current
    val haptics = LocalHaptics.current
    val interaction = remember { MutableInteractionSource() }
    val shape = Shapes.pill
    val (bg, fg) = when (style) {
        ButtonStyle.Primary -> Brush.horizontalGradient(listOf(colors.accent, colors.accentDeep)) to colors.onAccent
        ButtonStyle.Danger -> Brush.horizontalGradient(listOf(colors.danger, colors.danger)) to Color.White
        ButtonStyle.Tonal -> Brush.horizontalGradient(listOf(colors.accent.copy(alpha = 0.16f), colors.accent.copy(alpha = 0.16f))) to colors.accent
        ButtonStyle.Glass, ButtonStyle.Text -> Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)) to colors.textPrimary
    }
    val alpha by animateFloatAsState(if (enabled) 1f else 0.45f, LocalMotion.current.standard(), label = "btnAlpha")
    Row(
        modifier = modifier
            .heightIn(min = Sizes.touch)
            .pressFeedback(interaction, shape, enabled)
            .then(if (style == ButtonStyle.Glass) Modifier.glass(GlassLevel.L2, shape) else Modifier.clip(shape).background(bg))
            .toggleableClick(enabled && !loading, interaction) {
                haptics.perform(haptic)
                onClick()
            }
            .padding(horizontal = if (style == ButtonStyle.Text) Spacing.md else Spacing.xl, vertical = Spacing.md)
            .then(Modifier.semantics { if (loading) contentDescription = text }),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            GlassProgressRing(progress = null, size = 18.dp, strokeWidth = 2.dp, color = fg.copy(alpha = alpha))
            Spacer(Modifier.width(Spacing.sm))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg.copy(alpha = alpha), modifier = Modifier.size(Sizes.iconSm))
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg.copy(alpha = alpha), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun Modifier.toggleableClick(enabled: Boolean, interaction: MutableInteractionSource, onClick: () -> Unit): Modifier =
    this.then(
        Modifier.selectable(selected = false, enabled = enabled, role = Role.Button, interactionSource = interaction, indication = null, onClick = onClick)
    )

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color? = null,
    level: GlassLevel = GlassLevel.L2,
    haptic: HapticType = HapticType.LightTap,
) {
    val colors = LocalPocketColors.current
    val haptics = LocalHaptics.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(maxOf(size, Sizes.touch))
            .padding((maxOf(size, Sizes.touch) - size) / 2)
            .pressFeedback(interaction, CircleShape)
            .glass(level, CircleShape, blur = level == GlassLevel.L3)
            .selectable(selected = false, role = Role.Button, interactionSource = interaction, indication = null) {
                haptics.perform(haptic)
                onClick()
            }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint ?: colors.textPrimary, modifier = Modifier.size(Sizes.icon))
    }
}

@Composable
fun GlassFloatingActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = LocalPocketColors.current
    val haptics = LocalHaptics.current
    val interaction = remember { MutableInteractionSource() }
    @OptIn(ExperimentalFoundationApi::class)
    Box(
        modifier = modifier
            .size(Sizes.fab)
            .pressFeedback(interaction, CircleShape)
            .glass(GlassLevel.L3, CircleShape, tint = colors.accent)
            .background(Brush.linearGradient(listOf(colors.accent.copy(alpha = 0.85f), colors.accentDeep.copy(alpha = 0.95f))), CircleShape)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = {
                    haptics.perform(HapticType.MediumTap)
                    onClick()
                },
                onLongClick = onLongClick?.let { { haptics.perform(HapticType.LongPress); it() } },
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(Sizes.iconLg))
    }
}

// ---------------------------------------------------------- Navigation

data class NavItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/**
 * Floating glass navigation bar with a Liquid / Drop / Morphing selection indicator.
 * Provides micro-spring bounce on icon selection, liquid droplet morphing, and haptic feedback.
 */
@Composable
fun GlassNavigationBar(items: List<NavItem>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalPocketColors.current
    val haptics = LocalHaptics.current
    val motion = LocalMotion.current
    val shape = RoundedCornerShape(Radii.xl)

    BoxWithConstraints(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .fillMaxWidth()
            .height(Sizes.navBarHeight)
            .glass(GlassLevel.L3, shape),
    ) {
        val count = items.size.coerceAtLeast(1)
        val itemWidth = maxWidth / count
        val targetOffset = itemWidth * selectedIndex.coerceAtLeast(0)

        // Liquid spring: fast, organic, slightly bouncy for morphing droplet effect
        val liquidSpring = androidx.compose.animation.core.spring<Dp>(
            dampingRatio = if (motion.reduced) 1f else 0.62f,
            stiffness = if (motion.reduced) 1000f else 380f,
        )
        val animatedOffset by animateDpAsState(targetOffset, liquidSpring, label = "liquidIndicator")

        if (selectedIndex >= 0) {
            // Morphing liquid droplet pill
            Box(
                Modifier
                    .offset(x = animatedOffset)
                    .width(itemWidth)
                    .fillMaxHeight()
                    .padding(horizontal = 6.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(Radii.lg))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                colors.accent.copy(alpha = if (colors.isDark) 0.25f else 0.16f),
                                colors.accentSoft.copy(alpha = if (colors.isDark) 0.16f else 0.08f),
                            )
                        )
                    )
                    .border(
                        1.dp,
                        Brush.horizontalGradient(
                            listOf(
                                colors.accent.copy(alpha = if (colors.isDark) 0.45f else 0.35f),
                                colors.accentHighlight.copy(alpha = if (colors.isDark) 0.20f else 0.15f),
                            )
                        ),
                        RoundedCornerShape(Radii.lg)
                    )
            )

            // Liquid droplet top highlight bar
            Box(
                Modifier
                    .offset(x = animatedOffset + (itemWidth - 24.dp) / 2, y = 4.dp)
                    .size(width = 24.dp, height = 3.dp)
                    .clip(Shapes.pill)
                    .background(
                        Brush.horizontalGradient(
                            listOf(colors.accentHighlight, colors.accent)
                        )
                    )
            )
        }

        Row(Modifier.fillMaxWidth().fillMaxHeight().selectableGroup()) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val tint by animateColorAsState(
                    if (selected) colors.accent else colors.textSecondary,
                    motion.standard(),
                    label = "navTint"
                )
                val iconScale by animateFloatAsState(
                    if (selected && !motion.reduced) 1.12f else 1.0f,
                    androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 420f),
                    label = "iconScale"
                )
                val iconOffsetY by animateDpAsState(
                    if (selected && !motion.reduced) (-2).dp else 0.dp,
                    androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 400f),
                    label = "iconOffsetY"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (!selected) haptics.perform(HapticType.Selection)
                            onSelect(index)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        if (selected) item.selectedIcon else item.icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier
                            .offset(y = iconOffsetY)
                            .graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                            }
                            .size(Sizes.icon)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
                        color = tint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Top bar: optional back button, title and actions over a blurred glass strip once content scrolls under it. */
@Composable
fun GlassToolbar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backLabel: String = "",
    elevated: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = LocalPocketColors.current
    val motion = LocalMotion.current
    val glassAlpha by animateFloatAsState(if (elevated) 1f else 0f, motion.standard(), label = "toolbarGlass")
    Box(modifier = modifier.fillMaxWidth()) {
        if (glassAlpha > 0.01f) {
            Box(Modifier.matchParentSize().graphicsLayerAlpha(glassAlpha).glass(GlassLevel.L3, RoundedCornerShape(0.dp), borderWidth = 0.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Spacing.sm, vertical = Spacing.sm).heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, backLabel, onBack, level = GlassLevel.L1)
                Spacer(Modifier.width(Spacing.xs))
            } else {
                Spacer(Modifier.width(Spacing.md))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            actions()
        }
    }
}

private fun Modifier.graphicsLayerAlpha(alpha: Float): Modifier = this.then(Modifier.graphicsLayer { this.alpha = alpha })

// ------------------------------------------------------ Segmented / chips

@Composable
fun <T> GlassSegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPocketColors.current
    val haptics = LocalHaptics.current
    val motion = LocalMotion.current
    val shape = Shapes.pill
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(44.dp).glass(GlassLevel.L1, shape)) {
        val index = options.indexOf(selected).coerceAtLeast(0)
        val segment = maxWidth / options.size
        val offset by animateDpAsState(segment * index, motion.standard(), label = "segment")
        Box(
            Modifier.offset(x = offset).width(segment).fillMaxHeight().padding(4.dp)
                .clip(shape).background(if (colors.isDark) colors.surfaceElevated else colors.surface)
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight().selectableGroup()) {
            options.forEach { option ->
                val isSelected = option == selected
                val tint by animateColorAsState(if (isSelected) colors.textPrimary else colors.textSecondary, motion.standard(), label = "segTint")
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .selectable(isSelected, role = Role.RadioButton, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            if (!isSelected) haptics.perform(HapticType.Selection)
                            onSelect(option)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label(option), style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun GlassChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    accent: Color? = null,
) {
    val colors = LocalPocketColors.current
    val haptics = LocalHaptics.current
    val motion = LocalMotion.current
    val interaction = remember { MutableInteractionSource() }
    val tone = accent ?: colors.accent
    val bg by animateColorAsState(if (selected) tone.copy(alpha = if (colors.isDark) 0.24f else 0.14f) else Color.Transparent, motion.standard(), label = "chipBg")
    val fg by animateColorAsState(if (selected) (if (colors.isDark) colors.accentHighlight else tone) else colors.textSecondary, motion.standard(), label = "chipFg")
    Row(
        modifier = modifier
            .heightIn(min = 36.dp)
            .pressFeedback(interaction, Shapes.pill)
            .glass(GlassLevel.L1, Shapes.pill)
            .background(bg, Shapes.pill)
            .selectable(selected, role = Role.Checkbox, interactionSource = interaction, indication = null) {
                haptics.perform(HapticType.Selection)
                onClick()
            }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let {
            it()
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

// --------------------------------------------------------------- Inputs

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    error: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    password: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalPocketColors.current
    val borderColor by animateColorAsState(if (error != null) colors.danger else Color.Transparent, LocalMotion.current.standard(), label = "fieldBorder")
    Column(modifier) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary, modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xs))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = textStyle.copy(color = colors.textPrimary),
            cursorBrush = Brush.verticalGradient(listOf(colors.accent, colors.accent)),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onAny = { onImeAction() }),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().semantics { if (label != null) contentDescription = label },
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .glass(GlassLevel.L1, Shapes.field)
                        .then(if (error != null) Modifier.background(colors.danger.copy(alpha = 0.06f), Shapes.field) else Modifier)
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                ) {
                    leading?.let {
                        it()
                        Spacer(Modifier.width(Spacing.sm))
                    }
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, style = textStyle, color = colors.textTertiary)
                        }
                        inner()
                    }
                    trailing?.let {
                        Spacer(Modifier.width(Spacing.sm))
                        it()
                    }
                }
            },
        )
        if (borderColor != Color.Transparent || error != null) {
            AnimatedVisibility(error != null, enter = LocalMotion.current.expandEnter(), exit = LocalMotion.current.expandExit()) {
                Text(error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = colors.danger, modifier = Modifier.padding(start = Spacing.xs, top = Spacing.xs))
            }
        }
    }
}

@Composable
fun GlassSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = LocalPocketColors.current
    val haptics = LocalHaptics.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch) {
                haptics.perform(if (it) HapticType.Toggle else HapticType.ToggleOff)
                onCheckedChange(it)
            }
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(it, null, tint = colors.accent, modifier = Modifier.size(Sizes.icon))
            Spacer(Modifier.width(Spacing.md))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) colors.textPrimary else colors.textTertiary)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary) }
        }
        Spacer(Modifier.width(Spacing.md))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.accent,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = colors.textTertiary.copy(alpha = 0.25f),
                uncheckedBorderColor = Color.Transparent,
                uncheckedThumbColor = if (colors.isDark) colors.textSecondary else Color.White,
            ),
        )
    }
}

// ------------------------------------------------------ Sheets & dialogs

/** Bottom sheet with an L3 glass container, keyboard/inset handling and drag-to-dismiss. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassBottomSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalPocketColors.current
    val haptics = LocalHaptics.current
    LaunchedEffect(Unit) { haptics.perform(HapticType.SheetOpen) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = Shapes.sheet,
        containerColor = if (colors.isDark) colors.surface.copy(alpha = 0.97f) else colors.surface.copy(alpha = 0.98f),
        scrimColor = colors.scrim,
        dragHandle = {
            Box(Modifier.padding(top = Spacing.md, bottom = Spacing.sm).size(width = 40.dp, height = 4.dp).clip(Shapes.pill).background(colors.textTertiary.copy(alpha = 0.5f)))
        },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = Spacing.gutter).padding(bottom = Spacing.lg)) {
            title?.let {
                Text(it, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary, modifier = Modifier.padding(bottom = Spacing.lg).semantics { heading() })
            }
            content()
        }
    }
}


@Composable
fun GlassDialog(
    onDismiss: () -> Unit,
    title: String,
    message: String? = null,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String,
    destructive: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = LocalPocketColors.current
    val haptics = LocalHaptics.current
    LaunchedEffect(destructive) { if (destructive) haptics.perform(HapticType.Warning) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(Spacing.xxl)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(Shapes.cardLarge)
                .background(colors.surfaceElevated)
                .glass(GlassLevel.L2, Shapes.cardLarge)
                .padding(Spacing.xxl),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
            message?.let {
                Spacer(Modifier.height(Spacing.sm))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            }
            content?.let {
                Spacer(Modifier.height(Spacing.lg))
                it()
            }
            Spacer(Modifier.height(Spacing.xxl))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PocketButton(dismissText, onDismiss, style = ButtonStyle.Text, haptic = HapticType.LightTap)
                Spacer(Modifier.width(Spacing.sm))
                PocketButton(
                    confirmText,
                    onConfirm,
                    style = if (destructive) ButtonStyle.Danger else ButtonStyle.Primary,
                    haptic = if (destructive) HapticType.Delete else HapticType.Confirm,
                )
            }
        }
    }
}

// ------------------------------------------------------------- Progress

/** Determinate (0..1) or indeterminate (null) ring. */
@Composable
fun GlassProgressRing(progress: Float?, modifier: Modifier = Modifier, size: Dp = 40.dp, strokeWidth: Dp = 4.dp, color: Color? = null) {
    val colors = LocalPocketColors.current
    val motion = LocalMotion.current
    val tint = color ?: colors.accent
    val animated by animateFloatAsState(progress ?: 0f, motion.standard(), label = "ring")
    val sweepAnim = if (progress == null) rememberIndeterminateRotation() else 0f
    Box(
        modifier
            .size(size)
            .semantics {
                if (progress != null) progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
            }
            .drawBehind {
                val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
                drawArc(tint.copy(alpha = 0.18f), 0f, 360f, false, style = stroke)
                if (progress != null) drawArc(tint, -90f, 360f * animated, false, style = stroke)
                else drawArc(tint, sweepAnim - 90f, 100f, false, style = stroke)
            },
    )
}

@Composable
fun GlassProgressBar(progress: Float, modifier: Modifier = Modifier, color: Color? = null) {
    val colors = LocalPocketColors.current
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), LocalMotion.current.standard(), label = "bar")
    Box(
        modifier.fillMaxWidth().height(8.dp).clip(Shapes.pill).background(colors.textTertiary.copy(alpha = 0.15f))
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) }
            .drawBehind {
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(color ?: colors.accentSoft, color ?: colors.accent)),
                    size = size.copy(width = size.width * animated),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
                )
            },
    )
}

@Composable
private fun rememberIndeterminateRotation(): Float {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "spin")
    val angle by transition.animateFloat(
        0f, 360f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.LinearEasing)),
        label = "spinAngle",
    )
    return angle
}

/** Unused-offset helper to keep imports tidy for drawBehind lambdas. */
internal val ZeroOffset = Offset.Zero
