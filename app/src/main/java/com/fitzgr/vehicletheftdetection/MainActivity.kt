package com.fitzgr.vehicletheftdetection

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasRequiredPermissions()) startMonitoring()
        else TrackerStore.setScanStatus(this, "Bluetooth permissions are required for scanning")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var refresh by remember { mutableIntStateOf(0) }
                val records = remember(refresh) { TrackerStore.read(this) }
                val running = remember(refresh) { TrackerStore.isRunning(this) }
                Scaffold { padding ->
                    Column(Modifier.padding(padding).padding(16.dp)) {
                        Text("Vehicle Tracker Watch", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Experimental BLE monitor. A nearby Bluetooth device is NOT proof of an AirTag or a tracker attached to your vehicle.")
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                if (hasRequiredPermissions()) startMonitoring()
                                else requestNeededPermissions()
                                refresh++
                            }) { Text("Start monitoring") }
                            OutlinedButton(onClick = {
                                TrackerStore.setRunning(this@MainActivity, false)
                                TrackerStore.setScanStatus(this@MainActivity, "Stopped")
                                stopService(Intent(this@MainActivity, MonitorService::class.java))
                                refresh++
                            }) { Text("Stop") }
                        }
                        Text(if (running) "Monitoring active" else "Monitoring stopped")
                        Text("Scan status: ${TrackerStore.scanStatus(this@MainActivity)}")
                        TextButton(onClick = { refresh++ }) { Text("Refresh sightings") }
                        Text("Observed BLE devices: ${records.size}", style = MaterialTheme.typography.titleMedium)
                        LazyColumn {
                            items(records.sortedByDescending { it.lastSeen }) { record ->
                                HorizontalDivider()
                                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                                    Text(record.name.ifBlank { "Unnamed BLE signal" }, style = MaterialTheme.typography.titleSmall)
                                    Text("Signal key: ${record.key.takeLast(12)} · sightings ${record.hits} · last RSSI ${record.rssi} dBm")
                                    Text(if (record.trusted) "Trusted (this signal ID only)" else "Unfamiliar / unclassified")
                                    TextButton(onClick = {
                                        TrackerStore.trust(this@MainActivity, record.key, !record.trusted)
                                        refresh++
                                    }) { Text(if (record.trusted) "Remove trust" else "Mark recognized") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    private fun requiredPermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= 31) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    private fun hasRequiredPermissions() = requiredPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
    private fun startMonitoring() {
        TrackerStore.setScanStatus(this, "Starting Bluetooth scan...")
        ContextCompat.startForegroundService(this, Intent(this, MonitorService::class.java).setAction("START"))
    }
    private fun requestNeededPermissions() {
        val wanted = requiredPermissions().toMutableList()
        if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
        val missing = wanted.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray())
        else startMonitoring()
    }
}
