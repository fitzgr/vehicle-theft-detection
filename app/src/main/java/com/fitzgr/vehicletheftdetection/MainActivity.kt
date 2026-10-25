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
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNeededPermissions()
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
                                requestNeededPermissions()
                                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                                    ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, MonitorService::class.java).setAction("START"))
                                }
                                refresh++
                            }) { Text("Start monitoring") }
                            OutlinedButton(onClick = {
                                startService(Intent(this@MainActivity, MonitorService::class.java).setAction("STOP"))
                                refresh++
                            }) { Text("Stop") }
                        }
                        Text(if (running) "Monitoring requested" else "Stopped")
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
    private fun requestNeededPermissions() {
        val wanted = mutableListOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT <= 30) wanted += Manifest.permission.ACCESS_FINE_LOCATION
        val missing = wanted.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray())
    }
}
