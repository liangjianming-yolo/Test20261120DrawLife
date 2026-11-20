package com.test.test20261120drawlife.background

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

data class DailyAlarmPlan(val hour: Int, val minute: Int, val nextTriggerAtMillis: Long)

enum class AlarmScheduleStatus { DISABLED, SCHEDULED, NEEDS_PERMISSION, ERROR }

object AlarmReminderScheduler {
    const val ACTION_REMINDER = "com.test.test20261120drawlife.action.DAILY_REMINDER"
    const val PREFERENCES_NAME = "draw_life_daily_alarm"
    private const val REQUEST_CODE = 34954
    private const val KEY_HOUR = "hour"
    private const val KEY_MINUTE = "minute"
    private const val KEY_NEXT_TRIGGER = "next_trigger_at"

    fun readPlan(context: Context): DailyAlarmPlan? {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (!preferences.contains(KEY_NEXT_TRIGGER)) return null
        val hour = preferences.getInt(KEY_HOUR, -1)
        val minute = preferences.getInt(KEY_MINUTE, -1)
        val nextTrigger = preferences.getLong(KEY_NEXT_TRIGGER, 0L)
        if (hour !in 0..23 || minute !in 0..59 || nextTrigger <= 0L) return null
        return DailyAlarmPlan(hour, minute, nextTrigger)
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager(context).canScheduleExactAlarms()
    }

    @Synchronized
    fun setDailyReminder(context: Context, hour: Int, minute: Int): AlarmScheduleStatus {
        require(hour in 0..23 && minute in 0..59)
        val plan = DailyAlarmPlan(hour, minute, nextDailyTrigger(hour, minute))
        if (!savePlan(context, plan)) return AlarmScheduleStatus.ERROR
        return schedule(context, plan, source = "user")
    }

    @Synchronized
    fun restore(context: Context, source: String, timeChanged: Boolean = false): AlarmScheduleStatus {
        var plan = readPlan(context) ?: return AlarmScheduleStatus.DISABLED
        if (timeChanged) {
            // 用户修改时间或时区后，按新的本地时间重新计算每日提醒。
            plan = plan.copy(nextTriggerAtMillis = nextDailyTrigger(plan.hour, plan.minute))
            if (!savePlan(context, plan)) return AlarmScheduleStatus.ERROR
        }
        // 保留已保存的到期时间。冷启动可能由这次闹钟触发，不能在 Application
        // 中提前推进到明天，否则随后执行的接收器会漏掉本次提醒。
        return schedule(context, plan, source)
    }

    @Synchronized
    fun cancel(context: Context): Boolean {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (!preferences.edit().clear().commit()) {
            Log.e(BackgroundTaskScheduler.LOG_TAG, "Alarm plan cancellation could not be saved")
            return false
        }
        val operation = PendingIntent.getBroadcast(
            context, REQUEST_CODE, reminderIntent(context),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (operation != null) {
            alarmManager(context).cancel(operation)
            operation.cancel()
        }
        ReminderNotification.cancel(context)
        Log.i(BackgroundTaskScheduler.LOG_TAG, "Daily alarm cancelled")
        return true
    }

    @Synchronized
    fun onAlarmReceived(context: Context) {
        val plan = readPlan(context) ?: return
        val now = System.currentTimeMillis()
        if (now < plan.nextTriggerAtMillis) {
            // 忽略取消、改时或重复投递后残留的旧广播。
            Log.i(BackgroundTaskScheduler.LOG_TAG, "Ignored stale alarm broadcast")
            return
        }

        val nextPlan = plan.copy(nextTriggerAtMillis = nextDailyTrigger(plan.hour, plan.minute, now))
        // 先推进持久化状态，再发通知，避免冷启动恢复与重复投递多次提醒。
        if (!savePlan(context, nextPlan)) return
        schedule(context, nextPlan, source = "alarm_received")
        Log.i(
            BackgroundTaskScheduler.LOG_TAG,
            "Alarm received: scheduled=${plan.nextTriggerAtMillis}, actual=$now"
        )
        ReminderNotification.show(context)
    }

    private fun schedule(context: Context, plan: DailyAlarmPlan, source: String): AlarmScheduleStatus {
        if (!canScheduleExactAlarms(context)) {
            Log.w(BackgroundTaskScheduler.LOG_TAG, "Alarm saved; exact alarm permission missing: $source")
            return AlarmScheduleStatus.NEEDS_PERMISSION
        }
        return try {
            val operation = PendingIntent.getBroadcast(
                context, REQUEST_CODE, reminderIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            // 与样本相同使用 setExact + RTC_WAKEUP；同一个 PendingIntent 替换旧闹钟。
            // 若关机期间错过时间，过去的触发时间会请求尽快投递，补发一次后恢复每日计划。
            alarmManager(context).setExact(AlarmManager.RTC_WAKEUP, plan.nextTriggerAtMillis, operation)
            Log.i(
                BackgroundTaskScheduler.LOG_TAG,
                "Exact alarm scheduled: source=$source, trigger=${plan.nextTriggerAtMillis}"
            )
            AlarmScheduleStatus.SCHEDULED
        } catch (exception: SecurityException) {
            // 权限可能在检查与注册之间被撤销，保留计划等待再次授权。
            Log.w(BackgroundTaskScheduler.LOG_TAG, "Exact alarm permission rejected: $source", exception)
            AlarmScheduleStatus.NEEDS_PERMISSION
        } catch (exception: RuntimeException) {
            Log.e(BackgroundTaskScheduler.LOG_TAG, "Alarm scheduling failed: $source", exception)
            AlarmScheduleStatus.ERROR
        }
    }

    private fun savePlan(context: Context, plan: DailyAlarmPlan): Boolean {
        val saved = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_HOUR, plan.hour)
            .putInt(KEY_MINUTE, plan.minute)
            .putLong(KEY_NEXT_TRIGGER, plan.nextTriggerAtMillis)
            .commit()
        if (!saved) Log.e(BackgroundTaskScheduler.LOG_TAG, "Alarm plan could not be saved")
        return saved
    }

    private fun reminderIntent(context: Context) =
        Intent(context, AlarmReminderReceiver::class.java).setAction(ACTION_REMINDER)

    private fun alarmManager(context: Context): AlarmManager =
        requireNotNull(context.getSystemService(AlarmManager::class.java))

    internal fun nextDailyTrigger(
        hour: Int,
        minute: Int,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val time = LocalTime.of(hour, minute)
        var next = now.toLocalDate().atTime(time).atZone(zone)
        if (!next.isAfter(now)) {
            next = now.toLocalDate().plusDays(1).atTime(time).atZone(zone)
        }
        return next.toInstant().toEpochMilli()
    }
}
