package app.pocketos.ui.screens.quickadd

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pocketos.R
import app.pocketos.core.money.MoneyFormatter
import app.pocketos.domain.model.BillingCycle
import app.pocketos.domain.model.BillingUnit
import app.pocketos.domain.model.CategoryKind
import app.pocketos.domain.model.Money
import app.pocketos.domain.parser.QuickAddType
import app.pocketos.ui.LocalAppUi
import app.pocketos.ui.QuickAddRequest
import app.pocketos.ui.components.CategoryBadge
import app.pocketos.ui.components.ServiceIcon
import app.pocketos.ui.design.ButtonStyle
import app.pocketos.ui.design.GlassBottomSheet
import app.pocketos.ui.design.GlassCard
import app.pocketos.ui.design.GlassChip
import app.pocketos.ui.design.GlassDialog
import app.pocketos.ui.design.GlassLevel
import app.pocketos.ui.design.GlassSegmentedControl
import app.pocketos.ui.design.GlassTextField
import app.pocketos.ui.design.HapticType
import app.pocketos.ui.design.LocalHaptics
import app.pocketos.ui.design.LocalMotion
import app.pocketos.ui.design.PocketButton
import app.pocketos.ui.format.LocalFormatter
import app.pocketos.ui.pocketViewModel
import app.pocketos.ui.screens.common.CategoryPickerSheet
import app.pocketos.ui.screens.common.PocketDatePickerDialog
import app.pocketos.ui.screens.common.PocketTimePickerDialog
import app.pocketos.ui.screens.common.categoryLabel
import app.pocketos.ui.screens.reminders.RepeatSheet
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.Sizes
import app.pocketos.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * "Tell PocketOS what you need": natural language in, a confirmation card
 * out. Every interpreted value is visible and editable; guesses are marked
 * as such. Nothing is created until the user taps Add.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAddSheet(request: QuickAddRequest, onDismiss: () -> Unit, onOpenEditor: (QuickAddType, String?) -> Unit) {
    val vm = pocketViewModel(key = "quickadd-${request.hashCode()}") { QuickAddViewModel(it, request.prefill, request.preferredType) }
    val state by vm.state.collectAsState()
    val c = LocalPocketColors.current
    val haptics = LocalHaptics.current
    val motion = LocalMotion.current
    val ui = LocalAppUi.current
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val addedReminder = stringResource(R.string.reminder_added)
    val addedSubscription = stringResource(R.string.subscription_added)
    val addedTask = stringResource(R.string.task_added)
    val addedExpense = stringResource(R.string.expense_added)
    val addedIncome = stringResource(R.string.income_added)
    LaunchedEffect(request) {
        vm.reset(request.prefill, request.preferredType)
        runCatching { focus.requestFocus() }
    }

    GlassBottomSheet(onDismiss = onDismiss) {
        Text(stringResource(R.string.quick_add_title), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
        Spacer(Modifier.height(Spacing.xs))
        Text(stringResource(R.string.quick_add_subtitle), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        Spacer(Modifier.height(Spacing.lg))
        GlassTextField(
            value = state.text,
            onValueChange = vm::setText,
            placeholder = stringResource(R.string.quick_add_placeholder),
            modifier = Modifier.focusRequester(focus),
            imeAction = ImeAction.Done,
            singleLine = false,
            leading = { Icon(Icons.Rounded.AutoAwesome, null, tint = c.accentHighlight) },
        )
        Spacer(Modifier.height(Spacing.lg))

        AnimatedContent(
            targetState = state.draft == null,
            transitionSpec = { motion.cardEnter() togetherWith motion.tabExit() },
            label = "quickAddBody",
        ) { empty ->
            if (empty) {
                Column {
                    Text(stringResource(R.string.try_saying), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                    Spacer(Modifier.height(Spacing.sm))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        listOf(R.string.example_expense, R.string.example_income, R.string.example_netflix, R.string.example_call_mom, R.string.example_domain, R.string.example_internet).forEach { res ->
                            val text = stringResource(res)
                            GlassChip(text, false, { vm.setText(text) })
                        }
                    }
                    Spacer(Modifier.height(Spacing.xl))
                    Text(stringResource(R.string.or_fill_form), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                    Spacer(Modifier.height(Spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        PocketButton(stringResource(R.string.qa_reminder), { onOpenEditor(QuickAddType.REMINDER, null) }, Modifier.weight(1f), style = ButtonStyle.Glass)
                        PocketButton(stringResource(R.string.qa_subscription), { onOpenEditor(QuickAddType.SUBSCRIPTION, null) }, Modifier.weight(1f), style = ButtonStyle.Glass)
                    }
                }
            } else {
                val draft = state.draft ?: return@AnimatedContent
                Column {
                    ConfirmationCard(draft, vm)
                    Spacer(Modifier.height(Spacing.lg))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        PocketButton(stringResource(R.string.cancel), onDismiss, Modifier.weight(1f), style = ButtonStyle.Glass, haptic = HapticType.LightTap)
                        PocketButton(
                            stringResource(R.string.add),
                            {
                                scope.launch {
                                    val type = vm.add()
                                    if (type != null) {
                                        haptics.perform(HapticType.Success)
                                        ui.message(
                                            when (type) {
                                                QuickAddType.SUBSCRIPTION -> addedSubscription
                                                QuickAddType.TASK -> addedTask
                                                QuickAddType.REMINDER -> addedReminder
                                                QuickAddType.EXPENSE -> addedExpense
                                                QuickAddType.INCOME -> addedIncome
                                            }
                                        )
                                        onDismiss()
                                    } else {
                                        haptics.perform(HapticType.Error)
                                    }
                                }
                            },
                            Modifier.weight(1.4f),
                            enabled = draft.canAdd,
                            haptic = HapticType.MediumTap,
                        )
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    PocketButton(
                        stringResource(R.string.open_full_form),
                        { onOpenEditor(draft.type, draft.service?.id) },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        style = ButtonStyle.Text,
                        icon = Icons.Rounded.Tune,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmationCard(draft: QuickDraft, vm: QuickAddViewModel) {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val context = LocalContext.current
    var editing by remember { mutableStateOf<DraftField?>(null) }
    val today = java.time.LocalDate.now()
    val isSub = draft.type == QuickAddType.SUBSCRIPTION
    val kind = if (isSub) CategoryKind.SUBSCRIPTION else CategoryKind.REMINDER

    GlassCard(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, level = GlassLevel.L2, tint = c.accent, contentPadding = PaddingValues(Spacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = c.accentHighlight, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text(stringResource(R.string.understood_as), style = MaterialTheme.typography.labelLarge, color = c.accentHighlight)
        }
        Spacer(Modifier.height(Spacing.md))
        // Five kinds don't fit a segmented control; chips wrap gracefully in any language.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            listOf(QuickAddType.EXPENSE, QuickAddType.INCOME, QuickAddType.REMINDER, QuickAddType.TASK, QuickAddType.SUBSCRIPTION).forEach { t ->
                GlassChip(
                    stringResource(
                        when (t) {
                            QuickAddType.REMINDER -> R.string.kind_reminder
                            QuickAddType.TASK -> R.string.kind_task
                            QuickAddType.SUBSCRIPTION -> R.string.kind_subscription
                            QuickAddType.EXPENSE -> R.string.expense
                            QuickAddType.INCOME -> R.string.income
                        }
                    ),
                    draft.type == t,
                    { vm.changeType(t) },
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))

        if (isSub && draft.service != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Spacing.sm)) {
                ServiceIcon(draft.service, draft.title, draft.category, size = 40.dp)
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.service), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                    Text(draft.service.name, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                }
            }
        }
        if (draft.isMoney) {
            val txType = if (draft.type == QuickAddType.INCOME) app.pocketos.domain.finance.TxType.INCOME else app.pocketos.domain.finance.TxType.EXPENSE
            DraftRow(
                Icons.Rounded.Payments, stringResource(R.string.amount),
                draft.amount?.let { f.money(it.amountMinor, it.currency) } ?: stringResource(R.string.enter_amount),
                assumed = draft.amountScaled || draft.currencyAssumed,
                assumedNote = stringResource(if (draft.amountScaled) R.string.amount_scaled_note else R.string.currency_assumed),
                error = draft.amount == null,
            ) { editing = DraftField.AMOUNT }
            DraftRow(
                Icons.Rounded.Category, stringResource(R.string.category),
                app.pocketos.ui.screens.finance.financeCategoryName(draft.category),
                assumed = DraftField.CATEGORY !in draft.touched, assumedNote = stringResource(R.string.suggested),
                leading = {
                    app.pocketos.ui.components.ToneIcon(
                        app.pocketos.ui.screens.finance.financeCategoryIcon(draft.category),
                        app.pocketos.ui.screens.finance.financeCategoryColor(draft.category), size = 24.dp, filled = true,
                    )
                },
            ) { editing = DraftField.CATEGORY }
            DraftRow(Icons.Rounded.Event, stringResource(R.string.date), draft.date?.let { f.dayLabel(it, today) } ?: stringResource(R.string.today), assumed = false) { editing = DraftField.DATE }
            DraftRow(Icons.Rounded.Title, stringResource(R.string.note_optional), draft.title.ifBlank { "\u2014" }, assumed = false) { editing = DraftField.TITLE }
            when (editing) {
                DraftField.CATEGORY -> FinanceCategorySheet(txType, draft.category, { editing = null }) { cat -> vm.edit(DraftField.CATEGORY) { it.copy(category = cat) }; editing = null }
                else -> Unit
            }
        } else {
        DraftRow(Icons.Rounded.Title, stringResource(if (isSub) R.string.name else R.string.title), draft.title.ifBlank { stringResource(R.string.add_a_title) }, assumed = false, error = draft.title.isBlank()) { editing = DraftField.TITLE }
        }
        if (draft.isMoney) {
            // Money rows were rendered above.
        } else if (isSub) {
            DraftRow(
                Icons.Rounded.Payments, stringResource(R.string.amount),
                draft.amount?.let { f.money(it.amountMinor, it.currency) } ?: stringResource(R.string.amount_unknown),
                assumed = draft.currencyAssumed, assumedNote = stringResource(R.string.currency_assumed),
            ) { editing = DraftField.AMOUNT }
            DraftRow(Icons.Rounded.Repeat, stringResource(R.string.frequency), draft.billing?.let(f::billing).orEmpty(), assumed = draft.billingAssumed) { editing = DraftField.BILLING }
            DraftRow(Icons.Rounded.Event, stringResource(R.string.next_renewal), draft.date?.let { f.date(it, withWeekday = true) }.orEmpty(), assumed = draft.dateAssumed) { editing = DraftField.DATE }
            DraftRow(Icons.Rounded.NotificationsActive, stringResource(R.string.remind_me), f.offsets(draft.offsets), assumed = DraftField.OFFSETS !in draft.touched, assumedNote = stringResource(R.string.suggested)) { editing = DraftField.OFFSETS }
        } else {
            if (draft.type == QuickAddType.REMINDER || draft.date != null) {
                DraftRow(Icons.Rounded.Event, stringResource(R.string.date), draft.date?.let { f.dayLabel(it, today) } ?: stringResource(R.string.no_date), assumed = draft.dateAssumed) { editing = DraftField.DATE }
            }
            if (draft.date != null) {
                DraftRow(Icons.Rounded.Schedule, stringResource(R.string.time), draft.time?.let(f::time) ?: stringResource(R.string.all_day), assumed = draft.timeAssumed) { editing = DraftField.TIME }
            }
            if (draft.recurrence != null || draft.type == QuickAddType.REMINDER) {
                DraftRow(Icons.Rounded.Repeat, stringResource(R.string.repeat), f.recurrence(draft.recurrence), assumed = false) { editing = DraftField.RECURRENCE }
            }
        }
        if (!draft.isMoney) {
            DraftRow(Icons.Rounded.Category, stringResource(R.string.category), categoryLabel(kind, draft.category), assumed = DraftField.CATEGORY !in draft.touched, assumedNote = stringResource(R.string.suggested),
                leading = { CategoryBadge(kind, draft.category, null, size = 24.dp) }) { editing = DraftField.CATEGORY }
        }
    }

    when (editing) {
        DraftField.TITLE -> TextEditDialog(stringResource(if (isSub) R.string.name else R.string.title), draft.title, KeyboardType.Text, { editing = null }) { v ->
            vm.edit(DraftField.TITLE) { it.copy(title = v.take(200)) }
        }
        DraftField.AMOUNT -> AmountEditDialog(draft.amount, draft.amount?.currency ?: app.pocketos.ui.LocalAppContainer.current.latestSettings.defaultCurrency, { editing = null }) { m -> vm.edit(DraftField.AMOUNT) { it.copy(amount = m, currencyAssumed = false, amountScaled = false) } }
        DraftField.DATE -> app.pocketos.ui.components.CalendarDatePickerSheet(draft.date ?: today, { d -> vm.edit(DraftField.DATE) { it.copy(date = d, dateAssumed = false) }; editing = null }, { editing = null })
        DraftField.TIME -> PocketTimePickerDialog(draft.time ?: LocalTime.of(9, 0), android.text.format.DateFormat.is24HourFormat(context), { editing = null }) { t ->
            vm.edit(DraftField.TIME) { it.copy(time = t, timeAssumed = false) }; editing = null
        }
        DraftField.RECURRENCE -> RepeatSheet(draft.recurrence, draft.date ?: today, { editing = null }) { rule -> vm.edit(DraftField.RECURRENCE) { it.copy(recurrence = rule) }; editing = null }
        DraftField.BILLING -> BillingSheet(draft.billing, { editing = null }) { b -> vm.edit(DraftField.BILLING) { it.copy(billing = b, billingAssumed = false) }; editing = null }
        DraftField.CATEGORY -> if (!draft.isMoney) CategoryPickerSheet(kind, draft.category, { editing = null }) { cat -> vm.edit(DraftField.CATEGORY) { it.copy(category = cat) }; editing = null }
        DraftField.OFFSETS -> OffsetsSheet(draft.offsets, { editing = null }) { o -> vm.edit(DraftField.OFFSETS) { it.copy(offsets = o) }; editing = null }
        else -> Unit
    }
}

@Composable
private fun DraftRow(
    icon: ImageVector,
    label: String,
    value: String,
    assumed: Boolean,
    assumedNote: String? = null,
    error: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val c = LocalPocketColors.current
    val haptics = LocalHaptics.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touch)
            .selectable(false, role = Role.Button, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                haptics.perform(HapticType.LightTap)
                onClick()
            }
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) leading() else Icon(icon, null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.md))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.width(96.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(value, style = MaterialTheme.typography.bodyLarge, color = if (error) c.danger else c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            AnimatedVisibility(assumed) {
                Text(assumedNote ?: stringResource(R.string.assumed_tap_to_change), style = MaterialTheme.typography.labelSmall, color = c.accentHighlight)
            }
        }
    }
}

@Composable
private fun TextEditDialog(title: String, initial: String, keyboard: KeyboardType, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    GlassDialog(onDismiss, title, confirmText = stringResource(R.string.done), onConfirm = { onSave(value); onDismiss() }, dismissText = stringResource(R.string.cancel)) {
        GlassTextField(value, { value = it }, keyboardType = keyboard)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmountEditDialog(initial: Money?, currency: String, onDismiss: () -> Unit, onSave: (Money?) -> Unit) {
    var text by remember { mutableStateOf(initial?.let { MoneyFormatter.formatPlain(it.amountMinor, it.currency) }.orEmpty()) }
    var code by remember { mutableStateOf(currency) }
    var error by remember { mutableStateOf(false) }
    val errorText = stringResource(R.string.error_amount_invalid)
    GlassDialog(onDismiss, stringResource(R.string.amount), confirmText = stringResource(R.string.done), onConfirm = {
        if (text.isBlank()) { onSave(null); onDismiss() }
        else MoneyFormatter.parse(text, code)?.let { onSave(Money(it, code)); onDismiss() } ?: run { error = true }
    }, dismissText = stringResource(R.string.cancel)) {
        val f = app.pocketos.ui.format.LocalFormatter.current
        GlassTextField(
            text,
            { text = app.pocketos.ui.components.AmountInput.sanitize(it, code); error = false },
            keyboardType = KeyboardType.Decimal,
            error = if (error) errorText else null,
            textStyle = MaterialTheme.typography.headlineSmall,
            visualTransformation = remember(f.locale) { app.pocketos.ui.components.AmountInput.Grouping(f.locale.language == "fa") },
        )
        Spacer(Modifier.height(Spacing.md))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            app.pocketos.core.money.Currencies.common.take(8).forEach { cur -> app.pocketos.ui.components.CurrencyChip(cur, cur == code, { code = cur }) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BillingSheet(current: BillingCycle?, onDismiss: () -> Unit, onPick: (BillingCycle) -> Unit) {
    val f = LocalFormatter.current
    GlassBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.frequency)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            listOf(
                BillingCycle.WEEKLY, BillingCycle(BillingUnit.WEEK, 2), BillingCycle(BillingUnit.DAY, 30), BillingCycle.MONTHLY,
                BillingCycle(BillingUnit.MONTH, 3), BillingCycle(BillingUnit.MONTH, 6), BillingCycle.YEARLY, BillingCycle(BillingUnit.YEAR, 2),
            ).forEach { b -> GlassChip(f.billing(b), b == current, { onPick(b) }) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OffsetsSheet(current: List<Int>, onDismiss: () -> Unit, onPick: (List<Int>) -> Unit) {
    val f = LocalFormatter.current
    var selected by remember { mutableStateOf(current.toSet()) }
    GlassBottomSheet(onDismiss = { onPick(selected.sortedDescending()); onDismiss() }, title = stringResource(R.string.remind_me)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            listOf(0, 1, 2, 3, 7, 14, 30).forEach { d ->
                GlassChip(f.offsets(listOf(d)), d in selected, { selected = if (d in selected) selected - d else (selected + d).take(5).toSet() })
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        PocketButton(stringResource(R.string.done), { onPick(selected.sortedDescending()); onDismiss() }, modifier = Modifier.fillMaxWidth(), haptic = HapticType.Confirm)
    }
}

/** Money category picker: tone icons in a wrapping grid. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FinanceCategorySheet(type: app.pocketos.domain.finance.TxType, selected: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    GlassBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.category)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            app.pocketos.domain.finance.FinanceCategories.forType(type).forEach { cat ->
                GlassChip(
                    app.pocketos.ui.screens.finance.financeCategoryName(cat.id),
                    cat.id == selected,
                    { onPick(cat.id) },
                    leading = {
                        Icon(
                            app.pocketos.ui.screens.finance.financeCategoryIcon(cat.id), null,
                            tint = app.pocketos.ui.screens.finance.financeCategoryColor(cat.id), modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }
    }
}
