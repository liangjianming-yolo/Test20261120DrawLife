# Background Toolkit

这个 Android Library 封装了通知栏工具栏、前台服务、每日提醒、JobScheduler、WorkManager 和 FCM 消息处理。模块自带资源、后台组件和权限声明，入口 Activity 由宿主指定。示例 App 展示了完整接法。

## 复制到其他项目

1. 将 `background-toolkit` 目录复制到目标项目根目录。可以省略生成的 `build/`。
2. 在目标项目的 `settings.gradle.kts` 加入 `include(":background-toolkit")`，在应用模块的 `dependencies` 中加入 `implementation(project(":background-toolkit"))`。
3. 使用当前模块对应的构建环境。示例项目使用 AGP 9.4.1 和内置 Kotlin，宿主需要 `compileSdk` 37 和 `minSdk` 26 或以上，依赖从 `google()` 和 `mavenCentral()` 获取。若 Gradle 提示找不到 `com.android.library`，在宿主根项目的 `plugins` 中用宿主的 AGP 版本声明该插件并加上 `apply false`。
4. 在宿主 `Application.onCreate()` 中初始化。已有自己的 `Application` 时，直接把下面的调用加进去。后台组件也通过这个入口读取配置。

```kotlin
import com.drawlife.backgroundtoolkit.BackgroundToolkit

// Application.onCreate() 内，super.onCreate() 之后
BackgroundToolkit.initialize(
    this,
    BackgroundToolkit.Config(
        launchActivity = MainActivity::class.java,
        enableForegroundToolbar = true,
        enableDailyReminder = true
    )
)
```

## 按需启用

| 功能 | 接入 |
| --- | --- |
| 前台服务与工具栏 | 设置 `enableForegroundToolbar = true`。Activity 回到前台时调用 `BackgroundToolkit.refreshToolbar(this)`，并通过 `BackgroundToolkit.toolbarEntry(intent)` 读取通知按钮入口。Android 13 及以上由宿主申请通知权限。 |
| 每日提醒 | 设置 `enableDailyReminder = true`。宿主为每条提醒提供稳定的非空白字符串 key，相同 key 更新原计划。Android 12 及以上用 `canScheduleExactAlarms` 检查权限，授权后可用 `restoreDailyReminders` 补注册。 |
| JobScheduler | 配置 `jobId`，选用宿主未占用的 Job ID。每次执行后重新提交，最短等待 30 秒。与 WorkManager 同时使用时，把该 ID 留在 WorkManager 的 Job ID 范围之外。仓库中的示例 App 用 `Configuration.Provider` 保留了 `100`。 |
| WorkManager | 设置 `enablePeriodicWork = true`。模块注册唯一的 15 分钟联网周期任务。默认名称是 `com.drawlife.backgroundtoolkit.periodic`，可通过 `periodicWorkName` 指定。关闭已启用过的周期任务时，宿主可用 `WorkManager.cancelUniqueWork` 按名称清理。 |
| FCM | 在宿主配置 Firebase，打开 `enableFcmNotifications`，接入下面的消息服务。普通纯数据消息记录接收日志，通知消息使用单独的通知渠道。 |

配置开关控制运行时行为。库清单中的权限和静态接收器会合并到宿主清单，只使用部分功能的宿主可以通过 Manifest 合并规则移除闲置的声明。Job 和 Worker 是调度示例，实际触发时间、前台服务启动和通知展示受系统限制及用户权限影响。

## 多条每日提醒

下面的两个 key 由调用方定义。即使设置相同的时间，两条通知也可以同时保留。

```kotlin
BackgroundToolkit.setDailyReminder(this, key = "water", hour = 9, minute = 0, title = "喝水提醒")
BackgroundToolkit.setDailyReminder(this, key = "heart-rate", hour = 9, minute = 0, title = "心率提醒")

val water = BackgroundToolkit.readDailyReminder(this, key = "water")
val allPlans = BackgroundToolkit.readDailyReminders(this)
BackgroundToolkit.cancelDailyReminder(this, key = "water") // 只取消喝水提醒
```

每个 key 对应每天一个时间。同类提醒有多个时间时，可分别使用各自的记录 ID。可选参数 `title` 和 `message` 指定通知文案。用户点击通知后，宿主 Activity 可以用 `intent.getStringExtra(BackgroundToolkit.EXTRA_DAILY_REMINDER_KEY)` 读取 key。

旧版无 key 的调用继续操作原来的每日提醒，在 `readDailyReminders()` 返回的 Map 中对应 `null`。开机、重新授权及时间或时区变化时，工具库会恢复全部已保存的计划。

## FCM 消息服务

在宿主应用模块放置与 `applicationId` 匹配的 `google-services.json`，并应用 Google Services Gradle 插件。若宿主已有自己的 `FirebaseMessagingService`，在其 `onMessageReceived` 中调用 `ToolkitFcm.onMessageReceived(this, message)`。也可以在宿主清单的 `<application>` 中声明模块提供的服务和默认通知资源。

```xml
<service
    android:name="com.drawlife.backgroundtoolkit.fcm.DrawLifeMessagingService"
    android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>
<meta-data
    android:name="com.google.firebase.messaging.default_notification_icon"
    android:resource="@drawable/drawlife_toolkit_ic_notification" />
<meta-data
    android:name="com.google.firebase.messaging.default_notification_channel_id"
    android:value="@string/drawlife_toolkit_fcm_channel_id" />
```

`ToolkitFcm` 位于 `com.drawlife.backgroundtoolkit.fcm`。开启 FCM 后，初始化会获取注册 Token 并存入应用私有的 `noBackupFilesDir/drawlife-fcm-registration.json`。使用自定义消息服务时，在 `onNewToken(token)` 中调用 `ToolkitFcm.onNewToken(this, token)` 以保存 Firebase 刷新的值。测试时可以调用 `ToolkitFcm.requestRegistrationToken(this)`。

示例 App 开启了 `enableFcmPriorityHandling`。进入 `onMessageReceived` 的消息按 `RemoteMessage.priority` 分流，发送端请求的等级只用于日志。实际为 high 时，启用工具栏的宿主会尝试启动前台服务，同时从喝水、心率、每日提醒中随机选一条发布。普通或未知优先级只检查每日提醒，要求 App 在后台，手机亮屏、解锁且竖屏。首次展示要等安装满 5 分钟，此后距上次发布至少 43 分钟，每个本地日期最多发布 3 条。通知发布还受权限和渠道设置影响。后台由 Firebase SDK 直接展示的标准通知沿用消息自带的内容。

启用这项分流后，进程初始化推迟工具栏启动，宿主在页面回到前台时调用 `refreshToolbar()`。这样普通推送唤起进程时也会遵循普通分支。未开启分流的宿主仍可用 `enableFcmForegroundServiceProbe` 单独测试 Debug 高优先级消息。
