package es.alejandro.grandarbiter.ui.cardsearch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.alejandro.grandarbiter.domain.model.cards.CardDataInfo
import es.alejandro.grandarbiter.domain.model.cards.CardDetail
import es.alejandro.grandarbiter.domain.model.cards.CardSearchResult
import es.alejandro.grandarbiter.domain.model.customlists.CustomList
import es.alejandro.grandarbiter.domain.model.customlists.ListStatus
import es.alejandro.grandarbiter.domain.model.cards.FormatLegality
import es.alejandro.grandarbiter.domain.repository.CardDisplayPreferences
import es.alejandro.grandarbiter.domain.repository.CardRepository
import es.alejandro.grandarbiter.domain.repository.CustomListRepository
import es.alejandro.grandarbiter.domain.repository.SearchPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

const val ARG_CARD = "cardId"

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class CardSearchViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val repository: CardRepository,
    preferences: SearchPreferences,
) : ViewModel() {

    val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")

    /** Settings › "Search Scryfall automatically". Off: a Scryfall-only query waits for "Search on Scryfall". */
    val autoOnline: StateFlow<Boolean> = preferences.autoOnlineSearch
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** With automatic search off: the query the judge asked to search on Scryfall. */
    private val requested = MutableStateFlow<String?>(null)

    /**
     * Re-emits when an import finishes, so results appear by themselves after the first run.
     * The import's progress comes from memory: "Preparing" shows even if the first database read
     * has to wait behind the import.
     */
    val dataState: StateFlow<CardDataState> = combine(
        repository.observeDataInfo().map { true to it }.onStart { emit(false to null) },
        repository.observeImportProgress(),
    ) { (read, info), importing -> CardDataState.of(read, info, importing) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CardDataState.Checking)

    /**
     * The phone's answer and the query it answers; null while there is none (blank query, cards
     * not ready). Tagged so the screen never shows an answer to another query as this one's
     * ("0 cards found" for a query that hasn't been searched yet).
     */
    private val offline: StateFlow<Pair<String, CardSearchResult>?> = combine(query.debounce(200), dataState) { q, d -> q to d }
        .mapLatest { (q, d) ->
            if (q.isBlank() || d !is CardDataState.Ready) null else q to repository.search(q, limit = LIMIT)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Bumped by "Try again": runs the same online search once more. */
    private val retries = MutableStateFlow(0)

    /** The last successful online answer: (query, retry count, answer). */
    private var lastOnline: Triple<String, Int, CardSearchResult>? = null

    /**
     * Queries only Scryfall understands are searched there: by themselves once the judge stops
     * typing for [ONLINE_DEBOUNCE_MS] (never a request per keystroke; a newer query cancels the
     * older one), or, with automatic search off, only the query the judge asked for.
     * Emits (query, answer), answer null while it runs; null when nothing goes online.
     */
    private val online: StateFlow<Pair<String, CardSearchResult?>?> =
        combine(query.debounce(ONLINE_DEBOUNCE_MS), dataState, retries, autoOnline, requested) { q, d, n, auto, asked ->
            OnlineInput(q, d, n, auto || asked == q)
        }
            .transformLatest { (q, d, n, allowed) ->
                val local = if (q.isBlank() || d !is CardDataState.Ready || !allowed) null else repository.search(q, limit = 0)
                if (local !is CardSearchResult.NeedsOnline || !local.complete) {
                    emit(null)
                    return@transformLatest
                }
                // Back from a card: the same answer, not a new request (failures are asked again).
                lastOnline?.takeIf { it.first == q && it.second == n }?.let { emit(q to it.third); return@transformLatest }
                emit(q to null)
                val answer = repository.searchOnline(q)
                if (answer !is CardSearchResult.OnlineFailed) lastOnline = Triple(q, n, answer)
                emit(q to answer)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * What the screen shows. An answer only stands for the query it answers: while the current one
     * is being searched, the previous cards stay (or nothing, if there were none), never a "0 cards
     * found" that nobody searched. Online answers likewise: while one is on its way (or the judge is
     * still typing) the screen says it's searching Scryfall.
     */
    val results: StateFlow<SearchState> = combine(offline, online, query, autoOnline, requested) { searched, on, q, auto, asked ->
        val off = searched?.takeIf { it.first == q }?.second
        when {
            off == null -> (searched?.second as? CardSearchResult.Offline)?.takeIf { it.cards.isNotEmpty() }
                ?.let { SearchState.Shown(it) } ?: SearchState.Pending
            off !is CardSearchResult.NeedsOnline -> SearchState.Shown(off)
            !off.complete -> SearchState.Shown(off)                       // quotes/parentheses still open
            !auto && asked != q -> SearchState.AskToSearchOnline(off.parts)
            on != null && on.first == q && on.second != null -> SearchState.Shown(on.second!!)
            else -> SearchState.SearchingOnline
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState.Pending)

    fun onQueryChange(value: String) { savedStateHandle[KEY_QUERY] = value }

    /**
     * "Search on Scryfall" with automatic search off, or "Try again" after a failed online search.
     * The search key does the same. Does nothing for a query the phone answers by itself.
     */
    fun retryOnline() {
        when (val r = results.value) {
            is SearchState.AskToSearchOnline -> requested.value = query.value
            is SearchState.Shown -> if (r.result is CardSearchResult.OnlineFailed) retries.value++
            SearchState.SearchingOnline, SearchState.Pending -> Unit
        }
    }

    private data class OnlineInput(val query: String, val data: CardDataState, val retry: Int, val allowed: Boolean)

    companion object {
        /** Results shown; at the limit the header says "100+". */
        const val LIMIT = 100
        /** Pause before a query goes to Scryfall: long enough to finish a word, short enough to feel live. */
        const val ONLINE_DEBOUNCE_MS = 700L
        private const val KEY_QUERY = "query"
    }
}

sealed interface SearchState {
    data class Shown(val result: CardSearchResult) : SearchState
    /** The query has no answer yet (searching, or cards not ready): nothing to show. */
    data object Pending : SearchState
    data object SearchingOnline : SearchState
    /** Automatic search off: [parts] need Scryfall, and nothing is sent until the judge asks. */
    data class AskToSearchOnline(val parts: List<String>) : SearchState
}

sealed interface CardDetailState {
    data object Loading : CardDetailState
    data object NotFound : CardDetailState
    /** [lists]: the judge's custom lists that have this card, in their order; empty if none. */
    data class Loaded(val card: CardDetail, val lists: List<ListOnCard> = emptyList()) : CardDetailState
}

/** A custom list that has the card, and what it says about it. */
data class ListOnCard(val listName: String, val status: ListStatus)

class CardDetailViewModel(
    savedStateHandle: SavedStateHandle,
    repository: CardRepository,
    customLists: CustomListRepository,
    display: CardDisplayPreferences,
) : ViewModel() {
    private val oracleId: String = savedStateHandle.get<String>(ARG_CARD).orEmpty()

    val state: StateFlow<CardDetailState> = combine(
        flow { emit(repository.card(oracleId)) },
        customLists.observeAll(),
        display.hiddenCommunityFormats,
    ) { card, lists, hidden ->
        if (card == null) CardDetailState.NotFound
        else CardDetailState.Loaded(card.copy(legalities = visibleLegalities(card.legalities, hidden)), listsWith(oracleId, lists))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CardDetailState.Loading)

    companion object {
        /** Official formats always; community formats unless hidden in Settings. */
        fun visibleLegalities(legalities: List<FormatLegality>, hidden: Set<String>): List<FormatLegality> =
            legalities.filter { it.official || it.format !in hidden }

        /** Only the lists that have the card: absent from a list means legal / 0 points, which needs no row. */
        fun listsWith(oracleId: String, lists: List<CustomList>): List<ListOnCard> =
            lists.mapNotNull { list -> list.entries.firstOrNull { it.oracleId == oracleId }?.let { ListOnCard(list.name, it.status) } }
    }
}
