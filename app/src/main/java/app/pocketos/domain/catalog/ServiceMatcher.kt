package app.pocketos.domain.catalog

/**
 * Deterministic service recognition and search over the [ServiceCatalog].
 * No network or AI is involved: names, aliases, keywords and a bounded edit
 * distance are scored locally.
 */
class ServiceMatcher(private val catalog: ServiceCatalog) {

    data class Match(val service: ServiceInfo, val score: Int, val start: Int = -1, val end: Int = -1)

    private class Term(val text: String, val compact: String, val weight: Int, val service: ServiceInfo)

    private val terms: List<Term> = catalog.services.flatMap { s ->
        buildList {
            add(Term(TextNormalizer.normalize(s.name), TextNormalizer.compact(s.name), 100, s))
            s.aliases.forEach { add(Term(TextNormalizer.normalize(it), TextNormalizer.compact(it), 95, s)) }
            s.website?.substringBefore('/')?.let { host ->
                add(Term(TextNormalizer.normalize(host), TextNormalizer.compact(host), 90, s))
            }
        }
    }.filter { it.text.isNotEmpty() }

    private val categoryNames: Map<String, String> = catalog.categories.associate { it.id to TextNormalizer.normalize(it.name) }

    /** Exact recognition of a whole input like "Netflix" or "youtube premium". */
    fun recognize(input: String): ServiceInfo? {
        val n = TextNormalizer.normalize(input)
        val c = TextNormalizer.compact(input)
        if (n.isEmpty()) return null
        return terms.filter { it.text == n || it.compact == c }.maxByOrNull { it.weight + if (it.service.generic) 0 else 1 }?.service
    }

    /**
     * Finds the best service mentioned anywhere in a sentence ("pay my
     * netflix bill"), preferring longer and specific (non-generic) matches.
     * Returns the match with its character range in the normalised text.
     */
    fun findIn(sentence: String): Match? {
        val n = " " + TextNormalizer.normalize(sentence) + " "
        var best: Match? = null
        for (term in terms) {
            if (term.text.length < 2) continue
            val idx = n.indexOf(" " + term.text + " ")
            if (idx < 0) continue
            val score = term.text.length * 10 + term.weight / 10 + if (term.service.generic) 0 else 50
            if (best == null || score > best.score) {
                best = Match(term.service, score, idx, idx + term.text.length)
            }
        }
        return best
    }

    /**
     * Ranked search for the service picker and global search. Supports
     * prefixes ("net"), aliases, keywords/categories ("music") and small
     * typos ("netflx").
     */
    fun search(query: String, limit: Int = 20): List<Match> {
        val q = TextNormalizer.normalize(query)
        if (q.isEmpty()) return emptyList()
        val qc = q.replace(" ", "")
        val scores = HashMap<String, Match>()
        fun offer(service: ServiceInfo, score: Int) {
            val current = scores[service.id]
            if (current == null || score > current.score) scores[service.id] = Match(service, score)
        }
        for (term in terms) {
            val s = term.service
            val score = when {
                term.text == q || term.compact == qc -> 100 + term.weight
                term.text.startsWith(q) || term.compact.startsWith(qc) -> 80 + term.weight / 5 - (term.text.length - q.length).coerceAtMost(15)
                term.text.split(' ').any { it.startsWith(q) } -> 70 + term.weight / 10
                q.length >= 3 && term.text.contains(q) -> 55
                q.length >= 4 && TextNormalizer.editDistance(qc, term.compact.take(qc.length + 1), 2) <= (if (qc.length >= 7) 2 else 1) -> 45
                else -> 0
            }
            if (score > 0) offer(s, score)
        }
        for (s in catalog.services) {
            if (s.keywords.any { k -> TextNormalizer.normalize(k).let { it == q || (q.length >= 3 && it.startsWith(q)) } }) offer(s, 40)
            val categoryName = categoryNames[s.category]
            if (categoryName != null && q.length >= 3 && (categoryName.startsWith(q) || categoryName.split(' ').any { it.startsWith(q) })) offer(s, 30)
        }
        return scores.values
            .sortedWith(compareByDescending<Match> { it.score }.thenBy { it.service.generic }.thenBy { it.service.name.length })
            .take(limit)
    }
}
