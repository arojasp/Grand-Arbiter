package es.alejandro.grandarbiter.domain.customlists

import es.alejandro.grandarbiter.data.customlists.CustomListMapping
import es.alejandro.grandarbiter.data.local.entities.CustomListEntity
import es.alejandro.grandarbiter.data.local.entities.CustomListEntryEntity
import es.alejandro.grandarbiter.domain.model.customlists.CustomList
import es.alejandro.grandarbiter.domain.model.customlists.CustomListEntry
import es.alejandro.grandarbiter.domain.model.customlists.ListStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomListModelsTest {

    private fun e(name: String, status: ListStatus) = CustomListEntry(name.lowercase(), name, status)

    @Test fun order_pointsHighToLow_thenBanned_thenRestricted_thenName() {
        val list = CustomList(null, "L", "", listOf(
            e("Zeta", ListStatus.Restricted),
            e("beta", ListStatus.Banned),
            e("Alpha", ListStatus.Banned),
            e("One", ListStatus.Points(1)),
            e("Three", ListStatus.Points(3)),
            e("Another three", ListStatus.Points(3)),
        ))
        assertEquals(listOf("Another three", "Three", "One", "Alpha", "beta", "Zeta"), list.sorted.map { it.name })
    }

    @Test(expected = IllegalArgumentException::class)
    fun points_outOfRange_rejected() { ListStatus.Points(0) }

    @Test fun mapping_roundTrips() {
        val entries = listOf(e("A", ListStatus.Banned), e("B", ListStatus.Restricted), e("C", ListStatus.Points(7)))
        val rows = entries.map(CustomListMapping::toEntity).mapIndexed { i, row -> row.copy(id = i + 1L, listId = 5) }
        val list = CustomListMapping.toDomain(CustomListEntity(5, "7PH", "note", 0), rows)
        assertEquals(CustomList(5, "7PH", "note", entries), list)
    }

    @Test fun mapping_skipsBadRows() {
        val rows = listOf(
            CustomListEntryEntity(1, 5, "a", "A", "points", null),
            CustomListEntryEntity(2, 5, "b", "B", "points", 0),
            CustomListEntryEntity(3, 5, "c", "C", "legal", null),
            CustomListEntryEntity(4, 5, "d", "D", "banned", null),
        )
        val list = CustomListMapping.toDomain(CustomListEntity(5, "L", "", 0), rows)
        assertEquals(listOf("d"), list.entries.map { it.oracleId })
    }
}
