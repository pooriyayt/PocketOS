package app.pocketos.domain.categories

import app.pocketos.domain.catalog.TextNormalizer
import app.pocketos.domain.model.CategoryKind

/** A built-in category. Display names come from string resources keyed by [id]. */
data class BuiltInCategory(val id: String, val kind: CategoryKind, val iconKey: String, val color: Long)

object Categories {

    val reminder: List<BuiltInCategory> = listOf(
        BuiltInCategory("personal", CategoryKind.REMINDER, "person", 0xFF8B5CF6),
        BuiltInCategory("work", CategoryKind.REMINDER, "work", 0xFF3B82F6),
        BuiltInCategory("health", CategoryKind.REMINDER, "fitness", 0xFF10B981),
        BuiltInCategory("finance", CategoryKind.REMINDER, "finance", 0xFFF59E0B),
        BuiltInCategory("home", CategoryKind.REMINDER, "home", 0xFFEC4899),
        BuiltInCategory("shopping", CategoryKind.REMINDER, "shopping", 0xFFF97316),
        BuiltInCategory("learning", CategoryKind.REMINDER, "school", 0xFF06B6D4),
        BuiltInCategory("other", CategoryKind.REMINDER, "other", 0xFF94A3B8),
    )

    val subscription: List<BuiltInCategory> = listOf(
        BuiltInCategory("entertainment", CategoryKind.SUBSCRIPTION, "movie", 0xFFEF4444),
        BuiltInCategory("music", CategoryKind.SUBSCRIPTION, "music", 0xFF22C55E),
        BuiltInCategory("ai", CategoryKind.SUBSCRIPTION, "ai", 0xFFA855F7),
        BuiltInCategory("productivity", CategoryKind.SUBSCRIPTION, "productivity", 0xFF3B82F6),
        BuiltInCategory("developer", CategoryKind.SUBSCRIPTION, "code", 0xFF64748B),
        BuiltInCategory("hosting", CategoryKind.SUBSCRIPTION, "server", 0xFF0EA5E9),
        BuiltInCategory("domains", CategoryKind.SUBSCRIPTION, "domain", 0xFF14B8A6),
        BuiltInCategory("cloud_storage", CategoryKind.SUBSCRIPTION, "cloud", 0xFF6366F1),
        BuiltInCategory("design", CategoryKind.SUBSCRIPTION, "design", 0xFFEC4899),
        BuiltInCategory("communication", CategoryKind.SUBSCRIPTION, "chat", 0xFF06B6D4),
        BuiltInCategory("security", CategoryKind.SUBSCRIPTION, "security", 0xFF10B981),
        BuiltInCategory("shopping", CategoryKind.SUBSCRIPTION, "shopping", 0xFFF97316),
        BuiltInCategory("gaming", CategoryKind.SUBSCRIPTION, "gaming", 0xFF8B5CF6),
        BuiltInCategory("news", CategoryKind.SUBSCRIPTION, "news", 0xFF78716C),
        BuiltInCategory("education", CategoryKind.SUBSCRIPTION, "school", 0xFF06B6D4),
        BuiltInCategory("health", CategoryKind.SUBSCRIPTION, "fitness", 0xFF10B981),
        BuiltInCategory("utilities", CategoryKind.SUBSCRIPTION, "utilities", 0xFFF59E0B),
        BuiltInCategory("finance", CategoryKind.SUBSCRIPTION, "finance", 0xFFEAB308),
        BuiltInCategory("home", CategoryKind.SUBSCRIPTION, "home", 0xFFEC4899),
        BuiltInCategory("transport", CategoryKind.SUBSCRIPTION, "transport", 0xFF0EA5E9),
        BuiltInCategory("other", CategoryKind.SUBSCRIPTION, "other", 0xFF94A3B8),
    )

    fun builtIn(kind: CategoryKind, id: String): BuiltInCategory? =
        (if (kind == CategoryKind.REMINDER) reminder else subscription).firstOrNull { it.id == id }

    private val reminderKeywords: Map<String, List<String>> = mapOf(
        "work" to listOf("meeting", "email", "report", "client", "deadline", "presentation", "standup", "office", "boss", "project", "invoice client", "جلسه", "گزارش", "ایمیل", "مشتری", "پروژه", "کار"),
        "health" to listOf("doctor", "dentist", "medicine", "pill", "pills", "vitamin", "vitamins", "gym", "workout", "run", "yoga", "appointment", "therapy", "پزشک", "دکتر", "دندانپزشک", "دارو", "قرص", "باشگاه", "ورزش", "نوبت"),
        "finance" to listOf("pay", "bill", "rent", "bank", "tax", "taxes", "invoice", "loan", "transfer", "insurance", "salary", "payment", "پرداخت", "قبض", "اجاره", "بانک", "مالیات", "قسط", "بیمه", "حقوق"),
        "home" to listOf("clean", "laundry", "trash", "garbage", "plants", "water plants", "fix", "repair", "cook", "dishes", "تمیز", "لباس", "زباله", "گلدان", "تعمیر", "آشپزی", "خانه"),
        "shopping" to listOf("buy", "groceries", "grocery", "order", "shop", "pick up", "milk", "bread", "خرید", "سفارش", "شیر", "نان"),
        "learning" to listOf("study", "read", "course", "lesson", "homework", "exam", "practice", "learn", "مطالعه", "درس", "کلاس", "امتحان", "تمرین", "کتاب"),
        "personal" to listOf("call", "mom", "dad", "mother", "father", "birthday", "friend", "family", "text", "anniversary", "visit", "تماس", "زنگ", "مامان", "بابا", "مادر", "پدر", "تولد", "دوست", "خانواده"),
    )

    /**
     * Suggests a reminder category from the title. Returns null when there is
     * no confident match - callers fall back to the user's default.
     */
    fun suggestReminderCategory(text: String): String? {
        val n = " " + TextNormalizer.normalize(text) + " "
        var best: String? = null
        var bestLength = 0
        for ((category, words) in reminderKeywords) {
            for (word in words) {
                val w = TextNormalizer.normalize(word)
                if (w.length > bestLength && n.contains(" $w ")) {
                    best = category
                    bestLength = w.length
                }
            }
        }
        return best
    }

    private val subscriptionKeywords: Map<String, List<String>> = mapOf(
        "utilities" to listOf("internet", "electricity", "water", "gas", "phone", "mobile", "broadband", "wifi", "اینترنت", "برق", "آب", "گاز", "موبایل", "تلفن"),
        "finance" to listOf("insurance", "loan", "mortgage", "bank", "credit card", "بیمه", "وام", "قسط", "بانک"),
        "health" to listOf("gym", "fitness", "yoga", "pilates", "باشگاه", "ورزش"),
        "home" to listOf("rent", "cleaning", "اجاره"),
        "hosting" to listOf("hosting", "server", "vps", "هاست", "سرور"),
        "domains" to listOf("domain", "دامنه"),
        "entertainment" to listOf("streaming", "tv", "movies", "فیلم"),
        "music" to listOf("music", "podcast", "موسیقی"),
        "education" to listOf("course", "class", "tuition", "کلاس", "دوره", "شهریه"),
        "transport" to listOf("car", "parking", "metro", "bus", "ماشین", "پارکینگ"),
    )

    fun suggestSubscriptionCategory(text: String): String? {
        val n = " " + TextNormalizer.normalize(text) + " "
        return subscriptionKeywords.entries.firstOrNull { (_, words) ->
            words.any { n.contains(" " + TextNormalizer.normalize(it) + " ") }
        }?.key
    }
}
