package es.alejandro.grandarbiter.ui.tools.banned

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.alejandro.grandarbiter.domain.model.cards.CardDataInfo
import es.alejandro.grandarbiter.domain.model.cards.FormatBanList
import es.alejandro.grandarbiter.domain.model.customlists.CustomList
import es.alejandro.grandarbiter.domain.repository.CardRepository
import es.alejandro.grandarbiter.domain.repository.CustomListRepository
import es.alejandro.grandarbiter.ui.cardsearch.CardText
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import java.text.Normalizer

/** A format as shown: [matches] = oracle ids of cards matching the search (highlighted, auto-expanded). */
data class FormatBanView(val list: FormatBanList, val matches: Set<String> = emptySet()) {
    val autoExpand: Boolean get() = matches.isNotEmpty()
    /** A match among the category cards (ante, Conspiracy, Un-cards) also opens that block. */
    val autoExpandCategories: Boolean get() = list.byCategory.any { it.oracleId in matches }
}

/** Search over format names and card names. Pure, unit-tested. */
object BanListFilter {

    fun apply(lists: List<FormatBanList>, query: String): List<FormatBanView> {
        val q = normalize(query)
        if (q.isEmpty()) return lists.map { FormatBanView(it) }
        return lists.mapNotNull { list ->
            val matches = list.entries.filter { normalize(it.name).contains(q) }.map { it.oracleId }.toSet()
            when {
                matches.isNotEmpty() -> FormatBanView(list, matches)
                normalize(CardText.formatName(list.format)).contains(q) -> FormatBanView(list)
                else -> null
            }
        }
    }

    /** The same search over custom lists: a card match opens the list, a list-name match shows it. */
    fun applyCustom(lists: List<CustomList>, query: String): List<CustomListView> {
        val q = normalize(query)
        if (q.isEmpty()) return lists.map { CustomListView(it) }
        return lists.mapNotNull { list ->
            val matches = list.entries.filter { normalize(it.name).contains(q) }.map { it.oracleId }.toSet()
            when {
                matches.isNotEmpty() -> CustomListView(list, matches)
                normalize(list.name).contains(q) -> CustomListView(list)
                else -> null
            }
        }
    }

    /** Case and accent insensitive: "jotun" finds "Jötun Grunt". */
    fun normalize(s: String): String =
        Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}

/** A custom list as shown; [matches] like [FormatBanView.matches]. */
data class CustomListView(val list: CustomList, val matches: Set<String> = emptySet()) {
    val autoExpand: Boolean get() = matches.isNotEmpty()
    val key: String get() = "custom:${list.id}"
}

@OptIn(FlowPreview::class)
class BannedListsViewModel(
    private val savedStateHandle: SavedStateHandle,
    cards: CardRepository,
    customLists: CustomListRepository,
) : ViewModel() {

    val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")

    /** Null until the first emission (and while card data is still being imported for the first time). */
    private val lists: StateFlow<List<FormatBanList>?> = cards.observeBanLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val views: StateFlow<List<FormatBanView>?> = combine(lists, query.debounce(150)) { l, q ->
        l?.takeIf { it.isNotEmpty() }?.let { BanListFilter.apply(it, q) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val customViews: StateFlow<List<CustomListView>> = combine(customLists.observeAll(), query.debounce(150)) { l, q ->
        BanListFilter.applyCustom(l, q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dataInfo: StateFlow<CardDataInfo?> = cards.observeDataInfo()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onQueryChange(value: String) { savedStateHandle[KEY_QUERY] = value }

    private companion object {
        const val KEY_QUERY = "query"
    }
}
