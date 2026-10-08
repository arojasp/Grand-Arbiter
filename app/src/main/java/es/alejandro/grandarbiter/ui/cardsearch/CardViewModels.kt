package es.alejandro.grandarbiter.ui.cardsearch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.alejandro.grandarbiter.domain.model.cards.CardDataInfo
import es.alejandro.grandarbiter.domain.model.cards.CardDetail
import es.alejandro.grandarbiter.domain.model.cards.CardSummary
import es.alejandro.grandarbiter.domain.repository.CardRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

const val ARG_CARD = "cardId"

sealed interface CardDataState {
    data object Checking : CardDataState
    /** No data yet: the first import is running. */
    data object Preparing : CardDataState
    data class Ready(val info: CardDataInfo) : CardDataState
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class CardSearchViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val repository: CardRepository,
) : ViewModel() {

    val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")
    val inText: StateFlow<Boolean> = savedStateHandle.getStateFlow(KEY_IN_TEXT, false)

    /** Re-emits when an import finishes, so results appear by themselves after the first run. */
    val dataState: StateFlow<CardDataState> = repository.observeDataInfo()
        .map { info -> if (info == null) CardDataState.Preparing else CardDataState.Ready(info) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CardDataState.Checking)

    val results: StateFlow<List<CardSummary>> = combine(query.debounce(200), inText, dataState) { q, t, d -> Triple(q, t, d) }
        .mapLatest { (q, t, d) ->
            if (q.isBlank() || d !is CardDataState.Ready) emptyList() else repository.search(q, inText = t, limit = LIMIT)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) { savedStateHandle[KEY_QUERY] = value }
    fun onInTextChange(value: Boolean) { savedStateHandle[KEY_IN_TEXT] = value }

    companion object {
        /** Results shown; at the limit the header says "100+". */
        const val LIMIT = 100
        private const val KEY_QUERY = "query"
        private const val KEY_IN_TEXT = "inText"
    }
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
