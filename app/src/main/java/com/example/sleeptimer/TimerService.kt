package com.example.sleeptimer

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.*
import android.view.KeyEvent

class TimerService : Service() {
    companion object {
        const val ACTION_STOP = "stop"
        @Volatile var running = false
        @Volatile var endAt = 0L
        private const val CH = "timer"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var wl: PowerManager.WakeLock? = null

    private val tick = object : Runnable {
        override fun run() {
            val left = endAt - SystemClock.elapsedRealtime()
            if (left <= 0) {
                stopMusic()
            } else {
                notify(left)
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            cancel(); return START_NOT_STICKY
        }
        val min = intent?.getIntExtra("min", 0) ?: 0
        if (min <= 0) { stopSelf(); return START_NOT_STICKY }
        val ms = min * 60_000L
        endAt = SystemClock.elapsedRealtime() + ms
        running = true

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Hẹn giờ", NotificationManager.IMPORTANCE_LOW))
        val n = build(ms)
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)

        wl?.release()
        wl = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sleeptimer:wl").apply { acquire(ms + 30_000L) }

        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_NOT_STICKY
    }

    private fun build(left: Long): Notification {
        val stop = PendingIntent.getService(
            this, 0, Intent(this, TimerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Sẽ tắt nhạc sau ${fmt(left)}")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Hủy", stop).build())
            .build()
    }

    private fun notify(left: Long) =
        getSystemService(NotificationManager::class.java).notify(1, build(left))

    private fun stopMusic() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            )
            .setOnAudioFocusChangeListener { }
            .build()
        am.requestAudioFocus(req)
        handler.postDelayed({
            am.abandonAudioFocusRequest(req)
            cancel()
        }, 3000)
    }

    private fun cancel() {
        handler.removeCallbacksAndMessages(null)
        running = false
        wl?.let { if (it.isHeld) it.release() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        wl?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}

fun fmt(ms: Long): String {
    val s = (ms + 999) / 1000
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}
