package com.drawlife.backgroundtoolkit

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import com.drawlife.backgroundtoolkit.background.DrawLifeForegroundService
import com.drawlife.backgroundtoolkit.background.ForegroundServiceReceiver
import com.drawlife.backgroundtoolkit.background.alarm.AlarmReminderScheduler
import com.drawlife.backgroundtoolkit.background.alarm.AlarmScheduleStatus
import com.drawlife.backgroundtoolkit.background.alarm.DailyAlarmPlan
import com.drawlife.backgroundtoolkit.background.work.BackgroundTaskScheduler
import com.drawlife.backgroundtoolkit.fcm.FcmMessageHandler
import com.drawlife.backgroundtoolkit.fcm.ToolkitFcm
import com.drawlife.backgroundtoolkit.heartbeat.HeartbeatConfig
import com.drawlife.backgroundtoolkit.heartbeat.ProcessHeartbeat

object BackgroundToolkit {
    /** 宿主可用这个 extra 在提醒通知的点击 Intent 中传递提醒 key。 */
    const val EXTRA_DAILY_REMINDER_KEY = "com.drawlife.backgroundtoolkit.extra.DAILY_REMINDER_KEY"

    /** 提供相应的宿主实现即可启用该功能。后台组件在进程重建时也会读取这份配置。 */
    data class Config(
        val foregroundNotification: ForegroundNotificationProvider? = null,
        val dailyReminderNotification: DailyReminderNotificationProvider? = null,
        val fcmMessageHandler: FcmMessageHandler? = null,
        val jobId: Int? = null,
        val enablePeriodicWork: Boolean = false,
        val periodicWorkName: String = "com.drawlife.backgroundtoolkit.periodic",
        val legacyJobServiceName: String? = null,
        val heartbeat: HeartbeatConfig? = null
    )

    @Volatile
    internal var configuration: Config? = null
        private set

    @Synchronized
    fun initialize(context: Context, config: Config) {
        check(configuration == null || configuration == config) {
            "BackgroundToolkit was already initialized with a different configuration"
        }
        if (configuration != null) return

        val appContext = context.applicationContext
        configuration = config
        config.heartbeat?.let { ProcessHeartbeat.start(appContext, it) }
        if (config.fcmMessageHandler != null && ToolkitFcm.isConfigured(appContext)) {
            ToolkitFcm.requestRegistrationToken(appContext)
        }
        if (config.foregroundNotification != null) ForegroundServiceReceiver.registerForProcess(appContext)
        restoreDailyReminders(appContext, source = "application")
        if (config.jobId != null) BackgroundTaskScheduler.scheduleJob(appContext)
        if (config.enablePeriodicWork) BackgroundTaskScheduler.schedulePeriodicWork(appContext)
    }

    fun refreshToolbar(context: Context, source: String = "activity_resume") {
        DrawLifeForegroundService.start(context, source = source, refreshNotification = true)
    }

    fun toolbarNotificationsEnabled(context: Context): Boolean =
        notificationsEnabled(context, configuration?.foregroundNotification?.channelId)

    fun reminderNotificationsEnabled(context: Context): Boolean =
        notificationsEnabled(context, configuration?.dailyReminderNotification?.channelId)

    private fun notificationsEnabled(context: Context, channelId: String?): Boolean {
        if (channelId == null) return false
        val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        return manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(channelId)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun canScheduleExactAlarms(context: Context): Boolean =
        AlarmReminderScheduler.canScheduleExactAlarms(context)

    @JvmOverloads
    fun readDailyReminder(context: Context, key: String? = null): DailyAlarmPlan? =
        AlarmReminderScheduler.readPlan(context, key)

    /** 列出宿主设置的所有每日提醒。旧接口创建的提醒对应 null。 */
    fun readDailyReminders(context: Context): Map<String?, DailyAlarmPlan> =
        AlarmReminderScheduler.readPlans(context)

    fun setDailyReminder(context: Context, hour: Int, minute: Int): AlarmScheduleStatus =
        setDailyReminder(context, null, hour, minute)

    /** 相同 key 更新原提醒，不同 key 可以在同一分钟分别触发。 */
    @JvmOverloads
    fun setDailyReminder(
        context: Context, key: String?, hour: Int, minute: Int,
        title: String? = null, message: String? = null
    ): AlarmScheduleStatus {
        require(key == null || key.isNotBlank()) { "Expected a non-blank reminder key" }
        require(hour in 0..23 && minute in 0..59) { "Expected a valid local time" }
        if (configuration?.dailyReminderNotification == null) return AlarmScheduleStatus.DISABLED
        return AlarmReminderScheduler.setDailyReminder(context, key, hour, minute, title, message)
    }

    /** 保留旧接口的返回值：恢复所有计划后，返回无 key 提醒的状态。 */
    fun restoreDailyReminder(context: Context, source: String = "activity_resume"): AlarmScheduleStatus =
        restoreDailyReminders(context, source)[null] ?: AlarmScheduleStatus.DISABLED

    fun restoreDailyReminders(context: Context, source: String = "activity_resume"): Map<String?, AlarmScheduleStatus> {
        if (configuration?.dailyReminderNotification == null) return emptyMap()
        return AlarmReminderScheduler.restore(context, source = source)
    }

    @JvmOverloads
    fun cancelDailyReminder(context: Context, key: String? = null): Boolean =
        AlarmReminderScheduler.cancel(context, key)

    fun listenForDailyReminderChanges(context: Context, onChange: () -> Unit): AutoCloseable {
        val appContext = context.applicationContext
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
        AlarmReminderScheduler.registerPlanListener(appContext, listener)
        return AutoCloseable { AlarmReminderScheduler.unregisterPlanListener(appContext, listener) }
    }
}
