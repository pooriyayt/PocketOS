package app.pocketos.ui.screens.reminders

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import app.pocketos.AppContainer
import app.pocketos.R
import app.pocketos.domain.categories.Categories
import app.pocketos.domain.model.CategoryKind
import app.pocketos.domain.model.Reminder
import app.pocketos.domain.model.ReminderKind
import app.pocketos.ui.LocalAppContainer
import app.pocketos.ui.components.BottomClearance
import app.pocketos.ui.components.CategoryBadge
import app.pocketos.ui.components.EmptyState
import app.pocketos.ui.components.InlineBanner
import app.pocketos.ui.components.ReminderRow
import app.pocketos.ui.components.SkeletonList
import app.pocketos.ui.components.SwipeAction
import app.pocketos.ui.components.SwipeActionRow
import app.pocketos.ui.components.categoryName
import app.pocketos.ui.design.GlassBottomSheet
import app.pocketos.ui.design.GlassChip
import app.pocketos.ui.design.GlassIconButton
import app.pocketos.ui.design.GlassSegmentedControl
import app.pocketos.ui.design.GlassToolbar
import app.pocketos.ui.design.HapticType
import app.pocketos.ui.design.LocalMotion
import app.pocketos.ui.navigation.Routes
import app.pocketos.ui.pocketViewModel
import app.pocketos.ui.screens.common.MenuRow
import app.pocketos.ui.screens.common.ReminderMenu
import app.pocketos.ui.screens.common.rememberItemActions
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.Spacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class ReminderFilter { TODAY, UPCOMING, ALL, DONE }

class RemindersViewModel(private val c: AppContainer) : ViewModel() {
    val reminders: StateFlow<List<Reminder>?> = c.reminders.reminders.map<List<Reminder>, List<Reminder>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun today(): LocalDate = c.clock.today()

    fun dismissExactPrompt() {
        viewModelScope.launch { c.settings.update { it.copy(exactAlarmPromptDismissed = true) } }
    }
}

fun filterReminders(all: List<Reminder>, filter: ReminderFilter, category: String?, today: LocalDate): List<Reminder> {
    val byCategory = if (category == null) all else all.filter { it.category == category }
    return when (filter) {
        ReminderFilter.TODAY -> byCategory.filter { !it.isCompleted && it.dueDate != null && !it.dueDate.isAfter(today) }
        ReminderFilter.UPCOMING -> byCategory.filter { !it.isCompleted && (it.dueDate == null || it.dueDate.isAfter(today)) }
        ReminderFilter.ALL -> byCategory.filter { !it.isCompleted }
        ReminderFilter.DONE -> byCategory.filter { it.isCompleted }.sortedByDescending { it.completedAt }
    }
}

@Composable
fun RemindersScreen(nav: NavController) {
    val vm = pocketViewModel { RemindersViewModel(it) }
    val all by vm.reminders.collectAsState()
    val container = LocalAppContainer.current
    val settings by container.settings.settings.collectAsState(initial = container.latestSettings)
    val actions = rememberItemActions()
    val c = LocalPocketColors.current
    val motion = LocalMotion.current
    var filter by rememberSaveable { mutableStateOf(ReminderFilter.TODAY) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<Reminder?>(null) }
    var moreFor by remember { mutableStateOf<Reminder?>(null) }
    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 8 } }
    val today = vm.today()
    val context = LocalContext.current

    // Permission state is re-read whenever the screen resumes (the user may return from Settings).
    var notificationsAllowed by remember { mutableStateOf(container.notifier.canPost()) }
    var exactAllowed by remember { mutableStateOf(container.alarms.canScheduleExact()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsAllowed = container.notifier.canPost()
        exactAllowed = container.alarms.canScheduleExact()
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsAllowed = container.notifier.canPost() }
    val hasTimed = all.orEmpty().any { !it.isCompleted && it.dueDate != null }

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Spacing.gutter)) {
            item { Spacer(Modifier.statusBarsPadding().height(40.dp())) }
            item {
                Text(
                    stringResource(R.string.nav_reminders),
                    style = MaterialTheme.typography.headlineMedium,
                    color = c.textPrimary,
                    modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.md).semantics { heading() },
                )
            }
            item {
                InlineBanner(
                    visible = hasTimed && !notificationsAllowed,
                    icon = Icons.Rounded.NotificationsOff,
                    title = stringResource(R.string.notifications_off_title),
                    message = stringResource(R.string.notifications_off_message),
                    actionLabel = stringResource(R.string.allow),
                    onAction = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            container.appLock.allowExternalActivity()
                            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                        }
                    },
                    modifier = Modifier.padding(bottom = Spacing.md),
                )
            }
            item {
                InlineBanner(
                    visible = hasTimed && notificationsAllowed && !exactAllowed && !settings.exactAlarmPromptDismissed,
                    icon = Icons.Rounded.AlarmOn,
                    title = stringResource(R.string.exact_alarm_title),
                    message = stringResource(R.string.exact_alarm_message),
                    actionLabel = stringResource(R.string.allow),
                    onAction = {
                        if (Build.VERSION.SDK_INT >= 31) {
                            container.appLock.allowExternalActivity()
                            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)))
                        }
                    },
                    modifier = Modifier.padding(bottom = Spacing.md),
                )
            }
            item {
                GlassSegmentedControl(
                    options = ReminderFilter.entries,
                    selected = filter,
                    onSelect = { filter = it },
                    label = {
                        stringResource(
                            when (it) {
                                ReminderFilter.TODAY -> R.string.filter_today
                                ReminderFilter.UPCOMING -> R.string.filter_upcoming
                                ReminderFilter.ALL -> R.string.filter_all
                                ReminderFilter.DONE -> R.string.filter_done
                            }
                        )
                    },
                )
            }
            item {
                LazyRow(Modifier.padding(vertical = Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    item { GlassChip(stringResource(R.string.all_categories), selected = category == null, onClick = { category = null }) }
                    items(Categories.reminder, key = { it.id }) { cat ->
                        GlassChip(
                            categoryName(CategoryKind.REMINDER, cat.id),
                            selected = category == cat.id,
                            onClick = { category = if (category == cat.id) null else cat.id },
                            leading = { CategoryBadge(CategoryKind.REMINDER, cat.id, cat.iconKey, size = 20.dp()) },
                        )
                    }
                }
            }

            val list = all
            if (list == null) {
                item { SkeletonList(5) }
            } else {
                val visible = filterReminders(list, filter, category, today)
                if (visible.isEmpty()) {
                    item(key = "empty_$filter") {
                        val (icon, title, message) = when {
                            list.isEmpty() -> Triple(Icons.Rounded.TaskAlt, R.string.empty_reminders_title, R.string.empty_reminders_message)
                            filter == ReminderFilter.TODAY -> Triple(Icons.Rounded.WbSunny, R.string.empty_attention_title, R.string.empty_attention_message)
                            filter == ReminderFilter.UPCOMING -> Triple(Icons.Rounded.EventAvailable, R.string.empty_upcoming_title, R.string.empty_upcoming_message)
                            filter == ReminderFilter.DONE -> Triple(Icons.Rounded.CheckCircle, R.string.empty_done_title, R.string.empty_done_message)
                            else -> Triple(Icons.Rounded.TaskAlt, R.string.empty_reminders_title, R.string.empty_reminders_message)
                        }
                        EmptyState(
                            icon, stringResource(title), stringResource(message),
                            actionLabel = if (filter != ReminderFilter.DONE) stringResource(R.string.new_reminder) else null,
                            onAction = { nav.navigate(Routes.ReminderEditor()) },
                        )
                    }
                }
                items(visible, key = { it.id }) { reminder ->
                    SwipeActionRow(
                        modifier = Modifier.padding(bottom = Spacing.sm).animateItem(fadeInSpec = motion.fade(), placementSpec = motion.placement(), fadeOutSpec = motion.fade()),
                        startAction = SwipeAction(
                            stringResource(if (reminder.isCompleted) R.string.mark_not_done else R.string.complete),
                            Icons.Rounded.Check, c.success,
                        ) { actions.toggleComplete(reminder) },
                        endAction = SwipeAction(stringResource(R.string.more), Icons.Rounded.MoreHoriz, c.accent) { moreFor = reminder },
                    ) {
                        ReminderRow(
                            reminder = reminder,
                            today = today,
                            onToggle = { actions.toggleComplete(reminder) },
                            onClick = { nav.navigate(Routes.ReminderDetail(reminder.id)) },
                            onLongClick = { menuFor = reminder },
                        )
                    }
                }
            }
            item { BottomClearance() }
        }
        GlassToolbar(
            title = if (scrolled) stringResource(R.string.nav_reminders) else "",
            elevated = scrolled,
            actions = {
                GlassIconButton(Icons.Rounded.Search, stringResource(R.string.search), { nav.navigate(Routes.Search) })
            },
        )
    }

    ReminderMenu(menuFor, nav) { menuFor = null }
    moreFor?.let { r ->
        // Swipe-left sheet: snooze is one tap; delete still asks for confirmation via the menu.
        GlassBottomSheet(onDismiss = { moreFor = null }, title = r.title) {
            if (!r.isCompleted && r.dueDate != null) {
                listOf(10, 60, 24 * 60).forEach { minutes ->
                    MenuRow(Icons.Rounded.AlarmOn, snoozeLabel(minutes), { actions.snooze(r, minutes); moreFor = null }, haptic = HapticType.Confirm)
                }
            }
            MenuRow(Icons.Rounded.MoreHoriz, stringResource(R.string.more_actions), { menuFor = r; moreFor = null })
        }
    }
}

@Composable
fun snoozeLabel(minutes: Int): String = when {
    minutes < 60 -> androidx.compose.ui.res.pluralStringResource(R.plurals.snooze_minutes, minutes, minutes)
    minutes < 24 * 60 -> androidx.compose.ui.res.pluralStringResource(R.plurals.snooze_hours, minutes / 60, minutes / 60)
    else -> stringResource(R.string.snooze_tomorrow)
}

private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())
