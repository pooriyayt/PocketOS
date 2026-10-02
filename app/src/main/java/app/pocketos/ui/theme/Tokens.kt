package app.pocketos.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Central design tokens. UI code must use these instead of ad-hoc values so
 * the whole app keeps one rhythm.
 */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val huge = 48.dp

    /** Horizontal screen gutter. */
    val gutter = 20.dp

    /** Space reserved under content for the floating navigation bar. */
    val navBarClearance = 112.dp
}

object Radii {
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 22.dp
    val xl = 28.dp
    val sheet = 32.dp
}

object Shapes {
    val chip = RoundedCornerShape(Radii.sm)
    val field = RoundedCornerShape(Radii.md)
    val card = RoundedCornerShape(Radii.lg)
    val cardLarge = RoundedCornerShape(Radii.xl)
    val sheet = RoundedCornerShape(topStart = Radii.sheet, topEnd = Radii.sheet)
    val pill = RoundedCornerShape(50)
    val icon = RoundedCornerShape(Radii.sm)
    val fab = RoundedCornerShape(Radii.lg)
}

object Sizes {
    /** Minimum touch target (Material / WCAG). */
    val touch = 48.dp
    val iconSm = 18.dp
    val icon = 22.dp
    val iconLg = 28.dp
    val serviceIcon = 44.dp
    val serviceIconLg = 72.dp
    val navBarHeight = 66.dp
    val fab = 60.dp

    /** Content column cap so tablets and landscape get a centred, readable layout. */
    val contentMaxWidth = 720.dp
    val navBarMaxWidth = 560.dp
}
