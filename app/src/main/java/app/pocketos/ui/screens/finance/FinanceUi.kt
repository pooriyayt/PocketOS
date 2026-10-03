package app.pocketos.ui.screens.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketos.R
import app.pocketos.core.money.Currencies
import app.pocketos.domain.finance.FinanceCategories
import app.pocketos.domain.finance.TxType
import app.pocketos.domain.finance.Wallet
import app.pocketos.domain.finance.WalletType
import app.pocketos.ui.components.parseHex
import app.pocketos.ui.format.LocalFormatter
import app.pocketos.ui.theme.LocalPocketColors
import app.pocketos.ui.theme.Spacing

// ------------------------------------------------------------ Categories

fun financeCategoryIcon(id: String): ImageVector = when (id) {
    "food" -> Icons.Rounded.Restaurant
    "groceries" -> Icons.Rounded.LocalGroceryStore
    "transport" -> Icons.Rounded.DirectionsCar
    "shopping" -> Icons.Rounded.ShoppingBag
    "bills" -> Icons.Rounded.Receipt
    "housing" -> Icons.Rounded.Home
    "health" -> Icons.Rounded.MedicalServices
    "fun" -> Icons.Rounded.SportsEsports
    "education" -> Icons.Rounded.School
    "travel" -> Icons.Rounded.Flight
    "gifts", "gift_income" -> Icons.Rounded.CardGiftcard
    "subscriptions" -> Icons.Rounded.Subscriptions
    "salary" -> Icons.Rounded.Payments
    "freelance" -> Icons.Rounded.LaptopMac
    "business" -> Icons.Rounded.Business
    "investment" -> Icons.AutoMirrored.Rounded.TrendingUp
    "refund" -> Icons.Rounded.Replay
    FinanceCategories.TRANSFER -> Icons.Rounded.SwapHoriz
    else -> Icons.Rounded.Category
}

fun financeCategoryColor(id: String): Color =
    if (id == FinanceCategories.TRANSFER) Color(0xFF3B82F6)
    else FinanceCategories.find(id)?.let { Color(it.color) } ?: Color(0xFF94A3B8)

@Composable
fun financeCategoryName(id: String): String = stringResource(
    when (id) {
        "food" -> R.string.fin_cat_food
        "groceries" -> R.string.fin_cat_groceries
        "transport" -> R.string.fin_cat_transport
        "shopping" -> R.string.fin_cat_shopping
        "bills" -> R.string.fin_cat_bills
        "housing" -> R.string.fin_cat_housing
        "health" -> R.string.fin_cat_health
        "fun" -> R.string.fin_cat_fun
        "education" -> R.string.fin_cat_education
        "travel" -> R.string.fin_cat_travel
        "gifts" -> R.string.fin_cat_gifts
        "subscriptions" -> R.string.fin_cat_subscriptions
        "salary" -> R.string.fin_cat_salary
        "freelance" -> R.string.fin_cat_freelance
        "business" -> R.string.fin_cat_business
        "investment" -> R.string.fin_cat_investment
        "gift_income" -> R.string.fin_cat_gift_income
        "refund" -> R.string.fin_cat_refund
        "other_income" -> R.string.fin_cat_other_income
        FinanceCategories.TRANSFER -> R.string.fin_cat_transfer
        else -> R.string.fin_cat_other_expense
    }
)

// ---------------------------------------------------------------- Types

/** Colour of a money direction: spending red, earning green, transfers blue. */
@Composable
fun txTone(type: TxType): Color {
    val t = LocalPocketColors.current.tones
    return when (type) {
        TxType.EXPENSE -> t.red
        TxType.INCOME -> t.green
        TxType.TRANSFER -> t.blue
    }
}

fun walletTypeIcon(type: WalletType): ImageVector = when (type) {
    WalletType.CASH -> Icons.Rounded.Wallet
    WalletType.BANK -> Icons.Rounded.AccountBalance
    WalletType.CARD -> Icons.Rounded.CreditCard
    WalletType.SAVINGS -> Icons.Rounded.Savings
}

@Composable
fun walletTypeName(type: WalletType): String = stringResource(
    when (type) {
        WalletType.CASH -> R.string.wallet_type_cash
        WalletType.BANK -> R.string.wallet_type_bank
        WalletType.CARD -> R.string.wallet_type_card
        WalletType.SAVINGS -> R.string.wallet_type_savings
    }
)

val walletPalette = listOf("#7B6CFF", "#3FA9F5", "#10B981", "#F97316", "#EC4899", "#EAB308", "#14B8A6", "#475569")

fun walletColor(wallet: Wallet): Color = parseHex(wallet.color) ?: when (wallet.type) {
    WalletType.CASH -> Color(0xFF10B981)
    WalletType.BANK -> Color(0xFF3FA9F5)
    WalletType.CARD -> Color(0xFF7B6CFF)
    WalletType.SAVINGS -> Color(0xFFF97316)
}

/** Signed, coloured money string: "-120,000 Toman" / "+2,500,000 Toman". */
@Composable
fun signedMoney(type: TxType, amountMinor: Long, currency: String): String {
    val f = LocalFormatter.current
    val sign = when (type) {
        TxType.EXPENSE -> "−"
        TxType.INCOME -> "+"
        TxType.TRANSFER -> ""
    }
    return sign + f.money(amountMinor, currency, compact = true)
}

// ----------------------------------------------------------- Wallet card

/**
 * A wallet drawn like a premium bank card: its own gradient, light blooms,
 * a type glyph, the name and the live balance.
 */
@Composable
fun WalletCard(wallet: Wallet, balanceMinor: Long, modifier: Modifier = Modifier) {
    val f = LocalFormatter.current
    val base = walletColor(wallet)
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .shadow(14.dp, shape, clip = false, ambientColor = base.copy(alpha = 0.5f), spotColor = base.copy(alpha = 0.6f))
            .clip(shape)
            .background(Brush.linearGradient(listOf(base, lerp(base, Color(0xFF0B0C1A), 0.45f))))
            .drawBehind {
                drawCircle(Color.White.copy(alpha = 0.10f), radius = size.height * 0.75f, center = Offset(size.width * 1.0f, 0f))
                drawCircle(Color.White.copy(alpha = 0.07f), radius = size.height * 0.55f, center = Offset(size.width * 0.82f, size.height * 1.05f))
            }
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.04f))), shape)
            .padding(Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Icon(walletTypeIcon(wallet.type), null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(Currencies.flag(wallet.currency), fontSize = 18.sp)
        }
        Spacer(Modifier.height(Spacing.lg))
        Text(wallet.name, style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            f.money(balanceMinor, wallet.currency, compact = true),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Dashed "new wallet" tile that sits at the end of the wallet rail. */
@Composable
fun AddWalletCard(modifier: Modifier = Modifier) {
    val c = LocalPocketColors.current
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .clip(shape)
            .background(c.accent.copy(alpha = if (c.isDark) 0.08f else 0.05f))
            .drawBehind {
                drawRoundRect(
                    color = c.accent.copy(alpha = 0.45f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                    ),
                )
            }
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Spacing.sm))
        Box(Modifier.size(44.dp).clip(CircleShape).background(c.brandGradient), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, null, tint = c.onAccent)
        }
        Spacer(Modifier.height(Spacing.md))
        Text(stringResource(R.string.add_wallet), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
    }
}

