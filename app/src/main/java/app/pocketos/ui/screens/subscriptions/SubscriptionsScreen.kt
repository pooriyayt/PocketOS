package app.pocketos.ui.screens.subscriptions

import app.pocketos.ui.theme.Shapes
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import app.pocketos.core.money.Currencies
import app.pocketos.ui.components.GradientCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.pocketos.R
import app.pocketos.domain.insights.InsightsCalculator
import app.pocketos.domain.model.CategoryKind
import app.pocketos.domain.model.Subscription
import app.pocketos.domain.model.SubscriptionStatus
import app.pocketos.domain.recurrence.BillingCalculator
import app.pocketos.ui.LocalAppContainer
import app.pocketos.ui.components.BottomClearance
import app.pocketos.ui.components.EmptyState
import app.pocketos.ui.components.SkeletonList
import app.pocketos.ui.components.SubscriptionRow
import app.pocketos.ui.components.SwipeAction
import app.pocketos.ui.components.SwipeActionRow
import app.pocketos.ui.components.categoryName
import app.pocketos.ui.design.GlassBottomSheet
import app.pocketos.ui.design.GlassCard
import app.pocketos.ui.design.GlassChip
import app.pocketos.ui.design.GlassIconButton
import app.pocketos.ui.design.GlassLevel
import app.pocketos.ui.design.GlassSegmentedControl
import app.pocketos.ui.design.GlassToolbar
import app.pocketos.ui.design.HapticType
import app.pocketos.ui.design.LocalMotion
import app.pocketos.ui.format.LocalFormatter
import app.pocketos.ui.navigation.Routes
import app.pocketos.ui.screens.common.MenuRow
import app.pocketos.ui.screens.common.SubscriptionMenu
import app.pocketos.ui.screens.common.rememberItemActions
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.Spacing
import java.time.temporal.ChronoUnit

enum class SubscriptionFilter { ACTIVE, UPCOMING, INACTIVE, ALL }
enum class SubscriptionSort { RENEWAL, NAME, AMOUNT }

@Composable
fun SubscriptionsScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val subscriptions by container.subscriptions.subscriptions.collectAsState(initial = null)
    val catalog by container.catalog.catalog.collectAsState()
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val motion = LocalMotion.current
    val actions = rememberItemActions()
    var filter by rememberSaveable { mutableStateOf(SubscriptionFilter.ACTIVE) }
    var sort by rememberSaveable { mutableStateOf(SubscriptionSort.RENEWAL) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<Subscription?>(null) }
    var swipeFor by remember { mutableStateOf<Subscription?>(null) }
    var sorting by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 8 } }
    val today = container.clock.today()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Spacing.gutter)) {
            item { Spacer(Modifier.statusBarsPadding().height(40.dp)) }
            item {
                Text(stringResource(R.string.nav_subscriptions), style = MaterialTheme.typography.headlineMedium, color = c.textPrimary,
                    modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.md).semantics { heading() })
            }
            val all = subscriptions
            if (all == null) {
                item { SkeletonList(5) }
            } else {
                if (all.isNotEmpty()) {
                    item(key = "summary") {
                        val snapshot = remember(all) { InsightsCalculator.compute(all, emptyList(), emptyList(), today, container.clock.zone()) }
                        GradientCard(Modifier.fillMaxWidth().padding(bottom = Spacing.lg)) {
                            Text(
                                stringResource(R.string.monthly_spend).uppercase(f.locale),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.8f),
                            )
                            Spacer(Modifier.height(Spacing.sm))
                            if (snapshot.totals.isEmpty()) {
                                Text("—", style = MaterialTheme.typography.displaySmall, color = Color.White)
                            }
                            snapshot.totals.forEachIndexed { index, t ->
                                if (index > 0) Spacer(Modifier.height(Spacing.sm))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(Currencies.flag(t.currency), fontSize = if (index == 0) 22.sp else 16.sp)
                                    Spacer(Modifier.width(Spacing.sm))
                                    Text(
                                        f.money(t.monthlyMinor, t.currency),
                                        style = if (index == 0) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleLarge,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Text(
                                    stringResource(R.string.per_year_value, f.money(t.annualMinor, t.currency, compact = true)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.75f),
                                )
                            }
                            Spacer(Modifier.height(Spacing.md))
                            Row(
                                Modifier.clip(Shapes.pill).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = Spacing.md, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(6.dp).clip(Shapes.pill).background(Color.White))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    androidx.compose.ui.res.pluralStringResource(R.plurals.active_subscriptions, snapshot.activeSubscriptions, snapshot.activeSubscriptions),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                )
                            }
                            Text(
                                stringResource(R.string.estimate_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.65f),
                                modifier = Modifier.padding(top = Spacing.sm),
                            )
                        }
                    }
                }
                item {
                    GlassSegmentedControl(
                        options = SubscriptionFilter.entries,
                        selected = filter,
                        onSelect = { filter = it },
                        label = {
                            stringResource(
                                when (it) {
                                    SubscriptionFilter.ACTIVE -> R.string.filter_active
                                    SubscriptionFilter.UPCOMING -> R.string.filter_upcoming
                                    SubscriptionFilter.INACTIVE -> R.string.filter_inactive
                                    SubscriptionFilter.ALL -> R.string.filter_all
                                }
                            )
                        },
                    )
                }
                val categories = all.map { it.category }.distinct().sorted()
                if (categories.size > 1) {
                    item {
                        LazyRow(Modifier.padding(top = Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            item { GlassChip(stringResource(R.string.all_categories), category == null, { category = null }) }
                            items(categories) { cat ->
                                GlassChip(categoryName(CategoryKind.SUBSCRIPTION, cat), category == cat, { category = if (category == cat) null else cat })
                            }
                        }
                    }
                }
                val visible = all
                    .filter { category == null || it.category == category }
                    .filter {
                        when (filter) {
                            SubscriptionFilter.ACTIVE -> it.status == SubscriptionStatus.ACTIVE
                            SubscriptionFilter.UPCOMING -> it.status == SubscriptionStatus.ACTIVE && ChronoUnit.DAYS.between(today, it.nextRenewal) in 0..30
                            SubscriptionFilter.INACTIVE -> it.status != SubscriptionStatus.ACTIVE
                            SubscriptionFilter.ALL -> true
                        }
                    }
                    .let { list ->
                        when (sort) {
                            SubscriptionSort.RENEWAL -> list.sortedBy { it.nextRenewal }
                            SubscriptionSort.NAME -> list.sortedBy { it.name.lowercase() }
                            SubscriptionSort.AMOUNT -> list.sortedByDescending { s -> s.amount?.let { BillingCalculator.monthlyEstimate(it.amountMinor, s.billing) } ?: -1.0 }
                        }
                    }
                item { Spacer(Modifier.height(Spacing.md)) }
                if (visible.isEmpty()) {
                    item(key = "empty_$filter") {
                        if (all.isEmpty()) {
                            EmptyState(
                                Icons.Rounded.AccountBalanceWallet,
                                stringResource(R.string.empty_subscriptions_title),
                                stringResource(R.string.empty_subscriptions_message),
                                actionLabel = stringResource(R.string.add_subscription),
                                onAction = { nav.navigate(Routes.SubscriptionEditor()) },
                            )
                        } else {
                            EmptyState(Icons.Rounded.Inventory2, stringResource(R.string.empty_filter_title), stringResource(R.string.empty_filter_message), compact = true)
                        }
                    }
                }
                items(visible, key = { it.id }) { sub ->
                    SwipeActionRow(
                        modifier = Modifier.padding(bottom = Spacing.sm).animateItem(fadeInSpec = motion.fade(), placementSpec = motion.placement(), fadeOutSpec = motion.fade()),
                        endAction = SwipeAction(stringResource(R.string.more), Icons.Rounded.Edit, c.accent) { swipeFor = sub },
                    ) {
                        SubscriptionRow(
                            subscription = sub,
                            service = catalog.find(sub.serviceId),
                            today = today,
                            onClick = { nav.navigate(Routes.SubscriptionDetail(sub.id)) },
                            onLongClick = { menuFor = sub },
                        )
                    }
                }
            }
            item { BottomClearance() }
        }
        GlassToolbar(
            title = if (scrolled) stringResource(R.string.nav_subscriptions) else "",
            elevated = scrolled,
            actions = {
                GlassIconButton(Icons.Rounded.Sort, stringResource(R.string.sort), { sorting = true })
                GlassIconButton(Icons.Rounded.Search, stringResource(R.string.search), { nav.navigate(Routes.Search) })
            },
        )
    }

    SubscriptionMenu(menuFor, nav) { menuFor = null }
    swipeFor?.let { s ->
        GlassBottomSheet(onDismiss = { swipeFor = null }, title = s.name) {
            MenuRow(Icons.Rounded.Edit, stringResource(R.string.edit), { swipeFor = null; nav.navigate(Routes.SubscriptionEditor(s.id)) })
            if (s.status == SubscriptionStatus.ACTIVE) {
                MenuRow(Icons.Rounded.Inventory2, stringResource(R.string.archive), { actions.setStatus(s, SubscriptionStatus.PAUSED); swipeFor = null }, haptic = HapticType.Confirm)
            } else {
                MenuRow(Icons.Rounded.Inventory2, stringResource(R.string.resume), { actions.setStatus(s, SubscriptionStatus.ACTIVE); swipeFor = null }, haptic = HapticType.Confirm)
            }
            MenuRow(Icons.Rounded.Edit, stringResource(R.string.more_actions), { menuFor = s; swipeFor = null })
        }
    }
    if (sorting) {
        GlassBottomSheet(onDismiss = { sorting = false }, title = stringResource(R.string.sort_by)) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SubscriptionSort.entries.forEach { option ->
                    GlassChip(
                        stringResource(when (option) { SubscriptionSort.RENEWAL -> R.string.sort_renewal; SubscriptionSort.NAME -> R.string.sort_name; SubscriptionSort.AMOUNT -> R.string.sort_amount }),
                        sort == option,
                        { sort = option; sorting = false },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
