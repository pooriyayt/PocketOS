package app.pocketos.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Debounced refresh of all PocketOS widgets after data changes. */
class WidgetUpdater(private val context: Context, private val scope: CoroutineScope) {
    private var pending: Job? = null

    fun requestUpdate() {
        pending?.cancel()
        pending = scope.launch {
            delay(600)
            updateNow()
        }
    }

    suspend fun updateNow() {
        runCatching {
            TodayWidget().updateAll(context)
            UpcomingWidget().updateAll(context)
            RenewalWidget().updateAll(context)
            QuickActionsWidget().updateAll(context)
        }
    }
}
