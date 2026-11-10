package es.alejandro.grandarbiter.domain.model.customlists

/** What a custom list says about a card. */
sealed interface ListStatus {
    data object Banned : ListStatus
    data object Restricted : ListStatus
    data class Points(val value: Int) : ListStatus {
        init { require(value in POINTS_RANGE) { "points out of range: $value" } }
    }

    companion object {
        val POINTS_RANGE = 1..99
    }
}

/** A card in a custom list. [name] is kept so the list reads even without card data. */
data class CustomListEntry(val oracleId: String, val name: String, val status: ListStatus)

/** A judge-made list (7 Point Highlander, a local cube…). [id] is null until saved. */
data class CustomList(
    val id: Long?,
    val name: String,
    val note: String,
    val entries: List<CustomListEntry>,
) {
    /** Entries in display order: see [CustomListOrder]. */
    val sorted: List<CustomListEntry> get() = entries.sortedWith(CustomListOrder)
}

/** Points high to low, then Banned, then Restricted, then name. Same order in view and shared text. */
object CustomListOrder : Comparator<CustomListEntry> {
    override fun compare(a: CustomListEntry, b: CustomListEntry): Int {
        val byRank = rank(a.status).compareTo(rank(b.status))
        if (byRank != 0) return byRank
        val byName = a.name.compareTo(b.name, ignoreCase = true)
        return if (byName != 0) byName else a.oracleId.compareTo(b.oracleId)
    }

    private fun rank(status: ListStatus): Int = when (status) {
        is ListStatus.Points -> ListStatus.POINTS_RANGE.last - status.value     // most points first
        ListStatus.Banned -> ListStatus.POINTS_RANGE.last + 1
        ListStatus.Restricted -> ListStatus.POINTS_RANGE.last + 2
    }
}
