package es.alejandro.grandarbiter.domain.draft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The judge's own presets: names, order, save (replacing by name), rename, delete. */
class UserDraftPresetsTest {

    private val rows14 = DraftTimeRows.booster(14, BoosterDraftTimings.FALLBACK)
    private fun preset(id: String, name: String, times: DraftTimes = DraftTimes.MTR, minutes: Int = 25) =
        UserDraftPreset(id, name, DraftFormat.BOOSTER, times, minutes)

    @Test fun names_cleanedAndChecked() {
        assertEquals("RCQ Girona", UserDraftPresets.cleanName("  RCQ   Girona "))
        assertTrue(UserDraftPresets.isValidName("RCQ Girona"))
        assertFalse(UserDraftPresets.isValidName(""))
        assertFalse(UserDraftPresets.isValidName(" RCQ"))
        assertFalse(UserDraftPresets.isValidName("x".repeat(UserDraftPresets.MAX_NAME_LENGTH + 1)))
        assertTrue(UserDraftPresets.isValidName("x".repeat(UserDraftPresets.MAX_NAME_LENGTH)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rochester_hasNoPresets() { UserDraftPreset("1", "R", DraftFormat.ROCHESTER, DraftTimes.MTR, 40) }

    @Test fun saved_sortedByName_ignoringCase() {
        val list = listOf(preset("1", "cube night"), preset("2", "RCQ")).fold(emptyList<UserDraftPreset>(), UserDraftPresets::withSaved)
        val withA = UserDraftPresets.withSaved(list, preset("3", "Arena"))
        assertEquals(listOf("Arena", "cube night", "RCQ"), withA.map { it.name })
    }

    @Test fun saveWithAnExistingName_replacesIt_keepingItsId() {
        val list = listOf(preset("1", "RCQ"), preset("2", "Cube"))
        val pt = DraftTimesPreset.PRO_TOUR.times
        val saved = UserDraftPresets.withSaved(list, preset("9", "rcq", pt, 30))
        assertEquals(2, saved.size)
        val rcq = UserDraftPresets.named(saved, "RCQ")!!
        assertEquals("1", rcq.id)
        assertEquals("rcq", rcq.name)                       // the name as typed this time
        assertTrue(rcq.matches(pt, 30))
    }

    @Test fun rename_refusesTakenOrInvalidNames() {
        val list = listOf(preset("1", "RCQ"), preset("2", "Cube"))
        assertEquals(listOf("Cube night", "RCQ"), UserDraftPresets.withRenamed(list, "2", " Cube  night ")!!.map { it.name })
        assertNull(UserDraftPresets.withRenamed(list, "2", "rcq"))      // taken by another
        assertNull(UserDraftPresets.withRenamed(list, "2", "   "))
        assertEquals("rcq", UserDraftPresets.withRenamed(list, "1", "rcq")!!.first { it.id == "1" }.name)   // its own name, other case
    }

    @Test fun builtInNames_areReserved() {
        assertTrue(UserDraftPresets.isReserved("mtr"))
        assertTrue(UserDraftPresets.isReserved(" pro   tour "))
        assertFalse(UserDraftPresets.isReserved("Pro Tour Atlanta"))
        assertNull(UserDraftPresets.withRenamed(listOf(preset("1", "RCQ")), "1", "Pro tour"))
    }

    @Test fun delete_onlyThatOne() =
        assertEquals(listOf("2"), UserDraftPresets.without(listOf(preset("1", "A"), preset("2", "B")), "1").map { it.id })

    @Test fun matches_timesAndConstruction() {
        val p = preset("1", "A", DraftTimes.MTR.withAllPicks(5, rows14), 30)
        assertTrue(p.matches(DraftTimes.MTR.withAllPicks(5, rows14), 30))
        assertFalse(p.matches(DraftTimes.MTR.withAllPicks(5, rows14), 25))
        assertFalse(p.matches(DraftTimes.MTR, 30))
    }
}
