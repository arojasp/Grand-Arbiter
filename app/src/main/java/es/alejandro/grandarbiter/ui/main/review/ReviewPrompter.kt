package es.alejandro.grandarbiter.ui.main.review

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import es.alejandro.grandarbiter.domain.draft.DraftSessionStore
import es.alejandro.grandarbiter.domain.review.ReviewPolicy
import es.alejandro.grandarbiter.domain.review.ReviewStatsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Google Play In-App Review. Counts lookups ([lookupOpened]) and, when one ends
 * ([lookupClosed]), asks for a review if [ReviewPolicy] allows it. Never while a draft runs.
 * Play decides whether the card really shows and says nothing either way: the app just goes on.
 * Only works for an app installed from Play (not in a debug build from Studio).
 */
class ReviewPrompter(
    context: Context,
    private val stats: ReviewStatsStore,
    private val drafts: DraftSessionStore,
) {
    private val manager: ReviewManager = ReviewManagerFactory.create(context.applicationContext)

    suspend fun lookupOpened() = stats.updateReviewStats { ReviewPolicy.afterLookup(it, today()) }

    suspend fun lookupClosed(activity: Activity) {
        val today = today()
        if (!ReviewPolicy.shouldAsk(stats.reviewStats.first(), today)) return
        if (drafts.session.value?.isRunning == true) return
        // Marked before asking: if Play fails or shows nothing, we still wait the full interval.
        stats.updateReviewStats { it.copy(askedDay = today) }
        try {
            manager.launchReview(activity, manager.requestReview())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Review", "In-app review not shown", e)
        }
    }

    private fun today() = LocalDate.now().toEpochDay()
}