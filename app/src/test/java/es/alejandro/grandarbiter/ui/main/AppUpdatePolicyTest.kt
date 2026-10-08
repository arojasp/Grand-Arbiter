package es.alejandro.grandarbiter.ui.main

import es.alejandro.grandarbiter.ui.main.appupdate.AppUpdateAction
import es.alejandro.grandarbiter.ui.main.appupdate.AppUpdatePolicy
import es.alejandro.grandarbiter.ui.main.appupdate.PlayUpdateInfo
import es.alejandro.grandarbiter.ui.main.appupdate.PlayUpdateInfo.Availability
import org.junit.Assert.assertEquals
import org.junit.Test

/** When the judge is (and isn't) interrupted by an app update. */
class AppUpdatePolicyTest {

    private fun play(
        availability: Availability = Availability.AVAILABLE,
        versionCode: Int = 7,
        immediate: Boolean = true,
        flexible: Boolean = true,
        downloaded: Boolean = false,
    ) = PlayUpdateInfo(availability, versionCode, immediate, flexible, downloaded)

    private fun decide(
        info: PlayUpdateInfo?,
        required: Boolean = false,
        postponed: Boolean = false,
        offered: Boolean = false,
        declined: Int? = null,
    ) = AppUpdatePolicy.decide(info, required, postponed, offered, declined)

    // ------------------------------------------------------------ normal releases: never blocking

    @Test fun normalRelease_isFlexible_notImmediate() =
        assertEquals(AppUpdateAction.StartFlexible, decide(play()))

    @Test fun normalRelease_offeredOncePerSession() =
        assertEquals(AppUpdateAction.None, decide(play(), offered = true))

    @Test fun declinedVersion_notOfferedAgain_butANewerOneIs() {
        assertEquals(AppUpdateAction.None, decide(play(versionCode = 7), declined = 7))
        assertEquals(AppUpdateAction.StartFlexible, decide(play(versionCode = 8), declined = 7))
    }

    @Test fun nothingOnPlay_orNoPlay_doesNothing() {
        assertEquals(AppUpdateAction.None, decide(play(availability = Availability.NOT_AVAILABLE)))
        assertEquals(AppUpdateAction.None, decide(null))   // debug / sideloaded build
    }

    @Test fun flexibleNotAllowedByPlay_doesNothing() =
        assertEquals(AppUpdateAction.None, decide(play(flexible = false)))

    @Test fun downloaded_offersRestart_always() {
        assertEquals(AppUpdateAction.OfferRestart, decide(play(downloaded = true)))
        assertEquals(AppUpdateAction.OfferRestart, decide(play(downloaded = true), required = true))
    }

    // ------------------------------------------------------------ required (manifest minVersionCode / new schema)

    @Test fun required_isImmediate_evenIfThatVersionWasDeclinedBefore() =
        assertEquals(AppUpdateAction.StartImmediate, decide(play(versionCode = 7), required = true, declined = 7))

    @Test fun required_postponed_doesNotReopenOnEveryResume() =
        assertEquals(AppUpdateAction.None, decide(play(), required = true, postponed = true))

    @Test fun required_withoutImmediate_fallsBackToFlexible_thenToTheStore() {
        assertEquals(AppUpdateAction.StartFlexible, decide(play(immediate = false), required = true))
        assertEquals(AppUpdateAction.None, decide(play(immediate = false), required = true, offered = true))   // downloading
        assertEquals(AppUpdateAction.AskToUpdateFromStore, decide(play(immediate = false, flexible = false), required = true))
    }

    @Test fun required_butPlayCantHelp_sendsToTheStore() {
        assertEquals(AppUpdateAction.AskToUpdateFromStore, decide(null, required = true))
        // Staged rollout: Play doesn't offer it to this device yet.
        assertEquals(AppUpdateAction.AskToUpdateFromStore, decide(play(availability = Availability.NOT_AVAILABLE), required = true))
        assertEquals(AppUpdateAction.None, decide(null, required = true, postponed = true))
    }

    @Test fun interruptedImmediateUpdate_isResumed() {
        assertEquals(AppUpdateAction.ResumeImmediate, decide(play(availability = Availability.IMMEDIATE_IN_PROGRESS)))
        assertEquals(AppUpdateAction.None, decide(play(availability = Availability.IMMEDIATE_IN_PROGRESS, immediate = false)))
    }
}
