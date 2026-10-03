package app.pocketos.data.repository

import app.pocketos.core.AppClock
import app.pocketos.data.local.DatabaseManager
import app.pocketos.core.time.CalendarKind
import app.pocketos.data.local.CheckEntity
import app.pocketos.data.local.DebtEntity
import app.pocketos.data.local.InstallmentEntity
import app.pocketos.data.local.TransactionEntity
import app.pocketos.domain.finance.CheckDirection
import app.pocketos.domain.finance.CheckItem
import app.pocketos.domain.finance.CheckStatus
import app.pocketos.domain.finance.Debt
import app.pocketos.domain.finance.DebtDirection
import app.pocketos.domain.finance.FinanceCategories
import app.pocketos.domain.finance.InstallmentPlan
import app.pocketos.data.local.WalletEntity
import app.pocketos.domain.finance.Transaction
import app.pocketos.domain.finance.TxType
import app.pocketos.domain.finance.Wallet
import app.pocketos.domain.finance.WalletType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Wallets and transactions (local-only, inside the encrypted database). */
@OptIn(ExperimentalCoroutinesApi::class)
class FinanceRepository(
    private val databases: DatabaseManager,
    private val clock: AppClock,
    private val effects: SideEffects,
) {
    private val db get() = databases.current

    val wallets: Flow<List<Wallet>> = databases.active.flatMapLatest { it.db.wallets().observeAll() }
        .map { list -> list.map { it.toDomain() } }

    val transactions: Flow<List<Transaction>> = databases.active.flatMapLatest { it.db.transactions().observeAll() }
        .map { list -> list.map { it.toDomain() } }

    fun newId(): String = UUID.randomUUID().toString()

    /** Creates a first "cash" wallet so adding an expense never starts with a setup chore. */
    suspend fun ensureDefaultWallet(name: String, currency: String): Wallet? {
        if (db.wallets().count() > 0) return null
        val now = clock.now()
        return saveWallet(Wallet(newId(), name, WalletType.CASH, currency, 0, null, 0, false, now, now))
    }

    /** The first active wallet in [currency], creating a cash wallet for it when none exists. */
    suspend fun walletFor(currency: String, defaultName: String): Wallet {
        db.wallets().everything().map { it.toDomain() }
            .filter { it.currency == currency && !it.archived }
            .minByOrNull { it.sortOrder }
            ?.let { return it }
        val now = clock.now()
        return saveWallet(Wallet(newId(), defaultName, WalletType.CASH, currency, 0, null, 0, false, now, now))
    }

    suspend fun saveWallet(wallet: Wallet): Wallet {
        val existing = db.wallets().get(wallet.id)
        val now = clock.now()
        val saved = wallet.copy(
            name = wallet.name.trim().take(60),
            sortOrder = existing?.sortOrder ?: db.wallets().count(),
            createdAt = existing?.let { Instant.ofEpochMilli(it.createdAt) } ?: now,
            updatedAt = now,
        )
        db.wallets().upsert(saved.toEntity())
        effects.onDataChanged()
        return saved
    }

    /** Deletes a wallet together with its transactions. */
    suspend fun deleteWallet(id: String) {
        db.transactions().deleteForWallet(id)
        db.wallets().delete(id)
        effects.onDataChanged()
    }

    suspend fun saveTransaction(tx: Transaction): Transaction {
        val existing = db.transactions().get(tx.id)
        val now = clock.now()
        val saved = tx.copy(
            note = tx.note?.trim()?.take(200)?.ifEmpty { null },
            toWalletId = if (tx.type == TxType.TRANSFER) tx.toWalletId else null,
            createdAt = existing?.let { Instant.ofEpochMilli(it.createdAt) } ?: now,
            updatedAt = now,
        )
        db.transactions().upsert(saved.toEntity())
        effects.onDataChanged()
        return saved
    }

    /** Deletes and returns the removed transaction so the UI can offer undo. */
    suspend fun deleteTransaction(id: String): Transaction? {
        val entity = db.transactions().get(id) ?: return null
        db.transactions().delete(id)
        effects.onDataChanged()
        return entity.toDomain()
    }

    suspend fun restoreTransaction(tx: Transaction) {
        db.transactions().upsert(tx.toEntity())
        effects.onDataChanged()
    }

    // ----------------------------------------------------------- Installments

    val installments: Flow<List<InstallmentPlan>> = databases.active.flatMapLatest { it.db.installments().observeAll() }
        .map { list -> list.map { it.toDomain() } }

    suspend fun saveInstallment(plan: InstallmentPlan): InstallmentPlan {
        val existing = db.installments().get(plan.id)
        val now = clock.now()
        val total = plan.totalCount.coerceIn(1, 600)
        val saved = plan.copy(
            title = plan.title.trim().take(80),
            lender = plan.lender?.trim()?.take(80)?.ifEmpty { null },
            note = plan.note?.trim()?.take(500)?.ifEmpty { null },
            totalCount = total,
            paidCount = plan.paidCount.coerceIn(0, total),
            createdAt = existing?.let { Instant.ofEpochMilli(it.createdAt) } ?: now,
            updatedAt = now,
        )
        db.installments().upsert(saved.toEntity())
        effects.onInstallmentChanged(saved.id)
        effects.onDataChanged()
        return saved
    }

    suspend fun deleteInstallment(id: String) {
        db.installments().delete(id)
        effects.onInstallmentChanged(id)
        effects.onDataChanged()
    }

    /**
     * Marks the next installment as paid. When the plan has a wallet the
     * payment is also recorded there as an expense. Returns that
     * transaction's id (if any) so the UI can undo both together.
     */
    suspend fun payInstallment(id: String, note: String): String? {
        val plan = db.installments().get(id)?.toDomain()?.takeIf { !it.isFinished } ?: return null
        saveInstallment(plan.copy(paidCount = plan.paidCount + 1))
        val walletId = plan.walletId?.takeIf { db.wallets().get(it) != null } ?: return null
        val now = clock.now()
        return saveTransaction(
            Transaction(newId(), TxType.EXPENSE, plan.amountMinor, plan.currency, walletId, null,
                FinanceCategories.INSTALLMENTS, note, clock.today(), now, now)
        ).id
    }

    suspend fun undoInstallmentPayment(id: String, transactionId: String?) {
        val plan = db.installments().get(id)?.toDomain() ?: return
        saveInstallment(plan.copy(paidCount = (plan.paidCount - 1).coerceAtLeast(0)))
        transactionId?.let { deleteTransaction(it) }
    }

    // ------------------------------------------------------------------ Debts

    val debts: Flow<List<Debt>> = databases.active.flatMapLatest { it.db.debts().observeAll() }
        .map { list -> list.map { it.toDomain() } }

    /**
     * Saves a debt. For a new debt with [walletId], the money that changed
     * hands is recorded in that wallet (lending takes it out, borrowing adds it).
     */
    suspend fun saveDebt(debt: Debt, walletId: String? = null): Debt {
        val existing = db.debts().get(debt.id)
        val now = clock.now()
        val saved = debt.copy(
            person = debt.person.trim().take(80),
            note = debt.note?.trim()?.take(500)?.ifEmpty { null },
            settledMinor = debt.settledMinor.coerceIn(0, debt.amountMinor),
            createdAt = existing?.let { Instant.ofEpochMilli(it.createdAt) } ?: now,
            updatedAt = now,
        )
        db.debts().upsert(saved.toEntity())
        if (existing == null && walletId != null) {
            val lent = saved.direction == DebtDirection.OWED_TO_ME
            saveTransaction(
                Transaction(newId(), if (lent) TxType.EXPENSE else TxType.INCOME, saved.amountMinor, saved.currency, walletId, null,
                    if (lent) FinanceCategories.LENT else FinanceCategories.BORROWED, saved.person, saved.date, now, now)
            )
        }
        effects.onDebtChanged(saved.id)
        effects.onDataChanged()
        return saved
    }

    /** Records a (partial) repayment; optionally moves the money through [walletId]. */
    suspend fun repayDebt(id: String, amountMinor: Long, walletId: String?): Debt? {
        val debt = db.debts().get(id)?.toDomain() ?: return null
        val pay = amountMinor.coerceIn(0, debt.remainingMinor)
        if (pay <= 0) return debt
        val saved = saveDebt(debt.copy(settledMinor = debt.settledMinor + pay))
        if (walletId != null) {
            val now = clock.now()
            val owedToMe = debt.direction == DebtDirection.OWED_TO_ME
            saveTransaction(
                Transaction(newId(), if (owedToMe) TxType.INCOME else TxType.EXPENSE, pay, debt.currency, walletId, null,
                    if (owedToMe) FinanceCategories.DEBT_RECEIVED else FinanceCategories.DEBT_PAID, debt.person, clock.today(), now, now)
            )
        }
        return saved
    }

    suspend fun deleteDebt(id: String) {
        db.debts().delete(id)
        effects.onDebtChanged(id)
        effects.onDataChanged()
    }

    // ----------------------------------------------------------------- Checks

    val checks: Flow<List<CheckItem>> = databases.active.flatMapLatest { it.db.checks().observeAll() }
        .map { list -> list.map { it.toDomain() } }

    suspend fun saveCheck(check: CheckItem): CheckItem {
        val existing = db.checks().get(check.id)
        val now = clock.now()
        val saved = check.copy(
            title = check.title.trim().take(80),
            counterparty = check.counterparty.trim().take(80),
            sayadNumber = check.sayadNumber?.trim()?.take(40)?.ifEmpty { null },
            bankName = check.bankName?.trim()?.take(60)?.ifEmpty { null },
            note = check.note?.trim()?.take(500)?.ifEmpty { null },
            reminderDays = check.reminderDays.coerceIn(0, 30),
            createdAt = existing?.let { Instant.ofEpochMilli(it.createdAt) } ?: now,
            updatedAt = now,
        )
        db.checks().upsert(saved.toEntity())
        effects.onCheckChanged(saved.id)
        effects.onDataChanged()
        return saved
    }

    suspend fun markCheckStatus(id: String, status: CheckStatus, walletId: String? = null) {
        val check = db.checks().get(id)?.toDomain() ?: return
        val wasPending = check.isPending
        val now = clock.now()
        db.checks().updateStatus(id, status.wire, now.toEpochMilli())
        val targetWallet = walletId ?: check.walletId
        if (wasPending && status == CheckStatus.CLEARED && targetWallet != null && db.wallets().get(targetWallet) != null) {
            val isIssued = check.direction == CheckDirection.ISSUED
            val cat = if (isIssued) FinanceCategories.DEBT_PAID else FinanceCategories.DEBT_RECEIVED
            val note = (if (isIssued) "وصول چک پرداختی: " else "وصول چک دریافتی: ") + check.title.ifEmpty { check.counterparty }
            saveTransaction(
                Transaction(
                    newId(),
                    if (isIssued) TxType.EXPENSE else TxType.INCOME,
                    check.amountMinor,
                    check.currency,
                    targetWallet,
                    null,
                    cat,
                    note,
                    clock.today(),
                    now,
                    now,
                )
            )
        }
        effects.onCheckChanged(id)
        effects.onDataChanged()
    }

    suspend fun deleteCheck(id: String) {
        db.checks().delete(id)
        effects.onCheckChanged(id)
        effects.onDataChanged()
    }
}

fun InstallmentEntity.toDomain() = InstallmentPlan(
    id = id,
    title = title,
    lender = lender,
    amountMinor = amountMinor,
    currency = currency,
    totalCount = totalCount,
    paidCount = paidCount,
    firstDue = runCatching { LocalDate.parse(firstDue) }.getOrElse { LocalDate.now() },
    intervalMonths = intervalMonths.coerceAtLeast(1),
    calendar = runCatching { CalendarKind.valueOf(calendar) }.getOrDefault(CalendarKind.GREGORIAN),
    reminderDays = reminderDays,
    walletId = walletId,
    note = note,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

fun InstallmentPlan.toEntity() = InstallmentEntity(
    id = id,
    title = title,
    lender = lender,
    amountMinor = amountMinor,
    currency = currency,
    totalCount = totalCount,
    paidCount = paidCount,
    firstDue = firstDue.toString(),
    intervalMonths = intervalMonths,
    calendar = calendar.name,
    reminderDays = reminderDays,
    walletId = walletId,
    note = note,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

fun DebtEntity.toDomain() = Debt(
    id = id,
    person = person,
    direction = DebtDirection.fromWire(direction),
    amountMinor = amountMinor,
    currency = currency,
    settledMinor = settledMinor,
    date = runCatching { LocalDate.parse(date) }.getOrElse { LocalDate.now() },
    dueDate = dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    note = note,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

fun Debt.toEntity() = DebtEntity(
    id = id,
    person = person,
    direction = direction.wire,
    amountMinor = amountMinor,
    currency = currency,
    settledMinor = settledMinor,
    date = date.toString(),
    dueDate = dueDate?.toString(),
    note = note,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

fun WalletEntity.toDomain() = Wallet(
    id = id,
    name = name,
    type = WalletType.fromWire(type),
    currency = currency,
    openingBalanceMinor = openingBalanceMinor,
    color = color,
    sortOrder = sortOrder,
    archived = archived,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

fun Wallet.toEntity() = WalletEntity(
    id = id,
    name = name,
    type = type.wire,
    currency = currency,
    openingBalanceMinor = openingBalanceMinor,
    color = color,
    sortOrder = sortOrder,
    archived = archived,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

fun TransactionEntity.toDomain() = Transaction(
    id = id,
    type = TxType.fromWire(type),
    amountMinor = amountMinor,
    currency = currency,
    walletId = walletId,
    toWalletId = toWalletId,
    category = category,
    note = note,
    date = runCatching { LocalDate.parse(date) }.getOrElse { LocalDate.ofEpochDay(0) },
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

fun Transaction.toEntity() = TransactionEntity(
    id = id,
    type = type.wire,
    amountMinor = amountMinor,
    currency = currency,
    walletId = walletId,
    toWalletId = toWalletId,
    category = category,
    note = note,
    date = date.toString(),
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

fun CheckEntity.toDomain() = CheckItem(
    id = id,
    title = title,
    counterparty = counterparty,
    direction = CheckDirection.fromWire(direction),
    amountMinor = amountMinor,
    currency = currency,
    sayadNumber = sayadNumber,
    bankName = bankName,
    dueDate = runCatching { LocalDate.parse(dueDate) }.getOrElse { LocalDate.now() },
    issueDate = issueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    status = CheckStatus.fromWire(status),
    reminderDays = reminderDays,
    note = note,
    walletId = walletId,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

fun CheckItem.toEntity() = CheckEntity(
    id = id,
    title = title,
    counterparty = counterparty,
    direction = direction.wire,
    amountMinor = amountMinor,
    currency = currency,
    sayadNumber = sayadNumber,
    bankName = bankName,
    dueDate = dueDate.toString(),
    issueDate = issueDate?.toString(),
    status = status.wire,
    reminderDays = reminderDays,
    note = note,
    walletId = walletId,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

