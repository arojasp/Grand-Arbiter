package es.alejandro.grandarbiter.ui.cardsearch

import es.alejandro.grandarbiter.domain.model.cards.CardDataInfo

sealed interface CardDataState {
    /** A moment at start-up: nothing known yet and no import running. */
    data object Checking : CardDataState
    /** No data yet: the first import is running. [cardsSoFar]: null until it has written any. */
    data class Preparing(val cardsSoFar: Int?) : CardDataState
    data class Ready(val info: CardDataInfo) : CardDataState

    companion object {
        /**
         * [metaRead]: the database has answered at least once ([info] is its answer).
         * [importing]: cards written so far by an import in progress, null when none runs.
         * An import in progress shows "Preparing" even before the database answers: the first
         * read can wait behind the import, and the screen must not just stay empty meanwhile.
         * With data installed, an update import keeps the old data searchable: Ready.
         */
        fun of(metaRead: Boolean, info: CardDataInfo?, importing: Int?): CardDataState = when {
            info != null -> Ready(info)
            importing != null -> Preparing(importing.takeIf { it > 0 })
            metaRead -> Preparing(null)   // empty database: the start-up import is about to begin
            else -> Checking
        }
    }
}
