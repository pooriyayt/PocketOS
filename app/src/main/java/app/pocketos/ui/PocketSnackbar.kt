package app.pocketos.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pocketos.ui.design.ButtonStyle
import app.pocketos.ui.design.GlassLevel
import app.pocketos.ui.design.HapticType
import app.pocketos.ui.design.PocketButton
import app.pocketos.ui.design.glass
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.Shapes
import app.pocketos.ui.theme.Spacing

/** Glass snackbar; announced politely to screen readers. */
@Composable
fun PocketSnackbar(data: SnackbarData) {
    val c = LocalPocketColors.current
    Row(
        Modifier
            .padding(horizontal = Spacing.lg)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .glass(GlassLevel.L3, Shapes.card)
            .padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            data.visuals.message,
            style = MaterialTheme.typography.bodyMedium,
            color = c.textPrimary,
            modifier = Modifier.weight(1f).padding(vertical = Spacing.md),
        )
        data.visuals.actionLabel?.let { label ->
            Spacer(Modifier.width(Spacing.sm))
            PocketButton(label, { data.performAction() }, style = ButtonStyle.Text, haptic = HapticType.Confirm)
        }
    }
}
