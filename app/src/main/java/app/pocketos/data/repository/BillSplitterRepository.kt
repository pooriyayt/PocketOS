package app.pocketos.data.repository

import android.content.Context
import app.pocketos.domain.finance.SplitExpense
import app.pocketos.domain.finance.SplitMember
import app.pocketos.domain.finance.SplitSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Local-first, privacy-first repository for persisting Bill Splitter session state on-device.
 */
class BillSplitterRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val storageFile: File
        get() = File(context.filesDir, "bill_splitter_state.json")

    private val mutex = Mutex()
    private val _state = MutableStateFlow(SplitSessionState())
    val state: StateFlow<SplitSessionState> = _state.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            load()
        }
    }

    suspend fun load(): SplitSessionState = withContext(Dispatchers.IO) {
        mutex.withLock {
            val loaded = runCatching {
                if (storageFile.exists()) {
                    val content = storageFile.readText()
                    json.decodeFromString<SplitSessionState>(content)
                } else null
            }.getOrNull()

            val finalState = loaded ?: SplitSessionState()
            _state.value = finalState
            finalState
        }
    }

    suspend fun ensureSelfMember(selfName: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = _state.value
            if (current.members.isEmpty()) {
                val selfMember = SplitMember(
                    id = "self",
                    name = selfName.ifBlank { "من" },
                    isSelf = true,
                )
                val newState = current.copy(members = listOf(selfMember))
                persistLocked(newState)
            }
        }
    }

    suspend fun addMember(name: String): SplitMember = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = _state.value
            val newMember = SplitMember(name = name.trim(), isSelf = false)
            val updated = current.copy(members = current.members + newMember)
            persistLocked(updated)
            newMember
        }
    }

    suspend fun removeMember(memberId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = _state.value
            // Prevent removing self
            if (memberId == "self") return@withLock

            val remainingMembers = current.members.filter { it.id != memberId }

            // Clean up expenses:
            // 1. Remove expenses paid by this member
            // 2. Remove this member from participants of other expenses
            // 3. Drop expenses if no participants remain
            val cleanedExpenses = current.expenses.mapNotNull { exp ->
                if (exp.paidById == memberId) {
                    null
                } else {
                    val remainingParticipants = exp.participantIds.filter { it != memberId }
                    if (remainingParticipants.isEmpty()) {
                        null
                    } else {
                        exp.copy(participantIds = remainingParticipants)
                    }
                }
            }

            val updated = current.copy(
                members = remainingMembers,
                expenses = cleanedExpenses,
            )
            persistLocked(updated)
        }
    }

    suspend fun addExpense(expense: SplitExpense) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = _state.value
            val updated = current.copy(expenses = current.expenses + expense)
            persistLocked(updated)
        }
    }

    suspend fun removeExpense(expenseId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = _state.value
            val updated = current.copy(expenses = current.expenses.filter { it.id != expenseId })
            persistLocked(updated)
        }
    }

    suspend fun clear(selfName: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val selfMember = SplitMember(
                id = "self",
                name = selfName.ifBlank { "من" },
                isSelf = true,
            )
            val freshState = SplitSessionState(
                members = listOf(selfMember),
                expenses = emptyList(),
            )
            persistLocked(freshState)
        }
    }

    private fun persistLocked(newState: SplitSessionState) {
        _state.value = newState
        runCatching {
            val serialized = json.encodeToString(SplitSessionState.serializer(), newState)
            val tempFile = File(context.filesDir, "bill_splitter_state.json.tmp")
            tempFile.writeText(serialized)
            if (tempFile.exists()) {
                tempFile.renameTo(storageFile)
            }
        }
    }
}
