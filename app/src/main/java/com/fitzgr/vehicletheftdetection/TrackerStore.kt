package com.fitzgr.vehicletheftdetection

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SeenDevice(val key: String, val name: String, val hits: Int, val rssi: Int, val firstSeen: Long, val lastSeen: Long, val trusted: Boolean)

object TrackerStore {
    private const val PREFS = "tracker_store"
    fun isRunning(ctx: Context) = ctx.getSharedPreferences(PREFS, 0).getBoolean("running", false)
    fun scanStatus(ctx: Context) = ctx.getSharedPreferences(PREFS, 0).getString("scanStatus", "Not started") ?: "Not started"
    fun setScanStatus(ctx: Context, status: String) = ctx.getSharedPreferences(PREFS, 0).edit().putString("scanStatus", status).apply()
    fun setRunning(ctx: Context, value: Boolean) = ctx.getSharedPreferences(PREFS, 0).edit().putBoolean("running", value).apply()
    @Synchronized fun read(ctx: Context): List<SeenDevice> {
        val array = JSONArray(ctx.getSharedPreferences(PREFS, 0).getString("devices", "[]"))
        return (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            SeenDevice(item.getString("key"), item.optString("name"), item.optInt("hits"), item.optInt("rssi"), item.optLong("firstSeen"), item.optLong("lastSeen"), item.optBoolean("trusted"))
        }
    }
    @Synchronized fun record(ctx: Context, key: String, name: String, rssi: Int): SeenDevice {
        val now = System.currentTimeMillis()
        val all = read(ctx).toMutableList()
        val idx = all.indexOfFirst { it.key == key }
        val previous = all.getOrNull(idx)
        val updated = SeenDevice(key, name.ifBlank { previous?.name ?: "" }, (previous?.hits ?: 0) + 1, rssi, previous?.firstSeen ?: now, now, previous?.trusted ?: false)
        if (idx >= 0) all[idx] = updated else all += updated
        write(ctx, all.sortedByDescending { it.lastSeen }.take(250))
        return updated
    }
    @Synchronized fun trust(ctx: Context, key: String, trusted: Boolean) {
        write(ctx, read(ctx).map { if (it.key == key) it.copy(trusted = trusted) else it })
    }
    private fun write(ctx: Context, all: List<SeenDevice>) {
        val array = JSONArray()
        all.forEach { r ->
            array.put(JSONObject().put("key", r.key).put("name", r.name).put("hits", r.hits).put("rssi", r.rssi).put("firstSeen", r.firstSeen).put("lastSeen", r.lastSeen).put("trusted", r.trusted))
        }
        ctx.getSharedPreferences(PREFS, 0).edit().putString("devices", array.toString()).apply()
    }
}
