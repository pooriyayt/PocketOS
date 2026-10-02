package app.pocketos.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.pocketos.AppContainer
import app.pocketos.R
import app.pocketos.core.security.LockState
import app.pocketos.data.prefs.AppSettings
import app.pocketos.data.prefs.CalendarSystem
import app.pocketos.domain.parser.QuickAddType
import app.pocketos.ui.components.AmbientBackground
import app.pocketos.ui.components.UpdateOverlay
import app.pocketos.ui.design.GlassFloatingActionButton
import app.pocketos.ui.design.GlassNavigationBar
import app.pocketos.ui.design.LocalHazeState
import app.pocketos.ui.design.LocalMotion
import app.pocketos.ui.design.NavItem
import app.pocketos.ui.format.LocalFormatter
import app.pocketos.ui.format.UiFormatter
import app.pocketos.ui.navigation.Routes
import app.pocketos.ui.screens.calendar.CalendarScreen
import app.pocketos.ui.screens.home.HomeScreen
import app.pocketos.ui.screens.insights.InsightsScreen
import app.pocketos.ui.screens.lock.LockScreen
import app.pocketos.ui.screens.onboarding.OnboardingScreen
import app.pocketos.ui.screens.quickadd.QuickAddSheet
import app.pocketos.ui.screens.reminders.ReminderDetailScreen
import app.pocketos.ui.screens.reminders.ReminderEditorScreen
import app.pocketos.ui.screens.reminders.RemindersScreen
import app.pocketos.ui.screens.search.SearchScreen
import app.pocketos.ui.screens.settings.AboutScreen
import app.pocketos.ui.screens.settings.AppearanceScreen
import app.pocketos.ui.screens.settings.DataScreen
import app.pocketos.ui.screens.settings.NotificationSettingsScreen
import app.pocketos.ui.screens.settings.PrivacyNoticeScreen
import app.pocketos.ui.screens.settings.PrivacyScreen
import app.pocketos.ui.screens.settings.SecurityScreen
import app.pocketos.ui.screens.settings.SettingsScreen
import app.pocketos.ui.screens.subscriptions.SubscriptionDetailScreen
import app.pocketos.ui.screens.subscriptions.SubscriptionEditorScreen
import app.pocketos.ui.screens.subscriptions.SubscriptionsScreen
import app.pocketos.ui.theme.PocketTheme
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun PocketApp(container: AppContainer, settings: AppSettings, deepLinks: MutableStateFlow<Uri?>) {
    PocketTheme(settings) {
        val context = LocalContext.current
        val configuration = LocalConfiguration.current
        val locale = ConfigurationCompat.getLocales(configuration)[0] ?: java.util.Locale.getDefault()
        val solarHijri = when (settings.calendarSystem) {
            CalendarSystem.AUTO -> locale.language == "fa"
            CalendarSystem.SOLAR_HIJRI -> true
            CalendarSystem.GREGORIAN -> false
        }
        val formatter = remember(locale, solarHijri) { UiFormatter(context, locale, solarHijri) }
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val ui = remember { AppUiController(snackbar, scope) }
        val lock by container.appLock.state.collectAsState()

        CompositionLocalProvider(LocalAppContainer provides container, LocalFormatter provides formatter, LocalAppUi provides ui) {
            AmbientBackground {
                when {
                    !settings.onboardingCompleted -> OnboardingScreen()
                    lock == LockState.LOCKED -> LockScreen()
                    lock == LockState.UNLOCKED -> MainScaffold(settings, deepLinks) { deepLinks.value = null }
                    else -> Unit // UNKNOWN: the splash screen stays visible.
                }
                SnackbarHost(
                    snackbar,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp),
                ) { data -> PocketSnackbar(data) }
            }
        }
    }
}

private val topLevel = listOf(Routes.Home, Routes.Reminders, Routes.Subscriptions, Routes.Insights, Routes.Settings)

@Composable
private fun MainScaffold(settings: AppSettings, deepLinks: StateFlow<Uri?>, onDeepLinkHandled: () -> Unit) {
    val nav = rememberNavController()
    val haze = rememberHazeState()
    val ui = LocalAppUi.current
    val motion = LocalMotion.current
    val container = LocalAppContainer.current
    val updateState by container.updateManager.state.collectAsState()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val selected = when {
        destination?.hasRoute(Routes.Home::class) == true -> 0
        destination?.hasRoute(Routes.Reminders::class) == true -> 1
        destination?.hasRoute(Routes.Subscriptions::class) == true -> 2
        destination?.hasRoute(Routes.Insights::class) == true -> 3
        destination?.hasRoute(Routes.Settings::class) == true -> 4
        else -> -1
    }
    val pending by deepLinks.collectAsState()
    LaunchedEffect(pending) {
        pending?.let {
            handleDeepLink(it, nav, ui)
            onDeepLinkHandled()
        }
    }

    CompositionLocalProvider(LocalHazeState provides haze) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().hazeSource(haze)) {
                NavHost(
                    navController = nav,
                    startDestination = Routes.Home,
                    enterTransition = { motion.screenEnter() },
                    exitTransition = { motion.screenExit() },
                    popEnterTransition = { motion.tabEnter() },
                    popExitTransition = { motion.screenExit() },
                ) {
                    composable<Routes.Home> { HomeScreen(nav) }
                    composable<Routes.Reminders> { RemindersScreen(nav) }
                    composable<Routes.Subscriptions> { SubscriptionsScreen(nav) }
                    composable<Routes.Insights> { InsightsScreen(nav) }
                    composable<Routes.Settings> { SettingsScreen(nav) }
                    composable<Routes.ReminderDetail> { ReminderDetailScreen(nav, it.toRoute<Routes.ReminderDetail>().id) }
                    composable<Routes.ReminderEditor> { val r = it.toRoute<Routes.ReminderEditor>(); ReminderEditorScreen(nav, r.id, r.kind) }
                    composable<Routes.SubscriptionDetail> { SubscriptionDetailScreen(nav, it.toRoute<Routes.SubscriptionDetail>().id) }
                    composable<Routes.SubscriptionEditor> { val r = it.toRoute<Routes.SubscriptionEditor>(); SubscriptionEditorScreen(nav, r.id, r.serviceId) }
                    composable<Routes.Calendar> { CalendarScreen(nav) }
                    composable<Routes.Search> { SearchScreen(nav) }
                    composable<Routes.Security> { SecurityScreen(nav) }
                    composable<Routes.Appearance> { AppearanceScreen(nav) }
                    composable<Routes.Notifications> { NotificationSettingsScreen(nav) }
                    composable<Routes.Privacy> { PrivacyScreen(nav) }
                    composable<Routes.Data> { DataScreen(nav) }
                    composable<Routes.About> { AboutScreen(nav) }
                    composable<Routes.PrivacyNotice> { PrivacyNoticeScreen(nav) }
                }
            }

            AnimatedVisibility(selected >= 0, enter = motion.bannerEnter(), exit = motion.bannerExit(), modifier = Modifier.align(Alignment.BottomCenter)) {
                GlassNavigationBar(
                    items = listOf(
                        NavItem(stringResource(R.string.nav_home), Icons.Outlined.Home, Icons.Rounded.Home),
                        NavItem(stringResource(R.string.nav_reminders), Icons.Outlined.CheckCircle, Icons.Rounded.CheckCircle),
                        NavItem(stringResource(R.string.nav_subscriptions), Icons.Outlined.AccountBalanceWallet, Icons.Rounded.AccountBalanceWallet),
                        NavItem(stringResource(R.string.nav_insights), Icons.Outlined.Insights, Icons.Rounded.Insights),
                        NavItem(stringResource(R.string.nav_settings), Icons.Outlined.Settings, Icons.Rounded.Settings),
                    ),
                    selectedIndex = selected,
                    onSelect = { index ->
                        nav.navigate(topLevel[index]) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            AnimatedVisibility(
                selected in 0..3,
                enter = motion.popEnter(),
                exit = motion.popExit(),
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 24.dp, bottom = 92.dp),
            ) {
                GlassFloatingActionButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.quick_add),
                    onClick = { ui.openQuickAdd() },
                )
            }
        }
    }

    ui.quickAdd?.let { request ->
        QuickAddSheet(
            request = request,
            onDismiss = { ui.closeQuickAdd() },
            onOpenEditor = { type, serviceId ->
                ui.closeQuickAdd()
                when (type) {
                    QuickAddType.SUBSCRIPTION -> nav.navigate(Routes.SubscriptionEditor(serviceId = serviceId))
                    QuickAddType.TASK -> nav.navigate(Routes.ReminderEditor(kind = "task"))
                    QuickAddType.REMINDER -> nav.navigate(Routes.ReminderEditor())
                }
            },
        )
    }

    UpdateOverlay(container.updateManager, updateState)
}

private fun handleDeepLink(uri: Uri, nav: NavHostController, ui: AppUiController) {
    if (uri.scheme != "pocketos") return
    val segments = uri.pathSegments
    when (uri.host) {
        "reminder" -> segments.firstOrNull()?.let { nav.navigate(Routes.ReminderDetail(it)) }
        "subscription" -> segments.firstOrNull()?.let { nav.navigate(Routes.SubscriptionDetail(it)) }
        "reminders" -> nav.navigate(Routes.Reminders) { launchSingleTop = true }
        "subscriptions" -> nav.navigate(Routes.Subscriptions) { launchSingleTop = true }
        "calendar" -> nav.navigate(Routes.Calendar)
        "search" -> nav.navigate(Routes.Search)
        "home" -> nav.navigate(Routes.Home) { launchSingleTop = true }
        "add" -> when (uri.getQueryParameter("type")) {
            "reminder" -> nav.navigate(Routes.ReminderEditor())
            "subscription" -> nav.navigate(Routes.SubscriptionEditor())
            "task" -> nav.navigate(Routes.ReminderEditor(kind = "task"))
            else -> ui.openQuickAdd()
        }
    }
}
