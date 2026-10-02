package app.pocketos.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pocketos.AppContainer
import app.pocketos.domain.parser.QuickAddType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
val LocalAppUi = staticCompositionLocalOf<AppUiController> { error("AppUiController not provided") }

data class QuickAddRequest(val prefill: String = "", val preferredType: QuickAddType? = null)

/** App-wide UI services: snackbars with undo, and the Quick Add sheet. */
@Stable
class AppUiController(val snackbar: SnackbarHostState, private val scope: CoroutineScope) {
    var quickAdd by mutableStateOf<QuickAddRequest?>(null)
        private set

    fun openQuickAdd(prefill: String = "", type: QuickAddType? = null) {
        quickAdd = QuickAddRequest(prefill, type)
    }

    fun closeQuickAdd() {
        quickAdd = null
    }

    fun message(text: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text, duration = SnackbarDuration.Short)
        }
    }

    /** Shows [text] with an undo action; [onUndo] runs only if tapped. */
    fun undo(text: String, actionLabel: String, onUndo: suspend () -> Unit) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(text, actionLabel = actionLabel, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) onUndo()
        }
    }
}

/** Creates a ViewModel from the app container (manual DI, no reflection). */
@Composable
inline fun <reified VM : ViewModel> pocketViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = LocalAppContainer.current
    return viewModel(key = key) { create(container) }
}
