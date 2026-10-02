package app.pocketos.ui.screens.settings

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.pocketos.R
import app.pocketos.ui.design.GlassCard
import app.pocketos.ui.design.GlassLevel
import app.pocketos.ui.design.GlassToolbar
import app.pocketos.ui.design.HapticType
import app.pocketos.ui.design.LocalHaptics
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.Sizes
import app.pocketos.ui.theme.Spacing

/** Scrollable settings page with a glass toolbar. Never hosts ads. */
@Composable
fun SettingsPage(title: String, nav: NavController, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(top = 64.dp)
                .padding(horizontal = Spacing.gutter).navigationBarsPadding(),
        ) {
            content()
            Spacer(Modifier.height(Spacing.huge))
        }
        GlassToolbar(title = title, onBack = { nav.popBackStack() }, backLabel = stringResource(R.string.back), elevated = true)
    }
}

@Composable
fun SettingsGroup(title: String? = null, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalPocketColors.current
    title?.let {
        Text(it, style = MaterialTheme.typography.labelLarge, color = c.textSecondary, modifier = Modifier.padding(start = Spacing.xs, top = Spacing.xl, bottom = Spacing.sm).semantics { heading() })
    }
    GlassCard(Modifier.fillMaxWidth(), level = GlassLevel.L1, contentPadding = PaddingValues(vertical = Spacing.xs), content = content)
    footer?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = c.textTertiary, modifier = Modifier.padding(start = Spacing.xs, end = Spacing.xs, top = Spacing.sm))
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    tint: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = LocalPocketColors.current
    val haptics = LocalHaptics.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(
                if (onClick != null) Modifier.selectable(false, role = Role.Button, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    haptics.perform(HapticType.LightTap)
                    onClick()
                } else Modifier
            )
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(it, null, tint = tint ?: c.accent, modifier = Modifier.size(Sizes.icon))
            Spacer(Modifier.width(Spacing.md))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = tint ?: c.textPrimary)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.textSecondary) }
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.textTertiary)
        }
    }
}
