package app.pocketos.ui.screens.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pocketos.R
import app.pocketos.domain.catalog.ServiceInfo
import app.pocketos.domain.categories.Categories
import app.pocketos.domain.model.CategoryKind
import app.pocketos.ui.LocalAppContainer
import app.pocketos.ui.components.CategoryBadge
import app.pocketos.ui.components.ServiceIcon
import app.pocketos.ui.components.categoryName
import app.pocketos.ui.design.GlassCard
import app.pocketos.ui.design.GlassChip
import app.pocketos.ui.design.GlassLevel
import app.pocketos.ui.design.GlassTextField
import app.pocketos.ui.design.HapticType
import app.pocketos.ui.design.LocalHaptics
import app.pocketos.ui.design.LocalMotion
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.Spacing

/**
 * Inline service search used by the subscription flow. Typing "Net..."
 * filters immediately (local catalog, no network). Without a query it shows
 * recent and frequent services and category shortcuts. A free-text entry
 * that matches nothing becomes a custom service.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServicePicker(
    query: String,
    onQueryChange: (String) -> Unit,
    onPickService: (ServiceInfo) -> Unit,
    onPickCustom: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val matcher by container.catalog.matcher.collectAsState()
    val catalog by container.catalog.catalog.collectAsState()
    val recentIds by container.subscriptions.recentServiceIds.collectAsState(initial = emptyList())
    val frequentIds by container.subscriptions.frequentServiceIds.collectAsState(initial = emptyList())
    val c = LocalPocketColors.current
    val haptics = LocalHaptics.current
    val motion = LocalMotion.current
    var browseCategory by remember { mutableStateOf<String?>(null) }

    Column(modifier) {
        GlassTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.service_search_hint),
            leading = { Icon(Icons.Rounded.Search, null, tint = c.textSecondary) },
            imeAction = ImeAction.Done,
            onImeAction = { if (query.isNotBlank()) onPickCustom(query.trim()) },
        )
        Spacer(Modifier.height(Spacing.md))

        val results = remember(query, matcher) { if (query.isBlank()) emptyList() else matcher.search(query, 12).map { it.service } }
        val recognized = remember(query, matcher) { if (query.isBlank()) null else matcher.recognize(query) }
        val shown: List<ServiceInfo> = when {
            query.isNotBlank() -> results.filter { it.id != recognized?.id }
            browseCategory != null -> catalog.services.filter { it.category == browseCategory && !it.generic } + catalog.services.filter { it.category == browseCategory && it.generic }
            else -> (frequentIds + recentIds).distinct().mapNotNull { catalog.find(it) }.take(8)
        }

        if (query.isBlank()) {
            if (shown.isNotEmpty() && browseCategory == null) {
                Text(stringResource(R.string.recent_and_frequent), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                Spacer(Modifier.height(Spacing.sm))
            }
            Text(stringResource(R.string.browse_categories), style = MaterialTheme.typography.labelLarge, color = c.textSecondary,
                modifier = Modifier.padding(top = if (shown.isNotEmpty() && browseCategory == null) Spacing.md else 0.dp))
            Spacer(Modifier.height(Spacing.sm))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Categories.subscription.filter { it.id != "other" }.forEach { cat ->
                    GlassChip(
                        categoryName(CategoryKind.SUBSCRIPTION, cat.id),
                        browseCategory == cat.id,
                        { browseCategory = if (browseCategory == cat.id) null else cat.id },
                        leading = { CategoryBadge(CategoryKind.SUBSCRIPTION, cat.id, cat.iconKey, size = 20.dp) },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
        }

        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (recognized != null) {
                item(key = "recognized_" + recognized.id) {
                    GlassCard(
                        Modifier.fillMaxWidth().animateItem(fadeInSpec = motion.fade(), placementSpec = motion.placement(), fadeOutSpec = motion.fade()),
                        level = GlassLevel.L2,
                        tint = c.accent,
                        contentPadding = PaddingValues(Spacing.md),
                        onClick = {
                            haptics.perform(HapticType.Success)
                            onPickService(recognized)
                        },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ServiceIcon(recognized, recognized.name, recognized.category, size = 42.dp)
                            Spacer(Modifier.width(Spacing.md))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(recognized.name, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.width(Spacing.xs))
                                    Icon(Icons.Rounded.AutoAwesome, null, tint = c.accentHighlight, modifier = Modifier.size(16.dp))
                                }
                                Text(stringResource(R.string.auto_recognized_service), style = MaterialTheme.typography.bodySmall, color = c.accentHighlight)
                            }
                        }
                    }
                }
            }
            items(shown, key = { it.id }) { service ->
                GlassCard(
                    Modifier.fillMaxWidth().animateItem(fadeInSpec = motion.fade(), placementSpec = motion.placement(), fadeOutSpec = motion.fade()),
                    level = GlassLevel.L1,
                    contentPadding = PaddingValues(Spacing.sm),
                    onClick = {
                        haptics.perform(HapticType.Selection)
                        onPickService(service)
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ServiceIcon(service, service.name, service.category, size = 36.dp)
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(service.name, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(categoryName(CategoryKind.SUBSCRIPTION, service.category), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        }
                    }
                }
            }
            if (query.isNotBlank()) {
                item(key = "custom") {
                    GlassCard(Modifier.fillMaxWidth(), level = GlassLevel.L1, contentPadding = PaddingValues(Spacing.md), onClick = { onPickCustom(query.trim()) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Add, null, tint = c.accent)
                            Spacer(Modifier.width(Spacing.md))
                            Text(stringResource(R.string.use_custom_service, query.trim()), style = MaterialTheme.typography.bodyLarge, color = c.accent)
                        }
                    }
                }
            }
        }
    }
}
