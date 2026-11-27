package com.drawlife.backgroundtoolkit

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.drawlife.backgroundtoolkit.background.AlarmReminderScheduler
import com.drawlife.backgroundtoolkit.background.AlarmScheduleStatus
import com.drawlife.backgroundtoolkit.background.BackgroundTaskScheduler
import com.drawlife.backgroundtoolkit.background.DailyAlarmPlan
import com.drawlife.backgroundtoolkit.background.DrawLifeForegroundService
import com.drawlife.backgroundtoolkit.background.ForegroundServiceReceiver
import com.drawlife.backgroundtoolkit.background.ReminderNotification
import com.drawlife.backgroundtoolkit.background.ToolbarNotification
import com.drawlife.backgroundtoolkit.fcm.FcmReminderPolicy
import com.drawlife.backgroundtoolkit.fcm.ToolkitFcm

object BackgroundToolkit {
    /** 点击每日提醒通知后，宿主 Activity 可以从 Intent 读取这条提醒的 key。 */
    const val EXTRA_DAILY_REMINDER_KEY = "com.drawlife.backgroundtoolkit.extra.DAILY_REMINDER_KEY"

    data class Config(
        val launchActivity: Class<out Activity>,
        val enableForegroundToolbar: Boolean = false,
        val enableDailyReminder: Boolean = false,
        val jobId: Int? = null,
        val enablePeriodicWork: Boolean = false,
        val periodicWorkName: String = "com.drawlife.backgroundtoolkit.periodic",
        val enableFcmNotifications: Boolean = false,
        val enableFcmForegroundServiceProbe: Boolean = false,
        val legacyJobServiceName: String? = null,
        val enableFcmPriorityHandling: Boolean = false
    )

    enum class ToolbarEntry { TRACKER, INFO, SETTINGS }

    @Volatile
    internal var configuration: Config? = null
        private set

    @Synchronized
    fun initialize(context: Context, config: Config) {
        ToolkitFcm.printToken(context)
        check(configuration == null || configuration == config) {
            "BackgroundToolkit was already initialized with a different configuration"
        }
        if (configuration != null) return

        val appContext = context.applicationContext
        configuration = config
        val handleFcmPriority = config.enableFcmNotifications && config.enableFcmPriorityHandling
        if (handleFcmPriority) {
            (appContext as Application).registerActivityLifecycleCallbacks(FcmReminderPolicy)
        }
        if (config.enableFcmNotifications) {
            ToolkitFcm.createChannel(appContext)
            if (ToolkitFcm.isConfigured(appContext)) ToolkitFcm.requestRegistrationToken(appContext)
        }
        if (config.enableForegroundToolbar) ForegroundServiceReceiver.registerForProcess(appContext)
        restoreDailyReminders(appContext, source = "application")
        // 推送冷启动也会先创建 Application。启用优先级分流时，等页面、广播或高优先级消息请求服务。
        if (config.enableForegroundToolbar && !handleFcmPriority) {
            DrawLifeForegroundService.start(appContext, source = "application")
        }
        if (config.jobId != null) BackgroundTaskScheduler.scheduleJob(appContext)
        if (config.enablePeriodicWork) BackgroundTaskScheduler.schedulePeriodicWork(appContext)
    }

    fun refreshToolbar(context: Context, source: String = "activity_resume") {
        if (configuration?.enableForegroundToolbar == true) {
            DrawLifeForegroundService.start(context, source = source, refreshNotification = true)
        }
    }

    fun toolbarEntry(intent: Intent): ToolbarEntry? =
        when (intent.getStringExtra(ToolbarNotification.EXTRA_ENTRY)) {
            ToolbarNotification.ENTRY_TRACKER -> ToolbarEntry.TRACKER
            ToolbarNotification.ENTRY_INFO -> ToolbarEntry.INFO
            ToolbarNotification.ENTRY_SETTINGS -> ToolbarEntry.SETTINGS
            else -> null
        }

    fun toolbarNotificationsEnabled(context: Context): Boolean =
        ToolbarNotification.notificationsEnabled(context)

    fun reminderNotificationsEnabled(context: Context): Boolean =
        ReminderNotification.notificationsEnabled(context)

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
        if (configuration?.enableDailyReminder != true) return AlarmScheduleStatus.DISABLED
        return AlarmReminderScheduler.setDailyReminder(context, key, hour, minute, title, message)
    }

    /** 保留旧接口的返回值：恢复所有计划后，返回无 key 提醒的状态。 */
    fun restoreDailyReminder(context: Context, source: String = "activity_resume"): AlarmScheduleStatus =
        restoreDailyReminders(context, source)[null] ?: AlarmScheduleStatus.DISABLED

    fun restoreDailyReminders(context: Context, source: String = "activity_resume"): Map<String?, AlarmScheduleStatus> {
        if (configuration?.enableDailyReminder != true) return emptyMap()
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
