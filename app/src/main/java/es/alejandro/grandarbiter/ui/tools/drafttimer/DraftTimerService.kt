package es.alejandro.grandarbiter.ui.tools.drafttimer

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import es.alejandro.grandarbiter.data.draft.DraftSessionActions
import es.alejandro.grandarbiter.data.draft.DraftTicker
import es.alejandro.grandarbiter.domain.draft.DraftNotice
import es.alejandro.grandarbiter.domain.draft.DraftRunState
import es.alejandro.grandarbiter.domain.draft.DraftSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Keeps the draft timer going outside the draft screen: it watches [DraftTicker] (so the step-end
 * vibration and beep keep coming with the screen off or another app open) and shows the running
 * step in a notification. Runs for as long as a draft is running; it stops itself when the draft
 * ends. Started from the draft screen, while the app is visible (Android doesn't allow it later).
 *
 * The phone is kept awake only while a timed step runs, until a little after it ends: enough to
 * alert and move the notification to the next step. Paused, waiting or untimed, nothing is held.
 */
class DraftTimerService : Service() {

    private val ticker: DraftTicker by inject()
    private val actions: DraftSessionActions by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching: Job? = null
    private var shown: DraftNotice? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var showingStarting = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        DraftNotification.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == null) {
            // Started from the app: become a foreground service at once (Android gives it a few seconds),
            // already showing the current step when the draft is known.
            val notice = shown ?: currentNotice()?.also { shown = it; keepAwake(it) }
            startForeground(notice?.let { DraftNotification.build(this, it) } ?: DraftNotification.starting(this))
            showingStarting = notice == null
        } else {
            act(intent.action)
        }
        if (watching == null) watching = scope.launch { ticker.state.collect(::show) }
        return START_NOT_STICKY
    }

    /** A tap on the notification. Only what it offers right now: a stale tap does nothing. */
    private fun act(action: String?) {
        val requested = runCatching { DraftNotice.Action.valueOf(action.orEmpty().removePrefix(ACTION_PREFIX)) }.getOrNull() ?: return
        if (requested !in shown?.actions.orEmpty()) return
        when (requested) {
            DraftNotice.Action.PAUSE -> actions.pause()
            DraftNotice.Action.RESUME -> actions.resume()
            DraftNotice.Action.START_NEXT, DraftNotice.Action.PICKED -> actions.startNext()
            DraftNotice.Action.NEXT -> actions.next()
        }
    }

    private fun show(state: DraftRunState) {
        when (state) {
            DraftRunState.Loading -> return
            DraftRunState.NoDraft -> stop()
            is DraftRunState.Active -> {
                val notice = DraftNotice.of(state.snapshot, System.currentTimeMillis()) ?: return stop()
                if (notice.key != shown?.key) {
                    shown = notice
                    notify(notice)
                    keepAwake(notice)
                }
            }
        }
    }

    /** The draft as it is now, without waiting for the next tick. */
    private fun currentNotice(): DraftNotice? {
        val ready = ticker.source.value as? DraftTicker.Source.Ready ?: return null
        val now = System.currentTimeMillis()
        return DraftNotice.of(DraftSnapshot.of(ready.session, ready.plan, now), now)
    }

    private fun startForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, DraftNotification.ID, notification, type)
    }

    private fun notify(notice: DraftNotice) {
        if (showingStarting) {
            // Replace the "starting" notification the same way it was posted: a plain notify() could
            // reach the system before it and be covered by it.
            showingStarting = false
            startForeground(DraftNotification.build(this, notice))
            return
        }
        val manager = NotificationManagerCompat.from(this)
        // Without the permission (Android 13+) the system lists the service in its task manager only.
        if (manager.areNotificationsEnabled()) {
            try {
                manager.notify(DraftNotification.ID, DraftNotification.build(this, notice))
            } catch (e: SecurityException) {
                Log.w(TAG, "notification not allowed", e)
            }
        }
    }

    private fun keepAwake(notice: DraftNotice) {
        wakeLock?.takeIf { it.isHeld }?.release()
        val endsAt = notice.endsAtMs ?: return
        val lock = wakeLock ?: getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GrandArbiter:draft").also { it.setReferenceCounted(false); wakeLock = it }
        lock.acquire((endsAt - System.currentTimeMillis()).coerceAtLeast(0) + AWAKE_AFTER_END_MS)
    }

    private fun stop() {
        wakeLock?.takeIf { it.isHeld }?.release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DraftTimerService"
        private const val ACTION_PREFIX = "es.alejandro.grandarbiter.draft."

        /** After a step ends: time to alert and show the next step before the phone may sleep again. */
        private const val AWAKE_AFTER_END_MS = 10_000L

        /** From the draft screen, while it's visible. */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, DraftTimerService::class.java))
            } catch (e: IllegalStateException) {
                // The app went to the background just before: it starts the next time the draft screen shows.
                Log.w(TAG, "could not start", e)
            }
        }

        fun actionIntent(context: Context, action: DraftNotice.Action): PendingIntent = PendingIntent.getService(
            context, action.ordinal,
            Intent(context, DraftTimerService::class.java).setAction(ACTION_PREFIX + action.name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
