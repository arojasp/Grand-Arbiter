package es.alejandro.grandarbiter.ui.main.appupdate

/** What Google Play says about this install, mapped from AppUpdateInfo (kept Play-free for tests). */
data class PlayUpdateInfo(
    val availability: Availability,
    val availableVersionCode: Int,
    val immediateAllowed: Boolean,
    val flexibleAllowed: Boolean,
    /** A flexible update finished downloading and waits for a restart. */
    val downloaded: Boolean,
) {
    enum class Availability { AVAILABLE, IMMEDIATE_IN_PROGRESS, NOT_AVAILABLE, UNKNOWN }
}

sealed interface AppUpdateAction {
    data object None : AppUpdateAction
    /** Full-screen Play update: only when this version must be updated. */
    data object StartImmediate : AppUpdateAction
    /** An immediate update was interrupted (app killed, etc.): Play wants it resumed. */
    data object ResumeImmediate : AppUpdateAction
    /** Background download, the judge keeps working; restart offered when ready. */
    data object StartFlexible : AppUpdateAction
    data object OfferRestart : AppUpdateAction
    /** Must update, but Play can't do it in-app (not installed from Play, rollout pending…). */
    data object AskToUpdateFromStore : AppUpdateAction
}

/**
 * When to bother the judge about a new app version. Principles:
 *  - Documents never wait for this: they update by themselves (published rules bundle).
 *  - A normal release never blocks: flexible update, offered once per session, and not again
 *    for a version the judge already declined.
 *  - Blocking (immediate) only when [required]: the published manifest's minVersionCode, or a
 *    bundle this version can't read. Even then the judge can postpone it ([requiredPostponed])
 *    and keep using the installed rules: being locked out mid-ruling is worse than old rules.
 */
object AppUpdatePolicy {

    fun decide(
        info: PlayUpdateInfo?,                 // null: Play API unavailable (sideloaded, no Play Services…)
        required: Boolean,
        requiredPostponed: Boolean,
        flexibleOfferedThisSession: Boolean,
        declinedVersionCode: Int?,
    ): AppUpdateAction {
        val mustAsk = required && !requiredPostponed
        // Can't ask Play at all (debug or sideloaded build, no Play Services).
        if (info == null) return if (mustAsk) AppUpdateAction.AskToUpdateFromStore else AppUpdateAction.None
        if (info.downloaded) return AppUpdateAction.OfferRestart

        return when (info.availability) {
            PlayUpdateInfo.Availability.IMMEDIATE_IN_PROGRESS ->
                if (info.immediateAllowed) AppUpdateAction.ResumeImmediate else AppUpdateAction.None

            PlayUpdateInfo.Availability.AVAILABLE -> when {
                mustAsk && info.immediateAllowed -> AppUpdateAction.StartImmediate
                mustAsk && info.flexibleAllowed && !flexibleOfferedThisSession -> AppUpdateAction.StartFlexible
                mustAsk && !info.flexibleAllowed -> AppUpdateAction.AskToUpdateFromStore
                required -> AppUpdateAction.None    // postponed, or a flexible download already running
                info.flexibleAllowed && !flexibleOfferedThisSession && declinedVersionCode != info.availableVersionCode ->
                    AppUpdateAction.StartFlexible
                else -> AppUpdateAction.None
            }

            // Play has nothing for this device (no new version, or a staged rollout not reaching it yet).
            else -> if (mustAsk) AppUpdateAction.AskToUpdateFromStore else AppUpdateAction.None
        }
    }
}
