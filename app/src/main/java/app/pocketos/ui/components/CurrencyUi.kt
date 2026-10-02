package app.pocketos.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketos.core.money.Currencies
import app.pocketos.ui.design.GlassChip
import app.pocketos.ui.design.HapticType
import app.pocketos.ui.design.LocalHaptics
import app.pocketos.ui.design.LocalMotion
import app.pocketos.ui.format.LocalFormatter
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.Shapes
import app.pocketos.ui.theme.Spacing

/** A currency's flag in a soft round badge. */
@Composable
fun CurrencyFlag(code: String, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    val c = LocalPocketColors.current
    Box(
        modifier.size(size).clip(CircleShape).background(c.textPrimary.copy(alpha = if (c.isDark) 0.08f else 0.05f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(Currencies.flag(code), fontSize = (size.value * 0.58f).sp)
    }
}

/** Flag + code, used wherever a currency is named inline (totals, chips). */
@Composable
fun CurrencyLabel(code: String, modifier: Modifier = Modifier, color: Color? = null) {
    val c = LocalPocketColors.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(Currencies.flag(code), fontSize = 14.sp)
        Spacer(Modifier.width(6.dp))
        Text(code, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = color ?: c.textSecondary)
    }
}

/** Selectable chip showing a currency's flag and code. */
@Composable
fun CurrencyChip(code: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassChip(code, selected, onClick, modifier, leading = { Text(Currencies.flag(code), fontSize = 15.sp) })
}

/**
 * Full currency picker: one row per currency with its flag, code and local
 * name, and a check on the selected one. Sized for a bottom sheet.
 */
@Composable
fun CurrencyPickerList(selected: String, onPick: (String) -> Unit, modifier: Modifier = Modifier, codes: List<String> = Currencies.common) {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val haptics = LocalHaptics.current
    val motion = LocalMotion.current
    Column(modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        codes.forEach { code ->
            val isSelected = code == selected
            val bg by animateColorAsState(if (isSelected) c.accent.copy(alpha = if (c.isDark) 0.18f else 0.09f) else Color.Transparent, motion.standard(), label = "curBg")
            val outline by animateColorAsState(if (isSelected) c.accent.copy(alpha = 0.5f) else c.divider, motion.standard(), label = "curOutline")
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(Shapes.field)
                    .background(bg)
                    .border(1.dp, outline, Shapes.field)
                    .selectable(isSelected, role = Role.RadioButton, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        haptics.perform(HapticType.Selection)
                        onPick(code)
                    }
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CurrencyFlag(code, size = 36.dp)
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(code, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
                    Text(
                        Currencies.displayName(code, f.locale),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isSelected) Icon(Icons.Rounded.CheckCircle, null, tint = c.accent, modifier = Modifier.size(22.dp))
            }
        }
    }
}
