#!/usr/bin/env python3
"""Send a local debug data-message broadcast to a DrawLife Android emulator."""

import argparse
from collections import Counter
import re
import shlex
import subprocess
import time


PACKAGE = "com.test.test20261120drawlife"
RECEIVER = PACKAGE + "/.fcm.LocalFcmDataReceiver"
ACTION = PACKAGE + ".action.SIMULATE_FCM_DATA"
SERVICE = "DrawLifeForegroundService"
WORK_NAMESPACE = "androidx.work.systemjobscheduler"
LOCAL_RECEIPT = "FCM received: kind=data, dataFields=1, source=local"
LOG_PID = re.compile(r"^[A-Z]/DrawLife\(\s*(\d+)\):")


def shell(serial, *args, missing_pid_ok=False):
    result = subprocess.run(
        ["adb", "-s", serial, "shell", shlex.join(args)],
        capture_output=True,
        text=True,
        timeout=15,
    )
    if result.returncode and not (
        missing_pid_ok and result.returncode == 1 and not (result.stdout or result.stderr).strip()
    ):
        error = (result.stderr or result.stdout).strip()
        raise SystemExit(f"ADB 执行失败（{args[0]}）：{error or result.returncode}")
    return result.stdout.strip()


def pid(serial):
    return shell(serial, "pidof", PACKAGE, missing_pid_ok=True)


def services(serial):
    return shell(serial, "dumpsys", "activity", "services", PACKAGE)


def foreground_running(serial):
    deadline = time.monotonic() + 2
    while time.monotonic() < deadline:
        if "isForeground=true" in services(serial):
            return True
        time.sleep(0.2)
    return False


def stop_for_cold_start(serial):
    api = int(shell(serial, "getprop", "ro.build.version.sdk"))
    if api < 33:
        raise SystemExit("--cold-start 需要 Android 13 或以上的模拟器。")

    shell(serial, "input", "keyevent", "KEYCODE_HOME")
    shell(serial, "cmd", "jobscheduler", "cancel", "-u", "0", PACKAGE)
    if api >= 34:
        shell(serial, "cmd", "jobscheduler", "cancel", "-u", "0", "-n", WORK_NAMESPACE, PACKAGE)
    shell(serial, "cmd", "activity", "stop-app", "--user", "0", PACKAGE)

    deadline = time.monotonic() + 5
    while time.monotonic() < deadline:
        if not pid(serial) and SERVICE not in services(serial):
            break
        time.sleep(0.2)
    else:
        raise SystemExit("发送前仍有 DrawLife 进程或前台服务，无法确认冷启动。")

    package_state = shell(serial, "dumpsys", "package", PACKAGE)
    if not re.search(r"^\s*User 0:.*\bstopped=false\b", package_state, re.MULTILINE):
        raise SystemExit("DrawLife 处于强行停止状态或尚未初始化。请先正常打开调试版。")


def logs(serial):
    return Counter(shell(serial, "logcat", "-d", "-v", "brief", "-s", "DrawLife:I").splitlines())


def await_receipt(serial, baseline):
    deadline = time.monotonic() + 10
    while time.monotonic() < deadline:
        recent = logs(serial) - baseline
        for line in recent:
            if LOCAL_RECEIPT in line:
                match = LOG_PID.match(line)
                if match:
                    return match[1], recent
        time.sleep(0.2)
    raise SystemExit("未见本地数据接收日志。请确认目标模拟器安装了包含本地接收器的 Debug APK。")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default="emulator-5554", help="目标模拟器序列号")
    parser.add_argument("--value", default="drawlife-local-test", help="test_value 的值")
    parser.add_argument("--cold-start", action="store_true", help="先停止测试应用，再检查冷启动")
    args = parser.parse_args()
    if not re.fullmatch(r"emulator-[0-9]+", args.serial):
        parser.error("--serial 只接受 emulator-数字")
    if "\x00" in args.value:
        parser.error("--value 不可包含空字符")

    serial = args.serial
    print("本测试通过 ADB 注入本地广播，未经过 Firebase 或 Google Play 服务。", flush=True)
    try:
        previous_pid = pid(serial)
        if args.cold_start:
            stop_for_cold_start(serial)
        baseline = logs(serial)
        if args.cold_start and pid(serial):
            raise SystemExit("DrawLife 在发送前已重新启动，本次冷启动条件不成立。")

        shell(
            serial, "am", "broadcast", "--user", "0", "-n", RECEIVER, "-a", ACTION,
            "--es", "test_value", args.value,
        )
        received_pid, recent = await_receipt(serial, baseline)
        if args.cold_start and (
            received_pid == previous_pid
            or not any(f"Application.onCreate pid={received_pid}" in line for line in recent)
        ):
            raise SystemExit("已收到本地数据，但未确认应用进程是本次冷启动创建的。")

        running = foreground_running(serial)
        recent = logs(serial) - baseline
        print(f"准备测试时 PID: {previous_pid or '无'}")
        if args.cold_start:
            print("发送前：无应用进程及前台服务，应用未被强行停止。")
        print(f"接收本地数据的 PID: {received_pid}")
        print(LOCAL_RECEIPT)
        for line in recent:
            match = LOG_PID.match(line)
            if match and match[1] == received_pid and any(part in line for part in (
                f"Application.onCreate pid={received_pid}",
                "Foreground service request rejected",
                "ForegroundServiceStartNotAllowedException",
                "Foreground service active",
            )):
                print(line)
        print(f"前台服务状态: {'正在运行' if running else '未运行'}")
    except (OSError, subprocess.TimeoutExpired):
        raise SystemExit("ADB 不可用或命令超时，请检查目标模拟器连接。")


if __name__ == "__main__":
    main()
