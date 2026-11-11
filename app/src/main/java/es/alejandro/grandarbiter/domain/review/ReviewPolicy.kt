package es.alejandro.grandarbiter.domain.review

import kotlinx.coroutines.flow.Flow

/**
 * What the app counts to decide when to ask for a Play review. Days are epoch days. Kept on the
 * phone only: nothing is sent anywhere.
 * [lookups]: cards, rules, Quick References and release-note cards opened; [daysUsed]: distinct
 * days with at least one; [askedDay]: when the review was last asked for.
 */
data class ReviewStats(
    val firstDay: Long? = null,
    val lookups: Int = 0,
    val daysUsed: Int = 0,
    val lastLookupDay: Long? = null,
    val askedDay: Long? = null,
)

/**
 * When to ask. Google: only once the user has used the app enough to give useful feedback, never
 * from a button, never by interrupting; Play has its own quota on top, so this is a lower bound.
 * The app is used mostly to look things up, so the signal is lookups, and the moment is when one
 * ends (back from the card or rule).
 */
object ReviewPolicy {
    const val MIN_LOOKUPS = 10
    const val MIN_DAYS_USED = 3
    const val MIN_DAYS_SINCE_FIRST_USE = 7
    const val DAYS_BETWEEN_ASKS = 120

    fun afterLookup(s: ReviewStats, today: Long): ReviewStats = s.copy(
        firstDay = s.firstDay ?: today,
        lookups = s.lookups + 1,
        daysUsed = if (s.lastLookupDay == today) s.daysUsed else s.daysUsed + 1,
        lastLookupDay = today,
    )

    fun shouldAsk(s: ReviewStats, today: Long): Boolean {
        val first = s.firstDay ?: return false
        return s.lookups >= MIN_LOOKUPS &&
            s.daysUsed >= MIN_DAYS_USED &&
            today - first >= MIN_DAYS_SINCE_FIRST_USE &&
            (s.askedDay == null || today - s.askedDay >= DAYS_BETWEEN_ASKS)
    }
}

/** Where [ReviewStats] live (SettingsDataStore); an interface so the logic tests without Android. */
interface ReviewStatsStore {
    val reviewStats: Flow<ReviewStats>
    suspend fun updateReviewStats(transform: (ReviewStats) -> ReviewStats)
}