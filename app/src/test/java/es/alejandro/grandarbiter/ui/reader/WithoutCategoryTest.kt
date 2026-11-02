package es.alejandro.grandarbiter.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

/** The contents drop an IPG infraction's category, which its chapter already says. */
class WithoutCategoryTest {

    @Test fun ipgInfraction_keepsOnlyTheInfraction() {
        assertEquals("Missed Trigger", withoutCategory("Game Play Error — Missed Trigger"))
        assertEquals("Improperly Determining a Winner", withoutCategory("Unsporting Conduct — Improperly Determining a Winner"))
    }

    @Test fun titleWithoutSpacedDash_isUnchanged() {
        assertEquals("General Philosophy", withoutCategory("General Philosophy"))
        assertEquals("Appendix A—Penalty Quick Reference", withoutCategory("Appendix A—Penalty Quick Reference"))
    }
}
