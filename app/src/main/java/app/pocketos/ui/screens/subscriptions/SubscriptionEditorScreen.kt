package app.pocketos.ui.screens.subscriptions

import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import app.pocketos.ui.components.categoryName
import app.pocketos.ui.components.parseHex
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import app.pocketos.AppContainer
import app.pocketos.R
import app.pocketos.core.money.Currencies
import app.pocketos.core.money.MoneyFormatter
import app.pocketos.domain.catalog.ServiceInfo
import app.pocketos.domain.categories.Categories
import app.pocketos.domain.model.BillingCycle
import app.pocketos.domain.model.BillingUnit
import app.pocketos.domain.model.CategoryKind
import app.pocketos.domain.model.Money
import app.pocketos.domain.model.Subscription
import app.pocketos.domain.model.SubscriptionStatus
import app.pocketos.domain.recurrence.BillingCalculator
import app.pocketos.domain.smart.SmartDefaults
import app.pocketos.ui.LocalAppUi
import app.pocketos.ui.components.CategoryBadge
import app.pocketos.ui.components.ServiceIcon
import app.pocketos.ui.design.ButtonStyle
import app.pocketos.ui.design.GlassBottomSheet
import app.pocketos.ui.design.GlassCard
import app.pocketos.ui.design.GlassChip
import app.pocketos.ui.design.GlassIconButton
import app.pocketos.ui.design.GlassLevel
import app.pocketos.ui.design.GlassProgressBar
import app.pocketos.ui.design.GlassSegmentedControl
import app.pocketos.ui.design.GlassTextField
import app.pocketos.ui.design.GlassToolbar
import app.pocketos.ui.design.HapticType
import app.pocketos.ui.design.LocalHaptics
import app.pocketos.ui.design.LocalMotion
import app.pocketos.ui.design.PocketButton
import app.pocketos.ui.format.LocalFormatter
import app.pocketos.ui.pocketViewModel
import app.pocketos.ui.screens.common.CategoryPickerSheet
import app.pocketos.ui.screens.common.PocketDatePickerDialog
import app.pocketos.ui.screens.common.categoryLabel
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

data class SubscriptionForm(
    val id: String,
    val isNew: Boolean,
    val step: Int = 0,
    val loading: Boolean = false,
    val name: String = "",
    val serviceId: String? = null,
    val category: String = "other",
    val categoryTouched: Boolean = false,
    val amountText: String = "",
    val currency: String = "USD",
    val billing: BillingCycle = BillingCycle.MONTHLY,
    val billingSuggested: Boolean = false,
    val nextRenewal: LocalDate = LocalDate.now(),
    val renewalTouched: Boolean = false,
    val startDate: LocalDate? = null,
    val trialEnd: LocalDate? = null,
    val cancellationUrl: String = "",
    val notes: String = "",
    val offsets: List<Int> = listOf(1),
    val offsetsTouched: Boolean = false,
    val status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
    val color: String? = null,
    val createdAt: Instant? = null,
    val nameError: Boolean = false,
    val amountError: Boolean = false,
    val urlError: Boolean = false,
)

class SubscriptionEditorViewModel(private val c: AppContainer, id: String?, serviceId: String?) : ViewModel() {
    private val today = c.clock.today()
    private val _form = MutableStateFlow(
        SubscriptionForm(id = id ?: c.subscriptions.newId(), isNew = id == null, loading = id != null, step = if (id == null) 0 else -1,
            nextRenewal = BillingCalculator.occurrence(today, BillingCycle.MONTHLY, 1))
    )
    val form = _form.asStateFlow()

    init {
        viewModelScope.launch {
            if (id != null) {
                val s = c.subscriptions.get(id) ?: return@launch _form.update { it.copy(loading = false) }
                _form.value = SubscriptionForm(
                    id = s.id, isNew = false, step = -1, name = s.name, serviceId = s.serviceId, category = s.category, categoryTouched = true,
                    amountText = s.amount?.let { MoneyFormatter.formatPlain(it.amountMinor, it.currency) }.orEmpty(),
                    currency = s.amount?.currency ?: c.latestSettings.defaultCurrency, billing = s.billing, nextRenewal = s.nextRenewal, renewalTouched = true,
                    startDate = s.startDate, trialEnd = s.trialEnd, cancellationUrl = s.cancellationUrl.orEmpty(), notes = s.notes.orEmpty(),
                    offsets = s.reminderOffsets, offsetsTouched = true, status = s.status, color = s.color, createdAt = s.createdAt,
                )
            } else {
                val currency = SmartDefaults.preferredCurrency(c.subscriptions.recentCurrencies(), c.latestSettings.defaultCurrency)
                val preferred = SmartDefaults.preferredBilling(c.subscriptions.recentBillingCycles())
                _form.update { f ->
                    val billing = preferred ?: f.billing
                    f.copy(currency = currency, billing = billing, billingSuggested = preferred != null, nextRenewal = BillingCalculator.occurrence(today, billing, 1))
                        .withSmartOffsets()
                }
                if (!serviceId.isNullOrEmpty()) {
                    c.catalog.catalog.value.find(serviceId)?.let { pickService(it) }
                }
            }
        }
    }

    private fun SubscriptionForm.withSmartOffsets(): SubscriptionForm =
        if (offsetsTouched) this else copy(offsets = SmartDefaults.subscriptionReminderOffsets(billing, nextRenewal, today))

    fun update(transform: (SubscriptionForm) -> SubscriptionForm) = _form.update { transform(it).withSmartOffsets() }

    fun pickService(service: ServiceInfo) {
        viewModelScope.launch { c.subscriptions.recordServicePicked(service.id) }
        _form.update { f ->
            val hint = service.billing?.toCycle()
            val billing = hint ?: f.billing
            f.copy(
                name = service.name,
                serviceId = service.id,
                category = if (f.categoryTouched) f.category else service.category,
                color = service.color,
                billing = billing,
                billingSuggested = hint != null || f.billingSuggested,
                nextRenewal = if (f.renewalTouched) f.nextRenewal else BillingCalculator.occurrence(today, billing, 1),
                step = if (f.step == 0) 1 else f.step,
                nameError = false,
            ).withSmartOffsets()
        }
    }

    fun pickCustom(name: String) {
        _form.update { f ->
            val match = c.catalog.matcher.value.recognize(name)
            if (match != null) return@update f
            f.copy(
                name = name.take(120), serviceId = null,
                category = if (f.categoryTouched) f.category else Categories.suggestSubscriptionCategory(name) ?: "other",
                step = if (f.step == 0) 1 else f.step, nameError = false,
            )
        }
        c.catalog.matcher.value.recognize(name)?.let { pickService(it) }
    }

    /** Returns null on success or the step that needs attention. */
    suspend fun save(): Boolean {
        val f = _form.value
        val amount = if (f.amountText.isBlank()) null else MoneyFormatter.parse(f.amountText, f.currency)
        val urlOk = f.cancellationUrl.isBlank() || f.cancellationUrl.matches(Regex("^https?://\\S{3,490}$"))
        if (f.name.isBlank() || (f.amountText.isNotBlank() && amount == null) || !urlOk) {
            _form.update { it.copy(nameError = f.name.isBlank(), amountError = f.amountText.isNotBlank() && amount == null, urlError = !urlOk) }
            return false
        }
        val now = c.clock.now()
        c.subscriptions.save(
            Subscription(
                id = f.id, name = f.name, serviceId = f.serviceId, category = f.category,
                amount = amount?.let { Money(it, f.currency) },
                billing = f.billing,
                startDate = f.startDate ?: f.nextRenewal,
                nextRenewal = f.nextRenewal,
                trialEnd = f.trialEnd,
                cancellationUrl = f.cancellationUrl.ifBlank { null },
                notes = f.notes.ifBlank { null },
                reminderOffsets = f.offsets,
                status = f.status,
                color = f.color,
                createdAt = f.createdAt ?: now,
                updatedAt = now,
            )
        )
        if (f.isNew) c.settings.update { s -> if (amount != null) s.copy(defaultCurrency = f.currency) else s }
        return true
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubscriptionEditorScreen(nav: NavController, id: String?, serviceId: String?) {
    val vm = pocketViewModel(key = "sub-editor-$id-$serviceId") { SubscriptionEditorViewModel(it, id, serviceId) }
    val form by vm.form.collectAsState()
    val c = LocalPocketColors.current
    val haptics = LocalHaptics.current
    val ui = LocalAppUi.current
    val motion = LocalMotion.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var iconCustomizerOpen by remember { mutableStateOf(false) }
    val savedText = stringResource(if (form.isNew) R.string.subscription_added else R.string.changes_saved)

    fun save() {
        scope.launch {
            if (vm.save()) {
                haptics.perform(HapticType.Success)
                ui.message(savedText)
                nav.popBackStack()
            } else {
                haptics.perform(HapticType.Error)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(top = 64.dp)
                .imePadding().padding(horizontal = Spacing.gutter).navigationBarsPadding(),
        ) {
            if (form.isNew && form.step >= 0) {
                GlassProgressBar((form.step + 1) / 3f)
                Spacer(Modifier.height(Spacing.xl))
                AnimatedContent(
                    targetState = form.step,
                    transitionSpec = { motion.cardEnter() togetherWith motion.tabExit() },
                    label = "step",
                ) { step ->
                    Column {
                        when (step) {
                            0 -> {
                                StepTitle(stringResource(R.string.step_what_title), stringResource(R.string.step_what_subtitle))
                                ServicePicker(query, { query = it }, vm::pickService, vm::pickCustom)
                            }
                            1 -> {
                                ServiceHeader(form, onChange = { vm.update { it.copy(step = 0) } }, onCustomizeIcon = { iconCustomizerOpen = true })
                                StepTitle(stringResource(R.string.step_amount_title), stringResource(R.string.step_amount_subtitle))
                                AmountSection(form, vm)
                                Spacer(Modifier.height(Spacing.xl))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                    PocketButton(stringResource(R.string.back), { vm.update { it.copy(step = 0) } }, style = ButtonStyle.Glass, modifier = Modifier.weight(1f))
                                    PocketButton(stringResource(R.string.next), {
                                        if (form.amountText.isNotBlank() && MoneyFormatter.parse(form.amountText, form.currency) == null) {
                                            vm.update { it.copy(amountError = true) }
                                            haptics.perform(HapticType.Error)
                                        } else vm.update { it.copy(step = 2, amountError = false) }
                                    }, modifier = Modifier.weight(1f))
                                }
                            }
                            else -> {
                                ServiceHeader(form, onChange = { vm.update { it.copy(step = 0) } }, onCustomizeIcon = { iconCustomizerOpen = true })
                                StepTitle(stringResource(R.string.step_renewal_title), stringResource(R.string.step_renewal_subtitle))
                                RenewalSection(form, vm)
                                Spacer(Modifier.height(Spacing.lg))
                                AdvancedSection(form, vm)
                                Spacer(Modifier.height(Spacing.xl))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                    PocketButton(stringResource(R.string.back), { vm.update { it.copy(step = 1) } }, style = ButtonStyle.Glass, modifier = Modifier.weight(1f))
                                    PocketButton(stringResource(R.string.add_subscription), ::save, modifier = Modifier.weight(1.4f), haptic = HapticType.MediumTap)
                                }
                            }
                        }
                    }
                }
            } else {
                ServiceHeader(
                    form = form,
                    onChange = null,
                    editableName = true,
                    onNameChange = { n -> vm.update { it.copy(name = n.take(120), nameError = false) } },
                    onCustomizeIcon = { iconCustomizerOpen = true },
                )
                Spacer(Modifier.height(Spacing.lg))
                AmountSection(form, vm)
                Spacer(Modifier.height(Spacing.lg))
                RenewalSection(form, vm)
                Spacer(Modifier.height(Spacing.lg))
                AdvancedSection(form, vm, initiallyOpen = true)
                Spacer(Modifier.height(Spacing.xl))
                PocketButton(stringResource(R.string.save), ::save, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(Spacing.huge))
        }
        GlassToolbar(
            title = stringResource(if (form.isNew) R.string.new_subscription else R.string.edit_subscription),
            onBack = { nav.popBackStack() },
            backLabel = stringResource(R.string.back),
            elevated = true,
        )

        if (iconCustomizerOpen) {
            IconCustomizerSheet(
                form = form,
                onDismiss = { iconCustomizerOpen = false },
                onSelectService = { s -> vm.pickService(s); iconCustomizerOpen = false },
                onSelectCategory = { cat -> vm.update { it.copy(category = cat, serviceId = null) }; iconCustomizerOpen = false },
                onSelectColor = { col -> vm.update { it.copy(color = col) } },
            )
        }
    }
}

@Composable
private fun StepTitle(title: String, subtitle: String) {
    val c = LocalPocketColors.current
    Text(title, style = MaterialTheme.typography.headlineSmall, color = c.textPrimary, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(Spacing.xs))
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
    Spacer(Modifier.height(Spacing.lg))
}

@Composable
private fun ServiceHeader(
    form: SubscriptionForm,
    onChange: (() -> Unit)?,
    editableName: Boolean = false,
    onNameChange: (String) -> Unit = {},
    onCustomizeIcon: () -> Unit = {},
) {
    val container = app.pocketos.ui.LocalAppContainer.current
    val catalog by container.catalog.catalog.collectAsState()
    val c = LocalPocketColors.current
    GlassCard(Modifier.fillMaxWidth().padding(bottom = Spacing.lg), level = GlassLevel.L2, contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onCustomizeIcon() },
            ) {
                ServiceIcon(catalog.find(form.serviceId), form.name, form.category, size = 52.dp, customColor = form.color)
            }
            Spacer(Modifier.width(Spacing.md))
            if (editableName) {
                GlassTextField(
                    form.name, onNameChange, modifier = Modifier.weight(1f),
                    error = if (form.nameError) stringResource(R.string.error_name_required) else null,
                    textStyle = MaterialTheme.typography.titleMedium,
                )
            } else {
                Column(Modifier.weight(1f)) {
                    Text(form.name, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    Text(categoryLabel(CategoryKind.SUBSCRIPTION, form.category), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
            }
            onChange?.let { PocketButton(stringResource(R.string.change), it, style = ButtonStyle.Text) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmountSection(form: SubscriptionForm, vm: SubscriptionEditorViewModel) {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val haptics = LocalHaptics.current
    var currencySheet by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(form.billing.interval != 1) }
    Row(verticalAlignment = Alignment.Top) {
        GlassTextField(
            form.amountText,
            { v -> vm.update { it.copy(amountText = app.pocketos.ui.components.AmountInput.sanitize(v, it.currency), amountError = false) } },
            modifier = Modifier.weight(1f),
            placeholder = stringResource(R.string.amount_hint),
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done,
            error = if (form.amountError) stringResource(R.string.error_amount_invalid) else null,
            textStyle = MaterialTheme.typography.headlineSmall,
            visualTransformation = remember(f.locale) { app.pocketos.ui.components.AmountInput.Grouping(f.locale.language == "fa") },
        )
        Spacer(Modifier.width(Spacing.sm))
        app.pocketos.ui.design.GlassCard(
            Modifier.padding(top = 2.dp),
            level = app.pocketos.ui.design.GlassLevel.L1,
            shape = app.pocketos.ui.theme.Shapes.field,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.md, vertical = 14.dp),
            onClick = { currencySheet = true },
            onClickLabel = stringResource(R.string.currency),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                app.pocketos.ui.components.CurrencyFlag(form.currency, size = 24.dp)
                Spacer(Modifier.width(Spacing.sm))
                Text(form.currency, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Icon(Icons.Rounded.ExpandMore, null, tint = c.textTertiary, modifier = Modifier.size(18.dp))
            }
        }
    }
    Text(stringResource(R.string.amount_optional_note), style = MaterialTheme.typography.bodySmall, color = c.textTertiary, modifier = Modifier.padding(top = Spacing.xs, start = Spacing.xs))
    Spacer(Modifier.height(Spacing.lg))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.billing_cycle), style = MaterialTheme.typography.titleSmall, color = c.textSecondary, modifier = Modifier.weight(1f))
        if (form.billingSuggested) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = c.accentHighlight, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.suggested), style = MaterialTheme.typography.labelSmall, color = c.accentHighlight)
        }
    }
    Spacer(Modifier.height(Spacing.sm))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        listOf(BillingCycle.MONTHLY, BillingCycle.YEARLY, BillingCycle.WEEKLY, BillingCycle(BillingUnit.MONTH, 3)).forEach { cycle ->
            GlassChip(f.billing(cycle), !custom && form.billing == cycle, {
                custom = false
                vm.update { it.copy(billing = cycle, billingSuggested = false, nextRenewal = if (it.renewalTouched) it.nextRenewal else BillingCalculator.occurrence(LocalDate.now(), cycle, 1)) }
            })
        }
        GlassChip(stringResource(R.string.repeat_custom), custom, { custom = true })
    }
    AnimatedVisibility(custom, enter = LocalMotion.current.expandEnter(), exit = LocalMotion.current.expandExit()) {
        Column(Modifier.padding(top = Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.every), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                Spacer(Modifier.width(Spacing.md))
                GlassIconButton(Icons.Rounded.Remove, stringResource(R.string.decrease), {
                    if (form.billing.interval > 1) { vm.update { it.copy(billing = it.billing.copy(interval = it.billing.interval - 1), billingSuggested = false) }; haptics.perform(HapticType.DragTick) }
                }, size = 36.dp)
                Text(f.localizeDigits(form.billing.interval.toString()), style = MaterialTheme.typography.titleLarge, color = c.textPrimary, modifier = Modifier.padding(horizontal = Spacing.md))
                GlassIconButton(Icons.Rounded.Add, stringResource(R.string.increase), {
                    if (form.billing.interval < 365) { vm.update { it.copy(billing = it.billing.copy(interval = it.billing.interval + 1), billingSuggested = false) }; haptics.perform(HapticType.DragTick) }
                }, size = 36.dp)
            }
            Spacer(Modifier.height(Spacing.sm))
            GlassSegmentedControl(
                options = BillingUnit.entries,
                selected = form.billing.unit,
                onSelect = { u -> vm.update { it.copy(billing = it.billing.copy(unit = u), billingSuggested = false) } },
                label = { stringResource(when (it) { BillingUnit.DAY -> R.string.unit_days; BillingUnit.WEEK -> R.string.unit_weeks; BillingUnit.MONTH -> R.string.unit_months; BillingUnit.YEAR -> R.string.unit_years }) },
            )
        }
    }
    if (currencySheet) {
        app.pocketos.ui.components.CurrencyPickerSheet(
            selected = form.currency,
            onPick = { code ->
                vm.update { it.copy(currency = code, amountText = app.pocketos.ui.components.AmountInput.adapt(it.amountText, code)) }
                currencySheet = false
            },
            onDismiss = { currencySheet = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RenewalSection(form: SubscriptionForm, vm: SubscriptionEditorViewModel) {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    var pickDate by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    Text(stringResource(R.string.next_renewal), style = MaterialTheme.typography.titleSmall, color = c.textSecondary)
    Spacer(Modifier.height(Spacing.sm))
    val periodDate = BillingCalculator.occurrence(today, form.billing, 1)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        GlassChip(stringResource(R.string.today), form.nextRenewal == today, { vm.update { it.copy(nextRenewal = today, renewalTouched = true) } })
        GlassChip(stringResource(R.string.tomorrow), form.nextRenewal == today.plusDays(1), { vm.update { it.copy(nextRenewal = today.plusDays(1), renewalTouched = true) } })
        GlassChip(f.date(periodDate), form.nextRenewal == periodDate, { vm.update { it.copy(nextRenewal = periodDate, renewalTouched = true) } })
        val custom = form.nextRenewal !in listOf(today, today.plusDays(1), periodDate)
        GlassChip(if (custom) f.date(form.nextRenewal, withWeekday = true) else stringResource(R.string.pick_date), custom, { pickDate = true })
    }
    Spacer(Modifier.height(Spacing.lg))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.remind_me), style = MaterialTheme.typography.titleSmall, color = c.textSecondary, modifier = Modifier.weight(1f))
        if (!form.offsetsTouched) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = c.accentHighlight, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.suggested), style = MaterialTheme.typography.labelSmall, color = c.accentHighlight)
        }
    }
    Spacer(Modifier.height(Spacing.sm))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        listOf(0, 1, 3, 7, 14).forEach { d ->
            GlassChip(f.offsets(listOf(d)), d in form.offsets, {
                vm.update { s ->
                    val next = if (d in s.offsets) s.offsets - d else (s.offsets + d).sortedDescending().take(5)
                    s.copy(offsets = next, offsetsTouched = true)
                }
            })
        }
    }
    if (form.offsets.isEmpty()) {
        Text(stringResource(R.string.reminders_off_note), style = MaterialTheme.typography.bodySmall, color = c.textTertiary, modifier = Modifier.padding(top = Spacing.xs))
    }
    if (pickDate) PocketDatePickerDialog(form.nextRenewal, { pickDate = false }) { d -> vm.update { it.copy(nextRenewal = d, renewalTouched = true) }; pickDate = false }
}

@Composable
private fun AdvancedSection(form: SubscriptionForm, vm: SubscriptionEditorViewModel, initiallyOpen: Boolean = false) {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val motion = LocalMotion.current
    var open by remember { mutableStateOf(initiallyOpen) }
    var pickCategory by remember { mutableStateOf(false) }
    var pickTrial by remember { mutableStateOf(false) }
    var pickStart by remember { mutableStateOf(false) }
    PocketButton(stringResource(R.string.more_options), { open = !open }, style = ButtonStyle.Text, icon = if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, haptic = HapticType.LightTap)
    AnimatedVisibility(open, enter = motion.expandEnter(), exit = motion.expandExit()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(stringResource(R.string.category), style = MaterialTheme.typography.titleSmall, color = c.textSecondary)
            GlassChip(categoryLabel(CategoryKind.SUBSCRIPTION, form.category), true, { pickCategory = true },
                leading = { CategoryBadge(CategoryKind.SUBSCRIPTION, form.category, null, size = 20.dp) })
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PocketButton(form.startDate?.let { stringResource(R.string.started_on, f.date(it)) } ?: stringResource(R.string.start_date), { pickStart = true }, style = ButtonStyle.Glass, modifier = Modifier.weight(1f))
                PocketButton(form.trialEnd?.let { stringResource(R.string.trial_ends_on, f.date(it)) } ?: stringResource(R.string.free_trial), { pickTrial = true }, style = ButtonStyle.Glass, modifier = Modifier.weight(1f))
            }
            if (form.trialEnd != null) {
                PocketButton(stringResource(R.string.remove_trial), { vm.update { it.copy(trialEnd = null) } }, style = ButtonStyle.Text)
            }
            GlassTextField(
                form.cancellationUrl, { v -> vm.update { it.copy(cancellationUrl = v.trim().take(500), urlError = false) } },
                label = stringResource(R.string.cancellation_url), placeholder = "https://",
                keyboardType = KeyboardType.Uri,
                error = if (form.urlError) stringResource(R.string.error_url_invalid) else null,
            )
            GlassTextField(form.notes, { v -> vm.update { it.copy(notes = v.take(5000)) } }, label = stringResource(R.string.notes), singleLine = false, minLines = 2)
            if (!form.isNew) {
                Text(stringResource(R.string.status), style = MaterialTheme.typography.titleSmall, color = c.textSecondary)
                GlassSegmentedControl(
                    options = SubscriptionStatus.entries,
                    selected = form.status,
                    onSelect = { s -> vm.update { it.copy(status = s) } },
                    label = { stringResource(when (it) { SubscriptionStatus.ACTIVE -> R.string.status_active; SubscriptionStatus.PAUSED -> R.string.status_paused; SubscriptionStatus.CANCELLED -> R.string.status_cancelled; SubscriptionStatus.EXPIRED -> R.string.status_expired }) },
                )
            }
        }
    }
    if (pickCategory) CategoryPickerSheet(CategoryKind.SUBSCRIPTION, form.category, { pickCategory = false }) { cat ->
        vm.update { it.copy(category = cat, categoryTouched = true) }
        pickCategory = false
    }
    if (pickTrial) PocketDatePickerDialog(form.trialEnd ?: LocalDate.now().plusDays(7), { pickTrial = false }) { d -> vm.update { it.copy(trialEnd = d) }; pickTrial = false }
    if (pickStart) PocketDatePickerDialog(form.startDate ?: LocalDate.now(), { pickStart = false }) { d -> vm.update { it.copy(startDate = d) }; pickStart = false }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconCustomizerSheet(
    form: SubscriptionForm,
    onDismiss: () -> Unit,
    onSelectService: (ServiceInfo) -> Unit,
    onSelectCategory: (String) -> Unit,
    onSelectColor: (String) -> Unit,
) {
    val container = app.pocketos.ui.LocalAppContainer.current
    val catalog by container.catalog.catalog.collectAsState()
    val c = LocalPocketColors.current
    val haptics = LocalHaptics.current
    var catalogSearch by remember { mutableStateOf("") }

    val colors = listOf(
        "#10B981", "#059669", "#06B6D4", "#3B82F6",
        "#8B5CF6", "#EC4899", "#F59E0B", "#EF4444", "#64748B"
    )

    GlassBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.customize_icon_title)) {
        Text(stringResource(R.string.accent_color), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
        Spacer(Modifier.height(Spacing.sm))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            colors.forEach { hex ->
                val parsed = parseHex(hex) ?: Color.Gray
                val isSelected = form.color.equals(hex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(parsed)
                        .border(
                            if (isSelected) 2.dp else 1.dp,
                            if (isSelected) Color.White else Color.White.copy(alpha = 0.2f),
                            CircleShape,
                        )
                        .clickable {
                            haptics.perform(HapticType.Selection)
                            onSelectColor(hex)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.lg))
        Text(stringResource(R.string.symbol_library), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
        Spacer(Modifier.height(Spacing.sm))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Categories.subscription.forEach { cat ->
                val isSelected = form.serviceId == null && form.category == cat.id
                GlassChip(
                    text = categoryName(CategoryKind.SUBSCRIPTION, cat.id),
                    selected = isSelected,
                    onClick = {
                        haptics.perform(HapticType.Selection)
                        onSelectCategory(cat.id)
                    },
                    leading = { CategoryBadge(CategoryKind.SUBSCRIPTION, cat.id, cat.iconKey, size = 18.dp) },
                )
            }
        }

        Spacer(Modifier.height(Spacing.lg))
        Text(stringResource(R.string.search_brand_icons), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
        Spacer(Modifier.height(Spacing.sm))
        GlassTextField(
            value = catalogSearch,
            onValueChange = { catalogSearch = it },
            placeholder = "Search brand icons (Netflix, Spotify, GitHub...)",
            leading = { Icon(Icons.Rounded.Search, null, tint = c.textSecondary) },
        )
        if (catalogSearch.isNotBlank()) {
            Spacer(Modifier.height(Spacing.sm))
            val matches = remember(catalogSearch) {
                catalog.services.filter {
                    it.name.contains(catalogSearch, ignoreCase = true) ||
                        it.aliases.any { a -> a.contains(catalogSearch, ignoreCase = true) }
                }.take(6)
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                matches.forEach { service ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        level = GlassLevel.L1,
                        contentPadding = PaddingValues(Spacing.sm),
                        onClick = {
                            haptics.perform(HapticType.Selection)
                            onSelectService(service)
                        },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ServiceIcon(service, service.name, service.category, size = 32.dp)
                            Spacer(Modifier.width(Spacing.md))
                            Text(service.name, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                        }
                    }
                }
            }
        }
    }
}

