package es.alejandro.grandarbiter.data.draft

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import es.alejandro.grandarbiter.domain.draft.BeepVolume
import es.alejandro.grandarbiter.domain.draft.DraftAlerts
import es.alejandro.grandarbiter.domain.draft.VibrationStrength

/**
 * The step-end vibration (VIBRATE permission: normal, no prompt) and the optional short beep.
 * Both use the alarm channel, like a timer: the vibration isn't dropped when touch haptics are off,
 * and the beep plays with the phone in silent or vibrate mode. Its volume is a share of the alarm volume.
 *
 * Strength changes the length and the number of pulses, which every vibrator can do; the
 * amplitude only changes where the motor supports it ([Vibrator.hasAmplitudeControl]).
 */
class AndroidDraftAlerts(private val context: Context) : DraftAlerts {

    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)

    /** Re-read on every volume change (any stream, any app): the system sends one broadcast for all. */
    override val beepMuted: Flow<Boolean> = callbackFlow {
        fun muted() = (audio?.getStreamVolume(AudioManager.STREAM_ALARM) ?: 1) == 0
        trySend(muted())
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) { trySend(muted()) }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(VOLUME_CHANGED_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    /** One generator per volume: ToneGenerator takes its volume once, when it's created. */
    private val tones = mutableMapOf<BeepVolume, ToneGenerator>()

    @Synchronized
    override fun vibrate(strength: VibrationStrength) {
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        val effect = effect(strength, v.hasAmplitudeControl())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }

    @Synchronized
    override fun beep(volume: BeepVolume) {
        // ToneGenerator throws if the audio system can't give it a track: no beep, never a crash.
        runCatching {
            val generator = tones.getOrPut(volume) { ToneGenerator(AudioManager.STREAM_ALARM, toneVolume(volume)) }
            generator.startTone(ToneGenerator.TONE_PROP_BEEP2, BEEP_MS)
        }.onFailure { Log.w(TAG, "beep failed", it) }
    }

    private fun effect(strength: VibrationStrength, amplitudeControl: Boolean): VibrationEffect = when (strength) {
        VibrationStrength.LIGHT ->
            VibrationEffect.createOneShot(LIGHT_MS, if (amplitudeControl) LIGHT_AMPLITUDE else VibrationEffect.DEFAULT_AMPLITUDE)
        VibrationStrength.MEDIUM ->
            VibrationEffect.createOneShot(MEDIUM_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        VibrationStrength.STRONG -> {
            val timings = longArrayOf(0, MEDIUM_MS, PAUSE_MS, MEDIUM_MS)
            if (amplitudeControl) VibrationEffect.createWaveform(timings, intArrayOf(0, 255, 0, 255), NO_REPEAT)
            else VibrationEffect.createWaveform(timings, NO_REPEAT)
        }
    }

    private fun toneVolume(volume: BeepVolume): Int = when (volume) {
        BeepVolume.LOW -> 40
        BeepVolume.MEDIUM -> 65
        BeepVolume.HIGH -> 90
    }

    private companion object {
        const val TAG = "DraftAlerts"
        const val LIGHT_MS = 250L
        const val LIGHT_AMPLITUDE = 110   // 1..255
        const val MEDIUM_MS = 500L
        const val PAUSE_MS = 200L
        const val NO_REPEAT = -1
        const val BEEP_MS = 250
        /** AudioManager.VOLUME_CHANGED_ACTION: not public API, but the only signal for a volume change. */
        const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
    }
}
