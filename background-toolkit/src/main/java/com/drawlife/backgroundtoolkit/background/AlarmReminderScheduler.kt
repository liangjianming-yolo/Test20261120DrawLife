package com.drawlife.backgroundtoolkit.background

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.util.Log
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** hour 和 minute 保存用户选择的本地时间，nextTriggerAtMillis 保存下一次到期的时间戳（毫秒）。 */
data class DailyAlarmPlan(val hour: Int, val minute: Int, val nextTriggerAtMillis: Long)

/** 向宿主报告提醒状态，以便界面区分未启用、已设置、待授权和设置失败。 */
enum class AlarmScheduleStatus { DISABLED, SCHEDULED, NEEDS_PERMISSION, ERROR }

/** 每个宿主提供的 key 对应一条每日计划。null 沿用旧版单条提醒的存储和广播标识。 */
internal object AlarmReminderScheduler {
    private const val PREFERENCES_NAME = "draw_life_daily_alarm"
    private const val REQUEST_CODE = 34954
    private const val KEY_HOUR = "hour"
    private const val KEY_MINUTE = "minute"

    //保存和读取下一次每日提醒的触发时间，值为 Long 类型的毫秒时间戳
    private const val KEY_NEXT_TRIGGER = "next_trigger_at"
    private const val KEY_TITLE = "title"
    private const val KEY_MESSAGE = "message"
    const val REMINDER_SCHEME = "drawlife-reminder"

    // 只记本进程成功登记的时间。新进程首次恢复时，仍需用已保存的计划向系统登记。
    private val registeredTriggers = mutableMapOf<String?, Long>()

    private fun field(name: String, key: String?): String = if (key == null) name else "$name:${Uri.encode(key)}"

    /** 进程重建后从本地恢复计划。记录缺失或时间无效时返回 null。 */
    @Synchronized
    fun readPlan(context: Context, key: String?): DailyAlarmPlan? {
        val preferences = preferences(context)
        val hour = preferences.getInt(field(KEY_HOUR, key), -1)
        val minute = preferences.getInt(field(KEY_MINUTE, key), -1)
        val nextTrigger = preferences.getLong(field(KEY_NEXT_TRIGGER, key), 0L)
        if (hour !in 0..23 || minute !in 0..59 || nextTrigger <= 0L) return null
        return DailyAlarmPlan(hour, minute, nextTrigger)
    }

    @Synchronized
    fun readPlans(context: Context): Map<String?, DailyAlarmPlan> = buildMap {
        // 从已经提交的到期时间字段枚举计划，避免维护第二份可能不同步的 key 列表。
        val prefix = "$KEY_NEXT_TRIGGER:"
        for (name in preferences(context).all.keys) {
            val key = when {
                name == KEY_NEXT_TRIGGER -> null
                name.startsWith(prefix) -> Uri.decode(name.removePrefix(prefix))
                else -> continue
            }
            readPlan(context, key)?.let { put(key, it) }
        }
    }

    fun registerPlanListener(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences(context).registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterPlanListener(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences(context).unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager(context).canScheduleExactAlarms()
    }

    @Synchronized
    fun setDailyReminder(
        context: Context, key: String?, hour: Int, minute: Int, title: String?, message: String?
    ): AlarmScheduleStatus {
        require(hour in 0..23 && minute in 0..59)
        val plan = DailyAlarmPlan(hour, minute, nextDailyTrigger(hour, minute))
        // 先记住用户选择的时间，即使当前缺少精确闹钟权限，授权后也能恢复。
        val saved = preferences(context).edit()
            .putInt(field(KEY_HOUR, key), hour)
            .putInt(field(KEY_MINUTE, key), minute)
            .putLong(field(KEY_NEXT_TRIGGER, key), plan.nextTriggerAtMillis)
            .putString(field(KEY_TITLE, key), title)
            .putString(field(KEY_MESSAGE, key), message)
            .commit()
        if (!saved) return AlarmScheduleStatus.ERROR
        return schedule(context, key, plan, source = "user")
    }

    @Synchronized
    fun restore(
        context: Context,
        source: String,
        timeChanged: Boolean = false,
        forceReschedule: Boolean = false
    ): Map<String?, AlarmScheduleStatus> {
        if (forceReschedule) registeredTriggers.clear()
        return readPlans(context).mapValues { (key, plan) ->
            // 普通冷启动保留原到期时间，让已到点的广播补发。只有时间或时区变化才重算。
            val next = if (timeChanged) {
                plan.copy(nextTriggerAtMillis = nextDailyTrigger(plan.hour, plan.minute))
            } else plan
            if (next != plan && !saveTrigger(context, key, next.nextTriggerAtMillis)) {
                AlarmScheduleStatus.ERROR
            } else {
                schedule(context, key, next, source)
            }
        }
    }

    @Synchronized
    fun cancel(context: Context, key: String?): Boolean {
        // 先清除本地计划，这样已经在投递途中的旧广播也会被忽略。
        val editor = preferences(context).edit()
        for (name in listOf(KEY_HOUR, KEY_MINUTE, KEY_NEXT_TRIGGER, KEY_TITLE, KEY_MESSAGE)) {
            editor.remove(field(name, key))
        }
        if (!editor.commit()) {
            Log.e(BackgroundTaskScheduler.LOG_TAG, "Alarm plan cancellation could not be saved")
            return false
        }
        registeredTriggers.remove(key)
        val operation = PendingIntent.getBroadcast(
            context, REQUEST_CODE, reminderIntent(context, key),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (operation != null) {
            alarmManager(context).cancel(operation)
            operation.cancel()
        }
        ReminderNotification.cancel(context, key)
        Log.i(BackgroundTaskScheduler.LOG_TAG, "Daily alarm cancelled")
        return true
    }

    @Synchronized
    fun onAlarmReceived(context: Context, key: String?) {
        val plan = readPlan(context, key) ?: return
        val now = System.currentTimeMillis()
        if (now < plan.nextTriggerAtMillis) {
            // 忽略取消、改时或重复投递后残留的旧广播。
            Log.i(BackgroundTaskScheduler.LOG_TAG, "Ignored stale alarm broadcast")
            return
        }

        // 本轮已经投递，即使随后保存下一轮失败，恢复时也应能重新登记。
        registeredTriggers.remove(key)
        val nextPlan = plan.copy(nextTriggerAtMillis = nextDailyTrigger(plan.hour, plan.minute, now))
        // 先推进持久化状态，再发通知，避免冷启动恢复与重复投递多次提醒。
        if (!saveTrigger(context, key, nextPlan.nextTriggerAtMillis)) return
        schedule(context, key, nextPlan, source = "alarm_received")
        Log.i(
            BackgroundTaskScheduler.LOG_TAG,
            "Alarm received: scheduled=${plan.nextTriggerAtMillis}, actual=$now"
        )
        val prefs = preferences(context)
        ReminderNotification.show(
            context, key,
            title = prefs.getString(field(KEY_TITLE, key), null),
            message = prefs.getString(field(KEY_MESSAGE, key), null)
        )
    }

    private fun schedule(context: Context, key: String?, plan: DailyAlarmPlan, source: String): AlarmScheduleStatus {
        if (!canScheduleExactAlarms(context)) {
            registeredTriggers.remove(key)
            Log.w(BackgroundTaskScheduler.LOG_TAG, "Alarm saved; exact alarm permission missing: $source")
            return AlarmScheduleStatus.NEEDS_PERMISSION
        }
        if (registeredTriggers[key] == plan.nextTriggerAtMillis) {
            return AlarmScheduleStatus.SCHEDULED
        }
        // 只有 setExact 成功后才记录时间，失败时留给下次恢复重试。
        registeredTriggers.remove(key)
        return try {
            val operation = PendingIntent.getBroadcast(
                context, REQUEST_CODE, reminderIntent(context, key),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            // 与样本相同使用 setExact + RTC_WAKEUP；同一个 PendingIntent 替换旧闹钟。
            // 若关机期间错过时间，过去的触发时间会请求尽快投递，补发一次后恢复每日计划。
            alarmManager(context).setExact(AlarmManager.RTC_WAKEUP, plan.nextTriggerAtMillis, operation)
            registeredTriggers[key] = plan.nextTriggerAtMillis
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

    private fun saveTrigger(context: Context, key: String?, trigger: Long): Boolean =
        preferences(context).edit().putLong(field(KEY_NEXT_TRIGGER, key), trigger).commit()

    private fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun reminderAction(context: Context): String = "${context.packageName}.action.DAILY_REMINDER"

    // Intent.data 参与 PendingIntent 身份匹配，同一时间的不同提醒也能独立登记和取消。
    fun reminderData(key: String?): Uri? = key?.let { Uri.fromParts(REMINDER_SCHEME, it, null) }

    private fun reminderIntent(context: Context, key: String?) =
        Intent(context, AlarmReminderReceiver::class.java)
            .setAction(reminderAction(context))
            .setData(reminderData(key))

    private fun alarmManager(context: Context): AlarmManager =
        requireNotNull(context.getSystemService(AlarmManager::class.java))

    /** 按当地日历计算下次提醒，今天的设定时间已到或已过就取明天的同一时间。 */
    internal fun nextDailyTrigger(
        hour: Int,
        minute: Int,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault() //时区
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
