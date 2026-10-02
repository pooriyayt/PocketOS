package app.pocketos.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pocketos.ui.design.LocalMotion
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.Spacing

data class ChartSlice(val label: String, val value: Float, val color: Color)

/**
 * Donut chart drawn on Canvas (no chart library). [description] is read by
 * screen readers instead of the graphic, so the data is never visual-only.
 */
@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 156.dp,
    center: @Composable () -> Unit = {},
) {
    val c = LocalPocketColors.current
    val motion = LocalMotion.current
    val progress = remember(slices) { Animatable(if (motion.reduced) 1f else 0f) }
    LaunchedEffect(slices) { progress.animateTo(1f, motion.emphasized()) }
    val total = slices.sumOf { it.value.toDouble() }.toFloat().takeIf { it > 0f } ?: 1f
    Box(modifier.size(size).clearAndSetSemantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(c.textTertiary.copy(alpha = 0.12f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            var start = -90f
            val gap = if (slices.size > 1) 2.5f else 0f
            slices.forEach { slice ->
                val sweep = 360f * (slice.value / total) * progress.value
                if (sweep > gap) {
                    drawArc(slice.color, start + gap / 2, sweep - gap, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                start += 360f * (slice.value / total) * progress.value
            }
        }
        center()
    }
}

/** Vertical bar chart for small series (e.g. completions per week). */
@Composable
fun BarChart(
    values: List<Int>,
    labels: List<String>,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    highlightLast: Boolean = true,
) {
    val c = LocalPocketColors.current
    val motion = LocalMotion.current
    val progress = remember(values) { Animatable(if (motion.reduced) 1f else 0f) }
    LaunchedEffect(values) { progress.animateTo(1f, motion.emphasized()) }
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val barWidth = slot * 0.52f
            values.forEachIndexed { i, v ->
                val h = (size.height * (v.toFloat() / max) * progress.value).coerceAtLeast(3.dp.toPx())
                val x = slot * i + (slot - barWidth) / 2
                val isLast = i == values.lastIndex && highlightLast
                drawRoundRect(
                    brush = if (isLast) Brush.verticalGradient(listOf(c.accentHighlight, c.accent)) else Brush.verticalGradient(listOf(c.accent.copy(alpha = 0.55f), c.accent.copy(alpha = 0.3f))),
                    topLeft = Offset(x, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(barWidth / 3),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = Spacing.xs), horizontalArrangement = Arrangement.SpaceEvenly) {
            labels.forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = c.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
    }
}
