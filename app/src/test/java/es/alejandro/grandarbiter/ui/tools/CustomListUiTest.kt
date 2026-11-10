package es.alejandro.grandarbiter.ui.tools

import es.alejandro.grandarbiter.domain.model.customlists.CustomList
import es.alejandro.grandarbiter.domain.model.customlists.CustomListEntry
import es.alejandro.grandarbiter.domain.model.customlists.ListStatus
import es.alejandro.grandarbiter.ui.tools.banned.BanListFilter
import es.alejandro.grandarbiter.ui.tools.banned.FormState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomListUiTest {

    private val cube = CustomList(1, "Local Cube", "", listOf(CustomListEntry("j", "Jötun Grunt", ListStatus.Banned)))
    private val ph = CustomList(2, "7 Point Highlander", "", listOf(CustomListEntry("s", "Sol Ring", ListStatus.Points(3))))

    @Test fun filter_blank_showsAll() {
        assertEquals(listOf(1L, 2L), BanListFilter.applyCustom(listOf(cube, ph), " ").map { it.list.id })
    }

    @Test fun filter_cardMatch_opensTheList_accentInsensitive() {
        val views = BanListFilter.applyCustom(listOf(cube, ph), "jotun")
        assertEquals(listOf(1L), views.map { it.list.id })
        assertEquals(setOf("j"), views.single().matches)
        assertEquals(true, views.single().autoExpand)
    }

    @Test fun filter_listNameMatch_showsItClosed() {
        val views = BanListFilter.applyCustom(listOf(cube, ph), "highlander")
        assertEquals(listOf(2L), views.map { it.list.id })
        assertEquals(false, views.single().autoExpand)
    }

    @Test fun formState_roundTrips_namesWithTabsSafe() {
        val entries = listOf(
            CustomListEntry("a", "Fire // Ice", ListStatus.Points(12)),
            CustomListEntry("b", "Black Lotus", ListStatus.Restricted),
            CustomListEntry("c", "Shahrazad", ListStatus.Banned),
        )
        assertEquals(entries, entries.map(FormState::encode).map(FormState::decode))
    }

    @Test fun formState_badRows_dropped() {
        assertNull(FormState.decode("a\t0\tCard"))
        assertNull(FormState.decode("a\tX\tCard"))
        assertNull(FormState.decode("garbage"))
    }
}
