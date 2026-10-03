package app.pocketos.ui.screens.finance

import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.North
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.South
import androidx.compose.material.icons.rounded.SwapHoriz
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import app.pocketos.R
import app.pocketos.core.money.Currencies
import app.pocketos.core.money.MoneyFormatter
import app.pocketos.domain.finance.FinanceCalculator
import app.pocketos.domain.finance.MonthPeriod
import app.pocketos.domain.finance.Transaction
import app.pocketos.domain.finance.TxType
import app.pocketos.domain.finance.Wallet
import app.pocketos.domain.finance.WalletType
import app.pocketos.ui.LocalAppContainer
import app.pocketos.ui.LocalAppUi
import app.pocketos.ui.components.AmountInput
import app.pocketos.ui.components.BottomClearance
import app.pocketos.ui.components.ChartSlice
import app.pocketos.ui.components.CurrencyPickerSheet
import app.pocketos.ui.components.DonutChart
import app.pocketos.ui.components.EmptyState
import app.pocketos.ui.components.GradientCard
import app.pocketos.ui.components.SectionHeader
import app.pocketos.ui.components.ToneIcon
import app.pocketos.ui.components.parseHex
import app.pocketos.ui.design.ButtonStyle
import app.pocketos.ui.design.GlassBottomSheet
import app.pocketos.ui.design.GlassCard
import app.pocketos.ui.design.GlassDialog
import app.pocketos.ui.design.GlassIconButton
import app.pocketos.ui.design.GlassLevel
import app.pocketos.ui.design.GlassTextField
import app.pocketos.ui.design.HapticType
import app.pocketos.ui.design.LocalHaptics
import app.pocketos.ui.design.PocketButton
import app.pocketos.ui.design.appear
import app.pocketos.ui.format.LocalFormatter
import app.pocketos.ui.navigation.Routes
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Accounting home: net worth, this month's income and spending with a
 * daily trend, wallets as cards, where the money went, and the ledger
 * grouped by day. Every entry point to "add" is one tap away.
 */
@Composable
fun WalletScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val ui = LocalAppUi.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsState(initial = container.latestSettings)
    val wallets by container.finance.wallets.collectAsState(initial = null)
    val transactions by container.finance.transactions.collectAsState(initial = null)
    val defaultWalletName = stringResource(R.string.default_wallet_name)
    val deletedLabel = stringResource(R.string.transaction_deleted)
    val undoLabel = stringResource(R.string.undo)

    LaunchedEffect(Unit) { container.finance.ensureDefaultWallet(defaultWalletName, settings.defaultCurrency) }
    var editingWallet by remember { mutableStateOf<Wallet?>(null) }
    var creatingWallet by remember { mutableStateOf(false) }

    val plans by container.finance.installments.collectAsState(initial = emptyList())
    val debts by container.finance.debts.collectAsState(initial = emptyList())
    WalletContent(
        wallets = wallets,
        transactions = transactions,
        installments = plans,
        debts = debts,
        onInstallments = { nav.navigate(Routes.Installments) },
        onDebts = { nav.navigate(Routes.Debts) },
        defaultCurrency = settings.defaultCurrency,
        today = container.clock.today(),
        onSettings = { nav.navigate(Routes.Settings) },
        onAdd = { ui.openTransaction(it) },
        onEdit = { ui.openTransaction(it.type, it.id) },
        onDelete = { tx ->
            scope.launch {
                container.finance.deleteTransaction(tx.id)?.let { removed ->
                    ui.undo(deletedLabel, undoLabel) { container.finance.restoreTransaction(removed) }
                }
            }
        },
        onEditWallet = { editingWallet = it },
        onNewWallet = { creatingWallet = true },
    )

    if (creatingWallet || editingWallet != null) {
        WalletEditorSheet(
            existing = editingWallet,
            defaultCurrency = settings.defaultCurrency,
            onDismiss = { creatingWallet = false; editingWallet = null },
        )
    }
}

/** Stateless body of the wallet screen (data in, intents out). */
@Composable
internal fun WalletContent(
    wallets: List<Wallet>?,
    transactions: List<Transaction>?,
    installments: List<app.pocketos.domain.finance.InstallmentPlan>,
    debts: List<app.pocketos.domain.finance.Debt>,
    onInstallments: () -> Unit,
    onDebts: () -> Unit,
    defaultCurrency: String,
    today: LocalDate,
    onSettings: () -> Unit,
    onAdd: (TxType) -> Unit,
    onEdit: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit,
    onEditWallet: (Wallet) -> Unit,
    onNewWallet: () -> Unit,
) {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    var offset by rememberSaveable { mutableStateOf(0) }
    val period = MonthPeriod.of(today, f.calendar).plus(offset)

    val allWallets = wallets.orEmpty()
    val txs = transactions.orEmpty()
    val walletsById = allWallets.associateBy { it.id }
    val netWorth = FinanceCalculator.netWorth(allWallets, txs)
    val primaryCurrency = netWorth.keys.firstOrNull { it == defaultCurrency } ?: netWorth.keys.firstOrNull() ?: defaultCurrency
    val monthTotals = FinanceCalculator.totals(txs, period)
    val primaryTotals = monthTotals.firstOrNull { it.currency == primaryCurrency }
    val monthTxs = txs.filter { it.date in period }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Spacing.gutter)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }

        // ---- Title + month switcher
        item(key = "title") {
            Row(Modifier.fillMaxWidth().padding(top = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.nav_wallet),
                    style = MaterialTheme.typography.headlineMedium,
                    color = c.textPrimary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                GlassIconButton(Icons.Rounded.Settings, stringResource(R.string.nav_settings), onSettings)
            }
            Spacer(Modifier.height(Spacing.sm))
            MonthSwitcher(f.monthTitle(period.year, period.month, period.calendar), canGoNext = offset < 0, onPrev = { offset-- }, onNext = { offset++ })
        }

        // ---- Hero: balance + month flow + trend
        item(key = "hero") {
            val balance = netWorth[primaryCurrency] ?: 0
            GradientCard(Modifier.fillMaxWidth().padding(top = Spacing.lg).appear(0)) {
                Text(
                    stringResource(R.string.total_balance).uppercase(f.locale),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(Spacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Currencies.flag(primaryCurrency), fontSize = 24.sp)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        f.money(balance, primaryCurrency, compact = true),
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                netWorth.filterKeys { it != primaryCurrency }.forEach { (cur, amount) ->
                    Text(
                        "${Currencies.flag(cur)}  ${f.money(amount, cur, compact = true)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
                Spacer(Modifier.height(Spacing.md))
                Sparkline(FinanceCalculator.dailySpending(txs, period, primaryCurrency), Modifier.fillMaxWidth().height(48.dp))
                Spacer(Modifier.height(Spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FlowPill(Icons.Rounded.South, stringResource(R.string.earned_this_month), f.amount(primaryTotals?.incomeMinor ?: 0, primaryCurrency), Modifier.weight(1f))
                    FlowPill(Icons.Rounded.North, stringResource(R.string.spent_this_month), f.amount(primaryTotals?.expenseMinor ?: 0, primaryCurrency), Modifier.weight(1f))
                }
            }
        }

        // ---- Primary actions
        item(key = "actions") {
            Row(Modifier.fillMaxWidth().padding(top = Spacing.lg).appear(1), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ActionTile(Icons.Rounded.North, stringResource(R.string.expense), txTone(TxType.EXPENSE), Modifier.weight(1f)) { onAdd(TxType.EXPENSE) }
                ActionTile(Icons.Rounded.South, stringResource(R.string.income), txTone(TxType.INCOME), Modifier.weight(1f)) { onAdd(TxType.INCOME) }
                ActionTile(Icons.Rounded.SwapHoriz, stringResource(R.string.transfer), txTone(TxType.TRANSFER), Modifier.weight(1f)) { onAdd(TxType.TRANSFER) }
            }
        }

        // ---- Installments & debts
        item(key = "obligations") {
            val nextPlan = app.pocketos.domain.finance.ObligationCalculator.upcoming(installments).firstOrNull()
            val debtSummary = app.pocketos.domain.finance.ObligationCalculator.debtSummary(debts).firstOrNull { it.currency == primaryCurrency }
            Row(Modifier.fillMaxWidth().padding(top = Spacing.md).appear(2), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FeatureTile(
                    Icons.Rounded.EventRepeat, c.tones.blue, stringResource(R.string.installments),
                    nextPlan?.nextDue?.let { f.dayLabel(it, today) + " \u00b7 " + f.money(nextPlan.amountMinor, nextPlan.currency, compact = true) }
                        ?: stringResource(R.string.add_installment),
                    Modifier.weight(1f), onInstallments,
                )
                FeatureTile(
                    Icons.Rounded.Handshake, c.tones.cyan, stringResource(R.string.debts_title),
                    debtSummary?.let { f.money(it.netMinor, it.currency, compact = true) } ?: stringResource(R.string.add_debt),
                    Modifier.weight(1f), onDebts,
                )
            }
        }

        // ---- Wallets rail
        item(key = "wallets_h") { SectionHeader(stringResource(R.string.wallets), action = stringResource(R.string.add_wallet), onAction = onNewWallet) }
        item(key = "wallets") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md), contentPadding = PaddingValues(end = Spacing.gutter, bottom = Spacing.md)) {
                items(allWallets.filterNot { it.archived }, key = { it.id }) { w ->
                    WalletCard(
                        w,
                        FinanceCalculator.balance(w, txs),
                        Modifier.width(200.dp).selectable(false, role = Role.Button, interactionSource = remember { MutableInteractionSource() }, indication = null) { onEditWallet(w) },
                    )
                }
                item(key = "add") {
                    AddWalletCard(Modifier.width(140.dp).selectable(false, role = Role.Button, interactionSource = remember { MutableInteractionSource() }, indication = null) { onNewWallet() })
                }
            }
        }

        // ---- Where the money went
        val slices = FinanceCalculator.spendingByCategory(txs, period, primaryCurrency)
        if (slices.isNotEmpty()) {
            item(key = "cat_h") { SectionHeader(stringResource(R.string.spending_by_category)) }
            item(key = "cats") { CategoryBreakdown(slices.map { it.category to it.amountMinor }, primaryCurrency, primaryTotals?.expenseMinor ?: 0) }
        }

        // ---- Ledger
        item(key = "tx_h") { SectionHeader(stringResource(R.string.transactions)) }
        if (transactions != null && txs.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    Icons.Rounded.AccountBalanceWallet,
                    stringResource(R.string.no_transactions_title),
                    stringResource(R.string.no_transactions_message),
                    actionLabel = stringResource(R.string.add_expense),
                    onAction = { onAdd(TxType.EXPENSE) },
                    compact = true,
                )
            }
        } else if (monthTxs.isEmpty()) {
            item(key = "empty_month") {
                Text(
                    stringResource(R.string.no_transactions_month),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                    modifier = Modifier.padding(vertical = Spacing.lg, horizontal = Spacing.xs),
                )
            }
        }
        FinanceCalculator.groupByDay(monthTxs).forEach { group ->
            item(key = "d_" + group.date) {
                DayHeader(group.date, today, group.transactions)
            }
            items(group.transactions, key = { "t_" + it.id }) { tx ->
                TransactionRow(
                    tx,
                    walletsById,
                    onClick = { onEdit(tx) },
                    onLongClick = { onDelete(tx) },
                    modifier = Modifier.padding(bottom = Spacing.sm).animateItem(),
                )
            }
        }
        item { BottomClearance() }
    }
}

// ---------------------------------------------------------------- Pieces

@Composable
private fun MonthSwitcher(title: String, canGoNext: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    val c = LocalPocketColors.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(verticalAlignment = Alignment.CenterVertically) {
        GlassIconButton(if (rtl) Icons.AutoMirrored.Rounded.KeyboardArrowRight else Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.previous_month), onPrev, size = 36.dp, level = GlassLevel.L1)
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.padding(horizontal = Spacing.sm))
        if (canGoNext) {
            GlassIconButton(if (rtl) Icons.AutoMirrored.Rounded.KeyboardArrowLeft else Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.next_month), onNext, size = 36.dp, level = GlassLevel.L1)
        }
    }
}

@Composable
private fun FlowPill(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(Spacing.sm))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** White trend line of daily spending, drawn on the hero card. */
@Composable
private fun Sparkline(values: List<Long>, modifier: Modifier = Modifier) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val max = values.max().coerceAtLeast(1).toFloat()
        val stepX = size.width / (values.size - 1)
        fun x(i: Int) = if (rtl) size.width - i * stepX else i * stepX
        fun y(v: Long) = size.height - (v / max) * (size.height - 6.dp.toPx()) - 3.dp.toPx()
        val line = Path()
        values.forEachIndexed { i, v ->
            if (i == 0) line.moveTo(x(i), y(v)) else {
                val px = x(i - 1)
                val py = y(values[i - 1])
                val cx = (px + x(i)) / 2
                line.cubicTo(cx, py, cx, y(v), x(i), y(v))
            }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(x(values.lastIndex), size.height)
            lineTo(x(0), size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f), Color.Transparent)))
        drawPath(line, Color.White, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun ActionTile(icon: ImageVector, label: String, tone: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalPocketColors.current
    GlassCard(modifier, onClick = onClick, contentPadding = PaddingValues(vertical = Spacing.md, horizontal = Spacing.sm)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            ToneIcon(icon, tone, size = 34.dp, filled = true)
            Spacer(Modifier.width(Spacing.sm))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FeatureTile(icon: ImageVector, tone: Color, title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalPocketColors.current
    GlassCard(
        modifier,
        onClick = onClick,
        contentPadding = PaddingValues(Spacing.md),
        decoration = {
            drawRect(Brush.radialGradient(listOf(tone.copy(alpha = if (c.isDark) 0.20f else 0.10f), Color.Transparent), center = Offset(size.width, 0f), radius = size.width))
        },
    ) {
        ToneIcon(icon, tone, size = 40.dp, filled = true)
        Spacer(Modifier.height(Spacing.md))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CategoryBreakdown(rows: List<Pair<String, Long>>, currency: String, total: Long) {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                slices = rows.map { (cat, amount) -> ChartSlice(cat, amount.toFloat(), financeCategoryColor(cat)) },
                description = rows.joinToString { it.first },
                size = 128.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(f.amount(total, currency), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = c.textPrimary, maxLines = 1, modifier = Modifier.padding(horizontal = 22.dp))
                    Text(Currencies.displayName(currency, f.locale), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                }
            }
            Spacer(Modifier.width(Spacing.lg))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                rows.take(4).forEach { (cat, amount) ->
                    val share = if (total > 0) amount.toFloat() / total else 0f
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(financeCategoryColor(cat)))
                            Spacer(Modifier.width(6.dp))
                            Text(financeCategoryName(cat), style = MaterialTheme.typography.labelMedium, color = c.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(f.localizeDigits("${(share * 100).toInt()}%"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = c.textSecondary)
                        }
                        Spacer(Modifier.height(4.dp))
                        app.pocketos.ui.design.GlassProgressBar(share, color = financeCategoryColor(cat))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate, today: LocalDate, list: List<Transaction>) {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val byCurrency = list.filter { it.type != TxType.TRANSFER }.groupBy { it.currency }
    Row(Modifier.fillMaxWidth().padding(top = Spacing.md, bottom = Spacing.sm, start = Spacing.xs, end = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(f.dayLabel(date, today), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = c.textSecondary, modifier = Modifier.weight(1f).semantics { heading() })
        byCurrency.forEach { (cur, items) ->
            val net = items.sumOf { if (it.type == TxType.INCOME) it.amountMinor else -it.amountMinor }
            Text(
                // Bidi-isolated so the sign stays glued to the number in RTL text.
                "\u2066" + (if (net > 0) "+" else if (net < 0) "\u2212" else "") + f.amount(kotlin.math.abs(net), cur) + "\u2069 " +
                    Currencies.displayName(cur, f.locale),
                style = MaterialTheme.typography.labelMedium,
                color = if (net >= 0) c.tones.green else c.textTertiary,
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }
    }
}

@Composable
private fun TransactionRow(tx: Transaction, wallets: Map<String, Wallet>, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalPocketColors.current
    val tone = financeCategoryColor(tx.category)
    val title = tx.note ?: financeCategoryName(tx.category)
    val walletName = wallets[tx.walletId]?.name.orEmpty()
    val subtitle = when (tx.type) {
        TxType.TRANSFER -> "$walletName → ${wallets[tx.toWalletId]?.name.orEmpty()}"
        else -> if (tx.note != null) "${financeCategoryName(tx.category)} · $walletName" else walletName
    }
    GlassCard(
        modifier.fillMaxWidth(),
        onClick = onClick,
        onLongClick = onLongClick,
        contentPadding = PaddingValues(Spacing.md),
        decoration = {
            val start = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width
            drawRect(Brush.radialGradient(listOf(tone.copy(alpha = if (c.isDark) 0.16f else 0.08f), Color.Transparent), center = Offset(start, size.height / 2), radius = size.height * 1.5f))
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToneIcon(financeCategoryIcon(tx.category), tone, size = 44.dp, filled = true)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(Spacing.sm))
            val amountColor = when (tx.type) {
                TxType.INCOME -> c.tones.green
                TxType.EXPENSE -> c.textPrimary
                TxType.TRANSFER -> c.tones.blue
            }
            // In RTL a leading sign drifts away from the digits, so show a direction arrow instead.
            val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (rtl && tx.type != TxType.TRANSFER) {
                    Icon(if (tx.type == TxType.INCOME) Icons.Rounded.South else Icons.Rounded.North, null, tint = amountColor, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(2.dp))
                }
                Text(
                    if (rtl) LocalFormatter.current.money(tx.amountMinor, tx.currency, compact = true) else signedMoney(tx.type, tx.amountMinor, tx.currency),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = amountColor,
                    maxLines = 1,
                )
            }
        }
    }
}

// ---------------------------------------------------------- Wallet editor

@Composable
private fun WalletEditorSheet(existing: Wallet?, defaultCurrency: String, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var type by rememberSaveable { mutableStateOf(existing?.type ?: WalletType.CARD) }
    var currency by rememberSaveable { mutableStateOf(existing?.currency ?: defaultCurrency) }
    var balance by rememberSaveable { mutableStateOf(existing?.let { MoneyFormatter.formatPlain(it.openingBalanceMinor, it.currency) }.orEmpty()) }
    var color by rememberSaveable { mutableStateOf(existing?.color ?: walletPalette[1]) }
    var pickCurrency by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val transactions by container.finance.transactions.collectAsState(initial = emptyList())

    GlassBottomSheet(onDismiss = onDismiss, title = stringResource(if (existing == null) R.string.add_wallet else R.string.edit_wallet)) {
        Column(Modifier.fillMaxWidth()) {
            // Live preview of the card being designed.
            val preview = Wallet(
                id = existing?.id ?: "preview", name = name.ifBlank { walletTypeName(type) }, type = type, currency = currency,
                openingBalanceMinor = MoneyFormatter.parse(balance.ifEmpty { "0" }, currency) ?: 0, color = color, sortOrder = 0, archived = false,
                createdAt = java.time.Instant.EPOCH, updatedAt = java.time.Instant.EPOCH,
            )
            val previewBalance = if (existing != null) {
                FinanceCalculator.balance(preview, transactions)
            } else preview.openingBalanceMinor
            WalletCard(preview, previewBalance, Modifier.fillMaxWidth())
            Spacer(Modifier.height(Spacing.lg))

            GlassTextField(name, { name = it.take(60) }, label = stringResource(R.string.wallet_name), placeholder = stringResource(R.string.wallet_name_hint))
            Spacer(Modifier.height(Spacing.md))
            Text(stringResource(R.string.wallet_type), style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xs))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                WalletType.entries.forEach { t ->
                    app.pocketos.ui.design.GlassChip(
                        walletTypeName(t), t == type, { type = t },
                        leading = { Icon(walletTypeIcon(t), null, tint = if (t == type) c.accent else c.textSecondary, modifier = Modifier.size(16.dp)) },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
            Row(verticalAlignment = Alignment.Bottom) {
                GlassTextField(
                    balance,
                    { balance = AmountInput.sanitize(it, currency) },
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.opening_balance),
                    placeholder = stringResource(R.string.opening_balance_hint),
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                    visualTransformation = remember(f.locale) { AmountInput.Grouping(f.locale.language == "fa") },
                )
                Spacer(Modifier.width(Spacing.sm))
                GlassCard(
                    level = GlassLevel.L1,
                    contentPadding = PaddingValues(horizontal = Spacing.md, vertical = 14.dp),
                    // Currency can change until money has moved through the wallet.
                    onClick = if (existing == null || transactions.none { it.walletId == existing.id || it.toWalletId == existing.id }) ({ pickCurrency = true }) else null,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Currencies.flag(currency), fontSize = 18.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(currency, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))
            Text(stringResource(R.string.wallet_colour), style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                walletPalette.forEach { hex ->
                    val swatch = parseHex(hex) ?: c.accent
                    val selected = hex == color
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .border(if (selected) 3.dp else 0.dp, c.textPrimary, CircleShape)
                            .selectable(selected, role = Role.RadioButton, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                color = hex
                                haptics.perform(HapticType.Selection)
                            },
                        contentAlignment = Alignment.Center,
                    ) { if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                }
            }
            Spacer(Modifier.height(Spacing.xl))
            PocketButton(
                stringResource(R.string.save),
                {
                    scope.launch {
                        val now = java.time.Instant.now()
                        container.finance.saveWallet(
                            preview.copy(
                                id = existing?.id ?: container.finance.newId(),
                                name = name.ifBlank { preview.name },
                                sortOrder = existing?.sortOrder ?: 0,
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
                haptic = HapticType.Confirm,
            )
            if (existing != null) {
                Spacer(Modifier.height(Spacing.sm))
                PocketButton(stringResource(R.string.delete), { confirmDelete = true }, modifier = Modifier.fillMaxWidth(), style = ButtonStyle.Text, haptic = HapticType.Warning)
            }
        }
    }

    if (pickCurrency) {
        CurrencyPickerSheet(
            selected = currency,
            onPick = {
                currency = it
                balance = AmountInput.adapt(balance, it)
                pickCurrency = false
            },
            onDismiss = { pickCurrency = false },
        )
    }
    if (confirmDelete && existing != null) {
        GlassDialog(
            onDismiss = { confirmDelete = false },
            title = stringResource(R.string.delete_wallet_title),
            message = stringResource(R.string.delete_wallet_message, existing.name),
            confirmText = stringResource(R.string.delete),
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    container.finance.deleteWallet(existing.id)
                    onDismiss()
                }
            },
            dismissText = stringResource(R.string.cancel),
            destructive = true,
        )
    }
}

