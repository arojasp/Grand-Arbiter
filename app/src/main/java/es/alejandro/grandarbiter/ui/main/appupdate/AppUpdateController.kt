package es.alejandro.grandarbiter.ui.main.appupdate

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.ActivityResult as PlayActivityResult
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the UI shows about app updates. */
sealed interface AppUpdateUi {
    data object None : AppUpdateUi
    /** Flexible update downloaded: snackbar with "Restart". */
    data object RestartReady : AppUpdateUi
    /** This version must be updated; [inApp]: Play can do it here, else we open the store. */
    data class Required(val inApp: Boolean) : AppUpdateUi
}

/**
 * Google Play In-App Updates for [activity]. Checks on every resume (Play's recommendation, so an
 * interrupted immediate update resumes) and when [setRequired] flips. Decisions: [AppUpdatePolicy].
 *
 * Only works for installs from Play (internal testing / internal app sharing to try it): on a
 * debug or sideloaded build Play answers with an error and nothing is shown unless required.
 * Create it in onCreate (it registers an activity-result launcher).
 */
class AppUpdateController(
    private val activity: ComponentActivity,
    private val manager: AppUpdateManager = AppUpdateManagerFactory.create(activity),
) : DefaultLifecycleObserver {

    private val _ui = MutableStateFlow<AppUpdateUi>(AppUpdateUi.None)
    val ui: StateFlow<AppUpdateUi> = _ui.asStateFlow()

    private val prefs = activity.getSharedPreferences("app_update", Context.MODE_PRIVATE)
    private var required = false
    private var requiredPostponed = false
    private var flexibleOffered = false
    private var pending: Pair<Int, Int>? = null     // (update type, versionCode) of the flow on screen

    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult(), ::onFlowResult)

    private val installListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) _ui.value = AppUpdateUi.RestartReady
    }

    init {
        activity.lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) = manager.registerListener(installListener)
    override fun onResume(owner: LifecycleOwner) = check()
    override fun onDestroy(owner: LifecycleOwner) = manager.unregisterListener(installListener)

    /** From the rules updater: the published manifest says this version must be updated. */
    fun setRequired(value: Boolean) {
        if (value == required) return
        required = value
        check()
    }

    /** "Restart" on the snackbar: Play installs the downloaded update and relaunches the app. */
    fun restart() {
        _ui.value = AppUpdateUi.None
        manager.completeUpdate()
    }

    /** "Update" on the required dialog. */
    fun updateNow() {
        val inApp = (_ui.value as? AppUpdateUi.Required)?.inApp == true
        _ui.value = AppUpdateUi.None
        requiredPostponed = false
        if (inApp) check() else openStore()
    }

    /** "Not now" on the required dialog: keep working with the installed rules; asked again next launch. */
    fun postpone() {
        requiredPostponed = true
        _ui.value = AppUpdateUi.None
    }

    private fun check() {
        manager.appUpdateInfo
            .addOnSuccessListener { info -> act(info.toPlayInfo(), info) }
            .addOnFailureListener { e -> Log.i(TAG, "Play in-app updates unavailable: ${e.message}"); act(null, null) }
    }

    private fun act(info: PlayUpdateInfo?, raw: AppUpdateInfo?) {
        val action = AppUpdatePolicy.decide(
            info, required, requiredPostponed, flexibleOffered,
            declinedVersionCode = prefs.getInt(KEY_DECLINED, -1).takeIf { it >= 0 },
        )
        when (action) {
            AppUpdateAction.None -> Unit
            AppUpdateAction.OfferRestart -> _ui.value = AppUpdateUi.RestartReady
            AppUpdateAction.AskToUpdateFromStore -> _ui.value = AppUpdateUi.Required(inApp = false)
            AppUpdateAction.StartImmediate, AppUpdateAction.ResumeImmediate -> start(raw!!, AppUpdateType.IMMEDIATE)
            AppUpdateAction.StartFlexible -> { flexibleOffered = true; start(raw!!, AppUpdateType.FLEXIBLE) }
        }
    }

    private fun start(info: AppUpdateInfo, type: Int) {
        pending = type to info.availableVersionCode()
        manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(type).build())
    }

    private fun onFlowResult(result: ActivityResult) {
        val (type, versionCode) = pending ?: return
        pending = null
        if (result.resultCode == Activity.RESULT_OK) return
        when {
            // Declined the optional update: don't offer this version again.
            type == AppUpdateType.FLEXIBLE && !required -> prefs.edit { putInt(KEY_DECLINED, versionCode) }
            // Backed out of a required one (or it failed): explain why it matters and let them choose.
            required -> {
                requiredPostponed = true   // no immediate re-launch from onResume while the dialog is up
                _ui.value = AppUpdateUi.Required(inApp = result.resultCode != PlayActivityResult.RESULT_IN_APP_UPDATE_FAILED)
            }
        }
    }

    private fun openStore() {
        val pkg = activity.packageName
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, "market://details?id=$pkg".toUri()))
        } catch (_: ActivityNotFoundException) {
            activity.startActivity(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$pkg".toUri()))
        }
    }

    private fun AppUpdateInfo.toPlayInfo() = PlayUpdateInfo(
        availability = when (updateAvailability()) {
            UpdateAvailability.UPDATE_AVAILABLE -> PlayUpdateInfo.Availability.AVAILABLE
            UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> PlayUpdateInfo.Availability.IMMEDIATE_IN_PROGRESS
            UpdateAvailability.UPDATE_NOT_AVAILABLE -> PlayUpdateInfo.Availability.NOT_AVAILABLE
            else -> PlayUpdateInfo.Availability.UNKNOWN
        },
        availableVersionCode = availableVersionCode(),
        immediateAllowed = isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),
        flexibleAllowed = isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
        downloaded = installStatus() == InstallStatus.DOWNLOADED,
    )

    private companion object {
        const val TAG = "AppUpdate"
        const val KEY_DECLINED = "declined_version_code"
    }
}
