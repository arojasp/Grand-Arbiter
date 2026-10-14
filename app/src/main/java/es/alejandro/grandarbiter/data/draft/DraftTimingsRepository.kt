package es.alejandro.grandarbiter.data.draft

import android.util.Log
import es.alejandro.grandarbiter.domain.draft.DraftTimings
import es.alejandro.grandarbiter.domain.repository.RulesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Every format's draft times from the installed MTR; re-read when a rules update is installed. */
class DraftTimingsRepository(private val rules: RulesRepository) {

    val timings: Flow<DraftTimings> = rules.observeDocuments()
        .map {
            val blocks = rules.page(MtrDraftTimingsParser.PAGE_ID)?.sections?.flatMap { it.blocks }.orEmpty()
            if (MtrDraftTimingsParser.parse(blocks) == null) Log.w(TAG, "Booster Draft timing not readable: built-in values")
            if (MtrDraftTimingsParser.parseTwoHeadedGiant(blocks) == null) Log.w(TAG, "Two-Headed Giant timing not readable: built-in values")
            if (MtrDraftTimingsParser.parseRochester(blocks) == null) Log.w(TAG, "Rochester timing not readable: built-in values")
            MtrDraftTimingsParser.parseAll(blocks)
        }
        .distinctUntilChanged()

    private companion object { const val TAG = "DraftTimings" }
}
