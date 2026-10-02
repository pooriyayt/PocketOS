package app.pocketos.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pocketos.AppContainer
import app.pocketos.data.prefs.AppSettings
import app.pocketos.data.prefs.DashboardSection
import app.pocketos.domain.catalog.ServiceCatalog
import app.pocketos.domain.insights.DashboardBuilder
import app.pocketos.domain.insights.DashboardModel
import app.pocketos.domain.insights.Suggestion
import app.pocketos.domain.insights.SuggestionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

data class HomeUiState(
    val loading: Boolean = true,
    val model: DashboardModel? = null,
    val suggestions: List<Suggestion> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val catalog: ServiceCatalog = ServiceCatalog.EMPTY,
    val today: LocalDate = LocalDate.now(),
    val now: LocalDateTime = LocalDateTime.now(),
    val refreshing: Boolean = false,
)

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val dismissed = MutableStateFlow<Set<String>>(emptySet())
    private val refreshing = MutableStateFlow(false)

    /** Re-evaluates "today" and the greeting every minute. */
    private val ticker = flow {
        while (true) {
            emit(c.clock.localNow())
            delay(60_000)
        }
    }

    private val data = combine(
        c.reminders.reminders,
        c.subscriptions.subscriptions,
        c.reminders.eventsSince(c.clock.now().minus(Duration.ofDays(2))),
        ticker,
    ) { reminders, subs, events, now -> Quad(reminders, subs, events, now) }

    private val meta = combine(c.settings.settings, c.catalog.catalog) { s, cat -> Meta(s, cat) }

    val state: StateFlow<HomeUiState> = combine(data, meta, dismissed, refreshing) { d, m, dismissedIds, isRefreshing ->
        val today = d.now.toLocalDate()
        val model = DashboardBuilder.build(d.reminders, d.subs, d.events, today, d.now.toLocalTime(), c.clock.zone())
        val suggestions = SuggestionEngine.suggestions(d.subs, d.reminders, today, c.clock.now(), c.clock.zone())
            .filterNot { it.id in dismissedIds }
        HomeUiState(false, model, suggestions, m.settings, m.catalog, today, d.now, isRefreshing)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun dismissSuggestion(id: String) = dismissed.update { it + id }

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            c.subscriptions.rollForwardRenewals()
            c.notificationScheduler.reconcileAll()
            c.widgetUpdater.updateNow()
            delay(250)
            refreshing.value = false
        }
    }

    fun saveDashboard(order: List<DashboardSection>, hidden: Set<DashboardSection>) {
        viewModelScope.launch { c.settings.update { it.copy(dashboardOrder = order, dashboardHidden = hidden) } }
    }

    fun dismissFirstRunHint() {
        viewModelScope.launch { c.settings.update { it.copy(firstRunHintDismissed = true) } }
    }

    fun dismissGuidedTour() {
        viewModelScope.launch { c.settings.update { it.copy(guidedTourDismissed = true) } }
    }

    private data class Quad(
        val reminders: List<app.pocketos.domain.model.Reminder>,
        val subs: List<app.pocketos.domain.model.Subscription>,
        val events: List<app.pocketos.domain.model.ReminderEvent>,
        val now: LocalDateTime,
    )

    private data class Meta(val settings: AppSettings, val catalog: ServiceCatalog)
}
