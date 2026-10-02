package app.pocketos.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as GlanceColorProvider
import app.pocketos.MainActivity
import app.pocketos.R

/*
 * Home-screen widgets (Jetpack Glance). Android widgets cannot blur what is
 * behind them, so the "glass" look is approximated with a translucent
 * gradient background and a hairline border; there are no animations.
 * Widgets refresh after data changes while the app process runs, after
 * alarms, and periodically (Android limits background update frequency).
 */

private object WidgetColors {
    val text: GlanceColorProvider = ColorProvider(day = Color(0xFF0F172A), night = Color(0xFFF8FAFC))
    val secondary: GlanceColorProvider = ColorProvider(day = Color(0xFF475569), night = Color(0xFF9CA3AF))
    val accent: GlanceColorProvider = ColorProvider(day = Color(0xFF059669), night = Color(0xFF34D399))
    val warning: GlanceColorProvider = ColorProvider(day = Color(0xFFB45309), night = Color(0xFFFBBF24))
    val chip: GlanceColorProvider = ColorProvider(day = Color(0x1A10B981), night = Color(0x3310B981))
}

private fun openIntent(context: Context, uri: String) =
    Intent(Intent.ACTION_VIEW, Uri.parse(uri), context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

@Composable
private fun WidgetSurface(content: @Composable () -> Unit) {
    Box(
        modifier = GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_background)).padding(14.dp),
        contentAlignment = Alignment.TopStart,
    ) { content() }
}

@Composable
private fun Title(text: String) {
    Text(text, style = TextStyle(color = WidgetColors.secondary, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
}

@Composable
private fun AddButton(context: Context, uri: String, label: String) {
    Box(
        modifier = GlanceModifier.size(36.dp).background(ImageProvider(R.drawable.widget_chip)).clickable(actionStartActivity(openIntent(context, uri))),
        contentAlignment = Alignment.Center,
    ) {
        Image(ImageProvider(R.drawable.ic_widget_add), contentDescription = label, modifier = GlanceModifier.size(18.dp))
    }
}

// ------------------------------------------------------------- Today

class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(120.dp, 110.dp), DpSize(250.dp, 110.dp), DpSize(250.dp, 180.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetDataLoader.load(context)
        provideContent { GlanceTheme { TodayContent(data) } }
    }
}

@Composable
private fun TodayContent(data: WidgetSnapshot) {
    val context = LocalContext.current
    val wide = LocalSize.current.width >= 250.dp
    WidgetSurface {
        Column(modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(openIntent(context, "pocketos://home")))) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Title(data.dateText)
                    val headline = when {
                        data.failed -> context.getString(R.string.widget_open_app)
                        data.remainingToday == 0 && data.overdue == 0 -> context.getString(R.string.widget_all_clear)
                        else -> context.resources.getQuantityString(R.plurals.widget_due_today, data.remainingToday, data.remainingToday)
                    }
                    Text(headline, style = TextStyle(color = WidgetColors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold), maxLines = 2)
                }
                AddButton(context, "pocketos://add", context.getString(R.string.quick_add))
            }
            if (data.overdue > 0) {
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    context.resources.getQuantityString(R.plurals.widget_overdue, data.overdue, data.overdue),
                    style = TextStyle(color = WidgetColors.warning, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            data.next?.let { next ->
                Text(context.getString(R.string.widget_next), style = TextStyle(color = WidgetColors.secondary, fontSize = 11.sp))
                Text(next.title, style = TextStyle(color = WidgetColors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                Text(next.whenText, style = TextStyle(color = WidgetColors.accent, fontSize = 12.sp), maxLines = 1)
            } ?: if (wide) {
                Text(context.getString(R.string.widget_no_upcoming), style = TextStyle(color = WidgetColors.secondary, fontSize = 13.sp))
            } else Unit
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

// ---------------------------------------------------------- Upcoming

class UpcomingWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(180.dp, 110.dp), DpSize(250.dp, 180.dp), DpSize(250.dp, 280.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetDataLoader.load(context)
        provideContent { GlanceTheme { UpcomingContent(data) } }
    }
}

@Composable
private fun UpcomingContent(data: WidgetSnapshot) {
    val context = LocalContext.current
    val rows = when {
        LocalSize.current.height >= 280.dp -> 6
        LocalSize.current.height >= 180.dp -> 4
        else -> 2
    }
    WidgetSurface {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    context.getString(R.string.widget_upcoming),
                    style = TextStyle(color = WidgetColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(openIntent(context, "pocketos://reminders"))),
                )
                AddButton(context, "pocketos://add?type=reminder", context.getString(R.string.new_reminder))
            }
            Spacer(GlanceModifier.height(6.dp))
            if (data.upcoming.isEmpty()) {
                Text(
                    context.getString(if (data.failed) R.string.widget_open_app else R.string.widget_no_upcoming),
                    style = TextStyle(color = WidgetColors.secondary, fontSize = 13.sp),
                )
            }
            data.upcoming.take(rows).forEach { item ->
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clickable(actionStartActivity(openIntent(context, "pocketos://reminder/${item.id}"))),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = GlanceModifier.width(3.dp).height(28.dp).background(WidgetColors.accent)) {}
                    Spacer(GlanceModifier.width(8.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(item.title, style = TextStyle(color = WidgetColors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                        Text(item.whenText, style = TextStyle(color = WidgetColors.secondary, fontSize = 11.sp), maxLines = 1)
                    }
                }
            }
        }
    }
}

class UpcomingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UpcomingWidget()
}

// ----------------------------------------------------------- Renewal

class RenewalWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(120.dp, 110.dp), DpSize(250.dp, 110.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetDataLoader.load(context)
        provideContent { GlanceTheme { RenewalContent(data) } }
    }
}

@Composable
private fun RenewalContent(data: WidgetSnapshot) {
    val context = LocalContext.current
    val renewal = data.renewal
    WidgetSurface {
        Column(
            modifier = GlanceModifier.fillMaxSize().clickable(
                actionStartActivity(openIntent(context, renewal?.let { "pocketos://subscription/${it.id}" } ?: "pocketos://subscriptions"))
            ),
        ) {
            Title(context.getString(R.string.widget_next_renewal))
            Spacer(GlanceModifier.height(4.dp))
            if (renewal == null) {
                Text(
                    context.getString(if (data.failed) R.string.widget_open_app else R.string.widget_no_renewals),
                    style = TextStyle(color = WidgetColors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                )
            } else {
                Text(renewal.name, style = TextStyle(color = WidgetColors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                val countdown = when (renewal.daysUntil) {
                    0L -> context.getString(R.string.today)
                    1L -> context.getString(R.string.tomorrow)
                    else -> context.resources.getQuantityString(R.plurals.in_days, renewal.daysUntil.toInt(), renewal.daysUntil.toInt())
                }
                Text(countdown, style = TextStyle(color = WidgetColors.accent, fontSize = 22.sp, fontWeight = FontWeight.Bold))
                Text(
                    listOfNotNull(renewal.dateText, renewal.amountText).joinToString(" · "),
                    style = TextStyle(color = WidgetColors.secondary, fontSize = 12.sp),
                    maxLines = 1,
                )
            }
        }
    }
}

class RenewalWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RenewalWidget()
}

// ----------------------------------------------------- Quick actions

class QuickActionsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(180.dp, 70.dp), DpSize(250.dp, 110.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { GlanceTheme { QuickActionsContent() } }
    }
}

@Composable
private fun QuickActionsContent() {
    val context = LocalContext.current
    val actions = listOf(
        Triple(R.drawable.ic_widget_add, R.string.quick_add, "pocketos://add"),
        Triple(R.drawable.ic_widget_reminder, R.string.new_reminder, "pocketos://add?type=reminder"),
        Triple(R.drawable.ic_widget_subscription, R.string.new_subscription, "pocketos://add?type=subscription"),
        Triple(R.drawable.ic_widget_calendar, R.string.calendar, "pocketos://calendar"),
    )
    WidgetSurface {
        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            actions.forEachIndexed { index, (icon, label, uri) ->
                if (index > 0) Spacer(GlanceModifier.width(8.dp))
                Column(
                    modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(openIntent(context, uri))),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(modifier = GlanceModifier.size(44.dp).background(ImageProvider(R.drawable.widget_chip)), contentAlignment = Alignment.Center) {
                        Image(ImageProvider(icon), contentDescription = context.getString(label), modifier = GlanceModifier.size(22.dp))
                    }
                    if (LocalSize.current.height >= 110.dp) {
                        Spacer(GlanceModifier.height(4.dp))
                        Text(context.getString(label), style = TextStyle(color = WidgetColors.secondary, fontSize = 11.sp), maxLines = 1)
                    }
                }
            }
        }
    }
}

class QuickActionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickActionsWidget()
}
