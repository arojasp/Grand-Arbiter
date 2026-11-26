package es.alejandro.grandarbiter.domain.draft

import es.alejandro.grandarbiter.domain.draft.DraftNotice.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The notification offers only what the draft screen does with a plain tap: never a held jump
 * (previous, next on a timed step) and never "End draft".
 */
class DraftNoticeTest {

    private val booster = DraftConfig(manualStart = false)
    private fun plan(config: DraftConfig) = DraftPlans.create(config, DraftTimings.FALLBACK)
    private fun notice(config: DraftConfig, session: DraftSession, now: Long) =
        DraftNotice.of(DraftSnapshot.of(session, plan(config), now), now)

    private val held = setOf(Action.NEXT)   // NEXT is only allowed on an untimed step

    @Test fun timedPick_running_pause_andCountsDownToTheEnd() {
        val n = notice(booster, DraftSession(booster).started(0), now = 10_000)!!
        assertEquals(listOf(Action.PAUSE), n.actions)
        assertEquals(40_000L, n.endsAtMs)          // pick 1: 40 s
        assertNull(n.startedAtMs)
        assertNull(n.remainingMs)
        assertTrue(n.step is DraftStep.Pick)
    }

    @Test fun timedPick_paused_resume_withTimeLeft() {
        val n = notice(booster, DraftSession(booster).started(0).paused(10_000), now = 50_000)!!
        assertEquals(listOf(Action.RESUME), n.actions)
        assertEquals(30_000L, n.remainingMs)
        assertNull(n.endsAtMs)
    }

    @Test fun review_and_construction_onlyPause() {
        val p = plan(booster)
        val review = p.steps.indexOfFirst { it is DraftStep.Review }
        val construction = p.steps.indexOfFirst { it is DraftStep.Construction }
        for (index in listOf(review, construction)) {
            val n = notice(booster, DraftSession(booster, step = index).started(0), now = 1_000)!!
            assertEquals(listOf(Action.PAUSE), n.actions)
        }
    }

    @Test fun manualStart_waiting_startNext() {
        val manual = DraftConfig(manualStart = true)
        val n = notice(manual, DraftSession(manual).started(0), now = 41_000)!!
        assertTrue(n.isWaiting)
        assertEquals(listOf(Action.START_NEXT), n.actions)
        assertNull(n.endsAtMs)
        assertTrue(n.next is DraftStep.Pick)
    }

    @Test fun rochester_pick_picked_andPause_waiting_pickedOnly() {
        val rochester = DraftConfig(format = DraftFormat.ROCHESTER)
        val firstPick = plan(rochester).steps.indexOfFirst { it is DraftStep.Pick }
        val running = notice(rochester, DraftSession(rochester, step = firstPick).started(0), now = 1_000)!!
        assertEquals(listOf(Action.PICKED, Action.PAUSE), running.actions)
        val waiting = notice(rochester, DraftSession(rochester, step = firstPick).started(0), now = 60_000)!!
        assertTrue(waiting.isWaiting)
        assertEquals(listOf(Action.PICKED), waiting.actions)
    }

    @Test fun rochester_layOut_onlyPause() {
        val rochester = DraftConfig(format = DraftFormat.ROCHESTER)
        val n = notice(rochester, DraftSession(rochester).started(0), now = 1_000)!!
        assertTrue(n.step is DraftStep.LayOut)
        assertEquals(listOf(Action.PAUSE), n.actions)
    }

    @Test fun untimed_next_countsUpFromTheStart() {
        val untimed = booster.copy(times = DraftTimes.MTR.copy(timedPicks = false))
        val n = notice(untimed, DraftSession(untimed).started(0), now = 12_000)!!
        assertEquals(listOf(Action.NEXT), n.actions)
        assertEquals(0L, n.startedAtMs)
        assertNull(n.endsAtMs)
    }

    @Test fun untimed_paused_nextAndResume() {
        val untimed = booster.copy(times = DraftTimes.MTR.copy(timedPicks = false))
        val n = notice(untimed, DraftSession(untimed).started(0).paused(5_000), now = 12_000)!!
        assertEquals(listOf(Action.NEXT, Action.RESUME), n.actions)
        assertNull(n.startedAtMs)
    }

    @Test fun finished_noNotice() {
        val p = plan(booster)
        val last = p.steps.lastIndex
        assertNull(notice(booster, DraftSession(booster, step = last).started(0), now = 26 * 60_000L))
    }

    @Test fun timedSteps_neverOfferAHeldJump() {
        val p = plan(booster)
        p.steps.indices.forEach { i ->
            val n = notice(booster, DraftSession(booster, step = i).started(0), now = 500) ?: return@forEach
            assertTrue("step $i offers ${n.actions}", n.actions.none { it in held })
        }
    }

    @Test fun key_changesWithTheStep_notWithTheClock() {
        val s = DraftSession(booster).started(0)
        val a = notice(booster, s, now = 1_000)!!
        val b = notice(booster, s, now = 30_000)!!
        val c = notice(booster, s, now = 41_000)!!     // pick 2
        assertEquals(a.key, b.key)
        assertNotEquals(a.key, c.key)
        assertNotEquals(a.key, notice(booster, s.paused(2_000), now = 3_000)!!.key)
    }
}
