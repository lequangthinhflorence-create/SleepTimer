package com.example.sleeptimer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {
    private lateinit var tv: TextView
    private lateinit var et: EditText
    private val h = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            val left = if (TimerService.running) TimerService.endAt - SystemClock.elapsedRealtime() else 0L
            tv.text = fmt(maxOf(left, 0L))
            h.postDelayed(this, 500)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        tv = TextView(this).apply { textSize = 52f; gravity = Gravity.CENTER; text = "00:00:00" }
        et = EditText(this).apply {
            hint = "Số phút"; inputType = InputType.TYPE_CLASS_NUMBER; gravity = Gravity.CENTER
            setText("30")
        }
        val presets = LinearLayout(this).apply { gravity = Gravity.CENTER }
        for (m in listOf(15, 30, 45, 60)) {
            presets.addView(Button(this).apply {
                text = "$m'"; setOnClickListener { et.setText(m.toString()) }
            })
        }
        val start = Button(this).apply {
            text = "Bắt đầu"
            setOnClickListener {
                val m = et.text.toString().toIntOrNull() ?: return@setOnClickListener
                if (m <= 0) return@setOnClickListener
                startForegroundService(Intent(this@MainActivity, TimerService::class.java).putExtra("min", m))
            }
        }
        val cancel = Button(this).apply {
            text = "Hủy"
            setOnClickListener {
                startService(Intent(this@MainActivity, TimerService::class.java).setAction(TimerService.ACTION_STOP))
            }
        }
        val note = TextView(this).apply {
            text = "Hết giờ app sẽ tạm dừng nhạc đang phát (kể cả khi tắt màn hình)."
            gravity = Gravity.CENTER; setPadding(0, dp(24), 0, 0)
        }
        listOf(tv, et, presets, start, cancel, note).forEach { root.addView(it) }
        setContentView(root)
    }

    override fun onResume() { super.onResume(); h.post(refresh) }
    override fun onPause() { super.onPause(); h.removeCallbacks(refresh) }
}
