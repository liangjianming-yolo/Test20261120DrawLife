package com.drawlife.backgroundtoolkit.heartbeat

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "events")
internal data class PendingHeartbeat(
    // 旧表的自增主键没有显式 NOT NULL，保留其定义以便 Room 原地接管。
    @PrimaryKey(autoGenerate = true) val id: Long? = null,
    @ColumnInfo(name = "occurred_at") val occurredAtMillis: Long
)

@Dao
internal interface HeartbeatEventDao {
    @Insert
    fun insert(event: PendingHeartbeat): Long

    @Query("SELECT id, occurred_at AS occurredAtMillis FROM events ORDER BY id DESC LIMIT 1")
    fun latest(): HeartbeatEvent?

    @Query("SELECT id, occurred_at AS occurredAtMillis FROM events ORDER BY id LIMIT 100")
    fun pending(): List<HeartbeatEvent>

    @Query("DELETE FROM events WHERE occurred_at < :cutoff")
    fun deleteExpired(cutoff: Long)

    @Query("DELETE FROM events WHERE id <= :lastId")
    fun acknowledge(lastId: Long)
}

@Database(entities = [PendingHeartbeat::class], version = 2, exportSchema = false)
internal abstract class HeartbeatDatabase : RoomDatabase() {
    abstract fun events(): HeartbeatEventDao
}
