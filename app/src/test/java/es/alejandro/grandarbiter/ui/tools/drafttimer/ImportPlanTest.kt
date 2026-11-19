package es.alejandro.grandarbiter.ui.tools.drafttimer

import es.alejandro.grandarbiter.data.draft.SharedDraftPreset
import es.alejandro.grandarbiter.domain.draft.DraftFormat
import es.alejandro.grandarbiter.domain.draft.DraftTimes
import es.alejandro.grandarbiter.domain.draft.UserDraftPreset
import es.alejandro.grandarbiter.domain.draft.UserDraftPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What Import does with names: save, ask to replace, or ask for another name. */
class ImportPlanTest {

    private val booster = listOf(UserDraftPreset("1", "RCQ Girona", DraftFormat.BOOSTER, DraftTimes.MTR, 25))
    private val twoHg = listOf(UserDraftPreset("2", "Casual", DraftFormat.TWO_HEADED_GIANT, DraftTimes.MTR, 40))
    private val named: (DraftFormat, String) -> UserDraftPreset? = { format, name ->
        UserDraftPresets.named(if (format == DraftFormat.BOOSTER) booster else twoHg, name)
    }
    private fun shared(name: String, format: DraftFormat = DraftFormat.BOOSTER) = SharedDraftPreset(name, format, DraftTimes.MTR, 30)

    @Test fun newName_saves() = assertEquals(ImportStep.Save, ImportPlan.step(shared("Liga BCN"), named))

    @Test fun takenName_anyCase_asksToReplaceThatOne() {
        assertEquals(ImportStep.AskReplace(booster[0]), ImportPlan.step(shared("RCQ Girona"), named))
        assertEquals(ImportStep.AskReplace(booster[0]), ImportPlan.step(shared("rcq girona"), named))
    }

    /** Names are per format: Booster's "RCQ Girona" doesn't block a 2HG one, and the reverse. */
    @Test fun takenOnlyInTheOtherFormat_saves() {
        assertEquals(ImportStep.Save, ImportPlan.step(shared("RCQ Girona", DraftFormat.TWO_HEADED_GIANT), named))
        assertEquals(ImportStep.Save, ImportPlan.step(shared("Casual"), named))
        assertEquals(ImportStep.AskReplace(twoHg[0]), ImportPlan.step(shared("Casual", DraftFormat.TWO_HEADED_GIANT), named))
    }

    @Test fun builtInName_asksForAnother() {
        listOf("MTR", "mtr", "Pro Tour", "pro tour").forEach {
            assertEquals(it, ImportStep.AskName, ImportPlan.step(shared(it), named))
        }
    }

    @Test fun importAs_freeNames() {
        assertTrue(ImportPlan.isFree(shared("MTR"), "MTR copy", named))
        assertTrue(ImportPlan.isFree(shared("RCQ Girona"), "  RCQ   Girona 2 ", named))   // cleaned first
        assertTrue(ImportPlan.isFree(shared("Casual"), "Casual", named))                   // only 2HG has it
    }

    @Test fun importAs_takenOrBadNames() {
        assertFalse(ImportPlan.isFree(shared("X"), "RCQ Girona", named))
        assertFalse(ImportPlan.isFree(shared("X"), "rcq girona ", named))
        assertFalse(ImportPlan.isFree(shared("X"), "Pro Tour", named))
        assertFalse(ImportPlan.isFree(shared("X"), "   ", named))
        assertFalse(ImportPlan.isFree(shared("X"), "x".repeat(41), named))
        assertFalse(ImportPlan.isFree(shared("Casual", DraftFormat.TWO_HEADED_GIANT), "casual", named))
    }
}
