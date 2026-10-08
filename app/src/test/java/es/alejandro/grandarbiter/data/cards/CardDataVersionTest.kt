package es.alejandro.grandarbiter.data.cards

import es.alejandro.grandarbiter.data.cards.local.CardDataVersion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Decides whether the 30 MB download happens at all. */
class CardDataVersionTest {

    private val installed = CardDataVersion("2026-10-01T09:00:00.000+00:00", "2026-10-01T09:00:00.000+00:00")

    @Test fun same_isNotNewer() = assertFalse(installed.isNewerThan(installed))

    @Test fun eitherFileNewer_isNewer() {
        assertTrue(CardDataVersion("2026-10-02T09:00:00.000+00:00", installed.rulingsUpdatedAt).isNewerThan(installed))
        assertTrue(CardDataVersion(installed.oracleUpdatedAt, "2026-10-02T09:00:00.000+00:00").isNewerThan(installed))
    }

    @Test fun older_isNotNewer() =
        assertFalse(CardDataVersion("2026-09-01T09:00:00.000+00:00", "2026-09-01T09:00:00.000+00:00").isNewerThan(installed))

    @Test fun comparesInstants_notStrings() {
        // Same instant written differently: no re-download. 10:00+01:00 == 09:00Z.
        assertFalse(CardDataVersion("2026-10-01T10:00:00.000+01:00", "2026-10-01T09:00:00Z").isNewerThan(installed))
        // Later instant that sorts EARLIER as a string ("08" < "09"): must still be newer.
        assertTrue(CardDataVersion("2026-10-01T08:00:00.000-02:00", installed.rulingsUpdatedAt).isNewerThan(installed))
    }
}
