package es.alejandro.grandarbiter.domain.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** «Updated» stays until the user enters the section, and comes back with its next update. */
class SectionUpdatesTest {

    private val installed = mapOf("cr" to "2026-09-25", "ipg" to "2024-09-23", "release_notes" to "TRK")

    @Test fun firstRun_nothingIsUpdated_andEverythingGetsSeeded() {
        assertTrue(SectionUpdates.updated(installed, seen = emptyMap()).isEmpty())
        assertEquals(installed, SectionUpdates.unseeded(installed, seen = emptyMap()))
    }

    @Test fun sameVersionAsSeen_notUpdated() {
        assertTrue(SectionUpdates.updated(installed, seen = installed).isEmpty())
        assertTrue(SectionUpdates.unseeded(installed, seen = installed).isEmpty())
    }

    @Test fun newEffectiveDate_updated_onlyThatDocument() {
        val seen = installed + ("cr" to "2026-06-13")
        assertEquals(setOf("cr"), SectionUpdates.updated(installed, seen))
    }

    @Test fun newSetWithReleaseNotes_updated() {
        val seen = installed + ("release_notes" to "HOB")
        assertEquals(setOf("release_notes"), SectionUpdates.updated(installed, seen))
    }

    @Test fun enteringTheSection_storesItsVersion_andTheLabelGoes() {
        val seen = installed + ("cr" to "2026-06-13")
        val afterEntering = seen + ("cr" to installed.getValue("cr"))
        assertTrue(SectionUpdates.updated(installed, afterEntering).isEmpty())
    }

    @Test fun differentButOlderVersion_alsoUpdated() {
        val seen = installed + ("cr" to "2027-01-01")
        assertEquals(setOf("cr"), SectionUpdates.updated(installed, seen))
    }

    @Test fun sectionThatAppearsLater_isSeeded_notUpdated() {
        val withMtr = installed + ("mtr" to "2026-02-27")
        assertTrue(SectionUpdates.updated(withMtr, seen = installed).isEmpty())
        assertEquals(mapOf("mtr" to "2026-02-27"), SectionUpdates.unseeded(withMtr, seen = installed))
    }

    @Test fun seenSectionNoLongerInstalled_isIgnored() {
        val seen = installed + ("jar" to "2024-09-23")
        assertTrue(SectionUpdates.updated(installed, seen).isEmpty())
    }
}
