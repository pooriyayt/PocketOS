package app.pocketos.domain.finance

/**
 * Understands everyday money phrases in Persian and English for Quick Add:
 * "۱۵۰ تومن ساندویچ", "اسنپ ۸۰ هزار", "حقوق ۲۵ میلیون", "lunch 12$", "salary 3000".
 * Works on the parser's normalised text (ASCII digits, lower case).
 */
object FinanceKeywords {

    data class Intent(val type: TxType, val category: String)

    private val EXPENSE_CATEGORIES: List<Pair<String, List<String>>> = listOf(
        "food" to listOf("ساندویچ", "ناهار", "نهار", "شام", "صبحانه", "غذا", "رستوران", "پیتزا", "برگر", "کافه", "قهوه", "چای", "فست", "کباب", "جوجه", "بستنی", "شیرینی", "اسنپ فود", "اسنپ‌فود", "lunch", "dinner", "breakfast", "food", "pizza", "burger", "coffee", "cafe", "restaurant", "snack", "sandwich"),
        "groceries" to listOf("خواربار", "سوپر", "هایپر", "میوه", "نان", "نون", "شیر", "ماست", "تخم مرغ", "مرغ", "گوشت", "برنج", "groceries", "grocery", "supermarket", "fruit", "bread", "milk", "eggs"),
        "transport" to listOf("تاکسی", "اسنپ", "تپسی", "مترو", "اتوبوس", "بنزین", "سوخت", "پارکینگ", "کارواش", "عوارض", "taxi", "uber", "snapp", "metro", "bus", "fuel", "gas", "petrol", "parking", "train"),
        "housing" to listOf("اجاره", "رهن", "کرایه خانه", "کرایه خونه", "شارژ ساختمان", "rent", "mortgage"),
        "bills" to listOf("قبض", "برق", "قبض آب", "گاز", "اینترنت", "شارژ", "موبایل", "تلفن", "bill", "electricity", "water", "internet", "phone", "recharge", "top up"),
        "health" to listOf("دکتر", "پزشک", "دارو", "داروخانه", "بیمارستان", "دندان", "دندون", "آزمایش", "ویزیت", "doctor", "medicine", "pharmacy", "hospital", "dentist", "clinic"),
        "fun" to listOf("سینما", "فیلم", "کنسرت", "تفریح", "بازی", "پارک", "cinema", "movie", "concert", "game", "fun", "party"),
        "education" to listOf("کتاب", "کلاس", "دوره", "شهریه", "آموزش", "دانشگاه", "book", "class", "course", "tuition", "school"),
        "travel" to listOf("سفر", "هتل", "بلیط", "بلیت", "پرواز", "travel", "hotel", "ticket", "flight", "trip"),
        "gifts" to listOf("هدیه", "کادو", "gift", "present"),
        "shopping" to listOf("لباس", "کفش", "دیجی کالا", "دیجی‌کالا", "خرید", "clothes", "shoes", "shopping", "amazon"),
        "installments" to listOf("قسط", "اقساط", "installment", "loan payment"),
    )

    private val INCOME_CATEGORIES: List<Pair<String, List<String>>> = listOf(
        "salary" to listOf("حقوق", "دستمزد", "salary", "paycheck", "wage"),
        "freelance" to listOf("فریلنس", "پروژه", "freelance", "project", "client"),
        "business" to listOf("فروش", "مغازه", "sale", "sales", "business"),
        "investment" to listOf("سود", "سهام", "بورس", "dividend", "interest", "stocks", "profit"),
        "gift_income" to listOf("عیدی", "هدیه گرفتم", "کادو گرفتم"),
        "refund" to listOf("برگشت پول", "استرداد", "refund", "cashback"),
    )

    /** Words that only say "money came in", used to pick income when no category word is present. */
    private val INCOME_WORDS = listOf("درآمد", "دریافتی", "واریزی", "واریز شد", "گرفتم", "دریافت کردم", "income", "received", "earned", "got paid")

    /** Verbs that say "I spent" and should not end up in the note. */
    val SPEND_VERBS = Regex("(?<![\\p{L}\\p{N}])(?:خریدم|دادم|پرداخت کردم|پرداختم|خرج کردم|هزینه|خرج|spent|paid|bought|cost)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
    val EARN_VERBS = Regex("(?<![\\p{L}\\p{N}])(?:گرفتم|دریافت کردم|واریز شد|درآمد|earned|received|got paid|income)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)

    /** Phrases that mean "remind me", which keep a sentence a reminder even with an amount in it. */
    val REMINDER_WORDS = Regex("(?<![\\p{L}\\p{N}])(?:remind|reminder|alarm|call|meeting|appointment|یادم|یادآوری|یاد آوری|بنداز|زنگ|جلسه|نوبت|قرار)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)

    private fun containsWord(text: String, word: String): Boolean =
        if (word.any { it in 'a'..'z' }) Regex("(?<![a-z])${Regex.escape(word)}(?![a-z])").containsMatchIn(text)
        else text.contains(word)

    /** Money intent of [text], or null if it doesn't read like a transaction. */
    fun detect(text: String): Intent? {
        INCOME_CATEGORIES.firstOrNull { (_, words) -> words.any { containsWord(text, it) } }?.let { return Intent(TxType.INCOME, it.first) }
        if (INCOME_WORDS.any { containsWord(text, it) }) return Intent(TxType.INCOME, "other_income")
        EXPENSE_CATEGORIES.firstOrNull { (_, words) -> words.any { containsWord(text, it) } }?.let { return Intent(TxType.EXPENSE, it.first) }
        if (SPEND_VERBS.containsMatchIn(text)) return Intent(TxType.EXPENSE, "other_expense")
        return null
    }

    /** Best category for a note in the given direction. */
    fun categoryFor(text: String, type: TxType): String {
        val lists = if (type == TxType.INCOME) INCOME_CATEGORIES else EXPENSE_CATEGORIES
        return lists.firstOrNull { (_, words) -> words.any { containsWord(text.lowercase(), it) } }?.first
            ?: if (type == TxType.EXPENSE) "other_expense" else "other_income"
    }
}
