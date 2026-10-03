package app.pocketos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.pocketos.R
import app.pocketos.core.time.CalendarMath
import app.pocketos.domain.finance.MonthPeriod
import app.pocketos.ui.design.GlassBottomSheet
import app.pocketos.ui.design.GlassIconButton
import app.pocketos.ui.design.GlassLevel
import app.pocketos.ui.design.HapticType
import app.pocketos.ui.design.LocalHaptics
import app.pocketos.ui.design.PocketButton
import app.pocketos.ui.format.LocalFormatter
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields

/**
 * Date picker in the user's own calendar (Solar Hijri, lunar Hijri or
 * Gregorian): month header with arrows and a week grid. Returns an ISO date.
 */
@Composable
fun CalendarDatePickerSheet(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit, title: String? = null) {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val haptics = LocalHaptics.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val today = LocalDate.now()
    var offset by remember { mutableIntStateOf(0) }
    var selected by remember { mutableLongStateOf(initial.toEpochDay()) }
    val month = MonthPeriod.of(initial, f.calendar).plus(offset)
    val first = month.start
    val length = CalendarMath.monthLength(month.calendar, month.year, month.month)
    val weekStart = if (f.locale.language == "fa") DayOfWeek.SATURDAY else WeekFields.of(f.locale).firstDayOfWeek
    val lead = Math.floorMod(first.dayOfWeek.value - weekStart.value, 7)

    GlassBottomSheet(onDismiss = onDismiss, title = title) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(if (rtl) Icons.AutoMirrored.Rounded.KeyboardArrowRight else Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.previous_month), { offset-- }, size = 40.dp, level = GlassLevel.L1)
            Text(
                f.monthTitle(month.year, month.month, month.calendar),
                style = MaterialTheme.typography.titleMedium,
                color = c.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            GlassIconButton(if (rtl) Icons.AutoMirrored.Rounded.KeyboardArrowLeft else Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.next_month), { offset++ }, size = 40.dp, level = GlassLevel.L1)
        }
        Spacer(Modifier.height(Spacing.md))
        Row(Modifier.fillMaxWidth()) {
            (0 until 7).forEach { i ->
                Text(
                    f.weekdayShort(weekStart.plus(i.toLong())).take(if (f.locale.language == "fa") 1 else 2),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        val cells = lead + length
        Column {
            (0 until (cells + 6) / 7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    (0 until 7).forEach { col ->
                        val day = week * 7 + col - lead
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                            if (day in 0 until length) {
                                val date = first.plusDays(day.toLong())
                                val isSelected = date.toEpochDay() == selected
                                val isToday = date == today
                                Box(
                                    Modifier
                                        .heightIn(min = 40.dp)
                                        .aspectRatio(1f)
                                        .clip(CircleShape)
                                        .then(if (isSelected) Modifier.background(c.brandGradient) else Modifier)
                                        .then(if (isToday && !isSelected) Modifier.border(1.5.dp, c.accent, CircleShape) else Modifier)
                                        .selectable(isSelected, role = Role.RadioButton, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                            selected = date.toEpochDay()
                                            haptics.perform(HapticType.Selection)
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        f.dayOfMonth(date),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) c.onAccent else c.textPrimary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Text(
            f.date(LocalDate.ofEpochDay(selected), withYear = true, withWeekday = true),
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.md))
        PocketButton(
            stringResource(R.string.done),
            { onPick(LocalDate.ofEpochDay(selected)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            icon = Icons.Rounded.Check,
            haptic = HapticType.Confirm,
        )
    }
}

