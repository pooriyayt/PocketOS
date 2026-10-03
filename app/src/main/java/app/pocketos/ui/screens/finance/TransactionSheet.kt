package app.pocketos.ui.screens.finance

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.North
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketos.R
import app.pocketos.core.money.Currencies
import app.pocketos.core.money.MoneyFormatter
import app.pocketos.domain.finance.FinanceCategories
import app.pocketos.domain.finance.Transaction
import app.pocketos.domain.finance.TxType
import app.pocketos.domain.finance.Wallet
import app.pocketos.ui.LocalAppContainer
import app.pocketos.ui.LocalAppUi
import app.pocketos.ui.components.AmountInput
import app.pocketos.ui.components.ToneIcon
import app.pocketos.ui.design.GlassBottomSheet
import app.pocketos.ui.design.GlassLevel
import app.pocketos.ui.design.GlassTextField
import app.pocketos.ui.design.HapticType
import app.pocketos.ui.design.LocalHaptics
import app.pocketos.ui.design.LocalMotion
import app.pocketos.ui.design.PocketButton
import app.pocketos.ui.design.glass
import app.pocketos.ui.design.pressFeedback
import app.pocketos.ui.format.LocalFormatter
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.Shapes
import app.pocketos.ui.theme.Spacing
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** What the transaction sheet should open with. */
data class TransactionRequest(val type: TxType = TxType.EXPENSE, val editId: String? = null)

/**
 * Money entry, designed for speed: pick a direction, tap the amount on the
 * built-in keypad, tap a category, save. Wallet and date default to the
 * last-used wallet and today, so the common case is three taps.
 */
@Composable
fun TransactionSheet(request: TransactionRequest, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val ui = LocalAppUi.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsState(initial = container.latestSettings)
    val wallets by container.finance.wallets.collectAsState(initial = null)
    val defaultWalletName = stringResource(R.string.default_wallet_name)
    val savedLabel = stringResource(R.string.transaction_saved)
    val deletedLabel = stringResource(R.string.transaction_deleted)
    val undoLabel = stringResource(R.string.undo)
    var editing by remember { mutableStateOf<Transaction?>(null) }

    // First use: make sure there is a wallet to spend from.
    LaunchedEffect(Unit) { container.finance.ensureDefaultWallet(defaultWalletName, settings.defaultCurrency) }
    // Editing: load the transaction once.
    LaunchedEffect(request.editId) {
        val id = request.editId ?: return@LaunchedEffect
        editing = container.finance.transactions.first().firstOrNull { it.id == id }
    }

    GlassBottomSheet(onDismiss = onDismiss) {
        if (request.editId == null || editing != null) {
            TransactionEditor(
                initialType = request.type,
                editing = editing,
                wallets = wallets.orEmpty(),
                defaultCurrency = settings.defaultCurrency,
                onSave = { tx ->
                    scope.launch {
                        container.finance.saveTransaction(tx)
                        haptics.perform(HapticType.Success)
                        ui.message(savedLabel)
                        onDismiss()
                    }
                },
                onDelete = editing?.let { tx ->
                    {
                        scope.launch {
                            container.finance.deleteTransaction(tx.id)?.let { removed ->
                                ui.undo(deletedLabel, undoLabel) { container.finance.restoreTransaction(removed) }
                            }
                            onDismiss()
                        }
                    }
                },
            )
        }
    }
}

/** The entry form itself (state lives here; persistence is the caller's job). */
@Composable
internal fun TransactionEditor(
    initialType: TxType,
    editing: Transaction?,
    wallets: List<Wallet>,
    defaultCurrency: String,
    onSave: (Transaction) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val haptics = LocalHaptics.current
    var type by rememberSaveable { mutableStateOf(editing?.type ?: initialType) }
    var amount by rememberSaveable { mutableStateOf(editing?.let { MoneyFormatter.formatPlain(it.amountMinor, it.currency) }.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(editing?.category ?: FinanceCategories.defaultFor(initialType)) }
    var walletId by rememberSaveable { mutableStateOf(editing?.walletId) }
    var toWalletId by rememberSaveable { mutableStateOf(editing?.toWalletId) }
    var dateEpoch by rememberSaveable { mutableStateOf(editing?.date?.toEpochDay() ?: LocalDate.now().toEpochDay()) }
    var note by rememberSaveable { mutableStateOf(editing?.note.orEmpty()) }
    var pickDate by remember { mutableStateOf(false) }

    val active = wallets.filterNot { it.archived }
    if (walletId == null || active.none { it.id == walletId }) walletId = active.firstOrNull()?.id
    val wallet = active.firstOrNull { it.id == walletId }
    val currency = wallet?.currency ?: defaultCurrency
    val transferTargets = active.filter { it.id != walletId && it.currency == currency }
    if (type == TxType.TRANSFER && (toWalletId == null || transferTargets.none { it.id == toWalletId })) toWalletId = transferTargets.firstOrNull()?.id
    val date = LocalDate.ofEpochDay(dateEpoch)
    val tone = txTone(type)
    val amountMinor = MoneyFormatter.parse(amount.ifEmpty { "0" }, currency) ?: 0
    val canSave = amountMinor > 0 && wallet != null && (type != TxType.TRANSFER || toWalletId != null)

    fun save() {
        val w = wallet ?: return
        if (!canSave) return
        val now = java.time.Instant.now()
        onSave(
            Transaction(
                id = editing?.id ?: java.util.UUID.randomUUID().toString(),
                type = type,
                amountMinor = amountMinor,
                currency = w.currency,
                walletId = w.id,
                toWalletId = toWalletId,
                category = if (type == TxType.TRANSFER) FinanceCategories.TRANSFER else category,
                note = note,
                date = date,
                createdAt = editing?.createdAt ?: now,
                updatedAt = now,
            )
        )
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TypeSwitcher(type, Modifier.weight(1f)) { new ->
                if (new != type) {
                    type = new
                    category = FinanceCategories.defaultFor(new)
                    haptics.perform(HapticType.Selection)
                }
            }
            onDelete?.let { delete ->
                Spacer(Modifier.width(Spacing.sm))
                app.pocketos.ui.design.GlassIconButton(Icons.Rounded.DeleteOutline, stringResource(R.string.delete), delete, tint = c.danger, haptic = HapticType.Delete)
            }
        }

        // ---- Amount
        Spacer(Modifier.height(Spacing.xl))
        AmountDisplay(amount, currency, type, tone)
        Spacer(Modifier.height(Spacing.lg))

        // ---- Category / wallets
        if (type == TxType.TRANSFER) {
            if (transferTargets.isEmpty()) {
                Text(
                    stringResource(R.string.need_two_wallets),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.warning,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
                )
            }
            FieldLabel(stringResource(R.string.from_wallet))
            WalletPicker(active, walletId) { walletId = it }
            Spacer(Modifier.height(Spacing.md))
            FieldLabel(stringResource(R.string.to_wallet))
            WalletPicker(transferTargets, toWalletId) { toWalletId = it }
        } else {
            FieldLabel(stringResource(R.string.category))
            CategoryRail(type, category) {
                category = it
                haptics.perform(HapticType.Selection)
            }
            if (active.size > 1) {
                Spacer(Modifier.height(Spacing.md))
                FieldLabel(stringResource(R.string.wallet))
                WalletPicker(active, walletId) { walletId = it }
            }
        }

        // ---- Date
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            val today = LocalDate.now()
            DateChip(stringResource(R.string.today), date == today) { dateEpoch = today.toEpochDay() }
            DateChip(stringResource(R.string.yesterday), date == today.minusDays(1)) { dateEpoch = today.minusDays(1).toEpochDay() }
            val custom = date != today && date != today.minusDays(1)
            DateChip(if (custom) f.date(date) else stringResource(R.string.date), custom, Icons.Rounded.CalendarMonth) { pickDate = true }
        }

        // ---- Note
        Spacer(Modifier.height(Spacing.md))
        GlassTextField(note, { note = it.take(200) }, placeholder = stringResource(R.string.note_optional))

        // ---- Keypad + save
        Spacer(Modifier.height(Spacing.lg))
        Keypad(
            allowDecimal = Currencies.info(currency).fractionDigits > 0,
            onKey = { key ->
                haptics.perform(HapticType.LightTap)
                amount = when (key) {
                    "back" -> amount.dropLast(1)
                    else -> AmountInput.sanitize(amount + key, currency)
                }
            },
            onClear = { amount = "" },
        )
        Spacer(Modifier.height(Spacing.lg))
        PocketButton(
            stringResource(R.string.save),
            ::save,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            icon = Icons.Rounded.Check,
            enabled = canSave,
            haptic = HapticType.Confirm,
        )
    }

    if (pickDate) {
        app.pocketos.ui.components.CalendarDatePickerSheet(date, onPick = {
            dateEpoch = it.toEpochDay()
            pickDate = false
        }, onDismiss = { pickDate = false })
    }
}

// ------------------------------------------------------------- Pieces

@Composable
private fun TypeSwitcher(selected: TxType, modifier: Modifier = Modifier, onSelect: (TxType) -> Unit) {
    val c = LocalPocketColors.current
    Row(
        modifier.height(48.dp).glass(GlassLevel.L1, Shapes.pill).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(
            Triple(TxType.EXPENSE, R.string.expense, Icons.Rounded.North),
            Triple(TxType.INCOME, R.string.income, Icons.Rounded.South),
            Triple(TxType.TRANSFER, R.string.transfer, Icons.Rounded.SwapHoriz),
        ).forEach { (type, label, icon) ->
            val isSelected = type == selected
            val tone = txTone(type)
            val bg by animateFloatAsState(if (isSelected) 1f else 0f, LocalMotion.current.standard(), label = "typeBg")
            Row(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(Shapes.pill)
                    .background(Brush.horizontalGradient(listOf(tone.copy(alpha = bg), tone.copy(alpha = 0.78f * bg))))
                    .selectable(isSelected, role = Role.Tab, interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(type) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = if (isSelected) Color.White else c.textSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.White else c.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AmountDisplay(raw: String, currency: String, type: TxType, tone: Color) {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val persian = f.locale.language == "fa"
    val shown = AmountInput.Grouping(persian).filter(AnnotatedString(raw.ifEmpty { "0" })).text.text
    val color by animateColorAsState(if (raw.isEmpty()) c.textTertiary else tone, LocalMotion.current.standard(), label = "amountColor")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (type != TxType.TRANSFER && raw.isNotEmpty()) {
                Text(if (type == TxType.EXPENSE) "−" else "+", style = MaterialTheme.typography.displayMedium, color = color)
            }
            Text(
                shown,
                style = MaterialTheme.typography.displayMedium.copy(fontSize = if (shown.length > 12) 34.sp else 44.sp),
                color = color,
                maxLines = 1,
                modifier = Modifier.semantics { contentDescription = shown },
            )
        }
        Row(
            Modifier.clip(Shapes.pill).background(c.textPrimary.copy(alpha = if (c.isDark) 0.08f else 0.05f)).padding(horizontal = Spacing.md, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(Currencies.flag(currency), fontSize = 14.sp)
            Spacer(Modifier.width(6.dp))
            Text(Currencies.displayName(currency, f.locale), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = LocalPocketColors.current.textTertiary,
        modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.sm),
    )
}

@Composable
private fun CategoryRail(type: TxType, selected: String, onSelect: (String) -> Unit) {
    val c = LocalPocketColors.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), contentPadding = PaddingValues(horizontal = 2.dp)) {
        items(FinanceCategories.forType(type), key = { it.id }) { cat ->
            val isSelected = cat.id == selected
            val tone = financeCategoryColor(cat.id)
            Column(
                Modifier
                    .width(76.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isSelected) tone.copy(alpha = if (c.isDark) 0.18f else 0.10f) else Color.Transparent)
                    .border(1.5.dp, if (isSelected) tone.copy(alpha = 0.7f) else Color.Transparent, RoundedCornerShape(18.dp))
                    .selectable(isSelected, role = Role.RadioButton, interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(cat.id) }
                    .padding(vertical = Spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ToneIcon(financeCategoryIcon(cat.id), tone, size = 44.dp, filled = isSelected)
                Spacer(Modifier.height(6.dp))
                Text(
                    financeCategoryName(cat.id),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) c.textPrimary else c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun WalletPicker(wallets: List<Wallet>, selected: String?, onSelect: (String) -> Unit) {
    val c = LocalPocketColors.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        items(wallets, key = { it.id }) { w ->
            val isSelected = w.id == selected
            val tone = walletColor(w)
            Row(
                Modifier
                    .clip(Shapes.pill)
                    .background(if (isSelected) Brush.horizontalGradient(listOf(tone, tone.copy(alpha = 0.75f))) else Brush.horizontalGradient(listOf(c.textPrimary.copy(alpha = 0.06f), c.textPrimary.copy(alpha = 0.06f))))
                    .selectable(isSelected, role = Role.RadioButton, interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(w.id) }
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(walletTypeIcon(w.type), null, tint = if (isSelected) Color.White else tone, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(w.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = if (isSelected) Color.White else c.textPrimary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun DateChip(label: String, selected: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    app.pocketos.ui.design.GlassChip(
        label, selected, onClick,
        leading = icon?.let { { Icon(it, null, tint = LocalPocketColors.current.textSecondary, modifier = Modifier.size(16.dp)) } },
    )
}

/** Big, thumb-friendly number pad. The bottom-left key is "." or "000". */
@Composable
private fun Keypad(allowDecimal: Boolean, onKey: (String) -> Unit, onClear: () -> Unit) {
    val f = LocalFormatter.current
    val c = LocalPocketColors.current
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(if (allowDecimal) "." else "000", "0", "back"))
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                row.forEach { key ->
                    val interaction = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(56.dp)
                            .pressFeedback(interaction, RoundedCornerShape(18.dp))
                            .glass(GlassLevel.L1, RoundedCornerShape(18.dp))
                            .keypadClick(interaction, onClick = { onKey(key) }, onLongClick = if (key == "back") onClear else null)
                            .semantics { if (key == "back") contentDescription = "Backspace" },
                        contentAlignment = Alignment.Center,
                    ) {
                        when (key) {
                            "back" -> Icon(Icons.AutoMirrored.Rounded.Backspace, null, tint = c.textSecondary)
                            "." -> Text(if (f.locale.language == "fa") "٫" else ".", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
                            else -> Text(f.localizeDigits(key), style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.keypadClick(interaction: MutableInteractionSource, onClick: () -> Unit, onLongClick: (() -> Unit)?): Modifier =
    this.then(
        Modifier.combinedClickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick, onLongClick = onLongClick)
    )

