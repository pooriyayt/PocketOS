package app.pocketos.ui.screens.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.North
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.South
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import app.pocketos.R
import app.pocketos.core.money.Currencies
import app.pocketos.core.money.MoneyFormatter
import app.pocketos.core.time.CalendarMath
import app.pocketos.domain.finance.CheckDirection
import app.pocketos.domain.finance.CheckItem
import app.pocketos.domain.finance.CheckStatus
import app.pocketos.domain.finance.CheckSummary
import app.pocketos.domain.finance.Debt
import app.pocketos.domain.finance.DebtDirection
import app.pocketos.domain.finance.InstallmentPlan
import app.pocketos.domain.finance.MonthPeriod
import app.pocketos.domain.finance.ObligationCalculator
import app.pocketos.domain.finance.Wallet
import app.pocketos.ui.LocalAppContainer
import app.pocketos.ui.LocalAppUi
import app.pocketos.ui.components.AmountInput
import app.pocketos.ui.components.BottomClearance
import app.pocketos.ui.components.CalendarDatePickerSheet
import app.pocketos.ui.components.CurrencyPickerSheet
import app.pocketos.ui.components.EmptyState
import app.pocketos.ui.components.GradientCard
import app.pocketos.ui.components.SectionHeader
import app.pocketos.ui.components.StatusPill
import app.pocketos.ui.components.ToneIcon
import app.pocketos.ui.design.ButtonStyle
import app.pocketos.ui.design.GlassBottomSheet
import app.pocketos.ui.design.GlassCard
import app.pocketos.ui.design.GlassChip
import app.pocketos.ui.design.GlassDialog
import app.pocketos.ui.design.GlassIconButton
import app.pocketos.ui.design.GlassLevel
import app.pocketos.ui.design.GlassProgressBar
import app.pocketos.ui.design.GlassSegmentedControl
import app.pocketos.ui.design.GlassTextField
import app.pocketos.ui.design.HapticType
import app.pocketos.ui.design.LocalHaptics
import app.pocketos.ui.design.PocketButton
import app.pocketos.ui.design.appear
import app.pocketos.ui.format.LocalFormatter
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.LocalDate

// =========================================================== Installments

@Composable
fun InstallmentsScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val ui = LocalAppUi.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val plans by container.finance.installments.collectAsState(initial = null)
    val settings by container.settings.settings.collectAsState(initial = container.latestSettings)
    val paidLabel = stringResource(R.string.installment_paid)
    val undoLabel = stringResource(R.string.undo)
    val context = androidx.compose.ui.platform.LocalContext.current
    var editing by remember { mutableStateOf<InstallmentPlan?>(null) }
    var creating by remember { mutableStateOf(false) }

    InstallmentsContent(
        plans = plans,
        defaultCurrency = settings.defaultCurrency,
        today = container.clock.today(),
        onBack = { nav.popBackStack() },
        onAdd = { creating = true },
        onEdit = { editing = it },
        onPay = { plan ->
            scope.launch {
                val note = plan.title + " · " + context.getString(R.string.installment_n_of_m, plan.paidCount + 1, plan.totalCount)
                val txId = container.finance.payInstallment(plan.id, note)
                haptics.perform(HapticType.Success)
                ui.undo(paidLabel, undoLabel) { container.finance.undoInstallmentPayment(plan.id, txId) }
            }
        },
    )
    if (creating || editing != null) {
        InstallmentEditorSheet(editing, settings.defaultCurrency) { creating = false; editing = null }
    }
}

@Composable
internal fun InstallmentsContent(
    plans: List<InstallmentPlan>?,
    defaultCurrency: String,
    today: LocalDate,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (InstallmentPlan) -> Unit,
    onPay: (InstallmentPlan) -> Unit,
) {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val all = plans.orEmpty()
    val active = ObligationCalculator.upcoming(all)
    val finished = all.filter { it.isFinished }
    val remaining = ObligationCalculator.remainingByCurrency(all)
    val primary = remaining.keys.firstOrNull { it == defaultCurrency } ?: remaining.keys.firstOrNull() ?: defaultCurrency
    val month = MonthPeriod.of(today, f.calendar)
    val dueThisMonth = ObligationCalculator.dueBetween(all, month.start, month.end)[primary] ?: 0
    val next = active.firstOrNull()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Spacing.gutter)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item { ScreenTitle(stringResource(R.string.installments), onBack, onAdd) }
        item {
            GradientCard(Modifier.fillMaxWidth().padding(top = Spacing.lg).appear(0), colors = listOf(Color(0xFF0EA5E9), Color(0xFF7B6CFF))) {
                Text(stringResource(R.string.remaining_to_pay).uppercase(f.locale), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Currencies.flag(primary), fontSize = 22.sp)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(f.money(remaining[primary] ?: 0, primary, compact = true), style = MaterialTheme.typography.headlineLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(Spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    HeroStat(stringResource(R.string.due_this_month), f.amount(dueThisMonth, primary), Modifier.weight(1f))
                    HeroStat(
                        stringResource(R.string.next_due),
                        next?.nextDue?.let { f.dayLabel(it, today) } ?: "—",
                        Modifier.weight(1f),
                    )
                }
            }
        }
        if (plans != null && all.isEmpty()) {
            item {
                EmptyState(
                    Icons.Rounded.EventRepeat,
                    stringResource(R.string.no_installments_title),
                    stringResource(R.string.no_installments_message),
                    actionLabel = stringResource(R.string.add_installment),
                    onAction = onAdd,
                )
            }
        }
        if (active.isNotEmpty()) {
            item { SectionHeader(pluralStringOf(R.plurals.open_installments, active.size)) }
            items(active, key = { it.id }) { plan ->
                InstallmentCard(plan, today, onEdit = { onEdit(plan) }, onPay = { onPay(plan) }, modifier = Modifier.padding(bottom = Spacing.md).animateItem())
            }
        }
        if (finished.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.finished)) }
            items(finished, key = { "f_" + it.id }) { plan ->
                InstallmentCard(plan, today, onEdit = { onEdit(plan) }, onPay = {}, modifier = Modifier.padding(bottom = Spacing.sm).animateItem())
            }
        }
        item { BottomClearance() }
    }
}

@Composable
private fun pluralStringOf(id: Int, count: Int): String {
    val f = LocalFormatter.current
    return androidx.compose.ui.res.pluralStringResource(id, count, count).let(f::localizeDigits)
}

@Composable
private fun ScreenTitle(title: String, onBack: () -> Unit, onAdd: (() -> Unit)?) {
    val c = LocalPocketColors.current
    Row(Modifier.fillMaxWidth().padding(top = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), onBack, level = GlassLevel.L1)
        Spacer(Modifier.width(Spacing.sm))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = c.textPrimary, modifier = Modifier.weight(1f).semantics { heading() }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (onAdd != null) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(c.brandGradient).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, stringResource(R.string.add), tint = c.onAccent) }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun InstallmentCard(plan: InstallmentPlan, today: LocalDate, onEdit: () -> Unit, onPay: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val due = plan.nextDue
    val days = due?.let { ObligationCalculator.daysUntil(it, today) }
    val tone = when {
        due == null -> c.success
        days!! < 0 -> c.danger
        days <= 3 -> c.warning
        else -> c.tones.blue
    }
    GlassCard(
        modifier.fillMaxWidth(),
        onClick = onEdit,
        decoration = {
            val start = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr) 0f else size.width
            drawRect(androidx.compose.ui.graphics.Brush.radialGradient(listOf(tone.copy(alpha = if (c.isDark) 0.16f else 0.08f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(start, 0f), radius = size.width * 0.8f))
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToneIcon(Icons.Rounded.EventRepeat, tone, size = 44.dp, filled = due != null)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(plan.title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(plan.lender, if (due != null) stringResource(R.string.installment_n_of_m, plan.paidCount + 1, plan.totalCount).let(f::localizeDigits) else null).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(f.money(plan.amountMinor, plan.currency, compact = true), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = c.textPrimary)
        }
        Spacer(Modifier.height(Spacing.md))
        GlassProgressBar(plan.progress, color = tone)
        Spacer(Modifier.height(6.dp))
        Row {
            Text(
                stringResource(R.string.paid_of_total, f.localizeDigits(plan.paidCount.toString()), f.localizeDigits(plan.totalCount.toString())),
                style = MaterialTheme.typography.labelMedium,
                color = c.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(stringResource(R.string.ends_on, f.date(plan.lastDue, withYear = true)), style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
        }
        Spacer(Modifier.height(Spacing.sm))
        // Per-installment remaining breakdown
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(c.surfaceElevated.copy(alpha = 0.65f))
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    stringResource(R.string.remaining_loan_amount),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textSecondary,
                )
                Text(
                    f.money(plan.remainingMinor, plan.currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (plan.isFinished) c.success else c.warning,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(R.string.total_loan_amount),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textTertiary,
                )
                Text(
                    f.money(plan.amountMinor * plan.totalCount, plan.currency, compact = true),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = c.textPrimary,
                )
                Text(
                    stringResource(R.string.remaining_installments_badge, plan.remainingCount, plan.totalCount).let(f::localizeDigits),
                    style = MaterialTheme.typography.labelSmall,
                    color = tone,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        if (due != null) {
            Spacer(Modifier.height(Spacing.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(f.date(due, withYear = true, withWeekday = true), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                    StatusPill(if (days!! < 0) stringResource(R.string.overdue) else f.relative(due, today), tone, dot = true)
                }
                PocketButton(stringResource(R.string.mark_paid), onPay, style = ButtonStyle.Tonal, icon = Icons.Rounded.Check, haptic = HapticType.Success)
            }
        } else {
            Spacer(Modifier.height(Spacing.sm))
            StatusPill(stringResource(R.string.paid_off), c.success, icon = Icons.Rounded.Check)
        }
    }
}

@Composable
private fun InstallmentEditorSheet(existing: InstallmentPlan?, defaultCurrency: String, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val wallets by container.finance.wallets.collectAsState(initial = emptyList())
    val calendar = existing?.calendar ?: f.calendar
    var title by rememberSaveable { mutableStateOf(existing?.title.orEmpty()) }
    var lender by rememberSaveable { mutableStateOf(existing?.lender.orEmpty()) }
    var currency by rememberSaveable { mutableStateOf(existing?.currency ?: defaultCurrency) }
    var amount by rememberSaveable { mutableStateOf(existing?.let { MoneyFormatter.formatPlain(it.amountMinor, it.currency) }.orEmpty()) }
    var total by rememberSaveable { mutableStateOf(existing?.totalCount ?: 12) }
    var paid by rememberSaveable { mutableStateOf(existing?.paidCount ?: 0) }
    var nextEpoch by rememberSaveable { mutableStateOf((existing?.nextDue ?: existing?.lastDue ?: CalendarMath.plusMonths(LocalDate.now(), 1, calendar)).toEpochDay()) }
    var interval by rememberSaveable { mutableStateOf(existing?.intervalMonths ?: 1) }
    var remind by rememberSaveable { mutableStateOf(existing?.reminderDays ?: 3) }
    var walletId by rememberSaveable { mutableStateOf(existing?.walletId ?: wallets.firstOrNull { it.currency == currency }?.id) }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    var pickCurrency by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val amountMinor = MoneyFormatter.parse(amount.ifEmpty { "0" }, currency) ?: 0
    val canSave = title.isNotBlank() && amountMinor > 0 && total >= 1

    GlassBottomSheet(onDismiss = onDismiss, title = stringResource(if (existing == null) R.string.add_installment else R.string.edit_installment)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            GlassTextField(title, { title = it.take(80) }, label = stringResource(R.string.installment_title), placeholder = stringResource(R.string.installment_title_hint))
            Spacer(Modifier.height(Spacing.md))
            GlassTextField(lender, { lender = it.take(80) }, label = stringResource(R.string.lender_optional), placeholder = stringResource(R.string.lender_hint))
            Spacer(Modifier.height(Spacing.md))
            AmountWithCurrency(stringResource(R.string.amount_per_installment), amount, currency, { amount = AmountInput.sanitize(it, currency) }) { pickCurrency = true }
            Spacer(Modifier.height(Spacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Stepper(stringResource(R.string.installments_count), total, 1..600, Modifier.weight(1f)) { total = it; if (paid > it) paid = it }
                Stepper(stringResource(R.string.already_paid), paid, 0..total, Modifier.weight(1f)) { paid = it }
            }
            Spacer(Modifier.height(Spacing.lg))
            FieldTitle(stringResource(R.string.next_installment_date))
            GlassChip(
                f.date(LocalDate.ofEpochDay(nextEpoch), withYear = true, withWeekday = true),
                true,
                { pickDate = true },
                leading = { Icon(Icons.Rounded.CalendarMonth, null, tint = c.accent, modifier = Modifier.size(16.dp)) },
            )
            Spacer(Modifier.height(Spacing.lg))
            FieldTitle(stringResource(R.string.repeat_every))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(1, 2, 3, 6, 12).forEach { n ->
                    GlassChip(
                        if (n == 1) stringResource(R.string.every_month) else stringResource(R.string.every_n_months, f.localizeDigits(n.toString())),
                        interval == n,
                        { interval = n },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.lg))
            FieldTitle(stringResource(R.string.remind_me))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(0, 1, 3, 7).forEach { d ->
                    GlassChip(
                        if (d == 0) stringResource(R.string.on_the_day) else stringResource(R.string.days_before_n, f.localizeDigits(d.toString())),
                        remind == d,
                        { remind = d },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.lg))
            FieldTitle(stringResource(R.string.pay_from_wallet))
            Text(stringResource(R.string.pay_from_wallet_hint), style = MaterialTheme.typography.bodySmall, color = c.textTertiary, modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.sm))
            WalletOptions(wallets.filter { it.currency == currency && !it.archived }, walletId) { walletId = it }
            Spacer(Modifier.height(Spacing.lg))
            GlassTextField(note, { note = it.take(500) }, placeholder = stringResource(R.string.note_optional))
            Spacer(Modifier.height(Spacing.xl))
            PocketButton(
                stringResource(R.string.save),
                {
                    scope.launch {
                        val next = LocalDate.ofEpochDay(nextEpoch)
                        val now = java.time.Instant.now()
                        container.finance.saveInstallment(
                            InstallmentPlan(
                                id = existing?.id ?: container.finance.newId(),
                                title = title,
                                lender = lender,
                                amountMinor = amountMinor,
                                currency = currency,
                                totalCount = total,
                                paidCount = paid,
                                // The plan is anchored on its first installment; derive it from "next" and "already paid".
                                firstDue = CalendarMath.plusMonths(next, -paid * interval, calendar),
                                intervalMonths = interval,
                                calendar = calendar,
                                reminderDays = remind,
                                walletId = walletId,
                                note = note,
                                createdAt = existing?.createdAt ?: now,
                                updatedAt = now,
                            )
                        )
                        haptics.perform(HapticType.Success)
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                icon = Icons.Rounded.Check,
                enabled = canSave,
                haptic = HapticType.Confirm,
            )
            if (existing != null) {
                Spacer(Modifier.height(Spacing.sm))
                PocketButton(stringResource(R.string.delete), { confirmDelete = true }, modifier = Modifier.fillMaxWidth(), style = ButtonStyle.Text, haptic = HapticType.Warning)
            }
        }
    }
    if (pickCurrency) CurrencyPickerSheet(currency, { currency = it; amount = AmountInput.adapt(amount, it); walletId = null; pickCurrency = false }, { pickCurrency = false })
    if (pickDate) CalendarDatePickerSheet(LocalDate.ofEpochDay(nextEpoch), { nextEpoch = it.toEpochDay(); pickDate = false }, { pickDate = false })
    if (confirmDelete && existing != null) {
        GlassDialog(
            onDismiss = { confirmDelete = false },
            title = stringResource(R.string.delete_installment_title),
            message = existing.title,
            confirmText = stringResource(R.string.delete),
            onConfirm = { confirmDelete = false; scope.launch { container.finance.deleteInstallment(existing.id); onDismiss() } },
            dismissText = stringResource(R.string.cancel),
            destructive = true,
        )
    }
}

// ================================================================= Debts

enum class DebtFilter { OPEN, I_OWE, OWED_TO_ME, SETTLED }

@Composable
fun DebtsScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val ui = LocalAppUi.current
    val scope = rememberCoroutineScope()
    val debts by container.finance.debts.collectAsState(initial = null)
    val settings by container.settings.settings.collectAsState(initial = container.latestSettings)
    val recordedLabel = stringResource(R.string.payment_recorded)
    var editing by remember { mutableStateOf<Debt?>(null) }
    var creating by remember { mutableStateOf(false) }
    var repaying by remember { mutableStateOf<Debt?>(null) }

    DebtsContent(
        debts = debts,
        defaultCurrency = settings.defaultCurrency,
        today = container.clock.today(),
        onBack = { nav.popBackStack() },
        onAdd = { creating = true },
        onEdit = { editing = it },
        onRepay = { repaying = it },
        onSettle = { d -> scope.launch { container.finance.repayDebt(d.id, d.remainingMinor, null); ui.message(recordedLabel) } },
    )
    if (creating || editing != null) DebtEditorSheet(editing, settings.defaultCurrency, debts.orEmpty()) { creating = false; editing = null }
    repaying?.let { d -> RepaySheet(d) { repaying = null } }
}

@Composable
internal fun DebtsContent(
    debts: List<Debt>?,
    defaultCurrency: String,
    today: LocalDate,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Debt) -> Unit,
    onRepay: (Debt) -> Unit,
    onSettle: (Debt) -> Unit,
) {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    var filter by rememberSaveable { mutableStateOf(DebtFilter.OPEN) }
    val all = debts.orEmpty()
    val summaries = ObligationCalculator.debtSummary(all)
    val primary = summaries.firstOrNull { it.currency == defaultCurrency } ?: summaries.firstOrNull()
    val currency = primary?.currency ?: defaultCurrency
    val visible = all.filter {
        when (filter) {
            DebtFilter.OPEN -> !it.isSettled
            DebtFilter.I_OWE -> !it.isSettled && it.direction == DebtDirection.I_OWE
            DebtFilter.OWED_TO_ME -> !it.isSettled && it.direction == DebtDirection.OWED_TO_ME
            DebtFilter.SETTLED -> it.isSettled
        }
    }.sortedWith(compareBy<Debt> { it.dueDate ?: LocalDate.MAX }.thenByDescending { it.createdAt })

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Spacing.gutter)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item { ScreenTitle(stringResource(R.string.debts_title), onBack, onAdd) }
        item {
            GradientCard(Modifier.fillMaxWidth().padding(top = Spacing.lg).appear(0), colors = listOf(Color(0xFF14B8A6), Color(0xFF6366F1))) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    DebtHeroSide(Icons.Rounded.North, stringResource(R.string.you_owe_total), f.money(primary?.iOweMinor ?: 0, currency, compact = true), Modifier.weight(1f))
                    DebtHeroSide(Icons.Rounded.South, stringResource(R.string.owed_to_you_total), f.money(primary?.owedToMeMinor ?: 0, currency, compact = true), Modifier.weight(1f))
                }
                Spacer(Modifier.height(Spacing.md))
                Text(
                    stringResource(R.string.net_label) + ": " + f.money(primary?.netMinor ?: 0, currency, compact = true),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Text(pluralStringOf(R.plurals.open_debts, all.count { !it.isSettled }), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
            }
        }
        item {
            Spacer(Modifier.height(Spacing.lg))
            GlassSegmentedControl(
                DebtFilter.entries, filter, { filter = it },
                {
                    stringResource(
                        when (it) {
                            DebtFilter.OPEN -> R.string.open_items
                            DebtFilter.I_OWE -> R.string.i_owe
                            DebtFilter.OWED_TO_ME -> R.string.owed_to_me
                            DebtFilter.SETTLED -> R.string.settled
                        }
                    )
                },
            )
            Spacer(Modifier.height(Spacing.md))
        }
        if (debts != null && visible.isEmpty()) {
            item {
                EmptyState(
                    Icons.Rounded.Handshake,
                    stringResource(R.string.no_debts_title),
                    stringResource(R.string.no_debts_message),
                    actionLabel = if (all.isEmpty()) stringResource(R.string.add_debt) else null,
                    onAction = if (all.isEmpty()) onAdd else null,
                    compact = all.isNotEmpty(),
                )
            }
        }
        items(visible, key = { it.id }) { d ->
            DebtCard(d, today, { onEdit(d) }, { onRepay(d) }, { onSettle(d) }, Modifier.padding(bottom = Spacing.md).animateItem())
        }
        item { BottomClearance() }
    }
}

@Composable
private fun DebtHeroSide(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.16f)).padding(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DebtCard(d: Debt, today: LocalDate, onEdit: () -> Unit, onRepay: () -> Unit, onSettle: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val owe = d.direction == DebtDirection.I_OWE
    val tone = if (owe) c.tones.red else c.tones.green
    val initials = d.person.trim().split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }
    GlassCard(modifier.fillMaxWidth(), onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(tone, tone.copy(alpha = 0.7f)))),
                contentAlignment = Alignment.Center,
            ) { Text(initials.ifEmpty { "?" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White) }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(d.person, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(stringResource(if (owe) R.string.i_owe else R.string.owed_to_me), d.note).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(f.money(d.remainingMinor, d.currency, compact = true), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = if (d.isSettled) c.textTertiary else tone, maxLines = 1)
                if (d.settledMinor > 0 && !d.isSettled) {
                    Text(stringResource(R.string.remaining), style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                }
            }
        }
        if (d.settledMinor > 0 || d.dueDate != null) Spacer(Modifier.height(Spacing.md))
        if (d.settledMinor > 0) {
            GlassProgressBar(d.progress, color = tone)
            Spacer(Modifier.height(Spacing.sm))
        }
        d.dueDate?.takeIf { !d.isSettled }?.let { due ->
            val days = ObligationCalculator.daysUntil(due, today)
            StatusPill(
                f.date(due) + " · " + (if (days < 0) stringResource(R.string.overdue) else f.relative(due, today)),
                if (days < 0) c.danger else if (days <= 3) c.warning else c.tones.blue,
                dot = true,
            )
        }
        if (d.isSettled) {
            StatusPill(stringResource(R.string.settled), c.success, icon = Icons.Rounded.Check)
        } else {
            Spacer(Modifier.height(Spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PocketButton(stringResource(R.string.record_payment), onRepay, Modifier.weight(1f), style = ButtonStyle.Tonal)
                PocketButton(stringResource(R.string.settle_fully), onSettle, Modifier.weight(1f), style = ButtonStyle.Glass, icon = Icons.Rounded.Check, haptic = HapticType.Success)
            }
        }
    }
}

@Composable
private fun DebtEditorSheet(existing: Debt?, defaultCurrency: String, all: List<Debt>, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val wallets by container.finance.wallets.collectAsState(initial = emptyList())
    var direction by rememberSaveable { mutableStateOf(existing?.direction ?: DebtDirection.OWED_TO_ME) }
    var person by rememberSaveable { mutableStateOf(existing?.person.orEmpty()) }
    var currency by rememberSaveable { mutableStateOf(existing?.currency ?: defaultCurrency) }
    var amount by rememberSaveable { mutableStateOf(existing?.let { MoneyFormatter.formatPlain(it.amountMinor, it.currency) }.orEmpty()) }
    var dateEpoch by rememberSaveable { mutableStateOf((existing?.date ?: LocalDate.now()).toEpochDay()) }
    var dueEpoch by rememberSaveable { mutableStateOf(existing?.dueDate?.toEpochDay()) }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    var walletId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickCurrency by remember { mutableStateOf(false) }
    var pickDue by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val amountMinor = MoneyFormatter.parse(amount.ifEmpty { "0" }, currency) ?: 0
    val people = remember(all) { all.map { it.person }.distinct().take(8) }

    GlassBottomSheet(onDismiss = onDismiss, title = stringResource(if (existing == null) R.string.add_debt else R.string.edit_debt)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            GlassSegmentedControl(
                listOf(DebtDirection.OWED_TO_ME, DebtDirection.I_OWE), direction, { direction = it },
                { stringResource(if (it == DebtDirection.OWED_TO_ME) R.string.i_lent else R.string.i_borrowed) },
            )
            Spacer(Modifier.height(Spacing.lg))
            GlassTextField(person, { person = it.take(80) }, label = stringResource(R.string.person), placeholder = stringResource(R.string.person_hint))
            if (existing == null && people.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    people.forEach { p -> GlassChip(p, p == person, { person = p }) }
                }
            }
            Spacer(Modifier.height(Spacing.md))
            AmountWithCurrency(stringResource(R.string.amount), amount, currency, { amount = AmountInput.sanitize(it, currency) }) { pickCurrency = true }
            Spacer(Modifier.height(Spacing.lg))
            FieldTitle(stringResource(R.string.due_date_optional))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                GlassChip(stringResource(R.string.no_due_date), dueEpoch == null, { dueEpoch = null })
                GlassChip(
                    dueEpoch?.let { f.date(LocalDate.ofEpochDay(it), withYear = true) } ?: stringResource(R.string.date),
                    dueEpoch != null,
                    { pickDue = true },
                    leading = { Icon(Icons.Rounded.CalendarMonth, null, tint = c.textSecondary, modifier = Modifier.size(16.dp)) },
                )
            }
            if (existing == null) {
                Spacer(Modifier.height(Spacing.lg))
                FieldTitle(stringResource(R.string.record_in_wallet))
                WalletOptions(wallets.filter { it.currency == currency && !it.archived }, walletId) { walletId = it }
            }
            Spacer(Modifier.height(Spacing.lg))
            GlassTextField(note, { note = it.take(500) }, placeholder = stringResource(R.string.note_optional))
            Spacer(Modifier.height(Spacing.xl))
            PocketButton(
                stringResource(R.string.save),
                {
                    scope.launch {
                        val now = java.time.Instant.now()
                        container.finance.saveDebt(
                            Debt(
                                id = existing?.id ?: container.finance.newId(),
                                person = person,
                                direction = direction,
                                amountMinor = amountMinor,
                                currency = currency,
                                settledMinor = existing?.settledMinor ?: 0,
                                date = LocalDate.ofEpochDay(dateEpoch),
                                dueDate = dueEpoch?.let(LocalDate::ofEpochDay),
                                note = note,
                                createdAt = existing?.createdAt ?: now,
                                updatedAt = now,
                            ),
                            walletId = walletId,
                        )
                        haptics.perform(HapticType.Success)
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                icon = Icons.Rounded.Check,
                enabled = person.isNotBlank() && amountMinor > 0,
                haptic = HapticType.Confirm,
            )
            if (existing != null) {
                Spacer(Modifier.height(Spacing.sm))
                PocketButton(stringResource(R.string.delete), { confirmDelete = true }, modifier = Modifier.fillMaxWidth(), style = ButtonStyle.Text, haptic = HapticType.Warning)
            }
        }
    }
    if (pickCurrency) CurrencyPickerSheet(currency, { currency = it; amount = AmountInput.adapt(amount, it); walletId = null; pickCurrency = false }, { pickCurrency = false })
    if (pickDue) CalendarDatePickerSheet(dueEpoch?.let(LocalDate::ofEpochDay) ?: LocalDate.now().plusDays(7), { dueEpoch = it.toEpochDay(); pickDue = false }, { pickDue = false })
    if (confirmDelete && existing != null) {
        GlassDialog(
            onDismiss = { confirmDelete = false },
            title = stringResource(R.string.delete_debt_title),
            message = existing.person,
            confirmText = stringResource(R.string.delete),
            onConfirm = { confirmDelete = false; scope.launch { container.finance.deleteDebt(existing.id); onDismiss() } },
            dismissText = stringResource(R.string.cancel),
            destructive = true,
        )
    }
}

@Composable
private fun RepaySheet(debt: Debt, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val ui = LocalAppUi.current
    val f = LocalFormatter.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val wallets by container.finance.wallets.collectAsState(initial = emptyList())
    val recorded = stringResource(R.string.payment_recorded)
    var amount by rememberSaveable { mutableStateOf(MoneyFormatter.formatPlain(debt.remainingMinor, debt.currency)) }
    var walletId by rememberSaveable { mutableStateOf<String?>(null) }
    val amountMinor = MoneyFormatter.parse(amount.ifEmpty { "0" }, debt.currency) ?: 0

    GlassBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.record_payment) + " · " + debt.person) {
        Text(stringResource(R.string.remaining) + ": " + f.money(debt.remainingMinor, debt.currency), style = MaterialTheme.typography.bodyMedium, color = LocalPocketColors.current.textSecondary)
        Spacer(Modifier.height(Spacing.md))
        AmountWithCurrency(stringResource(R.string.payment_amount), amount, debt.currency, { amount = AmountInput.sanitize(it, debt.currency) }, onCurrency = null)
        Spacer(Modifier.height(Spacing.lg))
        FieldTitle(stringResource(R.string.record_in_wallet))
        WalletOptions(wallets.filter { it.currency == debt.currency && !it.archived }, walletId) { walletId = it }
        Spacer(Modifier.height(Spacing.xl))
        PocketButton(
            stringResource(R.string.save),
            {
                scope.launch {
                    container.finance.repayDebt(debt.id, amountMinor, walletId)
                    haptics.perform(HapticType.Success)
                    ui.message(recorded)
                    onDismiss()
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            icon = Icons.Rounded.Check,
            enabled = amountMinor > 0,
            haptic = HapticType.Confirm,
        )
    }
}

// ================================================================= Checks

enum class CheckFilter { ALL, PENDING, ISSUED, RECEIVED, CLEARED }

@Composable
fun ChecksScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val ui = LocalAppUi.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val checks by container.finance.checks.collectAsState(initial = null)
    val settings by container.settings.settings.collectAsState(initial = container.latestSettings)
    val clearedMsg = stringResource(R.string.check_cleared_msg)
    val bouncedMsg = stringResource(R.string.check_bounced_msg)
    var editing by remember { mutableStateOf<CheckItem?>(null) }
    var creating by remember { mutableStateOf(false) }

    ChecksContent(
        checks = checks,
        defaultCurrency = settings.defaultCurrency,
        today = container.clock.today(),
        onBack = { nav.popBackStack() },
        onAdd = { creating = true },
        onEdit = { editing = it },
        onMarkCleared = { check ->
            scope.launch {
                container.finance.markCheckStatus(check.id, CheckStatus.CLEARED)
                haptics.perform(HapticType.Success)
                ui.message(clearedMsg)
            }
        },
        onMarkBounced = { check ->
            scope.launch {
                container.finance.markCheckStatus(check.id, CheckStatus.BOUNCED)
                haptics.perform(HapticType.Warning)
                ui.message(bouncedMsg)
            }
        },
    )
    if (creating || editing != null) {
        CheckEditorSheet(editing, settings.defaultCurrency) { creating = false; editing = null }
    }
}

@Composable
internal fun ChecksContent(
    checks: List<CheckItem>?,
    defaultCurrency: String,
    today: LocalDate,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (CheckItem) -> Unit,
    onMarkCleared: (CheckItem) -> Unit,
    onMarkBounced: (CheckItem) -> Unit,
) {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    var filter by rememberSaveable { mutableStateOf(CheckFilter.ALL) }
    val all = checks.orEmpty()
    val summaries = ObligationCalculator.checkSummary(all)
    val primary = summaries.firstOrNull { it.currency == defaultCurrency } ?: summaries.firstOrNull()
    val currency = primary?.currency ?: defaultCurrency
    val visible = all.filter {
        when (filter) {
            CheckFilter.ALL -> true
            CheckFilter.PENDING -> it.isPending
            CheckFilter.ISSUED -> it.direction == CheckDirection.ISSUED
            CheckFilter.RECEIVED -> it.direction == CheckDirection.RECEIVED
            CheckFilter.CLEARED -> it.isCleared || it.isBounced
        }
    }.sortedWith(
        compareBy<CheckItem> { if (it.isPending) 0 else 1 }
            .thenBy { it.dueDate }
            .thenByDescending { it.createdAt }
    )

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Spacing.gutter)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item { ScreenTitle(stringResource(R.string.checks_title), onBack, onAdd) }
        item {
            GradientCard(Modifier.fillMaxWidth().padding(top = Spacing.lg).appear(0), colors = listOf(Color(0xFF0284C7), Color(0xFF6366F1))) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    HeroStatSide(
                        Icons.Rounded.North,
                        stringResource(R.string.pending_issued_total),
                        f.money(primary?.pendingIssuedMinor ?: 0, currency, compact = true),
                        Modifier.weight(1f),
                    )
                    HeroStatSide(
                        Icons.Rounded.South,
                        stringResource(R.string.pending_received_total),
                        f.money(primary?.pendingReceivedMinor ?: 0, currency, compact = true),
                        Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(Spacing.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ToneIcon(Icons.Rounded.Payments, Color.White, size = 32.dp, filled = false)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        pluralStringOf(R.plurals.open_checks, all.count { it.isPending }),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }
        }
        item {
            Spacer(Modifier.height(Spacing.lg))
            GlassSegmentedControl(
                CheckFilter.entries, filter, { filter = it },
                {
                    stringResource(
                        when (it) {
                            CheckFilter.ALL -> R.string.open_items
                            CheckFilter.PENDING -> R.string.check_status_pending
                            CheckFilter.ISSUED -> R.string.check_issued
                            CheckFilter.RECEIVED -> R.string.check_received
                            CheckFilter.CLEARED -> R.string.settled
                        }
                    )
                },
            )
            Spacer(Modifier.height(Spacing.md))
        }
        if (checks != null && visible.isEmpty()) {
            item {
                EmptyState(
                    Icons.Rounded.AccountBalance,
                    stringResource(R.string.no_checks_title),
                    stringResource(R.string.no_checks_message),
                    actionLabel = if (all.isEmpty()) stringResource(R.string.new_check) else null,
                    onAction = if (all.isEmpty()) onAdd else null,
                    compact = all.isNotEmpty(),
                )
            }
        }
        items(visible, key = { it.id }) { check ->
            CheckCard(
                check = check,
                today = today,
                onEdit = { onEdit(check) },
                onCleared = { onMarkCleared(check) },
                onBounced = { onMarkBounced(check) },
                modifier = Modifier.padding(bottom = Spacing.md).animateItem(),
            )
        }
        item { BottomClearance() }
    }
}

@Composable
private fun HeroStatSide(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.16f)).padding(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CheckCard(
    check: CheckItem,
    today: LocalDate,
    onEdit: () -> Unit,
    onCleared: () -> Unit,
    onBounced: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val isIssued = check.direction == CheckDirection.ISSUED
    val days = ObligationCalculator.daysUntil(check.dueDate, today)
    val tone = when {
        check.isCleared -> c.success
        check.isBounced -> c.danger
        days < 0 -> c.danger
        days <= 3 -> c.warning
        isIssued -> c.tones.red
        else -> c.tones.green
    }
    val promptClearance = check.isPending && days <= 0

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onEdit,
        decoration = {
            val start = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr) 0f else size.width
            drawRect(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    listOf(tone.copy(alpha = if (c.isDark) 0.16f else 0.08f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(start, 0f),
                    radius = size.width * 0.8f,
                )
            )
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToneIcon(if (isIssued) Icons.Rounded.North else Icons.Rounded.South, tone, size = 44.dp, filled = true)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(check.title.ifBlank { check.counterparty }, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(
                    stringResource(if (isIssued) R.string.check_payee else R.string.check_issuer) + ": " + check.counterparty,
                    check.bankName,
                ).joinToString(" · ")
                Text(sub, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(f.money(check.amountMinor, check.currency, compact = true), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = if (check.isCleared) c.textTertiary else c.textPrimary)
        }

        if (check.sayadNumber != null || check.note != null) {
            Spacer(Modifier.height(Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                check.sayadNumber?.let {
                    Text(
                        "${stringResource(R.string.check_number)}: ${f.localizeDigits(it)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (check.sayadNumber != null && check.note != null) {
                    Text(" · ", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                }
                check.note?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
            }
        }

        Spacer(Modifier.height(Spacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(f.date(check.dueDate, withYear = true, withWeekday = true), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                val statusText = when {
                    check.isCleared -> stringResource(R.string.check_status_cleared)
                    check.isBounced -> stringResource(R.string.check_status_bounced)
                    days < 0 -> stringResource(R.string.overdue)
                    else -> f.relative(check.dueDate, today)
                }
                StatusPill(statusText, tone, dot = check.isPending)
            }
            if (check.isPending && !promptClearance) {
                PocketButton(stringResource(R.string.check_status_cleared), onCleared, style = ButtonStyle.Tonal, icon = Icons.Rounded.Check, haptic = HapticType.Success)
            }
        }

        if (promptClearance) {
            Spacer(Modifier.height(Spacing.md))
            Column(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(tone.copy(alpha = if (c.isDark) 0.18f else 0.10f))
                    .padding(Spacing.md)
            ) {
                Text(
                    stringResource(R.string.check_prompt_cleared),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary,
                )
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    PocketButton(
                        stringResource(R.string.check_pass_btn),
                        onCleared,
                        Modifier.weight(1f),
                        style = ButtonStyle.Primary,
                        icon = Icons.Rounded.Check,
                        haptic = HapticType.Success,
                    )
                    PocketButton(
                        stringResource(R.string.check_bounce_btn),
                        onBounced,
                        Modifier.weight(1f),
                        style = ButtonStyle.Glass,
                        icon = Icons.Rounded.Cancel,
                        haptic = HapticType.Warning,
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckEditorSheet(existing: CheckItem?, defaultCurrency: String, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val wallets by container.finance.wallets.collectAsState(initial = emptyList())
    var direction by rememberSaveable { mutableStateOf(existing?.direction ?: CheckDirection.ISSUED) }
    var title by rememberSaveable { mutableStateOf(existing?.title.orEmpty()) }
    var counterparty by rememberSaveable { mutableStateOf(existing?.counterparty.orEmpty()) }
    var currency by rememberSaveable { mutableStateOf(existing?.currency ?: defaultCurrency) }
    var amount by rememberSaveable { mutableStateOf(existing?.let { MoneyFormatter.formatPlain(it.amountMinor, it.currency) }.orEmpty()) }
    var sayadNumber by rememberSaveable { mutableStateOf(existing?.sayadNumber.orEmpty()) }
    var bankName by rememberSaveable { mutableStateOf(existing?.bankName.orEmpty()) }
    var dueEpoch by rememberSaveable { mutableStateOf((existing?.dueDate ?: LocalDate.now().plusDays(7)).toEpochDay()) }
    var remind by rememberSaveable { mutableStateOf(existing?.reminderDays ?: 3) }
    var walletId by rememberSaveable { mutableStateOf(existing?.walletId ?: wallets.firstOrNull { it.currency == currency }?.id) }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    var pickCurrency by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val amountMinor = MoneyFormatter.parse(amount.ifEmpty { "0" }, currency) ?: 0
    val canSave = counterparty.isNotBlank() && amountMinor > 0

    GlassBottomSheet(onDismiss = onDismiss, title = stringResource(if (existing == null) R.string.new_check else R.string.edit_check)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            GlassSegmentedControl(
                listOf(CheckDirection.ISSUED, CheckDirection.RECEIVED), direction, { direction = it },
                { stringResource(if (it == CheckDirection.ISSUED) R.string.check_issued else R.string.check_received) },
            )
            Spacer(Modifier.height(Spacing.lg))
            GlassTextField(title, { title = it.take(80) }, label = stringResource(R.string.check_title_label), placeholder = stringResource(R.string.check_title_hint))
            Spacer(Modifier.height(Spacing.md))
            GlassTextField(
                counterparty,
                { counterparty = it.take(80) },
                label = stringResource(if (direction == CheckDirection.ISSUED) R.string.check_payee else R.string.check_issuer),
                placeholder = stringResource(R.string.person_hint),
            )
            Spacer(Modifier.height(Spacing.md))
            AmountWithCurrency(stringResource(R.string.amount), amount, currency, { amount = AmountInput.sanitize(it, currency) }) { pickCurrency = true }
            Spacer(Modifier.height(Spacing.md))
            GlassTextField(bankName, { bankName = it.take(60) }, label = stringResource(R.string.check_bank), placeholder = stringResource(R.string.check_bank_hint))
            Spacer(Modifier.height(Spacing.md))
            GlassTextField(sayadNumber, { sayadNumber = it.take(40) }, label = stringResource(R.string.check_number), placeholder = stringResource(R.string.check_sayad_hint))
            Spacer(Modifier.height(Spacing.lg))
            FieldTitle(stringResource(R.string.check_due_date))
            GlassChip(
                f.date(LocalDate.ofEpochDay(dueEpoch), withYear = true, withWeekday = true),
                true,
                { pickDate = true },
                leading = { Icon(Icons.Rounded.CalendarMonth, null, tint = c.accent, modifier = Modifier.size(16.dp)) },
            )
            Spacer(Modifier.height(Spacing.lg))
            FieldTitle(stringResource(R.string.check_remind_days))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(0, 1, 2, 3, 7).forEach { d ->
                    GlassChip(
                        if (d == 0) stringResource(R.string.on_the_day) else stringResource(R.string.days_before_n, f.localizeDigits(d.toString())),
                        remind == d,
                        { remind = d },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.lg))
            FieldTitle(stringResource(R.string.pay_from_wallet))
            WalletOptions(wallets.filter { it.currency == currency && !it.archived }, walletId) { walletId = it }
            Spacer(Modifier.height(Spacing.lg))
            GlassTextField(note, { note = it.take(500) }, placeholder = stringResource(R.string.note_optional))
            Spacer(Modifier.height(Spacing.xl))
            PocketButton(
                stringResource(R.string.save),
                {
                    scope.launch {
                        val now = java.time.Instant.now()
                        container.finance.saveCheck(
                            CheckItem(
                                id = existing?.id ?: container.finance.newId(),
                                title = title.ifBlank { counterparty },
                                counterparty = counterparty,
                                direction = direction,
                                amountMinor = amountMinor,
                                currency = currency,
                                sayadNumber = sayadNumber.ifBlank { null },
                                bankName = bankName.ifBlank { null },
                                dueDate = LocalDate.ofEpochDay(dueEpoch),
                                issueDate = existing?.issueDate ?: LocalDate.now(),
                                status = existing?.status ?: CheckStatus.PENDING,
                                reminderDays = remind,
                                note = note.ifBlank { null },
                                walletId = walletId,
                                createdAt = existing?.createdAt ?: now,
                                updatedAt = now,
                            )
                        )
                        haptics.perform(HapticType.Success)
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                icon = Icons.Rounded.Check,
                enabled = canSave,
                haptic = HapticType.Confirm,
            )
            if (existing != null) {
                Spacer(Modifier.height(Spacing.sm))
                PocketButton(stringResource(R.string.delete), { confirmDelete = true }, modifier = Modifier.fillMaxWidth(), style = ButtonStyle.Text, haptic = HapticType.Warning)
            }
        }
    }

    if (pickCurrency) CurrencyPickerSheet(currency, { currency = it; amount = AmountInput.adapt(amount, it); walletId = null; pickCurrency = false }, { pickCurrency = false })
    if (pickDate) CalendarDatePickerSheet(LocalDate.ofEpochDay(dueEpoch), { dueEpoch = it.toEpochDay(); pickDate = false }, { pickDate = false })
    if (confirmDelete && existing != null) {
        GlassDialog(
            onDismiss = { confirmDelete = false },
            title = stringResource(R.string.delete_check_title),
            message = existing.title,
            confirmText = stringResource(R.string.delete),
            onConfirm = { confirmDelete = false; scope.launch { container.finance.deleteCheck(existing.id); onDismiss() } },
            dismissText = stringResource(R.string.cancel),
            destructive = true,
        )
    }
}

// =============================================================== Shared

@Composable
private fun FieldTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = LocalPocketColors.current.textSecondary, modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.sm))
}

@Composable
private fun AmountWithCurrency(label: String, amount: String, currency: String, onAmount: (String) -> Unit, onCurrency: (() -> Unit)?) {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    Row(verticalAlignment = Alignment.Bottom) {
        GlassTextField(
            amount, onAmount,
            modifier = Modifier.weight(1f),
            label = label,
            placeholder = "0",
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done,
            textStyle = MaterialTheme.typography.titleLarge,
            visualTransformation = remember(f.locale) { AmountInput.Grouping(f.locale.language == "fa") },
        )
        Spacer(Modifier.width(Spacing.sm))
        GlassCard(level = GlassLevel.L1, contentPadding = PaddingValues(horizontal = Spacing.md, vertical = 15.dp), onClick = onCurrency) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Currencies.flag(currency), fontSize = 18.sp)
                Spacer(Modifier.width(6.dp))
                Text(currency, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val haptics = LocalHaptics.current
    Column(modifier) {
        FieldTitle(label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.Rounded.Remove, stringResource(R.string.decrease), {
                if (value > range.first) { onChange(value - 1); haptics.perform(HapticType.DragTick) }
            }, size = 40.dp, level = GlassLevel.L1)
            Text(
                f.localizeDigits(value.toString()),
                style = MaterialTheme.typography.headlineSmall,
                color = c.textPrimary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            GlassIconButton(Icons.Rounded.Add, stringResource(R.string.increase), {
                if (value < range.last) { onChange(value + 1); haptics.perform(HapticType.DragTick) }
            }, size = 40.dp, level = GlassLevel.L1)
        }
    }
}

/** "Don't record" plus one chip per wallet. */
@Composable
private fun WalletOptions(wallets: List<Wallet>, selected: String?, onSelect: (String?) -> Unit) {
    val c = LocalPocketColors.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        GlassChip(stringResource(R.string.no_wallet), selected == null, { onSelect(null) })
        wallets.forEach { w ->
            GlassChip(
                w.name, w.id == selected, { onSelect(w.id) },
                leading = { Icon(walletTypeIcon(w.type), null, tint = walletColor(w), modifier = Modifier.size(16.dp)) },
                accent = walletColor(w),
            )
        }
    }
}

