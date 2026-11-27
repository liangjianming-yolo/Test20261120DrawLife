# 本地 FCM 测试发送器

发送器通过 [FCM HTTP v1](https://firebase.google.com/docs/cloud-messaging/send/v1-api) 模拟服务端。它从 `app/google-services.json` 读取 Firebase 项目 ID，每次只向一个 DrawLife Android 应用实例发送。

当前项目为 `scanbuddy-2661`。使用 `python3 -m pip install -r tools/requirements.txt` 安装发送器依赖。脚本默认从 `~/.config/drawlife-fcm/scanbuddy-2661/serviceAccountKey.json` 读取服务账号密钥，向 Google 换取具有 `firebase.messaging` 范围的短期令牌。服务账号需要目标项目的 `cloudmessaging.messages.create` 权限。

可以用 `GOOGLE_APPLICATION_CREDENTIALS` 指定同一项目的其他密钥路径。显式指定的密钥优先；没有配置密钥时，发送器依次使用 `FCM_ACCESS_TOKEN` 和 gcloud 应用默认凭据。设备注册 Token 在发送时从 Debug 版首页复制。

```sh
python3 tools/send_fcm_test.py notification --title "DrawLife 测试" --body "验证通知到达"
python3 tools/send_fcm_test.py data --value "data-only-test"
python3 tools/send_fcm_test.py --validate-only notification
```

打开包含当前 Firebase 配置的 Debug 版后，App 会把设备注册 Token 存入应用私有的 `no_backup/drawlife-fcm-registration.json`。Firebase 刷新 Token 时会更新该文件。运行 `python3 tools/send_fcm_test.py sync-token` 可将默认模拟器的当前 Token 同步到 Mac 上的 `~/.config/drawlife-fcm/scanbuddy-2661/registration-token-emulator-5554.json`。使用其他设备时，在子命令前加 `--serial 设备序列号`。

发送前脚本会尝试从所选设备同步，设备离线时使用该设备上次同步的 Token。也可通过标准输入显式提供一行 Token；交互模式没有可用缓存时可粘贴首页复制的 Token。`--validate-only` 只让 FCM 校验请求。

两个发送命令都默认使用 `normal`。给测试设备单独验证高优先级时，可以用 `notification --priority high` 或 `data --priority high`。应用正式使用时，`high` 适用于需要及时显示的用户可见通知。FCM 可能降低长期不产生可见通知的消息优先级，因此请求优先级与实际送达优先级可能不同。[Firebase 优先级说明](https://firebase.google.com/docs/cloud-messaging/android-message-priority#deprioritization_of_high_priority_fcm_on_android)

通知消息在目标模拟器的通知栏中核对。示例 App 收到纯数据消息时，会根据实际投递优先级处理。可在 Logcat 的 `DrawLife` 标签下查找 `FCM priority`。

## 对照真实 FCM 消息的两种优先级

将 App 切到后台，保持手机亮屏、解锁、竖屏并允许 FCM 通知。分别发送两条纯数据消息。

```sh
python3 tools/send_fcm_test.py data --priority normal
python3 tools/send_fcm_test.py data --priority high
```

这些展示和频控规则由示例 App 的 `DemoFcmMessageHandler` 实现。日志中 `received=normal` 或 `unknown` 会进入普通提醒检查。安装满 5 分钟、距上次普通提醒至少 43 分钟且当日未满 3 条时，可以收到「每日提醒」。`received=high` 会尝试发布一条本地提醒，并请求工具栏服务。服务日志在 `DrawLife_Log` 标签下，启动来源为 `fcm_high_priority`，已运行时会记录 `already active`。用 `adb -s emulator-5554 shell dumpsys activity services com.test.test20261120drawlife` 查看服务状态。

## 本地接收 Timer 心跳

在 Mac 上运行接收器并保留终端，在另一个终端给目标设备设置端口转发，然后打开含本次改动的 Debug 版。

```sh
python3 tools/receive_heartbeat.py
```

```sh
adb -s emulator-5554 reverse tcp:8765 tcp:8765
adb -s emulator-5554 logcat -s DrawLifeHeartbeat
```

Debug 版启动时立即检查，此后每分钟检查一次。首次生成的事件会打印 `ALIVE recorded`，通常在 2 分钟后再产生一条。接收器打印 `received` 和实际新增的 `new`，App 收到 HTTP 2xx 后打印 `ALIVE uploaded`。在两条心跳之间结束进程并重新打开，未到期时应看到 `check generated=false`。停止接收器后生成的心跳会留在手机，重新启动接收器后，下一条心跳到期时可以一起收到。测试接收器按安装标识和事件 ID 持久化去重。

## 本地模拟纯数据冷启动

在模拟器安装本项目的 Debug APK 后，可以用本地广播验证数据处理和进程、前台服务状态，无需服务器 OAuth：

```sh
python3 tools/simulate_fcm_data.py --serial emulator-5554 --cold-start --value "cold-start-test"
```

`--cold-start` 只停止指定模拟器中的 DrawLife，并取消它的系统任务，避免任务先一步拉起应用。需要 Android 13 或以上。该结果仅验证本地模拟入口和当前应用行为，真实 FCM 投递及 Google Play 服务授予的启动条件仍需用上面的 HTTP v1 发送器单独测试。
