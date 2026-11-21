# 本地 FCM 测试发送器

发送器通过 [FCM HTTP v1](https://firebase.google.com/docs/cloud-messaging/send/v1-api) 模拟服务端。它从 `app/google-services.json` 读取 Firebase 项目 ID，每次只向一个 DrawLife Android 应用实例发送。

首次使用时，请用在目标项目中拥有 `cloudmessaging.messages.create` 权限的 Google 账号执行 `gcloud auth application-default login`。已有短期 OAuth Access Token 时，也可以通过环境变量 `FCM_ACCESS_TOKEN` 提供。令牌需包含 `firebase.messaging` 或 `cloud-platform` [授权范围](https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages/send#authorization-scopes)。

```sh
python3 tools/send_fcm_test.py notification --title "DrawLife 测试" --body "验证通知到达"
python3 tools/send_fcm_test.py data --value "data-only-test"
python3 tools/send_fcm_test.py --validate-only notification
```

出现提示后，粘贴调试版首页里「复制 FCM 测试 Token」取得的设备注册 Token。脚本也支持从标准输入读取一行 Token。`--validate-only` 需要服务端授权，只让 FCM 校验请求，不投递消息。

两个发送命令都默认使用 `normal`。给测试设备单独验证高优先级时，可以用 `notification --priority high` 或 `data --priority high`。应用正式使用时，`high` 适用于需要及时显示的用户可见通知。FCM 可能降低长期不产生可见通知的消息优先级，因此请求优先级与实际送达优先级可能不同。[Firebase 优先级说明](https://firebase.google.com/docs/cloud-messaging/android-message-priority#deprioritization_of_high_priority_fcm_on_android)

通知消息在目标模拟器的通知栏中核对。普通纯数据消息只记录接收事件，可运行 `adb -s emulator-5554 logcat -d -s DrawLife:I` 查找 `FCM received: kind=data`。

## 验证真实高优先级 data 是否能启动前台服务

先运行下面的本地脚本，确认应用在后台且服务未运行。再发一条带测试标记的真实消息：

```sh
python3 tools/simulate_fcm_data.py --serial emulator-5554 --cold-start
python3 tools/send_fcm_test.py data --priority high --value fgs-high-priority-test
```

此测试只在 Debug APK 的真实 FCM 回调中启用。日志中的 `FCM priority` 记录请求和实际送达优先级。若实际为 `high`，查找 `source=fcm_high_priority_test`，再用 `adb -s emulator-5554 shell dumpsys activity services com.test.test20261120drawlife` 确认 `isForeground=true`。

## 本地模拟纯数据冷启动

在模拟器安装本项目的 Debug APK 后，可以用本地广播验证数据处理和进程、前台服务状态，无需服务器 OAuth：

```sh
python3 tools/simulate_fcm_data.py --serial emulator-5554 --cold-start --value "cold-start-test"
```

`--cold-start` 只停止指定模拟器中的 DrawLife，并取消它的系统任务，避免任务先一步拉起应用。需要 Android 13 或以上。该结果仅验证本地模拟入口和当前应用行为，真实 FCM 投递及 Google Play 服务授予的启动条件仍需用上面的 HTTP v1 发送器单独测试。
