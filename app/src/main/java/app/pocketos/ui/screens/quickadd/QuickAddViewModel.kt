package app.pocketos.ui.screens.quickadd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pocketos.AppContainer
import app.pocketos.domain.catalog.ServiceInfo
import app.pocketos.domain.model.BillingCycle
import app.pocketos.domain.model.BillingUnit
import app.pocketos.domain.model.Frequency
import app.pocketos.domain.model.Money
import app.pocketos.domain.model.Priority
import app.pocketos.domain.model.RecurrenceRule
import app.pocketos.domain.model.Reminder
import app.pocketos.domain.model.ReminderKind
import app.pocketos.domain.model.Subscription
import app.pocketos.domain.parser.QuickAddParser
import app.pocketos.domain.parser.QuickAddResult
import app.pocketos.domain.parser.QuickAddType
import app.pocketos.domain.recurrence.BillingCalculator
import app.pocketos.domain.smart.SmartDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

enum class DraftField { TYPE, TITLE, AMOUNT, DATE, TIME, BILLING, RECURRENCE, CATEGORY, OFFSETS }

/** What will be created. Starts from the parser; any field the user edits is kept across re-parses. */
data class QuickDraft(
    val type: QuickAddType = QuickAddType.TASK,
    val title: String = "",
    val service: ServiceInfo? = null,
    val amount: Money? = null,
    val currencyAssumed: Boolean = false,
    val date: LocalDate? = null,
    val dateAssumed: Boolean = false,
    val time: LocalTime? = null,
    val timeAssumed: Boolean = false,
    val billing: BillingCycle? = null,
    val billingAssumed: Boolean = false,
    val recurrence: RecurrenceRule? = null,
    val category: String = "personal",
    val priority: Priority = Priority.NORMAL,
    val offsets: List<Int> = emptyList(),
    val touched: Set<DraftField> = emptySet(),
) {
    val canAdd: Boolean get() = title.isNotBlank()
}

data class QuickAddState(val text: String = "", val draft: QuickDraft? = null)

@OptIn(FlowPreview::class)
class QuickAddViewModel(private val c: AppContainer, prefill: String, preferredType: QuickAddType?) : ViewModel() {
    private val input = MutableStateFlow(prefill)
    private val _state = MutableStateFlow(QuickAddState(prefill))
    val state = _state.asStateFlow()
    private val forcedType = preferredType

    init {
        viewModelScope.launch {
            input.debounce(120).collect { text -> reparse(text) }
        }
    }

    fun setText(text: String) {
        _state.update { it.copy(text = text.take(300)) }
        input.value = text.take(300)
    }

    private suspend fun reparse(text: String) {
        if (text.isBlank()) {
            _state.update { it.copy(draft = null) }
            return
        }
        val result = withContext(Dispatchers.Default) {
            val s = c.latestSettings
            val parser = QuickAddParser(
                matcher = c.catalog.matcher.value,
                defaultCurrency = SmartDefaults.preferredCurrency(c.subscriptions.recentCurrencies(), s.defaultCurrency),
                defaultReminderTime = s.reminderDefaultTime,
                monthFirstDates = Locale.getDefault().country == "US",
            )
            parser.parse(text, c.clock.localNow())
        }
        _state.update { st -> st.copy(draft = merge(result, st.draft)) }
    }

    private fun merge(r: QuickAddResult, old: QuickDraft?): QuickDraft {
        val touched = old?.touched.orEmpty()
        fun <T> pick(field: DraftField, fresh: T, previous: T?): T = if (field in touched && old != null) previous as T else fresh
        var draft = QuickDraft(
            type = pick(DraftField.TYPE, forcedType ?: r.type, old?.type),
            title = pick(DraftField.TITLE, r.title, old?.title),
            service = r.service,
            amount = pick(DraftField.AMOUNT, r.amount, old?.amount),
            currencyAssumed = if (DraftField.AMOUNT in touched) false else r.currencyAssumed,
            date = pick(DraftField.DATE, r.date, old?.date),
            dateAssumed = if (DraftField.DATE in touched) false else r.dateAssumed,
            time = pick(DraftField.TIME, r.time, old?.time),
            timeAssumed = if (DraftField.TIME in touched) false else r.timeAssumed,
            billing = pick(DraftField.BILLING, r.billing, old?.billing),
            billingAssumed = if (DraftField.BILLING in touched) false else r.billingAssumed,
            recurrence = pick(DraftField.RECURRENCE, r.recurrence, old?.recurrence),
            category = pick(DraftField.CATEGORY, r.category, old?.category),
            priority = r.priority,
            offsets = pick(DraftField.OFFSETS, r.reminderOffsets, old?.offsets),
            touched = touched,
        )
        if (forcedType != null && forcedType != r.type) draft = convert(draft, forcedType)
        return draft
    }

    fun edit(field: DraftField, transform: (QuickDraft) -> QuickDraft) {
        _state.update { st -> st.draft?.let { st.copy(draft = transform(it).copy(touched = it.touched + field)) } ?: st }
    }

    fun changeType(type: QuickAddType) {
        _state.update { st -> st.draft?.let { st.copy(draft = convert(it, type).copy(touched = it.touched + DraftField.TYPE)) } ?: st }
    }

    /** Converts a draft between reminder/task/subscription, carrying over compatible values. */
    private fun convert(d: QuickDraft, type: QuickAddType): QuickDraft {
        val today = c.clock.today()
        return when (type) {
            QuickAddType.SUBSCRIPTION -> {
                val billing = d.billing ?: d.recurrence?.let { rule ->
                    when (rule.frequency) {
                        Frequency.DAILY -> BillingCycle(BillingUnit.DAY, rule.interval)
                        Frequency.WEEKLY -> BillingCycle(BillingUnit.WEEK, rule.interval)
                        Frequency.MONTHLY -> BillingCycle(BillingUnit.MONTH, rule.interval)
                        Frequency.YEARLY -> BillingCycle(BillingUnit.YEAR, rule.interval)
                    }
                } ?: d.service?.billing?.toCycle() ?: BillingCycle.MONTHLY
                val date = d.date ?: BillingCalculator.occurrence(today, billing, 1)
                d.copy(
                    type = type, billing = billing, date = date, time = null,
                    category = d.service?.category ?: if (d.category in reminderOnly) "other" else d.category,
                    offsets = d.offsets.ifEmpty { SmartDefaults.subscriptionReminderOffsets(billing, date, today) },
                )
            }
            QuickAddType.REMINDER -> {
                val rule = d.recurrence ?: d.billing?.let { b ->
                    RecurrenceRule(
                        when (b.unit) {
                            BillingUnit.DAY -> Frequency.DAILY
                            BillingUnit.WEEK -> Frequency.WEEKLY
                            BillingUnit.MONTH -> Frequency.MONTHLY
                            BillingUnit.YEAR -> Frequency.YEARLY
                        },
                        b.interval,
                    )
                }
                d.copy(type = type, recurrence = rule, date = d.date ?: today, time = d.time ?: c.latestSettings.reminderDefaultTime,
                    category = if (d.category in subscriptionOnly) "finance" else d.category)
            }
            QuickAddType.TASK -> d.copy(type = type, category = if (d.category in subscriptionOnly) "personal" else d.category)
        }
    }

    /** Saves the confirmed draft. Returns the created item's type or null if nothing was saved. */
    suspend fun add(): QuickAddType? {
        val d = _state.value.draft ?: return null
        if (!d.canAdd) return null
        val now = c.clock.now()
        when (d.type) {
            QuickAddType.SUBSCRIPTION -> {
                val billing = d.billing ?: BillingCycle.MONTHLY
                val renewal = d.date ?: BillingCalculator.occurrence(c.clock.today(), billing, 1)
                c.subscriptions.save(
                    Subscription(
                        id = c.subscriptions.newId(), name = d.title.trim(), serviceId = d.service?.id, category = d.category,
                        amount = d.amount, billing = billing, startDate = renewal, nextRenewal = renewal,
                        reminderOffsets = d.offsets, color = d.service?.color, createdAt = now, updatedAt = now,
                    )
                )
            }
            QuickAddType.REMINDER, QuickAddType.TASK -> {
                c.reminders.save(
                    Reminder(
                        id = c.reminders.newId(),
                        kind = if (d.type == QuickAddType.TASK) ReminderKind.TASK else ReminderKind.REMINDER,
                        title = d.title.trim(),
                        dueDate = d.date,
                        dueTime = if (d.date != null) d.time else null,
                        recurrence = d.recurrence?.anchoredTo(d.date),
                        priority = d.priority,
                        category = d.category,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }
        }
        return d.type
    }

    companion object {
        private val reminderOnly = setOf("personal", "work", "learning")
        private val subscriptionOnly = setOf("entertainment", "music", "ai", "productivity", "developer", "hosting", "domains", "cloud_storage", "design", "communication", "security", "gaming", "news", "education", "utilities", "transport")
    }
}
