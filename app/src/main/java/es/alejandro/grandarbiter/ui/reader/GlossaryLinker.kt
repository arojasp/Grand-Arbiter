package es.alejandro.grandarbiter.ui.reader

import androidx.compose.runtime.staticCompositionLocalOf
import es.alejandro.grandarbiter.data.rules.bundle.DocumentId
import es.alejandro.grandarbiter.data.rules.bundle.GlossaryEntry
import es.alejandro.grandarbiter.domain.repository.RulesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A glossary term found in a text: [start] until [end] (exclusive). */
data class GlossaryMatch(val start: Int, val end: Int, val entry: GlossaryEntry)

/**
 * Finds CR glossary terms in rules text, Yawgatog-style. Pure: unit-tested.
 *
 *  - Longest term wins ("Mana Value" over "Mana"); case-insensitive; simple plurals ("tokens").
 *  - Each term at most once per text (one paragraph): a link per mention would bury the text.
 *  - Skipped: obsolete terms, and words in nearly every rule ("If", "Player", "Card", …), where a
 *    link adds noise, not help. With this, a rule averages ~2.4 links.
 *  - Subtypes and tokens (Case, Class, Role, Map, Forest, Clue token…) are capitalized when the CR
 *    means the Magic term, so they link only capitalized: "in this case" is not the Case subtype.
 */
class GlossaryLinker(entries: List<GlossaryEntry>) {

    private val byKey: Map<String, GlossaryEntry>
    private val capitalizedOnly: Set<String>
    private val regex: Regex?

    init {
        val usable = LinkedHashMap<String, GlossaryEntry>()
        val capitalized = HashSet<String>()
        for (entry in entries) {
            if (OBSOLETE in entry.term) continue
            val key = matchText(entry.term).lowercase()
            if (key.length < 2 || key in COMMON || key in usable) continue   // duplicates: first wins
            usable[key] = entry
            if (isSubtypeOrToken(key, entry.definition)) capitalized += key
        }
        byKey = usable
        capitalizedOnly = capitalized
        regex = if (usable.isEmpty()) null else {
            val alternatives = usable.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
            Regex("(?<![\\p{L}\\p{N}-])($alternatives)(?:s|es)?(?![\\p{L}\\p{N}-])", RegexOption.IGNORE_CASE)
        }
    }

    val termCount: Int get() = byKey.size

    fun find(text: String, alreadyLinked: MutableSet<String> = HashSet()): List<GlossaryMatch> {
        val r = regex ?: return emptyList()
        val out = ArrayList<GlossaryMatch>()
        for (m in r.findAll(text)) {
            val word = m.groupValues[1]
            val key = word.lowercase()
            val entry = byKey[key] ?: continue
            if (key in capitalizedOnly && word[0].isLowerCase()) continue
            if (!alreadyLinked.add(key)) continue
            out += GlossaryMatch(m.range.first, m.range.last + 1, entry)
        }
        return out
    }

    companion object {
        private const val OBSOLETE = "(Obsolete)"

        /** In nearly every rule: linking them would make every line a link. */
        val COMMON = setOf(
            "if", "player", "card", "ability", "creature", "effect", "spell", "permanent", "object", "cost",
            "mana", "battlefield", "cast", "damage", "trigger", "stack", "zone", "target", "counter", "step",
            "type", "copy", "name", "enter", "team", "becomes", "phase", "pay", "land", "graveyard", "library",
            "hand", "color", "owner", "power", "toughness", "draw", "play", "attack", "block", "destroy",
            "sacrifice", "exile", "discard", "reveal", "search", "tap", "untap", "shuffle", "activate",
        )

        /**
         * Defined as a subtype or a token ("An enchantment subtype", "A Map token is…", "Roles are
         * … tokens"), but not a card type ("artifact", "instant": lowercase in the CR) nor a
         * "… type" / "… counter" phrase.
         */
        fun isSubtypeOrToken(key: String, definition: String): Boolean {
            if (key.endsWith(" type") || key.endsWith(" counter")) return false
            val plain = InlineMarkup.plainText(definition)
            if (CARD_TYPE.containsMatchIn(plain.substringBefore(". "))) return false
            return TYPE_DEFINITION.containsMatchIn(plain)
        }

        private val CARD_TYPE = Regex("\\bcard type\\b", RegexOption.IGNORE_CASE)
        private val TYPE_DEFINITION = Regex(
            "^(?:\\d\\.\\s*)?(?:(?:An?|One of the (?:five )?basic)\\s+(?:[\\w-]+\\s+){0,2}(?:sub)?types?\\b" +
                "|An?\\s+[\\w’-]+(?:\\s+[\\w’-]+)?\\s+token\\s+is\\b" +
                "|[\\w’-]+s\\s+are\\s+[\\w\\s]*tokens\\b)",
            RegexOption.IGNORE_CASE,
        )

        /** "Counter (noun)" -> "Counter"; "Banding, “Bands with Other”" -> "Banding". */
        fun matchText(term: String): String = term.substringBefore(" (").substringBefore(", “").trim()
    }
}

/** Glossary links for the text below; null = plain text (non-CR documents, still loading). */
class GlossaryLinks(val linker: GlossaryLinker, val onTermClick: (GlossaryEntry) -> Unit)

val LocalGlossary = staticCompositionLocalOf<GlossaryLinks?> { null }

/**
 * Builds the linker once per installed rules version and shares it between pages (compiling a
 * 650-term regex per page would be wasted work). Rebuilt after a rules update.
 */
class GlossaryProvider(private val repository: RulesRepository) {
    private val mutex = Mutex()
    private var cachedFor: Any? = null
    private var cached: GlossaryLinker? = null

    suspend fun linker(): GlossaryLinker = mutex.withLock {
        val version = repository.observeDocuments().first().map { it.id to it.effectiveDate }
        cached?.takeIf { cachedFor == version }
            ?: withContext(Dispatchers.Default) { GlossaryLinker(repository.glossary(DocumentId.CR)) }
                .also { cached = it; cachedFor = version }
    }
}
