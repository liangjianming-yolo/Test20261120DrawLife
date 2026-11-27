package com.drawlife.backgroundtoolkit.fcm

import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import com.drawlife.backgroundtoolkit.R
import com.drawlife.backgroundtoolkit.background.BackgroundTaskScheduler
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

internal object FcmReminderPolicy : Application.ActivityLifecycleCallbacks {
    private val installWaitMillis = TimeUnit.MINUTES.toMillis(5)
    private val normalIntervalMillis = TimeUnit.MINUTES.toMillis(43)
    private const val NORMAL_DAILY_LIMIT = 3
    private val highMessages = listOf(
        R.string.drawlife_toolkit_fcm_water_title to R.string.drawlife_toolkit_fcm_water_body,
        R.string.drawlife_toolkit_fcm_heart_rate_title to R.string.drawlife_toolkit_fcm_heart_rate_body,
        R.string.drawlife_toolkit_fcm_daily_title to R.string.drawlife_toolkit_fcm_daily_body
    )

    // Activity 回调在主线程增减计数，FCM 回调在工作线程读取。前台服务不算可见页面。
    @Volatile private var startedActivities = 0

    override fun onActivityStarted(activity: Activity) { startedActivities++ }
    override fun onActivityStopped(activity: Activity) { startedActivities-- }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    fun showHigh(context: Context, launchActivity: Class<out Activity>, messageId: String?) {
        val (title, body) = highMessages.random()
        FcmNotification.show(context, launchActivity, messageId, context.getString(title), context.getString(body))
    }

    /** 同一进程连续收到消息时，串行检查和更新普通提醒的次数。高优先级提醒单独处理。 */
    @Synchronized
    fun checkNormal(context: Context, launchActivity: Class<out Activity>, messageId: String?) {
        if (startedActivities > 0 ||
            context.getSystemService(PowerManager::class.java)?.isInteractive != true ||
            context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked != false ||
            context.resources.configuration.orientation != Configuration.ORIENTATION_PORTRAIT
        ) {
            Log.i(BackgroundTaskScheduler.LOG_TAG, "FCM normal reminder skipped: requires background, unlocked portrait screen")
            return
        }

        val now = System.currentTimeMillis()
        @Suppress("DEPRECATION")
        val installedAt = context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        val prefs = context.getSharedPreferences("draw_life_fcm_reminders", Context.MODE_PRIVATE)
        val lastShown = prefs.getLong("last_shown", 0L)
        val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        val count = if (prefs.getLong("day", -1L) == today) prefs.getInt("count", 0) else 0
        if (now - installedAt < installWaitMillis ||
            (now >= lastShown && now - lastShown < normalIntervalMillis) ||
            count >= NORMAL_DAILY_LIMIT
        ) {
            Log.i(BackgroundTaskScheduler.LOG_TAG, "FCM normal reminder skipped: install wait, cooldown or daily limit")
            return
        }

        val posted = FcmNotification.show(
            context, launchActivity, messageId,
            context.getString(R.string.drawlife_toolkit_fcm_daily_title),
            context.getString(R.string.drawlife_toolkit_fcm_daily_body)
        )
        if (posted) {
            prefs.edit().putLong("last_shown", now).putLong("day", today).putInt("count", count + 1).apply()
        }
    }
}
