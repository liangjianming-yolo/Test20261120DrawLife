package com.drawlife.backgroundtoolkit.fcm

import android.content.Context
import com.google.firebase.messaging.RemoteMessage

/** Firebase 实际投递的优先级，可能与发送端请求的优先级不同。 */
enum class FcmDeliveryPriority { HIGH, NORMAL, UNKNOWN }

/** 宿主在 FCM 回调线程处理消息，并决定通知内容、展示条件和点击行为。 */
fun interface FcmMessageHandler {
    fun onMessageReceived(context: Context, message: RemoteMessage, priority: FcmDeliveryPriority)
}
