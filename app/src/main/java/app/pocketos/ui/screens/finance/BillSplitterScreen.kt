package app.pocketos.ui.screens.finance

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import app.pocketos.ui.design.GlassIconButton
import app.pocketos.ui.design.GlassLevel
import app.pocketos.ui.design.GlassTextField
import app.pocketos.ui.design.PocketButton
import app.pocketos.ui.format.LocalFormatter
import app.pocketos.ui.theme.LocalPocketColors
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
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsState(initial = container.latestSettings)
    val currency = settings.defaultCurrency

    val members = remember {
        mutableStateListOf(
            SplitMember(name = "من"),
            SplitMember(name = "علی"),
            SplitMember(name = "رضا"),
        )
    }
    val expenses = remember { mutableStateListOf<SplitExpense>() }

    var showAddMember by remember { mutableStateOf(false) }
    var newMemberName by remember { mutableStateOf("") }

    var showAddExpense by remember { mutableStateOf(false) }
    var newExpenseTitle by remember { mutableStateOf("") }
    var newExpenseAmountText by remember { mutableStateOf("") }
    var selectedPayerId by remember { mutableStateOf("") }
    val selectedParticipantIds = remember { mutableStateListOf<String>() }

    val settlements = remember(members.toList(), expenses.toList()) {
        SplitCalculator.calculateSettlement(members, expenses, currency)
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
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    stringResource(R.string.back),
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
            }
        }

        // Hero Settlement Result
        item(key = "settlement_hero") {
            GradientCard(Modifier.fillMaxWidth().padding(top = Spacing.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                            if (settlements.isEmpty()) stringResource(R.string.split_all_settled)
                            else "${f.localizeDigits(settlements.size.toString())} انتقال برای تسویه کامل",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // Settlement items
        if (settlements.isNotEmpty()) {
            items(settlements, key = { "${it.debtorId}_${it.creditorId}_${it.amountMinor}" }) { s ->
                SettlementRow(
                    settlement = s,
                    onRecordAsDebt = {
                        scope.launch {
                            val now = Instant.now()
                            val personName = if (s.debtorName == "من" || s.debtorName.equals("me", ignoreCase = true)) s.creditorName else s.debtorName
                            val isIOwe = s.debtorName == "من" || s.debtorName.equals("me", ignoreCase = true)
                            container.finance.saveDebt(
                                Debt(
                                    id = container.finance.newId(),
                                    person = personName,
                                    direction = if (isIOwe) DebtDirection.I_OWE else DebtDirection.OWED_TO_ME,
                                    amountMinor = s.amountMinor,
                                    currency = s.currency,
                                    settledMinor = 0L,
                                    date = LocalDate.now(),
                                    dueDate = LocalDate.now().plusDays(7),
                                    note = "ثبت شده از دستیار دنگ و سفر PocketOS",
                                    createdAt = now,
                                    updatedAt = now,
                                )
                            )
                            ui.message("در بخش طلب و بدهی ذخیره شد")
                        }
                    },
                )
            }
        }

        // Members Section
        item(key = "members_header") {
            Spacer(Modifier.height(Spacing.md))
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
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.md),
            ) {
                members.forEach { m ->
                    GlassChip(
                        text = m.name,
                        selected = true,
                        onClick = {
                            if (members.size > 2) {
                                members.remove(m)
                                expenses.removeAll { it.paidById == m.id || it.participantIds.contains(m.id) }
                            }
                        },
                        leading = {
                            Icon(Icons.Rounded.People, contentDescription = null, Modifier.size(16.dp))
                        },
                    )
                }
            }
        }

        // Expenses Section
        item(key = "expenses_header") {
            SectionHeader(
                title = stringResource(R.string.split_expenses_header),
                action = stringResource(R.string.split_add_expense),
                onAction = {
                    if (members.isNotEmpty()) {
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
                    title = stringResource(R.string.split_empty_desc),
                    message = "",
                    modifier = Modifier.padding(vertical = Spacing.xl),
                )
            }
        } else {
            items(expenses, key = { it.id }) { exp ->
                val payer = members.firstOrNull { it.id == exp.paidById }?.name ?: "—"
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
                                "پرداخت‌کننده: $payer (${f.localizeDigits(exp.participantIds.size.toString())} نفر سهم)",
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
                            Icons.Rounded.Delete,
                            stringResource(R.string.delete),
                            onClick = { expenses.remove(exp) },
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
        }

        item { BottomClearance() }
    }

    // Dialog Add Member
    if (showAddMember) {
        GlassBottomSheet(onDismiss = { showAddMember = false }) {
            Column(Modifier.padding(Spacing.lg)) {
                Text(
                    stringResource(R.string.split_add_member),
                    style = MaterialTheme.typography.titleLarge,
                    color = c.textPrimary,
                )
                Spacer(Modifier.height(Spacing.md))
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
                            members.add(SplitMember(name = trimmed))
                            newMemberName = ""
                            showAddMember = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    // Dialog Add Expense
    if (showAddExpense) {
        GlassBottomSheet(onDismiss = { showAddExpense = false }) {
            Column(Modifier.padding(Spacing.lg)) {
                Text(
                    stringResource(R.string.split_add_expense),
                    style = MaterialTheme.typography.titleLarge,
                    color = c.textPrimary,
                )
                Spacer(Modifier.height(Spacing.md))
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
                    color = c.textSecondary,
                )
                Spacer(Modifier.height(Spacing.xs))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    members.forEach { m ->
                        GlassChip(
                            text = m.name,
                            selected = selectedPayerId == m.id,
                            onClick = { selectedPayerId = m.id },
                            leading = null,
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.md))

                // Participants selection
                Text(
                    stringResource(R.string.split_for_whom),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textSecondary,
                )
                Spacer(Modifier.height(Spacing.xs))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    members.forEach { m ->
                        val isChecked = selectedParticipantIds.contains(m.id)
                        GlassChip(
                            text = m.name,
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
                            expenses.add(
                                SplitExpense(
                                    title = newExpenseTitle.trim(),
                                    amountMinor = amountMinor,
                                    paidById = selectedPayerId,
                                    participantIds = selectedParticipantIds.toList(),
                                )
                            )
                            showAddExpense = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SettlementRow(
    settlement: SplitSettlement,
    onRecordAsDebt: () -> Unit,
) {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current

    GlassCard(
        Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        level = GlassLevel.L2,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToneIcon(Icons.Rounded.Handshake, c.tones.violet)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    "${settlement.debtorName} ➔ ${settlement.creditorName}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary,
                )
                Text(
                    stringResource(
                        R.string.split_owes_text,
                        settlement.debtorName,
                        f.money(settlement.amountMinor, settlement.currency, compact = true),
                        settlement.creditorName,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }
            PocketButton(
                text = stringResource(R.string.split_record_to_debt),
                onClick = onRecordAsDebt,
                style = ButtonStyle.Glass,
                modifier = Modifier.height(34.dp),
            )
        }
    }
}
