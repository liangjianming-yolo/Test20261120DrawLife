# Background Toolkit

`background-toolkit` 是可复用的 Android 库。它负责前台服务、每日闹钟、后台任务调度和 Firebase Cloud Messaging（FCM）回调转发。接入项目提供通知样式、文案、点击动作，以及推送展示规则。DrawLife 的测试通知和频控规则都在示例 `app` 中。

## 接入步骤

1. 将 `background-toolkit` 目录复制到目标项目根目录，省略生成的 `build/`。
2. 在 `settings.gradle.kts` 加入 `include(":background-toolkit")`。在宿主应用模块加入 `implementation(project(":background-toolkit"))`。
3. 核对构建环境。库当前使用 `compileSdk` 37、`minSdk` 26，示例项目使用 AGP 9.4.1 和内置 Kotlin。宿主使用 SDK 37 编译，且 `minSdk` 至少为 26。依赖来自 `google()` 和 `mavenCentral()`。
4. 在宿主 `Application.onCreate()` 中初始化。选用哪些功能，就传入对应的实现。 [示例应用配置](../app/src/main/java/com/test/test20261120drawlife/DrawLifeApplication.kt)

```kotlin
BackgroundToolkit.initialize(
    this,
    BackgroundToolkit.Config(
        foregroundNotification = AppForegroundNotification,
        dailyReminderNotification = AppDailyReminderNotification,
        fcmMessageHandler = AppFcmHandler,
    ),
)
```

上面的三个对象由宿主实现。配置里的对应字段默认为 `null`。库清单中的权限和组件会合并进宿主清单，按需检查合并结果。

从旧版升级时，将 `enableForegroundToolbar`、`enableDailyReminder` 和 `enableFcmNotifications` 分别换为对应的宿主实现。`launchActivity` 和 `toolbarEntry()` 的导航逻辑移到宿主创建和读取 `PendingIntent` 的地方。旧版 `enableFcmPriorityHandling` 和 `enableFcmForegroundServiceProbe` 的处理逻辑可在宿主的 FCM 处理器中实现。

## 前台服务与通知

实现 `com.drawlife.backgroundtoolkit.ForegroundNotificationProvider`。提供稳定的 `notificationId`、`channelId`，并在 `createNotification(context)` 创建渠道和 `Notification`。图标、布局、文案和点击用的 `PendingIntent` 都由宿主设置。工具库拿到通知后调用 `startForeground()`。 [Demo 工具栏](../app/src/main/java/com/test/test20261120drawlife/background/DemoToolbarNotification.kt)

在页面回到前台时调用 `BackgroundToolkit.refreshToolbar(this)`。Android 13 及以上由宿主申请通知权限。进程初始化会注册广播接收器。页面刷新、系统允许的广播和宿主显式选择的高优先级推送可以请求服务。

库中的前台服务类型是 `dataSync`。接入时应有对应的用户可感知数据同步任务。只需在通知栏提供入口时，可直接发布普通通知。Android 15 及以上对 `dataSync` 服务有开机启动限制和后台运行时长限制。 [Android 前台服务类型](https://developer.android.com/develop/background-work/services/fgs/service-types#data-sync) · [Android 15 前台服务限制](https://developer.android.com/about/versions/15/behavior-changes-15#fgs-changes)

## 每日提醒

实现 `com.drawlife.backgroundtoolkit.DailyReminderNotificationProvider`。提供 `channelId`，并在 `createNotification(context, key, title, message)` 中创建渠道和通知。闹钟到点时，工具库会传入保存的参数。返回 `null` 可跳过本次展示。工具库按 key 发布和撤下通知。点击后需要识别提醒时，可将 `BackgroundToolkit.EXTRA_DAILY_REMINDER_KEY` 写入宿主自己的 `PendingIntent`。 [Demo 提醒通知](../app/src/main/java/com/test/test20261120drawlife/background/DemoDailyReminderNotification.kt)

每个 key 对应每天的一个时刻。用稳定的非空白字符串标识提醒，相同 key 会更新原计划。不同 key 即使设在同一分钟，也各自触发。

```kotlin
BackgroundToolkit.setDailyReminder(
    this,
    key = "daily-summary",
    hour = 9,
    minute = 0,
    title = getString(R.string.summary_reminder_title),
    message = getString(R.string.summary_reminder_body),
)
val plan = BackgroundToolkit.readDailyReminder(this, key = "daily-summary")
BackgroundToolkit.cancelDailyReminder(this, key = "daily-summary")
```

这里的字符串资源由宿主定义。旧版无 key 的计划在 `readDailyReminders()` 中对应 `null`，旧计划的标题和正文也可能为空。宿主可在通知实现中补上默认文案。库以 `setExact()` 注册每日闹钟。缺少精确闹钟权限时仍会保存计划，授权后可恢复注册。开机、应用更新、系统时间或时区变化也会触发恢复。

所有每日提醒都有工具库专用的通知标签，宿主的前台服务可以沿用原通知 ID。升级后，再次展示或取消某个提醒时，工具库会撤下能确认属于它的旧通知。如果旧通知指向的 Activity 已移除，可在通知栏手动划掉这条旧提醒。

## FCM 消息服务

宿主如需接收远程推送，应配置与 `applicationId` 匹配的 `google-services.json` 和 Google Services Gradle 插件。接入工具库回调时，传入 `com.drawlife.backgroundtoolkit.fcm.FcmMessageHandler`。它接收 `Context`、原始 `RemoteMessage` 和根据实际送达优先级生成的 `FcmDeliveryPriority`，取值为 `HIGH`、`NORMAL` 或 `UNKNOWN`。宿主据此决定是否展示、展示哪条通知，以及文案和频率。 [Demo 推送处理](../app/src/main/java/com/test/test20261120drawlife/fcm/DemoFcmMessageHandler.kt)

宿主已有 `FirebaseMessagingService` 时，保留现有服务声明。可以由它直接处理推送，也可以调用 `ToolkitFcm.onMessageReceived(this, message)`，将消息交给配置的处理器。每条消息选择一条处理路径。

宿主也可以在 `<application>` 中注册工具库提供的转发服务。它读取同一份 `fcmMessageHandler` 配置。

```xml
<service
    android:name="com.drawlife.backgroundtoolkit.fcm.DrawLifeMessagingService"
    android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>
```

宿主的过滤条件只对进入 `onMessageReceived` 的消息生效。App 在后台时，带 `notification` 的标准消息通常由 Firebase SDK 直接展示。如果一类推送必须先经过业务过滤，发送端使用纯数据消息，并由宿主决定是否发布通知。 [Firebase 消息接收说明](https://firebase.google.com/docs/cloud-messaging/android/receive-messages)

Firebase SDK 自动展示的通知可使用宿主自己的默认图标和渠道。在 `<application>` 中添加下面的配置，并由宿主在初始化时创建同 ID 的通知渠道。

```xml
<meta-data
    android:name="com.google.firebase.messaging.default_notification_icon"
    android:resource="@drawable/ic_notification" />
<meta-data
    android:name="com.google.firebase.messaging.default_notification_channel_id"
    android:value="@string/fcm_channel_id" />
```

宿主确需通过高优先级推送触发服务时，可在现有服务或配置的处理器中调用 `ToolkitFcm.requestForegroundToolbar(context, message)`。这个入口需要宿主提供 `foregroundNotification`，且消息实际送达为 `HIGH`。采用现有服务的宿主可以直接调用，只配置前台服务通知。最终能否启动由 Android 决定。 [Firebase 消息优先级](https://firebase.google.com/docs/cloud-messaging/android-message-priority)

需要工具库保存注册 Token 时，可调用 `ToolkitFcm.requestRegistrationToken(context)` 获取当前值。如果使用自有 `FirebaseMessagingService`，在它的 `onNewToken(token)` 中调用 `ToolkitFcm.onNewToken(this, token)`，以后每次刷新都会更新应用私有目录中的 `noBackupFilesDir/drawlife-fcm-registration.json`。Token 缓存可独立于 `fcmMessageHandler` 使用。工具库自带的消息服务已转发这个回调。

## 进程内 Timer 心跳

在 `BackgroundToolkit.Config` 传入 `heartbeat = HeartbeatConfig(...)` 即可启用。进程启动立即检查，以后每分钟检查。每次生成心跳，把发生时间加上 0～30 分钟的随机偏移写入 SharedPreferences，作为下一轮的时间基准。从这个基准经过 720 分钟后才再次到期。进程重启沿用保存的基准，过期只生成一条。Room 管理原有 SQLite 文件中的 `events` 表，只存待上传的 ALIVE 事件。

配置的 `upload(installationId, events)` 在心跳线程执行，整批接收成功时返回 `true`。每批最多 100 条，失败的记录在产生新心跳、进程重启或一小时后可再次尝试，保留 3 天。服务端可用安装标识和事件 ID 去重。省略回调时只记录本地事件。示例 App 的正式版从 `heartbeat_upload_url` 读取 HTTPS 地址，Debug 版使用本地接收器和 2 分钟间隔。[Debug 测试步骤](../tools/README.md#本地接收-timer-心跳)

## 后台任务

- `jobId` 启用 JobScheduler，选择宿主未占用的编号。每轮任务结束后重新提交，最短等待 30 秒。若同时用 WorkManager，将该编号留在 WorkManager 的 Job ID 范围之外。
- `enablePeriodicWork = true` 启用一个需要联网的 15 分钟周期任务。`periodicWorkName` 指定唯一任务名。关闭已启用的周期任务时，可用 `WorkManager.cancelUniqueWork` 按名称取消。

库中的 Job 和 Worker 用日志验证系统调度，实际执行时机由系统决定。宿主按自身任务需求启用。

已有项目升级时，沿用原来的 `jobId` 和 `periodicWorkName`。工具库能识别旧版库的 JobService 和 Worker 类名。如果 Job 最早由宿主自己的服务注册，将该服务的完整类名填入 `legacyJobServiceName`。同一编号上的其他服务会保留。

## PulseSense 接入对应关系

| PulseSense 现有实现 | 接入方式 |
| --- | --- |
| `Notifications.showResidentNotification()` | 当前通过 `notify()` 发布常驻通知。需要接入 `dataSync` 服务时，从现有方法抽出创建通知的部分，用作 `ForegroundNotificationProvider`。沿用原渠道、ID `0x9001`、布局和点击入口。首页和通知授权成功后的入口改为刷新服务通知，同一张通知交由服务发布。 |
| `CloudFirebaseMessagingService`、`FcmNotificationPolicy` | 继续使用原有服务和展示规则。若已有业务回调，保留原调用链即可。确需联动前台服务时，在业务决定展示推送后，对实际送达为高优先级的消息调用 `requestForegroundToolbar()`。 |
| `ReminderWork`、`ReminderManager` | 现有健康提醒支持按星期选择。获得精确闹钟权限时会使用 `setExactAndAllowWhileIdle()`，可在 Doze 下触发。保留这套调度，库的每日闹钟适合固定时分、每天重复的场景。 |

PulseSense 当前配置为 AGP 8.13.2、`compileSdk` 36 和 `minSdk` 28。复制模块前先对齐 SDK 与构建插件配置，再检查依赖版本。PulseSense 的 `minSdk` 已满足库要求。
