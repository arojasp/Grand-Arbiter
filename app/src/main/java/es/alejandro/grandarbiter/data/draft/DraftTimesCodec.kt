package es.alejandro.grandarbiter.data.draft

import es.alejandro.grandarbiter.domain.draft.DraftTimes
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The judge's own draft times as a short JSON string (SharedPreferences) and back. Only changes are
 * stored, so the MTR's times are null. Anything unreadable decodes to the MTR's: never a crash,
 * never a draft that can't start. Pure: unit-tested.
 */
object DraftTimesCodec {

    /** The stored shape; separate from the domain class so the domain has no serialization. */
    @Serializable
    private data class Stored(
        val timed: Boolean = true,
        /** "14" -> "1" -> 45: pack size -> pick -> seconds. */
        val picks: Map<Int, Map<Int, Int>> = emptyMap(),
        /** "2" -> 120; "0": every review. */
        val reviews: Map<Int, Int> = emptyMap(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(times: DraftTimes): String? =
        if (times.isMtr) null else json.encodeToString(Stored.serializer(), Stored(times.timedPicks, times.pickSeconds, times.reviewSeconds))

    fun decode(text: String?): DraftTimes {
        if (text.isNullOrBlank()) return DraftTimes.MTR
        return runCatching {
            val s = json.decodeFromString(Stored.serializer(), text)
            DraftTimes(s.timed, s.picks.filterValues { it.isNotEmpty() }, s.reviews)
        }.getOrDefault(DraftTimes.MTR)
    }
}
