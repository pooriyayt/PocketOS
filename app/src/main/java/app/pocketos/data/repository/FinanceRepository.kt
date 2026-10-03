package app.pocketos.data.repository

import app.pocketos.core.AppClock
import app.pocketos.data.local.DatabaseManager
import app.pocketos.data.local.TransactionEntity
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
}

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
