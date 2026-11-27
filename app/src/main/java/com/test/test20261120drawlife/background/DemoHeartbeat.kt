package com.test.test20261120drawlife.background

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.drawlife.backgroundtoolkit.heartbeat.HeartbeatConfig
import com.drawlife.backgroundtoolkit.heartbeat.HeartbeatEvent
import com.test.test20261120drawlife.R
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal object DemoHeartbeat {
    private const val TAG = "DrawLifeHeartbeat"

    fun config(context: Context): HeartbeatConfig {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            // adb reverse 将设备回环地址的 8765 端口转发给 Mac 上的测试接收器。
            return HeartbeatConfig(intervalMinutes = 2, maxJitterMinutes = 0) { installation, events ->
                post(URL("http://127.0.0.1:8765/heartbeat"), installation, events)
            }
        }
        val endpoint = context.getString(R.string.heartbeat_upload_url).trim()
        if (endpoint.isEmpty()) return HeartbeatConfig()
        val url = URL(endpoint)
        require(url.protocol == "https") { "Set heartbeat_upload_url to an HTTPS URL" }
        return HeartbeatConfig { installation, events -> post(url, installation, events) }
    }

    private fun post(url: URL, installation: String, events: List<HeartbeatEvent>): Boolean {
        val records = JSONArray()
        for (event in events) {
            records.put(JSONObject().put("id", event.id).put("type", "ALIVE").put("at_ms", event.occurredAtMillis))
        }
        val payload = JSONObject().put("installation_id", installation).put("events", records)
        val body = payload.toString().toByteArray(Charsets.UTF_8)
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            if (status !in 200..299) Log.w(TAG, "Heartbeat endpoint returned HTTP $status")
            return status in 200..299
        } finally {
            connection.disconnect()
        }
    }
}
