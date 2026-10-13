package es.alejandro.grandarbiter.data.draft

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import es.alejandro.grandarbiter.domain.draft.DraftAlerts

/**
 * One clear vibration when a draft step runs out (VIBRATE permission: normal, no prompt), plus an
 * optional short beep. Alarm usage so it isn't dropped when touch haptics are off; the beep uses
 * the notification stream, so it respects silent mode.
 */
class AndroidDraftAlerts(context: Context) : DraftAlerts {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    private var tone: ToneGenerator? = null

    @Synchronized
    override fun stepEnded(beep: Boolean) {
        vibrate()
        if (beep) beep()
    }

    private fun vibrate() {
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        val effect = VibrationEffect.createOneShot(VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }

    private fun beep() {
        // ToneGenerator throws if the audio system can't give it a track: no beep, never a crash.
        runCatching {
            val generator = tone ?: ToneGenerator(AudioManager.STREAM_NOTIFICATION, BEEP_VOLUME).also { tone = it }
            generator.startTone(ToneGenerator.TONE_PROP_BEEP2, BEEP_MS)
        }.onFailure { Log.w(TAG, "beep failed", it) }
    }

    private companion object {
        const val TAG = "DraftAlerts"
        const val VIBRATION_MS = 500L
        const val BEEP_MS = 250
        const val BEEP_VOLUME = 90   // 0..100
    }
}
