package com.drawlife.backgroundtoolkit

import android.app.Notification
import android.content.Context

/** 宿主提供前台服务通知的标识、渠道和内容。 */
interface ForegroundNotificationProvider {
    val notificationId: Int
    val channelId: String

    /** 创建通知前，确保 [channelId] 对应的通知渠道已注册。 */
    fun createNotification(context: Context): Notification
}
