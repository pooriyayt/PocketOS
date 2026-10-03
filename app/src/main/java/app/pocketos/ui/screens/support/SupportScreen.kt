package app.pocketos.ui.screens.support

import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import androidx.navigation.NavController
import app.pocketos.R
import app.pocketos.ads.AdConfig
import app.pocketos.ads.AdsManager
import app.pocketos.ui.LocalAppContainer
import app.pocketos.ui.components.ToneIcon
import app.pocketos.ui.design.GlassCard
import app.pocketos.ui.design.GlassIconButton
import app.pocketos.ui.design.GlassLevel
import app.pocketos.ui.design.GlassProgressRing
import app.pocketos.ui.design.HapticType
import app.pocketos.ui.design.LocalHaptics
import app.pocketos.ui.design.PocketButton
import app.pocketos.ui.design.appear
import app.pocketos.ui.design.rememberBreath
import app.pocketos.ui.format.LocalFormatter
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.Spacing
import com.adivery.sdk.AdiveryAdListener
import com.adivery.sdk.AdiveryNativeAdView

/**
 * The opt-in place for ads: the user chooses to watch a rewarded video to
 * support the app. Shows a thank-you moment and a running count.
 */
@Composable
fun SupportScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val ads = container.ads
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val state by ads.rewarded.collectAsState()
    val count by ads.supportCount.collectAsState()
    val thanks by ads.thanks.collectAsState()
    var lastThanks by remember { androidx.compose.runtime.mutableIntStateOf(ads.thanks.value) }
    var celebrating by remember { mutableStateOf(false) }
    var showThanksDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { ads.prepareRewarded(context) }
    // After each newly completed ad, celebrate with hearts animation, haptic pattern and dialog
    LaunchedEffect(thanks) {
        if (thanks > lastThanks) {
            lastThanks = thanks
            haptics.perform(HapticType.Success)
            celebrating = true
            showThanksDialog = true
            ads.prepareRewarded(context)
        }
    }

    Box(Modifier.fillMaxSize()) {
        SupportContent(
            state = state,
            supportCount = count,
            celebrating = celebrating,
            onBack = { nav.popBackStack() },
            onWatch = { ads.showRewarded(context) },
            onRetry = { ads.prepareRewarded(context) },
            showNative = true,
        )

        if (celebrating) {
            FloatingHeartsOverlay(
                active = true,
                onFinished = { celebrating = false },
            )
        }

        if (showThanksDialog) {
            app.pocketos.ui.design.GlassDialog(
                onDismiss = {
                    showThanksDialog = false
                    celebrating = false
                },
                title = stringResource(R.string.thanks_dialog_title),
                message = stringResource(R.string.thanks_dialog_body),
                confirmText = stringResource(R.string.thanks_dialog_dismiss),
                onConfirm = {
                    haptics.perform(HapticType.Confirm)
                    showThanksDialog = false
                    celebrating = false
                },
                dismissText = "",
            )
        }
    }
}

@Composable
internal fun SupportContent(
    state: AdsManager.RewardedState,
    supportCount: Int,
    celebrating: Boolean,
    onBack: () -> Unit,
    onWatch: () -> Unit,
    onRetry: () -> Unit,
    showNative: Boolean,
) {
    val c = LocalPocketColors.current
    val f = LocalFormatter.current
    val breath = rememberBreath()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = Spacing.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(top = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), onBack, level = GlassLevel.L1)
            Spacer(Modifier.width(Spacing.sm))
            Text(stringResource(R.string.support_title), style = MaterialTheme.typography.titleLarge, color = c.textPrimary, modifier = Modifier.semantics { heading() })
        }
        Spacer(Modifier.height(Spacing.xxl))
        // Glowing heart.
        Box(Modifier.size(170.dp).appear(0), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Brush.radialGradient(listOf(c.tones.pink.copy(alpha = 0.40f + 0.2f * breath), Color.Transparent)))
            }
            Box(
                Modifier.size(96.dp).scale(0.96f + 0.06f * breath).clip(RoundedCornerShape(32.dp))
                    .background(Brush.linearGradient(listOf(c.tones.pink, c.accent))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.size(48.dp)) }
        }
        Spacer(Modifier.height(Spacing.lg))
        Text(
            stringResource(if (celebrating) R.string.support_thanks else R.string.support_headline),
            style = MaterialTheme.typography.headlineMedium,
            color = c.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.appear(1),
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(stringResource(R.string.support_body), style = MaterialTheme.typography.bodyLarge, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.appear(2))
        Spacer(Modifier.height(Spacing.xl))

        GlassCard(Modifier.fillMaxWidth().appear(3)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToneIcon(Icons.Rounded.Favorite, c.tones.pink, size = 44.dp, filled = supportCount > 0)
                Spacer(Modifier.width(Spacing.md))
                Text(
                    if (supportCount > 0) f.localizeDigits(pluralStringResource(R.plurals.supported_times, supportCount, supportCount))
                    else stringResource(R.string.supporter_count_zero),
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary,
                )
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        when (state) {
            AdsManager.RewardedState.Unavailable -> {
                Text(stringResource(R.string.ad_unavailable), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(Spacing.md))
                PocketButton(stringResource(R.string.try_again), onRetry, Modifier.fillMaxWidth().heightIn(min = 56.dp), icon = Icons.Rounded.Refresh)
            }
            AdsManager.RewardedState.Ready, AdsManager.RewardedState.Showing ->
                PocketButton(stringResource(R.string.watch_ad), onWatch, Modifier.fillMaxWidth().heightIn(min = 60.dp), icon = Icons.Rounded.PlayArrow, haptic = HapticType.Confirm)
            else -> Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                GlassProgressRing(null, size = 22.dp, strokeWidth = 3.dp)
                Spacer(Modifier.width(Spacing.md))
                Text(stringResource(R.string.ad_loading), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, tint = c.textTertiary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.support_privacy), style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
        }

        Spacer(Modifier.height(Spacing.xl))
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
        GlassCard(Modifier.fillMaxWidth().appear(4)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ToneIcon(Icons.Rounded.Favorite, c.tones.pink, size = 44.dp, filled = true)
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.support_donate_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = c.textPrimary,
                        )
                        Text(
                            stringResource(R.string.support_donate_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.md))
                PocketButton(
                    stringResource(R.string.support_donate_btn),
                    onClick = { uriHandler.openUri("https://daramet.com/pooriyayt") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    haptic = HapticType.Confirm,
                )
            }
        }

        if (showNative) {
            Spacer(Modifier.height(Spacing.xl))
            NativeAdCard(Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(Spacing.huge))
    }
}

/**
 * Adivery native ad dressed as a PocketOS card with an "Ad" label. Nothing is
 * shown until an ad has actually loaded, and the slot disappears on error.
 */
@Composable
fun NativeAdCard(modifier: Modifier = Modifier) {
    if (AdConfig.NATIVE_PLACEMENT.isBlank()) return
    val c = LocalPocketColors.current
    var loaded by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    if (failed) return
    val colors by rememberUpdatedState(c)
    GlassCard(modifier.then(if (loaded) Modifier else Modifier.height(1.dp)), contentPadding = androidx.compose.foundation.layout.PaddingValues(if (loaded) Spacing.md else 0.dp)) {
        AnimatedVisibility(loaded) {
            Row(Modifier.padding(bottom = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.ad_label),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = c.onAccent,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(c.textTertiary).padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                AdiveryNativeAdView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    setPlacementId(AdConfig.NATIVE_PLACEMENT)
                    setNativeAdLayout(R.layout.pocket_native_ad)
                    setListener(object : AdiveryAdListener() {
                        override fun onAdLoaded() {
                            loaded = true
                            // Theme the inflated template to match the app.
                            findViewById<TextView>(R.id.adivery_headline)?.setTextColor(colors.textPrimary.toArgb())
                            findViewById<TextView>(R.id.adivery_description)?.setTextColor(colors.textSecondary.toArgb())
                            findViewById<TextView>(R.id.adivery_advertiser)?.setTextColor(colors.textTertiary.toArgb())
                        }

                        override fun onError(reason: String) {
                            failed = true
                        }
                    })
                    // The SDK renders into the template, so wait for the first layout pass.
                    doOnLayout { runCatching { loadAd() }.onFailure { failed = true } }
                }
            },
        )
    }
}

private data class HeartParticle(
    val initialXRatio: Float,
    val speed: Float,
    val swayAmplitude: Float,
    val swayFrequency: Float,
    val size: Float,
    val color: Color,
    val initialDelay: Float,
)

@Composable
fun FloatingHeartsOverlay(
    active: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!active) return
    val anim = remember { Animatable(0f) }
    val colors = listOf(
        Color(0xFFF43F5E), // Rose
        Color(0xFFFB7185), // Soft rose
        Color(0xFFE11D48), // Crimson
        Color(0xFFA855F7), // Purple
        Color(0xFFEC4899), // Pink
        Color(0xFFF59E0B), // Amber
        Color(0xFF06B6D4), // Cyan
    )

    val particles = remember {
        List(30) { i ->
            val rand = kotlin.random.Random(i * 37 + 11)
            HeartParticle(
                initialXRatio = 0.08f + rand.nextFloat() * 0.84f,
                speed = 0.75f + rand.nextFloat() * 0.55f,
                swayAmplitude = 25f + rand.nextFloat() * 35f,
                swayFrequency = 2.5f + rand.nextFloat() * 3f,
                size = 28f + rand.nextFloat() * 30f,
                color = colors[i % colors.size],
                initialDelay = rand.nextFloat() * 0.35f,
            )
        }
    }

    LaunchedEffect(active) {
        anim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2800, easing = LinearOutSlowInEasing),
        )
        onFinished()
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {}
    ) {
        val w = size.width
        val h = size.height
        val t = anim.value

        particles.forEach { p ->
            val localT = ((t - p.initialDelay) / (1f - p.initialDelay)).coerceIn(0f, 1f)
            if (localT > 0f && localT < 1f) {
                val startX = w * p.initialXRatio
                val startY = h * 0.9f
                val y = startY - (localT * p.speed * h * 0.95f)
                val x = startX + kotlin.math.sin(localT * p.swayFrequency * Math.PI.toFloat()) * p.swayAmplitude
                val alpha = if (localT < 0.15f) (localT / 0.15f) else (1f - localT)
                val scale = if (localT < 0.15f) (localT / 0.15f) else 1f

                val heartSize = p.size * scale
                val path = Path().apply {
                    val half = heartSize / 2f
                    moveTo(x, y + half * 0.4f)
                    cubicTo(x - half, y - half * 0.5f, x - heartSize, y + half * 0.2f, x, y + heartSize)
                    cubicTo(x + heartSize, y + half * 0.2f, x + half, y - half * 0.5f, x, y + half * 0.4f)
                    close()
                }
                drawPath(path, color = p.color.copy(alpha = alpha.coerceIn(0f, 1f)))
            }
        }
    }
}

