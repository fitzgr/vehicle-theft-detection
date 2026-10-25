package com.fitzgr.vehicletheftdetection

import android.Manifest
import android.app.*
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class MonitorService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val channelId = "vehicle_monitor"
    private val alertChannel = "vehicle_alert"
    private var scanning = false
    private val currentWindow = mutableSetOf<String>()
    private val recentWindows = mutableMapOf<String, Int>()
    private var baselineInitialized = false
    private val bluetooth by lazy { getSystemService(BLUETOOTH_SERVICE) as BluetoothManager }
    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val key = result.device.address ?: return
            val name = result.scanRecord?.deviceName ?: ""
            currentWindow += key
            TrackerStore.record(this@MonitorService, key, name, result.rssi)
        }
        override fun onScanFailed(errorCode: Int) {
            scanning = false
            TrackerStore.setScanStatus(this@MonitorService, "Bluetooth scan failed (code $errorCode)")
            TrackerStore.setRunning(this@MonitorService, false)
            stopSelf()
        }
    }
    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate()
        createChannels()
        startForeground(1, NotificationCompat.Builder(this, channelId).setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("Vehicle tracker monitoring").setContentText("Scanning nearby Bluetooth signals").setOngoing(true).build())
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopScan()
            TrackerStore.setRunning(this, false)
            TrackerStore.setScanStatus(this, "Stopped")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val permission = if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_SCAN else Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            TrackerStore.setScanStatus(this, "Bluetooth scanning permission was not granted")
            TrackerStore.setRunning(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        TrackerStore.setRunning(this, true)
        beginWindow()
        return START_STICKY
    }
    private fun beginWindow() {
        stopScan()
        currentWindow.clear()
        try {
            val scanner = bluetooth.adapter?.bluetoothLeScanner
            if (scanner == null) {
                TrackerStore.setScanStatus(this, "Bluetooth is off or BLE scanning is unavailable")
                TrackerStore.setRunning(this, false)
                stopSelf()
                return
            }
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build(), callback)
            scanning = true
            TrackerStore.setScanStatus(this, "Scanning nearby BLE signals")
        } catch (_: SecurityException) {
            scanning = false
            TrackerStore.setScanStatus(this, "Bluetooth access denied")
            TrackerStore.setRunning(this, false)
            stopSelf()
            return
        }
        handler.postDelayed({ endWindow() }, 20000)
    }
    private fun endWindow() {
        stopScan()
        val observed = currentWindow.toSet()
        recentWindows.keys.toList().forEach { key -> if (key !in observed) recentWindows.remove(key) }
        observed.forEach { key -> recentWindows[key] = (recentWindows[key] ?: 0) + 1 }
        if (baselineInitialized) {
            val trusted = TrackerStore.read(this).filter { it.trusted }.map { it.key }.toSet()
            val newPersistent = observed.filter { it !in trusted && recentWindows[it] == 3 }
            if (newPersistent.isNotEmpty()) alert(newPersistent.size)
        }
        baselineInitialized = true
        handler.postDelayed({ beginWindow() }, 10000)
    }
    private fun alert(count: Int) {
        val notification = NotificationCompat.Builder(this, alertChannel)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Unfamiliar Bluetooth signals nearby")
            .setContentText("$count new signal(s) repeatedly detected. Check parked and stationary; not verified trackers.")
            .setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(2, notification)
    }
    private fun stopScan() {
        handler.removeCallbacksAndMessages(null)
        if (scanning) {
            try { bluetooth.adapter?.bluetoothLeScanner?.stopScan(callback) } catch (_: SecurityException) { }
        }
        scanning = false
    }
    private fun createChannels() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(channelId, "Monitoring", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(alertChannel, "Unfamiliar signals", NotificationManager.IMPORTANCE_HIGH))
    }
    override fun onDestroy() { stopScan(); TrackerStore.setRunning(this, false); super.onDestroy() }
}
