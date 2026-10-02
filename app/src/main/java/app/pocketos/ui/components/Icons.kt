package app.pocketos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush as GBrush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketos.domain.categories.Categories
import app.pocketos.domain.catalog.ServiceInfo
import app.pocketos.domain.model.CategoryKind
import app.pocketos.ui.theme.LocalPocketColors
import java.util.concurrent.ConcurrentHashMap

/** Icon keys used by categories and the catalog -> Material symbols. */
fun categoryIcon(key: String?): ImageVector = when (key) {
    "person" -> Icons.Rounded.Person
    "work" -> Icons.Rounded.Work
    "fitness" -> Icons.Rounded.FitnessCenter
    "finance" -> Icons.Rounded.AccountBalance
    "home" -> Icons.Rounded.Home
    "shopping" -> Icons.Rounded.ShoppingBag
    "school" -> Icons.Rounded.School
    "movie" -> Icons.Rounded.Movie
    "music" -> Icons.Rounded.MusicNote
    "ai" -> Icons.Rounded.AutoAwesome
    "productivity" -> Icons.Rounded.TaskAlt
    "code" -> Icons.Rounded.Code
    "server" -> Icons.Rounded.Dns
    "domain" -> Icons.Rounded.Language
    "cloud" -> Icons.Rounded.Cloud
    "design" -> Icons.Rounded.Brush
    "chat" -> Icons.Rounded.Chat
    "security" -> Icons.Rounded.Security
    "gaming" -> Icons.Rounded.SportsEsports
    "news" -> Icons.Rounded.Newspaper
    "utilities" -> Icons.Rounded.Bolt
    "transport" -> Icons.Rounded.DirectionsCar
    else -> Icons.Rounded.Category
}

fun categoryColor(kind: CategoryKind, id: String, customColor: String? = null): Color =
    customColor?.let { parseHex(it) } ?: Categories.builtIn(kind, id)?.let { Color(it.color) } ?: Color(0xFF94A3B8)

fun parseHex(hex: String?): Color? = hex?.takeIf { it.matches(Regex("^#[0-9A-Fa-f]{6}$")) }?.let { Color(android.graphics.Color.parseColor(it)) }

/** Brand glyphs parsed once from validated catalog path data. */
private object GlyphCache {
    private val cache = ConcurrentHashMap<String, ImageVector>()

    fun get(service: ServiceInfo): ImageVector? {
        val icon = service.icon ?: return null
        return cache.getOrPut(service.id) {
            runCatching {
                val nodes = PathParser().parsePathString(icon.path).toNodes()
                ImageVector.Builder(
                    name = service.id,
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = icon.viewbox.toFloat(),
                    viewportHeight = icon.viewbox.toFloat(),
                ).addPath(pathData = nodes, fill = SolidColor(Color.White)).build()
            }.getOrNull() ?: return null
        }
    }
}

/**
 * Service icon: the brand glyph on its brand colour when the catalog has
 * one, otherwise a gradient monogram, otherwise the category symbol. Never a
 * broken image, never a network request.
 */
@Composable
fun ServiceIcon(
    service: ServiceInfo?,
    name: String,
    category: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    customColor: String? = null,
) {
    val c = LocalPocketColors.current
    val brand = parseHex(customColor) ?: parseHex(service?.color) ?: categoryColor(CategoryKind.SUBSCRIPTION, category)
    val glyph = remember(service?.id) { service?.let(GlyphCache::get) }
    // Very dark brand colours (GitHub, X...) get a lifted tile so they stay visible in dark mode.
    val tile = if (c.isDark && brand.luminance() < 0.06f) Color(0xFF2A3042) else brand
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(size * 0.3f)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(GBrush.linearGradient(listOf(tile, tile.copy(alpha = 0.78f))))
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape),
        contentAlignment = Alignment.Center,
    ) {
        val fg = if (tile.luminance() > 0.6f) Color(0xFF0F172A) else Color.White
        when {
            glyph != null -> Icon(glyph, contentDescription = null, tint = fg, modifier = Modifier.size(size * 0.5f))
            name.isNotBlank() && !(service?.generic ?: false) -> Text(
                monogram(name),
                color = fg,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.36f).sp,
            )
            else -> Icon(
                categoryIcon(Categories.builtIn(CategoryKind.SUBSCRIPTION, service?.category ?: category)?.iconKey),
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}

private fun monogram(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val letters = if (words.size >= 2) "${words[0].first()}${words[1].first()}" else name.trim().take(2)
    return letters.uppercase()
}

@Composable
fun CategoryBadge(kind: CategoryKind, id: String, iconKey: String?, modifier: Modifier = Modifier, size: Dp = 36.dp, customColor: String? = null) {
    val color = categoryColor(kind, id, customColor)
    Box(
        modifier.size(size).clip(androidx.compose.foundation.shape.RoundedCornerShape(size * 0.32f)).background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(categoryIcon(iconKey ?: Categories.builtIn(kind, id)?.iconKey), null, tint = color, modifier = Modifier.size(size * 0.55f))
    }
}
