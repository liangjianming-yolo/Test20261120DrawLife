package com.drawlife.backgroundtoolkit.heartbeat

import android.content.Context
import android.util.Log
import java.util.Timer
import java.util.concurrent.TimeUnit
import kotlin.concurrent.timerTask

/** 一次安装内单调递增的 id 可用于服务端对重试请求去重。 */
data class HeartbeatEvent(val id: Long, val occurredAtMillis: Long)

/** upload 在心跳线程同步执行，整批被服务端接受后才返回 true。 */
data class HeartbeatConfig(
    val intervalMinutes: Int = 720,
    val maxJitterMinutes: Int = 30,
    val upload: ((installationId: String, events: List<HeartbeatEvent>) -> Boolean)? = null
) {
    init {
        require(intervalMinutes > 0 && maxJitterMinutes >= 0)
    }
}

internal object ProcessHeartbeat {
    private const val TAG = "DrawLifeHeartbeat"
    private val timer by lazy { Timer("DrawLifeHeartbeat", true) }

    /** 每个进程启动一次，首次检查沿用 SharedPreferences 中的心跳基准。 */
    fun start(context: Context, config: HeartbeatConfig) {
        val store = HeartbeatStore(context, config)
        var retryAt = 0L
        val task = timerTask {
            try {
                val now = System.currentTimeMillis()
                val generated = store.recordIfDue(now)
                Log.d(TAG, "check generated=$generated")
                if (generated) Log.i(TAG, "ALIVE recorded at=$now")

                val upload = config.upload ?: return@timerTask
                if (!generated && now < retryAt) return@timerTask
                val batch = store.pending()
                if (batch.isEmpty()) return@timerTask
                // 上传失败后保留整批，到下一次心跳或一小时后重试。重启后也可补报。
                retryAt = now + TimeUnit.HOURS.toMillis(1)
                if (upload(store.installationId(), batch)) {
                    store.acknowledge(batch.last().id)
                    retryAt = 0L
                    Log.i(TAG, "ALIVE uploaded count=${batch.size}")
                } else {
                    Log.w(TAG, "ALIVE upload pending count=${batch.size}")
                }
            } catch (e: Exception) {
                // TimerTask 抛出的异常会结束 Timer 线程，保留后续检查机会。
                Log.w(TAG, "Heartbeat check failed", e)
            }
        }
        timer.schedule(task, 0L, TimeUnit.MINUTES.toMillis(1))
    }
}
