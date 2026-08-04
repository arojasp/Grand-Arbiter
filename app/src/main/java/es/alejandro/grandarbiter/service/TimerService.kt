package es.alejandro.grandarbiter.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import es.alejandro.grandarbiter.MainActivity
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.domain.timer.TimerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.util.Locale
import java.util.concurrent.TimeUnit

class TimerService : Service() {

    private val timerManager: TimerManager by inject()
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var timerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTimer()
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESET -> resetTimer()
        }
        return START_NOT_STICKY
    }

    private fun startTimer() {
        if (timerManager.isRunning.value) return

        timerManager.setRunning(true)
        startForeground(NOTIFICATION_ID, createNotification("00:00"))
        
        timerJob = serviceScope.launch {
            val startTime = System.currentTimeMillis() - timerManager.elapsedTime.value
            while (timerManager.isRunning.value) {
                val elapsed = System.currentTimeMillis() - startTime
                timerManager.updateElapsedTime(elapsed)
                updateNotification(formatTime(elapsed))
                delay(1000)
            }
        }
    }

    private fun pauseTimer() {
        timerManager.setRunning(false)
        timerJob?.cancel()
        stopForeground(STOP_FOREGROUND_DETACH)
    }

    private fun resetTimer() {
        pauseTimer()
        timerManager.updateElapsedTime(0L)
        stopSelf()
    }

    private fun createNotification(content: String): Notification {
        val channelId = "timer_channel"
        val channelName = "Timer Service"
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Judge Call Timer")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun formatTime(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
    }

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESET = "ACTION_RESET"
        const val NOTIFICATION_ID = 1
    }
}
