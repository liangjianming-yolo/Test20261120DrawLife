package com.drawlife.backgroundtoolkit.heartbeat

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** SharedPreferences 保存计时基准，Room 管理等待上报的 ALIVE 事件。 */
internal class HeartbeatStore(context: Context, private val config: HeartbeatConfig) {
    private val prefs = context.getSharedPreferences("drawlife_heartbeat", Context.MODE_PRIVATE)
    private val intervalMillis = TimeUnit.MINUTES.toMillis(config.intervalMinutes.toLong())
    private val events by lazy {
        val path = File(context.noBackupFilesDir, "drawlife-heartbeat.db").absolutePath
        Room.databaseBuilder(context, HeartbeatDatabase::class.java, path)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // 新建事件库时重置可能由系统备份恢复的旧安装标识。
                    check(prefs.edit().clear().putString(INSTALLATION_ID, UUID.randomUUID().toString()).commit())
                }
            })
            .addMigrations(object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) = migrateSchedule(db)
            })
            .build()
            .events()
    }

    // commit 失败时 SharedPreferences 的内存值也可能已更新，只有落盘成功才推进这个 ID。
    private var lastSavedEventId: Long? = null

    private fun migrateSchedule(db: SupportSQLiteDatabase) {
        db.query(
            "SELECT installation_id, last_at, next_at, (SELECT COALESCE(MAX(id), 0) FROM events) FROM schedule"
        ).use { row ->
            check(row.moveToFirst())
            val edit = prefs.edit().clear()
                .putString(INSTALLATION_ID, row.getString(0))
                .putLong(LAST_EVENT_ID, row.getLong(3))
            if (row.getLong(2) != 0L) {
                // 把旧到期时间换算为基准，升级后第一次检查仍按原来的时间到期。
                edit.putLong(LAST_AT, row.getLong(1)).putLong(BASE_AT, row.getLong(2) - intervalMillis)
            }
            check(edit.commit())
        }
        db.execSQL("DROP TABLE schedule")
    }

    fun recordIfDue(now: Long): Boolean {
        val latest = events.latest() // 第一次查询会先完成建库或迁移，再读取偏好。
        val savedId = lastSavedEventId ?: prefs.getLong(LAST_EVENT_ID, 0L).also { lastSavedEventId = it }
        // 上次若在事件入库后、基准落盘前退出，先用已入队的那条事件补齐计时。
        if (latest != null && latest.id > savedId) saveTiming(latest.id, latest.occurredAtMillis)
        events.deleteExpired(now - TimeUnit.DAYS.toMillis(3))

        val lastAt = prefs.getLong(LAST_AT, 0L)
        val nextAt = prefs.getLong(BASE_AT, 0L) + intervalMillis
        if (prefs.contains(BASE_AT) && now >= lastAt && now < nextAt) return false

        val id = events.insert(PendingHeartbeat(occurredAtMillis = now))
        saveTiming(id, now)
        return true
    }

    private fun saveTiming(id: Long, occurredAt: Long) {
        val jitter = (0..config.maxJitterMinutes).random()
        val baseAt = occurredAt + TimeUnit.MINUTES.toMillis(jitter.toLong())
        check(prefs.edit().putLong(LAST_EVENT_ID, id).putLong(LAST_AT, occurredAt)
            .putLong(BASE_AT, baseAt).commit()) { "Could not save heartbeat timing" }
        lastSavedEventId = id
    }

    fun installationId(): String = checkNotNull(prefs.getString(INSTALLATION_ID, null))

    fun pending(): List<HeartbeatEvent> = events.pending()

    fun acknowledge(lastId: Long) {
        events.acknowledge(lastId)
    }

    private companion object {
        const val INSTALLATION_ID = "installation_id"
        const val LAST_AT = "last_at"
        const val BASE_AT = "heartbeat_base_at"
        const val LAST_EVENT_ID = "last_event_id"
    }
}
