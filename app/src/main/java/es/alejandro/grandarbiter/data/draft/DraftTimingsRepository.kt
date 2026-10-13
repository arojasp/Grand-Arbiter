package es.alejandro.grandarbiter.data.draft

import android.util.Log
import es.alejandro.grandarbiter.domain.draft.BoosterDraftTimings
import es.alejandro.grandarbiter.domain.repository.RulesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Booster Draft times from the installed MTR; re-read when a rules update is installed. */
class DraftTimingsRepository(private val rules: RulesRepository) {

    val timings: Flow<BoosterDraftTimings> = rules.observeDocuments()
        .map {
            val blocks = rules.page(MtrDraftTimingsParser.PAGE_ID)?.sections?.flatMap { it.blocks }.orEmpty()
            MtrDraftTimingsParser.parse(blocks) ?: run {
                Log.w(TAG, "Appendix B draft timing not readable: using built-in values")
                BoosterDraftTimings.FALLBACK
            }
        }
        .distinctUntilChanged()

    private companion object { const val TAG = "DraftTimings" }
}
