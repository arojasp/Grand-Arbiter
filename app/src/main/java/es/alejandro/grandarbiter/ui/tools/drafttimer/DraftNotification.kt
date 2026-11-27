package es.alejandro.grandarbiter.ui.tools.drafttimer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import androidx.core.app.NotificationCompat
import es.alejandro.grandarbiter.MainActivity
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.domain.draft.DraftNotice
import es.alejandro.grandarbiter.domain.draft.DraftStep
import es.alejandro.grandarbiter.domain.draft.PassDirection

/**
 * The running draft's notification: the step, what comes next, the time (counted by the system, so
 * nothing updates it every second) and the actions of [DraftNotice]. Promoted to a Live Update
 * where the system allows it (Android 16+): kept at the top, expanded, with a status bar chip.
 */
object DraftNotification {
    const val CHANNEL = "draft_timer"
    const val ID = 1001

    /** MainActivity: open the running draft. */
    const val EXTRA_OPEN_DRAFT = "es.alejandro.grandarbiter.OPEN_DRAFT"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.draft_notif_channel), NotificationManager.IMPORTANCE_LOW).apply {
            // Silent: the step-end vibration and beep are the app's own, at the levels set in Settings.
            description = context.getString(R.string.draft_notif_channel_desc)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Shown for the moment between the service starting and the first tick. */
    fun starting(context: Context): Notification =
        base(context).setContentTitle(context.getString(R.string.tools_draft_timer)).build()

    fun build(context: Context, notice: DraftNotice): Notification {
        val res = context.resources
        val step = notice.step
        val b = base(context)
        val title = stepTitle(res, step)
        when {
            notice.isWaiting -> b
                .setContentTitle(res.getString(R.string.draft_notif_time_up, title))
                .setContentText(notice.next?.let { nextText(res, it) })
                .setShortCriticalText(formatClock(0))
            !notice.isRunning -> b
                .setContentTitle(res.getString(R.string.draft_paused_label, title))
                .setContentText(notice.remainingMs?.let { res.getString(R.string.draft_notif_left, formatClock(it)) }
                    ?: stepText(res, step, notice.next))
                .also { n -> notice.remainingMs?.let { n.setShortCriticalText(formatClock(it)) } }
            else -> b.setContentTitle(title).setContentText(stepText(res, step, notice.next))
        }
        notice.endsAtMs?.let {
            b.setUsesChronometer(true).setChronometerCountDown(true).setShowWhen(true).setWhen(countdownBase(it))
        }
        notice.startedAtMs?.let { b.setUsesChronometer(true).setShowWhen(true).setWhen(it) }
        if (notice.endsAtMs == null && notice.startedAtMs == null) b.setShowWhen(false)
        notice.actions.forEach { action -> b.addAction(0, actionLabel(res, action, notice), DraftTimerService.actionIntent(context, action)) }
        return b.build()
    }

    private fun base(context: Context): NotificationCompat.Builder {
        val open = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_DRAFT, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_draft_timer)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setRequestPromotedOngoing(true)
            .setContentIntent(PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
    }

    private fun stepTitle(res: Resources, step: DraftStep): String = when (step) {
        is DraftStep.Pick -> step.actor?.let { res.getString(R.string.draft_notif_pick_actor, it, step.pack) }
            ?: res.getString(R.string.draft_notif_pick, step.pick, step.pack)
        is DraftStep.LayOut -> res.getString(R.string.draft_notif_layout, step.pack)
        is DraftStep.Review -> res.getString(R.string.draft_notif_review, step.afterPack)
        is DraftStep.Construction -> res.getString(R.string.draft_construction)
    }

    private fun stepText(res: Resources, step: DraftStep, next: DraftStep?): String = when (step) {
        is DraftStep.Pick -> if (step.actor != null) {
            (next as? DraftStep.Pick)?.actor?.let { res.getString(R.string.draft_notif_next, it) }.orEmpty()
        } else {
            listOfNotNull(
                res.getString(R.string.draft_notif_untimed).takeIf { !step.isTimed },
                pass(res, step.direction),
                next?.let { nextText(res, it) },
            ).joinToString(" · ")
        }
        is DraftStep.LayOut -> res.getString(R.string.draft_picks_first, step.opener)
        is DraftStep.Review -> res.getString(R.string.draft_notif_next_pack, step.nextPack, pass(res, step.nextDirection))
        is DraftStep.Construction -> res.getString(R.string.draft_notif_construction_text)
    }

    private fun nextText(res: Resources, next: DraftStep): String = when (next) {
        is DraftStep.Pick -> next.actor?.let { res.getString(R.string.draft_notif_next, it) }
            ?: res.getString(R.string.draft_notif_next_pick,
                next.durationMs?.let { res.getString(R.string.draft_next_pick_value, next.cardsLeft, (it / 1000).toInt()) }
                    ?: res.getString(R.string.draft_next_pick_value_untimed, next.cardsLeft))
        is DraftStep.LayOut -> res.getString(R.string.draft_notif_next, res.getString(R.string.draft_notif_layout, next.pack))
        is DraftStep.Review -> res.getString(R.string.draft_notif_next,
            next.durationMs?.let { res.getString(R.string.draft_next_review_value, formatDuration(it)) } ?: res.getString(R.string.draft_review))
        is DraftStep.Construction -> res.getString(R.string.draft_notif_next,
            res.getString(R.string.draft_next_construction_value, formatClock(next.durationMs)))
    }

    private fun pass(res: Resources, direction: PassDirection): String =
        res.getString(if (direction == PassDirection.LEFT) R.string.draft_notif_pass_left else R.string.draft_notif_pass_right)

    private fun actionLabel(res: Resources, action: DraftNotice.Action, notice: DraftNotice): String = when (action) {
        DraftNotice.Action.PAUSE -> res.getString(R.string.draft_pause)
        DraftNotice.Action.RESUME -> res.getString(R.string.draft_resume)
        DraftNotice.Action.PICKED -> res.getString(R.string.draft_picked)
        DraftNotice.Action.START_NEXT -> startLabel(res, notice.step, notice.next)
        DraftNotice.Action.NEXT -> untimedNextLabel(res, notice.step, notice.next)
    }
}

/** What moves on from [ended] to [next] in manual start: "Start pick 5", "Start pack 2", "Start review"… */
internal fun startLabel(res: Resources, ended: DraftStep, next: DraftStep?): String = when (next) {
    is DraftStep.Pick -> when (ended) {
        is DraftStep.Review -> res.getString(R.string.draft_start_pack, next.pack)
        is DraftStep.LayOut -> res.getString(R.string.draft_start_picks)
        else -> res.getString(R.string.draft_start_pick, next.pick)
    }
    is DraftStep.LayOut -> res.getString(R.string.draft_start_pack, next.pack)
    is DraftStep.Review -> res.getString(R.string.draft_start_review)
    is DraftStep.Construction -> res.getString(R.string.draft_start_construction)
    null -> res.getString(R.string.draft_finish)
}

/** Moving on from an untimed pick or review: "Next pick", "Start pack 2", "Start deck construction". */
internal fun untimedNextLabel(res: Resources, step: DraftStep, next: DraftStep?): String = when {
    step is DraftStep.Review -> res.getString(R.string.draft_start_pack, step.nextPack)
    next is DraftStep.Pick -> res.getString(R.string.draft_next_pick_button)
    else -> startLabel(res, step, next)
}

/**
 * The system countdown shows the remaining time rounded DOWN, so given the real end it reaches 0:00
 * up to a second before the step ends. Ending it 999 ms later makes it show what the draft screen
 * shows ([formatClock], rounded UP): 0:00 only once the step is over and the alert plays.
 */
internal fun countdownBase(endsAtMs: Long): Long = endsAtMs + 999
