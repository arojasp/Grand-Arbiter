package es.alejandro.grandarbiter.ui.cardsearch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.alejandro.grandarbiter.domain.model.cards.CardDataInfo
import es.alejandro.grandarbiter.domain.model.cards.CardDetail
import es.alejandro.grandarbiter.domain.model.cards.CardSearchResult
import es.alejandro.grandarbiter.domain.repository.CardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
) : ViewModel() {

    val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")

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

    private val offline: StateFlow<CardSearchResult> = combine(query.debounce(200), dataState) { q, d -> q to d }
        .mapLatest { (q, d) ->
            if (q.isBlank() || d !is CardDataState.Ready) CardSearchResult.Empty else repository.search(q, limit = LIMIT)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CardSearchResult.Empty)

    /** The last online search: for which query, and its answer (null while it runs). */
    private val online = MutableStateFlow<Pair<String, CardSearchResult?>?>(null)

    /**
     * What the screen shows. An online answer only stands while the query is the one searched:
     * typing again goes back to the offline result (or to "search online?" for the new query).
     */
    val results: StateFlow<SearchState> = combine(offline, online, query) { off, on, q ->
        when {
            off is CardSearchResult.NeedsOnline && on != null && on.first == q ->
                on.second?.let { SearchState.Shown(it) } ?: SearchState.SearchingOnline
            else -> SearchState.Shown(off)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState.Shown(CardSearchResult.Empty))

    fun onQueryChange(value: String) { savedStateHandle[KEY_QUERY] = value }

    /**
     * The search key / "Search on Scryfall". Only for queries that need it, and one request per
     * press: Scryfall asks apps not to send a request per keystroke.
     */
    fun searchOnline() {
        val q = query.value
        // Offline queries never go out; "Try again" after a failed online search does.
        val shown = (results.value as? SearchState.Shown)?.result
        if (shown !is CardSearchResult.NeedsOnline && shown !is CardSearchResult.OnlineFailed) return
        if (online.value?.let { it.first == q && it.second == null } == true) return   // already running
        online.value = q to null
        viewModelScope.launch {
            val answer = repository.searchOnline(q)
            if (online.value?.first == q) online.value = q to answer
        }
    }

    companion object {
        /** Results shown; at the limit the header says "100+". */
        const val LIMIT = 100
        private const val KEY_QUERY = "query"
    }
}

sealed interface SearchState {
    data class Shown(val result: CardSearchResult) : SearchState
    data object SearchingOnline : SearchState
}

sealed interface CardDetailState {
    data object Loading : CardDetailState
    data object NotFound : CardDetailState
    data class Loaded(val card: CardDetail) : CardDetailState
}

class CardDetailViewModel(
    savedStateHandle: SavedStateHandle,
    repository: CardRepository,
) : ViewModel() {
    private val oracleId: String = savedStateHandle.get<String>(ARG_CARD).orEmpty()

    val state: StateFlow<CardDetailState> = flow {
        emit(repository.card(oracleId)?.let { CardDetailState.Loaded(it) } ?: CardDetailState.NotFound)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CardDetailState.Loading)
}
