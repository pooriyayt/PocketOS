package app.pocketos.ui.screens.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.pocketos.R
import app.pocketos.domain.catalog.ServiceCatalog
import app.pocketos.domain.catalog.ServiceInfo
import app.pocketos.domain.catalog.ServiceMatcher
import app.pocketos.domain.catalog.TextNormalizer
import app.pocketos.domain.categories.Categories
import app.pocketos.domain.model.CategoryKind
import app.pocketos.domain.model.Reminder
import app.pocketos.domain.model.ReminderKind
import app.pocketos.domain.model.Subscription
import app.pocketos.ui.LocalAppContainer
import app.pocketos.ui.components.CategoryBadge
import app.pocketos.ui.components.EmptyState
import app.pocketos.ui.components.ReminderRow
import app.pocketos.ui.components.ServiceIcon
import app.pocketos.ui.components.SubscriptionRow
import app.pocketos.ui.components.categoryName
import app.pocketos.ui.design.GlassCard
import app.pocketos.ui.design.GlassIconButton
import app.pocketos.ui.design.GlassLevel
import app.pocketos.ui.design.GlassTextField
import app.pocketos.ui.design.LocalMotion
import app.pocketos.ui.navigation.Routes
import app.pocketos.ui.screens.common.rememberItemActions
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.Spacing

data class SearchResults(
    val reminders: List<Reminder> = emptyList(),
    val tasks: List<Reminder> = emptyList(),
    val subscriptions: List<Subscription> = emptyList(),
    val services: List<ServiceInfo> = emptyList(),
    val reminderCategories: List<String> = emptyList(),
    val subscriptionCategories: List<String> = emptyList(),
) {
    val isEmpty get() = reminders.isEmpty() && tasks.isEmpty() && subscriptions.isEmpty() && services.isEmpty() && reminderCategories.isEmpty() && subscriptionCategories.isEmpty()
}

/**
 * Local fuzzy search: token prefixes, substrings and one-typo tolerance for
 * longer words. Subscriptions also match through their service's aliases and
 * keywords ("music" finds a Spotify subscription).
 */
object GlobalSearch {
    fun matches(query: String, text: String): Boolean {
        val q = TextNormalizer.normalize(query)
        if (q.isEmpty()) return false
        val t = TextNormalizer.normalize(text)
        if (t.contains(q)) return true
        val words = t.split(' ')
        return q.split(' ').all { token ->
            words.any { w -> w.startsWith(token) || (token.length >= 4 && TextNormalizer.editDistance(token, w.take(token.length + 1), 1) <= 1) }
        }
    }

    fun search(
        query: String,
        reminders: List<Reminder>,
        subscriptions: List<Subscription>,
        catalog: ServiceCatalog,
        matcher: ServiceMatcher,
        categoryLabel: (CategoryKind, String) -> String,
    ): SearchResults {
        if (query.isBlank()) return SearchResults()
        val matchingReminders = reminders.filter { matches(query, it.title) || (it.notes?.let { n -> matches(query, n) } ?: false) || matches(query, categoryLabel(CategoryKind.REMINDER, it.category)) }
        val matchingSubs = subscriptions.filter { s ->
            val service = catalog.find(s.serviceId)
            matches(query, s.name) ||
                matches(query, categoryLabel(CategoryKind.SUBSCRIPTION, s.category)) ||
                (service != null && (service.aliases + service.keywords).any { matches(query, it) })
        }
        val subscribedServices = subscriptions.mapNotNull { it.serviceId }.toSet()
        return SearchResults(
            reminders = matchingReminders.filter { it.kind == ReminderKind.REMINDER }.take(20),
            tasks = matchingReminders.filter { it.kind == ReminderKind.TASK }.take(20),
            subscriptions = matchingSubs.take(20),
            services = matcher.search(query, 8).map { it.service }.filterNot { it.id in subscribedServices },
            reminderCategories = Categories.reminder.map { it.id }.filter { matches(query, categoryLabel(CategoryKind.REMINDER, it)) },
            subscriptionCategories = Categories.subscription.map { it.id }.filter { matches(query, categoryLabel(CategoryKind.SUBSCRIPTION, it)) },
        )
    }
}

@Composable
fun SearchScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val reminders by container.reminders.reminders.collectAsState(initial = emptyList())
    val subscriptions by container.subscriptions.subscriptions.collectAsState(initial = emptyList())
    val catalog by container.catalog.catalog.collectAsState()
    val matcher by container.catalog.matcher.collectAsState()
    val c = LocalPocketColors.current
    val motion = LocalMotion.current
    val actions = rememberItemActions()
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val today = container.clock.today()
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // Category labels resolved once per composition for the pure search function.
    val reminderNames = Categories.reminder.associate { it.id to categoryName(CategoryKind.REMINDER, it.id) }
    val subscriptionNames = Categories.subscription.associate { it.id to categoryName(CategoryKind.SUBSCRIPTION, it.id) }
    val results = remember(query, reminders, subscriptions, catalog, matcher) {
        GlobalSearch.search(query, reminders, subscriptions, catalog, matcher) { kind, id ->
            (if (kind == CategoryKind.REMINDER) reminderNames[id] else subscriptionNames[id]) ?: id
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), { nav.popBackStack() }, level = GlassLevel.L1)
            Spacer(Modifier.width(Spacing.sm))
            GlassTextField(
                query, { query = it.take(100) },
                modifier = Modifier.weight(1f).focusRequester(focus),
                placeholder = stringResource(R.string.search_hint),
                leading = { Icon(Icons.Rounded.Search, null, tint = c.textSecondary) },
                imeAction = ImeAction.Search,
            )
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.sm)) {
            if (query.isBlank()) {
                item { EmptyState(Icons.Rounded.Search, stringResource(R.string.search_empty_title), stringResource(R.string.search_empty_message), compact = true) }
            } else if (results.isEmpty) {
                item { EmptyState(Icons.Rounded.SearchOff, stringResource(R.string.search_no_results_title), stringResource(R.string.search_no_results_message, query), compact = true) }
            }
            if (results.reminders.isNotEmpty()) {
                item { Group(stringResource(R.string.group_reminders)) }
                items(results.reminders, key = { "r" + it.id }) { r ->
                    ReminderRow(r, today, { actions.toggleComplete(r) }, { nav.navigate(Routes.ReminderDetail(r.id)) }, { nav.navigate(Routes.ReminderDetail(r.id)) },
                        Modifier.padding(bottom = Spacing.sm).animateItem(fadeInSpec = motion.fade(), placementSpec = motion.placement(), fadeOutSpec = motion.fade()))
                }
            }
            if (results.tasks.isNotEmpty()) {
                item { Group(stringResource(R.string.group_tasks)) }
                items(results.tasks, key = { "t" + it.id }) { r ->
                    ReminderRow(r, today, { actions.toggleComplete(r) }, { nav.navigate(Routes.ReminderDetail(r.id)) }, { nav.navigate(Routes.ReminderDetail(r.id)) },
                        Modifier.padding(bottom = Spacing.sm).animateItem())
                }
            }
            if (results.subscriptions.isNotEmpty()) {
                item { Group(stringResource(R.string.group_subscriptions)) }
                items(results.subscriptions, key = { "s" + it.id }) { s ->
                    SubscriptionRow(s, catalog.find(s.serviceId), today, { nav.navigate(Routes.SubscriptionDetail(s.id)) }, { nav.navigate(Routes.SubscriptionDetail(s.id)) },
                        Modifier.padding(bottom = Spacing.sm).animateItem())
                }
            }
            if (results.services.isNotEmpty()) {
                item { Group(stringResource(R.string.group_services)) }
                items(results.services, key = { "svc" + it.id }) { service ->
                    GlassCard(Modifier.fillMaxWidth().padding(bottom = Spacing.sm).animateItem(), level = GlassLevel.L1, contentPadding = PaddingValues(Spacing.md),
                        onClick = { nav.navigate(Routes.SubscriptionEditor(serviceId = service.id)) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ServiceIcon(service, service.name, service.category, size = 36.dp)
                            Spacer(Modifier.width(Spacing.md))
                            Column(Modifier.weight(1f)) {
                                Text(service.name, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(categoryName(CategoryKind.SUBSCRIPTION, service.category), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                            }
                            Icon(Icons.Rounded.Add, stringResource(R.string.add_subscription), tint = c.accent)
                        }
                    }
                }
            }
            if (results.reminderCategories.isNotEmpty() || results.subscriptionCategories.isNotEmpty()) {
                item { Group(stringResource(R.string.group_categories)) }
                items(results.reminderCategories.map { CategoryKind.REMINDER to it } + results.subscriptionCategories.map { CategoryKind.SUBSCRIPTION to it }, key = { "c" + it.first + it.second }) { (kind, id) ->
                    GlassCard(Modifier.fillMaxWidth().padding(bottom = Spacing.sm), level = GlassLevel.L1, contentPadding = PaddingValues(Spacing.md),
                        onClick = { nav.navigate(if (kind == CategoryKind.REMINDER) Routes.Reminders else Routes.Subscriptions) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CategoryBadge(kind, id, null, size = 32.dp)
                            Spacer(Modifier.width(Spacing.md))
                            Text(categoryName(kind, id), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
                            Text(stringResource(if (kind == CategoryKind.REMINDER) R.string.nav_reminders else R.string.nav_subscriptions), style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(Spacing.huge)) }
        }
    }
}

@Composable
private fun Group(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = LocalPocketColors.current.textSecondary,
        modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.sm).semantics { heading() })
}
