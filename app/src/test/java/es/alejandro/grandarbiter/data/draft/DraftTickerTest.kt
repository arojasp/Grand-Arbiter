package es.alejandro.grandarbiter.data.draft

import es.alejandro.grandarbiter.domain.draft.BeepVolume
import es.alejandro.grandarbiter.domain.draft.DraftAlertSettings
import es.alejandro.grandarbiter.domain.draft.DraftAlerts
import es.alejandro.grandarbiter.domain.draft.DraftConfig
import es.alejandro.grandarbiter.domain.draft.DraftFormat
import es.alejandro.grandarbiter.domain.draft.DraftRunState
import es.alejandro.grandarbiter.domain.draft.DraftSession
import es.alejandro.grandarbiter.domain.draft.DraftSessionStore
import es.alejandro.grandarbiter.domain.draft.DraftTimings
import es.alejandro.grandarbiter.domain.draft.VibrationStrength
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ticking and the step-end alerts, in virtual time (the ticker's clock is the test clock).
 * Booster Draft, 3 × 14, MTR times, automatic start: pick 1 ends at 40 s, pick 2 at 75 s.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DraftTickerTest {

    private class FakeStore(session: DraftSession?) : DraftSessionStore {
        override val session = MutableStateFlow(session)
        override val lastConfig = DraftConfig()
        override fun setup(format: DraftFormat): DraftConfig? = null
        override fun saveSetup(config: DraftConfig) {}
        override fun save(session: DraftSession?) { this.session.value = session }
    }

    private class FakeAlerts : DraftAlerts {
        val vibrations = mutableListOf<VibrationStrength>()
        val beeps = mutableListOf<BeepVolume>()
        override fun vibrate(strength: VibrationStrength) { vibrations += strength }
        override fun beep(volume: BeepVolume) { beeps += volume }
        override val beepMuted: Flow<Boolean> = flowOf(false)
    }

    private class Rig(scope: TestScope, settings: DraftAlertSettings = DraftAlertSettings()) {
        val clock: () -> Long = { scope.testScheduler.currentTime }
        val store = FakeStore(DraftSession(DraftConfig(manualStart = false)).started(0))
        val alerts = FakeAlerts()
        val settingsFlow: StateFlow<DraftAlertSettings> = MutableStateFlow(settings)
        val ticker = DraftTicker(store, flowOf(DraftTimings.FALLBACK), settingsFlow, alerts, scope.backgroundScope, clock)
        val actions = DraftSessionActions(store, ticker, clock)
    }

    private fun TestScope.watch(rig: Rig): Job = backgroundScope.launch { rig.ticker.state.collect {} }

    /** Moves the test clock to [ms] and runs what is due. */
    private fun TestScope.at(ms: Long) {
        advanceTimeBy(ms - testScheduler.currentTime)
        runCurrent()
    }

    @Test fun oneWatcher_oneAlertPerStepEnd() = runTest {
        val rig = Rig(this)
        watch(rig)
        at(39_500); assertEquals(0, rig.alerts.vibrations.size)
        at(40_500); assertEquals(1, rig.alerts.vibrations.size)
        at(76_000); assertEquals(2, rig.alerts.vibrations.size)
    }

    @Test fun twoWatchers_stillOneAlert() = runTest {
        val rig = Rig(this)
        watch(rig); watch(rig)
        at(40_500)
        assertEquals(1, rig.alerts.vibrations.size)
    }

    @Test fun nobodyWatching_noTicking_noAlert() = runTest {
        val rig = Rig(this)
        at(41_000)
        assertEquals(0, rig.alerts.vibrations.size)
    }

    @Test fun watchingAgainAfterMinutes_noBurst_thenTheNextEnd() = runTest {
        val rig = Rig(this)
        val job = watch(rig)
        at(10_000); job.cancel()
        at(200_000)                 // picks 1–7 ended meanwhile (40, 75, 105, 130, 155, 175, 195 s)
        watch(rig); runCurrent()
        assertEquals(0, rig.alerts.vibrations.size)
        at(211_000)                 // pick 8 (7 cards left, 15 s) ends at 210 s
        assertEquals(1, rig.alerts.vibrations.size)
    }

    @Test fun watchingAgain_firstValueIsFresh() = runTest {
        val rig = Rig(this)
        val job = watch(rig)
        at(5_000); job.cancel()
        at(100_000)
        val first = rig.ticker.state.first() as DraftRunState.Active
        assertTrue("elapsed ${first.snapshot.elapsedMs}", first.snapshot.elapsedMs >= 100_000)
    }

    @Test fun aJump_neverAlerts() = runTest {
        val rig = Rig(this)
        watch(rig)
        at(39_500); rig.actions.next()     // pick 2 starts now: 35 s, ends at 74.5 s
        at(41_000); assertEquals(0, rig.alerts.vibrations.size)
        at(75_000); assertEquals(1, rig.alerts.vibrations.size)
    }

    @Test fun paused_noAlert_thenResumed_alertsOnce() = runTest {
        val rig = Rig(this)
        watch(rig)
        at(10_000); rig.actions.togglePause()
        at(70_000); assertEquals(0, rig.alerts.vibrations.size)
        rig.actions.togglePause()            // 30 s of pick 1 left: ends at 100 s
        at(99_500); assertEquals(0, rig.alerts.vibrations.size)
        at(100_500); assertEquals(1, rig.alerts.vibrations.size)
    }

    @Test fun settingsDecideWhatPlays() = runTest {
        val rig = Rig(this, DraftAlertSettings(vibrate = false, beep = true, volume = BeepVolume.LOW))
        watch(rig)
        at(40_500)
        assertEquals(emptyList<VibrationStrength>(), rig.alerts.vibrations)
        assertEquals(listOf(BeepVolume.LOW), rig.alerts.beeps)
    }

    @Test fun strengthIsPassedOn() = runTest {
        val rig = Rig(this, DraftAlertSettings(strength = VibrationStrength.STRONG))
        watch(rig)
        at(40_500)
        assertEquals(listOf(VibrationStrength.STRONG), rig.alerts.vibrations)
    }

    @Test fun endDraft_noDraft() = runTest {
        val rig = Rig(this)
        watch(rig)
        at(5_000); rig.actions.endDraft(); runCurrent()
        assertEquals(DraftRunState.NoDraft, rig.ticker.state.first())
        at(41_000); assertEquals(0, rig.alerts.vibrations.size)
    }
}
