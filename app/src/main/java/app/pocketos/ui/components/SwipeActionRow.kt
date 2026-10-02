package app.pocketos.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.pocketos.ui.design.HapticType
import app.pocketos.ui.design.LocalHaptics
import app.pocketos.ui.design.LocalMotion
import app.pocketos.ui.theme.Shapes
import app.pocketos.ui.theme.Spacing
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

data class SwipeAction(val label: String, val icon: ImageVector, val color: Color, val onTrigger: () -> Unit)

/**
 * Swipe-to-act row. Dragging past the threshold gives one haptic tick and
 * reveals the action label; releasing beyond it triggers the action.
 * "Start" means the reading-start edge (right swipe in LTR). Swipe actions
 * are also exposed as accessibility custom actions, because gestures alone
 * are not accessible.
 */
@Composable
fun SwipeActionRow(
    modifier: Modifier = Modifier,
    startAction: SwipeAction? = null,
    endAction: SwipeAction? = null,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHaptics.current
    val motion = LocalMotion.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var armed by remember { mutableStateOf(false) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    val thresholdPx = with(density) { 96.dp.toPx() }

    BoxWithConstraints(
        modifier.fillMaxWidth().semantics {
            customActions = listOfNotNull(
                startAction?.let { a -> CustomAccessibilityAction(a.label) { a.onTrigger(); true } },
                endAction?.let { a -> CustomAccessibilityAction(a.label) { a.onTrigger(); true } },
            )
        },
    ) {
        val maxDrag = with(density) { maxWidth.toPx() } * 0.55f
        // Logical offset: positive = towards reading end (start action revealed).
        val logical = offset.value
        val reveal = if (logical > 0) startAction else if (logical < 0) endAction else null
        if (reveal != null) {
            val progress = (abs(logical) / thresholdPx).coerceIn(0f, 1f)
            Box(
                Modifier.matchParentSize().clip(Shapes.card).background(reveal.color.copy(alpha = 0.18f + 0.5f * progress)),
                contentAlignment = if (logical > 0) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Column(
                    Modifier.padding(horizontal = Spacing.xl).graphicsLayer {
                        val s = 0.8f + 0.2f * progress
                        scaleX = s
                        scaleY = s
                        alpha = 0.4f + 0.6f * progress
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(reveal.icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Text(reveal.label, style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
        }
        Box(
            Modifier
                .offset { IntOffset((if (rtl) -logical else logical).roundToInt(), 0) }
                .pointerInput(startAction, endAction, rtl) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val value = offset.value
                            val action = when {
                                value >= thresholdPx -> startAction
                                value <= -thresholdPx -> endAction
                                else -> null
                            }
                            scope.launch { offset.animateTo(0f, motion.standard()) }
                            armed = false
                            action?.onTrigger?.invoke()
                        },
                        onDragCancel = {
                            armed = false
                            scope.launch { offset.animateTo(0f, motion.standard()) }
                        },
                    ) { change, dragAmount ->
                        val delta = if (rtl) -dragAmount else dragAmount
                        val next = offset.value + delta
                        val allowed = when {
                            next > 0 && startAction == null -> 0f
                            next < 0 && endAction == null -> 0f
                            else -> next.coerceIn(-maxDrag, maxDrag)
                        }
                        if (allowed != offset.value) change.consume()
                        scope.launch { offset.snapTo(allowed) }
                        val nowArmed = abs(allowed) >= thresholdPx
                        if (nowArmed != armed) {
                            armed = nowArmed
                            if (nowArmed) haptics.perform(HapticType.SwipeThreshold)
                        }
                    }
                },
        ) {
            content()
        }
    }
}
