package com.spaceslimmer.core

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import uniffi.ss_core.CoreEngine

object SystemMonitor {
    private const val POLL_MS = 30_000L

    private var handler: Handler? = null
    private var running = false
    private var onThermalThrottled: ((Boolean) -> Unit)? = null
    private var lastThrottled = false

    fun start(
        context: Context,
        engineProvider: () -> CoreEngine?,
        onThermalThrottled: ((Boolean) -> Unit)? = null,
    ) {
        this.onThermalThrottled = onThermalThrottled
        if (running) return
        running = true
        handler = Handler(Looper.getMainLooper())
        val appContext = context.applicationContext
        val tick = object : Runnable {
            override fun run() {
                if (!running) return
                update(appContext, engineProvider)
                handler?.postDelayed(this, POLL_MS)
            }
        }
        handler?.post(tick)
    }

    fun stop() {
        running = false
        handler?.removeCallbacksAndMessages(null)
        handler = null
        onThermalThrottled = null
        lastThrottled = false
    }

    private fun update(context: Context, engineProvider: () -> CoreEngine?) {
        val engine = engineProvider() ?: return
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val thermalLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (pm.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE,
                PowerManager.THERMAL_STATUS_LIGHT,
                -> 0u
                PowerManager.THERMAL_STATUS_MODERATE -> 1u
                PowerManager.THERMAL_STATUS_SEVERE -> 2u
                PowerManager.THERMAL_STATUS_CRITICAL,
                PowerManager.THERMAL_STATUS_EMERGENCY,
                PowerManager.THERMAL_STATUS_SHUTDOWN,
                -> 3u
                else -> 0u
            }
        } else {
            0u
        }
        engine.updateThermal(thermalLevel.toUByte())

        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) {
            ((level * 100) / scale).coerceIn(0, 100).toUByte()
        } else {
            100u
        }
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        engine.updatePower(pct, charging)

        val throttled = thermalLevel >= 2u
        if (throttled != lastThrottled) {
            lastThrottled = throttled
            onThermalThrottled?.invoke(throttled)
        }
    }
}
