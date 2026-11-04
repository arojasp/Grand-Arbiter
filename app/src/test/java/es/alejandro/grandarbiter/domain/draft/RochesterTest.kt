package es.alejandro.grandarbiter.domain.draft

import es.alejandro.grandarbiter.domain.draft.DraftPosition.Finished
import es.alejandro.grandarbiter.domain.draft.DraftPosition.Running
import es.alejandro.grandarbiter.domain.draft.DraftPosition.Waiting
import es.alejandro.grandarbiter.domain.draft.DraftStep.LayOut
import es.alejandro.grandarbiter.domain.draft.DraftStep.Pick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Team Rochester at the table: MTR 8.5 + Appendix B. Who opens, who picks, and "Picked". */
class RochesterTest {

    private val defaults = DraftConfig.defaultsFor(DraftFormat.ROCHESTER, manualStart = false)
    private fun plan(config: DraftConfig = defaults) = DraftPlans.create(config, DraftTimings.FALLBACK)

    // ---- order ----

    @Test fun picks_matchTheMtrExample() {
        // MTR 8.5: Team 1 picks first, 1B lays out the first pack, 15 cards.
        assertEquals(
            listOf("1B", "1C", "2A", "2B", "2C", "1A", "1A", "2C", "2B", "2A", "1C", "1B", "1B", "1C", "2A"),
            RochesterOrder.picks("1B", clockwise = true, cards = 15),
        )
    }

    @Test fun picks_counterclockwise_snakeTheOtherWay() =
        assertEquals(listOf("1A", "2C", "2B", "2A", "1C", "1B", "1B", "1C"), RochesterOrder.picks("1A", clockwise = false, cards = 8))

    @Test fun openers_playerBOfTheTeamPickingFirst_startsTheDraft() {
        assertEquals("1B", RochesterOrder.openers(firstTeam = 1, groups = 1).first())
        assertEquals("2B", RochesterOrder.openers(firstTeam = 2, groups = 1).first())
    }

    @Test fun openers_horseshoe_lastOfAGroupOpensTheNext_directionReverses() {
        assertEquals(
            listOf(
                "1B", "1C", "2A", "2B", "2C", "1A",   // group 1, clockwise
                "1A", "2C", "2B", "2A", "1C", "1B",   // group 2, counterclockwise
                "1B", "1C", "2A", "2B", "2C", "1A",   // group 3, clockwise
            ),
            RochesterOrder.openers(firstTeam = 1, groups = 3),
        )
    }

    @Test fun everyPlayerOpensOncePerGroup() {
        RochesterOrder.openers(firstTeam = 2, groups = 3).chunked(6).forEach {
            assertEquals(RochesterOrder.SEATS.toSet(), it.toSet())
        }
    }

    // ---- plan ----

    @Test fun defaults_3PacksPerPlayer_15Cards_40Minutes_18Packs() {
        assertEquals(3, defaults.packs)
        assertEquals(15, defaults.cardsPerPack)
        assertEquals(40, defaults.constructionMinutes)
        assertEquals(18, defaults.totalPacks)
    }

    @Test fun plan_everyPack_layOutThenAPickPerCard() {
        val steps = plan().steps
        assertEquals(18, steps.filterIsInstance<LayOut>().size)
        assertEquals(18 * 15, steps.filterIsInstance<Pick>().size)
        assertEquals(LayOut(1, "1B", PassDirection.LEFT, 20_000), steps[0])
        val first = steps[1] as Pick
        assertEquals("1B", first.actor)
        assertEquals(5_000L, first.durationMs)
        assertTrue(first.waitsForPicked)
        assertEquals(LayOut(7, "1A", PassDirection.RIGHT, 20_000), steps.filterIsInstance<LayOut>()[6])   // group 2
    }

    @Test fun estimate_atMost68m30s() {
        val e = DraftEstimate.of(plan())
        assertEquals((68 * 60 + 30) * 1_000L, e.totalMs)
        assertEquals(List(18) { 20 }, e.layOutSeconds)
        assertTrue(e.atMost)
        assertEquals(5, e.pickSeconds)
        assertEquals(18, e.packs)
    }

    // ---- running: picks wait for "Picked" even without manual start ----

    // 1 pack per player = 6 packs at the table, each: layout 20 s + 5 picks of 5 s; then construction.
    private val p = plan(defaults.copy(packs = 1, cardsPerPack = 5))
    private val started = DraftSession(defaults.copy(packs = 1, cardsPerPack = 5)).started(now = 0)

    @Test fun layOut_runsIntoTheFirstPick_byItself() {
        val pos = started.positionAt(p, 22_000) as Running
        assertEquals(1, pos.index)
        assertEquals(3_000L, pos.remainingMs)
    }

    @Test fun pick_atZero_waits_doesNotRunIntoTheNext() {
        assertEquals(Waiting(1, p.steps[1]), started.positionAt(p, 25_000))
        assertEquals(Waiting(1, p.steps[1]), started.positionAt(p, 90_000))
        assertEquals(25_000, started.elapsedAt(p, 90_000))
    }

    @Test fun picked_early_startsTheNextPickWithFiveSeconds() {
        val s = started.startNext(p, now = 21_500)          // 1B took the card after 1.5 s
        val pos = s.positionAt(p, 21_500) as Running
        assertEquals(2, pos.index)
        assertEquals(5_000L, pos.remainingMs)
    }

    @Test fun picked_atZero_movesOn() {
        val s = started.startNext(p, now = 40_000)
        assertEquals(2, (s.positionAt(p, 40_000) as Running).index)
    }

    @Test fun lastPick_picked_thenConstructionRunsToTheEnd() {
        var s = started
        var now = 1_000L   // during the first layout
        repeat(p.steps.size - 1) { s = s.startNext(p, now); now += 1_000 }   // every layout and pick, quickly
        val pos = s.positionAt(p, now) as Running
        assertTrue(pos.step is DraftStep.Construction)
        assertEquals(Finished, s.positionAt(p, now + 40 * 60_000L))
    }

    @Test fun alert_onceWhenAPickRunsOut() {
        val before = started.elapsedAt(p, 24_500)
        val atZero = started.elapsedAt(p, 25_400)
        assertTrue(DraftAlertPolicy.shouldAlert(p, before, atZero))
        assertFalse(DraftAlertPolicy.shouldAlert(p, atZero, started.elapsedAt(p, 26_400)))
    }

    @Test fun snapshot_upcoming_namesWhoPicksNext() {
        val s = DraftSnapshot.of(started, p, 22_000)
        assertEquals(listOf("1C", "2A", "2B"), s.upcoming.filterIsInstance<Pick>().take(3).map { it.actor })
    }

    @Test fun manualStart_alsoWaitsAfterTheLayOut() {
        val manual = DraftSession(defaults.copy(packs = 1, cardsPerPack = 5, manualStart = true)).started(now = 0)
        assertEquals(Waiting(0, p.steps[0]), manual.positionAt(p, 30_000))
    }

    @Test(expected = IllegalArgumentException::class)
    fun firstTeam_mustBe1or2() { defaults.copy(firstTeam = 3) }
}
