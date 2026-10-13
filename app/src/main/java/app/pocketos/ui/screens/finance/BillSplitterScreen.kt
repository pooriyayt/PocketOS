package app.pocketos.ui.screens.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.pocketos.R
import app.pocketos.core.money.MoneyFormatter
import app.pocketos.domain.finance.Debt
import app.pocketos.domain.finance.DebtDirection
import app.pocketos.domain.finance.SplitCalculator
import app.pocketos.domain.finance.SplitExpense
import app.pocketos.domain.finance.SplitMember
import app.pocketos.domain.finance.SplitSettlement
import app.pocketos.ui.LocalAppContainer
import app.pocketos.ui.LocalAppUi
import app.pocketos.ui.components.AmountInput
import app.pocketos.ui.components.BottomClearance
import app.pocketos.ui.components.EmptyState
import app.pocketos.ui.components.GradientCard
import app.pocketos.ui.components.SectionHeader
import app.pocketos.ui.components.ToneIcon
import app.pocketos.ui.design.ButtonStyle
import app.pocketos.ui.design.GlassBottomSheet
import app.pocketos.ui.design.GlassCard
import app.pocketos.ui.design.GlassChip
import app.pocketos.ui.design.GlassDialog
import app.pocketos.ui.design.GlassIconButton
import app.pocketos.ui.design.GlassLevel
import app.pocketos.ui.design.GlassTextField
import app.pocketos.ui.design.PocketButton
import app.pocketos.ui.format.LocalFormatter
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.Shapes
import app.pocketos.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

@Composable
fun BillSplitterScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val ui = LocalAppUi.current
    val f = LocalFormatter.current
    val c = LocalPocketColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsState(initial = container.latestSettings)
    val currency = settings.defaultCurrency

    val defaultSelfName = settings.displayName.ifBlank { stringResource(R.string.split_member_me) }

    val splitState by container.billSplitter.state.collectAsState()
    val members = splitState.members
    val expenses = splitState.expenses

    // Ensure at least "self" exists on startup
    LaunchedEffect(defaultSelfName) {
        container.billSplitter.ensureSelfMember(defaultSelfName)
    }

    var showAddMember by remember { mutableStateOf(false) }
    var newMemberName by remember { mutableStateOf("") }
    var memberToDelete by remember { mutableStateOf<SplitMember?>(null) }

    var showClearConfirm by remember { mutableStateOf(false) }

    var showAddExpense by remember { mutableStateOf(false) }
    var newExpenseTitle by remember { mutableStateOf("") }
    var newExpenseAmountText by remember { mutableStateOf("") }
    var selectedPayerId by remember { mutableStateOf("") }
    val selectedParticipantIds = remember { mutableStateListOf<String>() }

    val recordedSettlements = remember { mutableStateListOf<String>() }

    fun resolveMemberName(m: SplitMember): String =
        if (m.isSelf && (m.name.isBlank() || m.name == "من" || m.name == "من (شما)" || m.name == "You")) {
            defaultSelfName
        } else {
            m.name
        }

    val settlements = remember(members, expenses, currency, defaultSelfName) {
        val mappedMembers = members.map { it.copy(name = resolveMemberName(it)) }
        SplitCalculator.calculateSettlement(mappedMembers, expenses, currency)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.gutter),
    ) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }

        // Top App Bar
        item(key = "header") {
            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    onClick = { nav.popBackStack() },
                )
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.split_bills_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = c.textPrimary,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        stringResource(R.string.split_bills_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                    )
                }
                if (members.size > 1 || expenses.isNotEmpty()) {
                    GlassIconButton(
                        icon = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.split_clear_all),
                        onClick = { showClearConfirm = true },
                    )
                }
            }
        }

        // Hero Status Card
        item(key = "settlement_hero") {
            GradientCard(Modifier.fillMaxWidth().padding(top = Spacing.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        members.size < 2 -> {
                            ToneIcon(Icons.Rounded.People, c.tones.blue)
                            Spacer(Modifier.width(Spacing.md))
                            Column {
                                Text(
                                    stringResource(R.string.split_members_header).uppercase(f.locale),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White.copy(alpha = 0.8f),
                                )
                                Text(
                                    stringResource(R.string.split_at_least_two_members),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        expenses.isEmpty() -> {
                            ToneIcon(Icons.Rounded.ReceiptLong, c.tones.violet)
                            Spacer(Modifier.width(Spacing.md))
                            Column {
                                Text(
                                    stringResource(R.string.split_expenses_header).uppercase(f.locale),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White.copy(alpha = 0.8f),
                                )
                                Text(
                                    stringResource(R.string.split_no_expenses_tip),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        settlements.isEmpty() -> {
                            ToneIcon(Icons.Rounded.Check, c.tones.green)
                            Spacer(Modifier.width(Spacing.md))
                            Column {
                                Text(
                                    stringResource(R.string.split_settlement_header).uppercase(f.locale),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White.copy(alpha = 0.8f),
                                )
                                Text(
                                    stringResource(R.string.split_all_settled),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        else -> {
                            ToneIcon(Icons.Rounded.SwapHoriz, c.tones.green)
                            Spacer(Modifier.width(Spacing.md))
                            Column {
                                Text(
                                    stringResource(R.string.split_settlement_header).uppercase(f.locale),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White.copy(alpha = 0.8f),
                                )
                                Text(
                                    stringResource(R.string.split_settlement_transfers, f.localizeDigits(settlements.size.toString())),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Settlement Cards (when debts exist)
        if (settlements.isNotEmpty()) {
            item(key = "settlement_section_title") {
                Spacer(Modifier.height(Spacing.lg))
                Text(
                    stringResource(R.string.split_settlement_header),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary,
                )
            }

            items(settlements, key = { "${it.debtorId}_${it.creditorId}_${it.amountMinor}" }) { s ->
                val settlementKey = "${s.debtorId}_${s.creditorId}_${s.amountMinor}"
                val isRecorded = recordedSettlements.contains(settlementKey)

                SettlementCard(
                    settlement = s,
                    isRecorded = isRecorded,
                    onRecordAsDebt = {
                        scope.launch {
                            val now = Instant.now()
                            val isMeDebtor = s.debtorId == "self" || s.debtorName == defaultSelfName
                            val personName = if (isMeDebtor) s.creditorName else s.debtorName
                            val direction = if (isMeDebtor) DebtDirection.I_OWE else DebtDirection.OWED_TO_ME

                            container.finance.saveDebt(
                                Debt(
                                    id = container.finance.newId(),
                                    person = personName,
                                    direction = direction,
                                    amountMinor = s.amountMinor,
                                    currency = s.currency,
                                    settledMinor = 0L,
                                    date = LocalDate.now(),
                                    dueDate = LocalDate.now().plusDays(7),
                                    note = "ثبت شده از دستیار دنگ PocketOS (${s.debtorName} ➔ ${s.creditorName})",
                                    createdAt = now,
                                    updatedAt = now,
                                )
                            )
                            recordedSettlements.add(settlementKey)
                            ui.message(context.getString(R.string.split_added_to_debts))
                        }
                    },
                )
            }
        }

        // Members Section
        item(key = "members_header") {
            Spacer(Modifier.height(Spacing.lg))
            SectionHeader(
                title = stringResource(R.string.split_members_header),
                action = stringResource(R.string.split_add_member),
                onAction = { showAddMember = true },
            )
        }

        item(key = "members_chips") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
            ) {
                members.forEach { m ->
                    val displayName = resolveMemberName(m)
                    if (m.isSelf) {
                        // Current user ("من (شما)") - Highlighted chip, cannot be deleted
                        Box(
                            modifier = Modifier
                                .clip(Shapes.pill)
                                .background(c.accent.copy(alpha = if (c.isDark) 0.22f else 0.12f))
                                .border(1.dp, c.accent.copy(alpha = 0.5f), Shapes.pill)
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.Person,
                                    contentDescription = null,
                                    tint = c.accent,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    displayName,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (c.isDark) c.accentHighlight else c.accent,
                                )
                            }
                        }
                    } else {
                        // Companion member - Can be deleted
                        Box(
                            modifier = Modifier
                                .clip(Shapes.pill)
                                .background(if (c.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f))
                                .border(1.dp, c.glassBorder, Shapes.pill)
                                .padding(start = Spacing.md, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    displayName,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = c.textPrimary,
                                )
                                Spacer(Modifier.width(4.dp))
                                GlassIconButton(
                                    icon = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.split_delete_member),
                                    onClick = {
                                        val hasExpenses = expenses.any { it.paidById == m.id || it.participantIds.contains(m.id) }
                                        if (hasExpenses) {
                                            memberToDelete = m
                                        } else {
                                            scope.launch { container.billSplitter.removeMember(m.id) }
                                        }
                                    },
                                    size = 28.dp,
                                    tint = c.textSecondary,
                                    level = GlassLevel.L1,
                                )
                            }
                        }
                    }
                }

                // Inline "+ Add Person" chip
                GlassChip(
                    text = stringResource(R.string.split_add_member),
                    selected = false,
                    onClick = { showAddMember = true },
                    leading = {
                        Icon(Icons.Rounded.Add, contentDescription = null, Modifier.size(16.dp))
                    },
                )
            }

            if (members.size < 2) {
                Text(
                    stringResource(R.string.split_need_two_members_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.sm),
                )
            }
        }

        // Expenses Section
        item(key = "expenses_header") {
            Spacer(Modifier.height(Spacing.md))
            SectionHeader(
                title = stringResource(R.string.split_expenses_header),
                action = if (members.size >= 2) stringResource(R.string.split_add_expense) else null,
                onAction = {
                    if (members.size >= 2) {
                        selectedPayerId = members.first().id
                        selectedParticipantIds.clear()
                        selectedParticipantIds.addAll(members.map { it.id })
                        newExpenseTitle = ""
                        newExpenseAmountText = ""
                        showAddExpense = true
                    }
                },
            )
        }

        if (expenses.isEmpty()) {
            item(key = "empty_expenses") {
                EmptyState(
                    icon = Icons.Rounded.ReceiptLong,
                    title = if (members.size < 2) stringResource(R.string.split_at_least_two_members) else stringResource(R.string.split_no_expenses_tip),
                    message = "",
                    modifier = Modifier.padding(vertical = Spacing.lg),
                )
            }
        } else {
            items(expenses, key = { it.id }) { exp ->
                val payerMember = members.firstOrNull { it.id == exp.paidById }
                val payerName = payerMember?.let { resolveMemberName(it) } ?: "—"
                val participantCount = exp.participantIds.size

                GlassCard(
                    Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                    level = GlassLevel.L2,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ToneIcon(Icons.Rounded.ReceiptLong, c.tones.blue)
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                exp.title.ifBlank { "هزینه بدون عنوان" },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = c.textPrimary,
                            )
                            Text(
                                stringResource(
                                    R.string.split_expense_payer_and_count,
                                    payerName,
                                    f.localizeDigits(participantCount.toString()),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary,
                            )
                        }
                        Text(
                            f.money(exp.amountMinor, currency, compact = true),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = c.textPrimary,
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        GlassIconButton(
                            icon = Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.delete),
                            onClick = {
                                scope.launch { container.billSplitter.removeExpense(exp.id) }
                            },
                            size = 32.dp,
                        )
                    }
                }
            }
        }

        item { BottomClearance() }
    }

    // Dialog: Add Member
    if (showAddMember) {
        GlassBottomSheet(
            onDismiss = { showAddMember = false },
            title = stringResource(R.string.split_add_member),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm)) {
                GlassTextField(
                    value = newMemberName,
                    onValueChange = { newMemberName = it },
                    placeholder = stringResource(R.string.split_member_name_hint),
                    singleLine = true,
                )
                Spacer(Modifier.height(Spacing.lg))
                PocketButton(
                    text = stringResource(R.string.save),
                    onClick = {
                        val trimmed = newMemberName.trim()
                        if (trimmed.isNotEmpty()) {
                            scope.launch { container.billSplitter.addMember(trimmed) }
                            newMemberName = ""
                            showAddMember = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    // Dialog: Add Expense
    if (showAddExpense) {
        GlassBottomSheet(
            onDismiss = { showAddExpense = false },
            title = stringResource(R.string.split_add_expense),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm)) {
                GlassTextField(
                    value = newExpenseTitle,
                    onValueChange = { newExpenseTitle = it },
                    placeholder = stringResource(R.string.split_expense_title_hint),
                    singleLine = true,
                )
                Spacer(Modifier.height(Spacing.md))
                GlassTextField(
                    value = newExpenseAmountText,
                    onValueChange = { newExpenseAmountText = AmountInput.sanitize(it, currency) },
                    label = stringResource(R.string.amount),
                    placeholder = "0",
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                    visualTransformation = remember(f.locale) { AmountInput.Grouping(f.locale.language == "fa") },
                )
                Spacer(Modifier.height(Spacing.md))

                // Payer selection
                Text(
                    stringResource(R.string.split_paid_by),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = c.textSecondary,
                )
                Spacer(Modifier.height(Spacing.xs))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    members.forEach { m ->
                        val displayName = resolveMemberName(m)
                        GlassChip(
                            text = displayName,
                            selected = selectedPayerId == m.id,
                            onClick = { selectedPayerId = m.id },
                            leading = null,
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.md))

                // Participants selection
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.split_for_whom),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = c.textSecondary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        PocketButton(
                            text = stringResource(R.string.split_select_all),
                            onClick = {
                                selectedParticipantIds.clear()
                                selectedParticipantIds.addAll(members.map { it.id })
                            },
                            style = ButtonStyle.Text,
                        )
                        PocketButton(
                            text = stringResource(R.string.split_clear_selection),
                            onClick = {
                                selectedParticipantIds.clear()
                            },
                            style = ButtonStyle.Text,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    members.forEach { m ->
                        val isChecked = selectedParticipantIds.contains(m.id)
                        val displayName = resolveMemberName(m)
                        GlassChip(
                            text = displayName,
                            selected = isChecked,
                            onClick = {
                                if (isChecked) {
                                    if (selectedParticipantIds.size > 1) selectedParticipantIds.remove(m.id)
                                } else {
                                    selectedParticipantIds.add(m.id)
                                }
                            },
                            leading = if (isChecked) ({
                                Icon(Icons.Rounded.Check, null, Modifier.size(14.dp))
                            }) else null,
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.xl))
                PocketButton(
                    text = stringResource(R.string.save),
                    onClick = {
                        val amountMinor = MoneyFormatter.parse(newExpenseAmountText.ifEmpty { "0" }, currency) ?: 0L
                        if (amountMinor > 0 && selectedParticipantIds.isNotEmpty()) {
                            scope.launch {
                                container.billSplitter.addExpense(
                                    SplitExpense(
                                        title = newExpenseTitle.trim(),
                                        amountMinor = amountMinor,
                                        paidById = selectedPayerId,
                                        participantIds = selectedParticipantIds.toList(),
                                    )
                                )
                            }
                            showAddExpense = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    // Dialog: Confirm Delete Member
    memberToDelete?.let { m ->
        GlassDialog(
            onDismiss = { memberToDelete = null },
            title = stringResource(R.string.split_delete_member),
            message = stringResource(R.string.split_delete_member_confirm, resolveMemberName(m)),
            confirmText = stringResource(R.string.delete),
            onConfirm = {
                scope.launch { container.billSplitter.removeMember(m.id) }
                memberToDelete = null
            },
            dismissText = stringResource(R.string.cancel),
            destructive = true,
        )
    }

    // Dialog: Confirm Clear All
    if (showClearConfirm) {
        GlassDialog(
            onDismiss = { showClearConfirm = false },
            title = stringResource(R.string.split_clear_all),
            message = stringResource(R.string.split_clear_confirm),
            confirmText = stringResource(R.string.split_clear_all),
            onConfirm = {
                scope.launch {
                    container.billSplitter.clear(defaultSelfName)
                    recordedSettlements.clear()
                }
                showClearConfirm = false
            },
            dismissText = stringResource(R.string.cancel),
            destructive = true,
        )
    }
}

@Composable
private fun SettlementCard(
    settlement: SplitSettlement,
    isRecorded: Boolean,
    onRecordAsDebt: () -> Unit,
) {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current

    GlassCard(
        Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        level = GlassLevel.L2,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(Spacing.md),
        ) {
            // Direction row: Debtor -> Creditor + Amount
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    // Debtor Pill (Red tone)
                    Box(
                        modifier = Modifier
                            .clip(Shapes.pill)
                            .background(c.danger.copy(alpha = if (c.isDark) 0.22f else 0.12f))
                            .padding(horizontal = Spacing.sm, vertical = 4.dp),
                    ) {
                        Text(
                            settlement.debtorName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (c.isDark) Color(0xFFFF8A80) else c.danger,
                            maxLines = 1,
                        )
                    }

                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = c.textSecondary,
                        modifier = Modifier.size(16.dp),
                    )

                    // Creditor Pill (Green tone)
                    Box(
                        modifier = Modifier
                            .clip(Shapes.pill)
                            .background(c.tones.green.copy(alpha = if (c.isDark) 0.22f else 0.12f))
                            .padding(horizontal = Spacing.sm, vertical = 4.dp),
                    ) {
                        Text(
                            settlement.creditorName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (c.isDark) Color(0xFFA7F3D0) else c.tones.green,
                            maxLines = 1,
                        )
                    }
                }

                Spacer(Modifier.width(Spacing.sm))

                // Prominent Amount
                Text(
                    f.money(settlement.amountMinor, settlement.currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = c.accent,
                )
            }

            Spacer(Modifier.height(Spacing.sm))

            // Explanation Text (Given full width with unclipped typography)
            Text(
                stringResource(
                    R.string.split_owes_text,
                    settlement.debtorName,
                    f.money(settlement.amountMinor, settlement.currency),
                    settlement.creditorName,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
            )

            Spacer(Modifier.height(Spacing.md))

            // Record in Debts Action Button
            PocketButton(
                text = if (isRecorded) stringResource(R.string.split_added_to_debts) else stringResource(R.string.split_record_to_debt),
                icon = if (isRecorded) Icons.Rounded.Check else Icons.Rounded.Handshake,
                onClick = onRecordAsDebt,
                style = if (isRecorded) ButtonStyle.Tonal else ButtonStyle.Glass,
                enabled = !isRecorded,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
