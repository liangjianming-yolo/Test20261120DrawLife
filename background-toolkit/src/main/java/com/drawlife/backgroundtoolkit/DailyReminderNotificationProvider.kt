package com.drawlife.backgroundtoolkit

import android.app.Notification
import android.content.Context

/** 宿主创建每日提醒通知并决定内容和点击入口。 */
interface DailyReminderNotificationProvider {
    val channelId: String

    /**
     * 构建通知前注册 [channelId] 对应的渠道。旧版无 key 提醒的 [key] 为 null。
     * 返回 null 可跳过本次通知，已安排的下次提醒继续生效。
     */
    fun createNotification(
        context: Context,
        key: String?,
        title: String?,
        message: String?
    ): Notification?
}
