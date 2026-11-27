#!/usr/bin/env python3
"""Send a Firebase Cloud Messaging test to one DrawLife Android installation."""

import argparse
from contextlib import closing
from getpass import GetPassWarning, getpass
from http.client import HTTPException, HTTPSConnection
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import warnings


PACKAGE_NAME = "com.test.test20261120drawlife"
CONFIG = Path(__file__).resolve().parents[1] / "app" / "google-services.json"


def project_id():
    try:
        config = json.loads(CONFIG.read_text(encoding="utf-8"))
        project = config["project_info"]["project_id"]
        packages = {
            client["client_info"].get("android_client_info", {}).get("package_name")
            for client in config["client"]
        }
    except (OSError, KeyError, TypeError, AttributeError, ValueError):
        raise SystemExit("无法读取 app/google-services.json 中的 Firebase 项目配置。")
    if PACKAGE_NAME not in packages or not isinstance(project, str):
        raise SystemExit("Firebase 配置与当前 Android 应用不匹配。")
    if not re.fullmatch(r"[a-z][a-z0-9-]{0,29}", project):
        raise SystemExit("Firebase 项目 ID 格式无效。")
    return project


def access_token(project):
    credential_file = os.environ.get("GOOGLE_APPLICATION_CREDENTIALS")
    key = Path.home() / ".config" / "drawlife-fcm" / project / "serviceAccountKey.json"
    if credential_file:
        key = Path(credential_file).expanduser()
    if credential_file or key.exists():
        try:
            from google.auth.exceptions import GoogleAuthError
            from google.auth.transport.requests import Request
            from google.oauth2.service_account import Credentials
        except ImportError:
            raise SystemExit("请先安装发送器依赖：python3 -m pip install -r tools/requirements.txt")

        try:
            info = json.loads(key.read_text(encoding="utf-8"))
            if (
                not isinstance(info, dict)
                or info.get("type") != "service_account"
                or info.get("project_id") != project
                or info.get("token_uri") != "https://oauth2.googleapis.com/token"
            ):
                raise SystemExit("服务账号密钥与目标 Firebase 项目或 Google OAuth 端点不匹配。")
            credentials = Credentials.from_service_account_info(
                info, scopes=["https://www.googleapis.com/auth/firebase.messaging"]
            )
            credentials.refresh(Request())
            return credentials.token
        except (OSError, ValueError, GoogleAuthError):
            raise SystemExit("服务账号认证失败，请检查密钥、网络连接和系统时间。")

    token = os.environ.get("FCM_ACCESS_TOKEN", "").strip()
    if not token:
        try:
            token = subprocess.check_output(
                ["gcloud", "auth", "application-default", "print-access-token"],
                stderr=subprocess.DEVNULL,
                text=True,
                timeout=30,
            ).strip()
        except (OSError, subprocess.SubprocessError):
            raise SystemExit(
                "没有可用的服务端 OAuth 凭据。请提供 FCM_ACCESS_TOKEN，"
                "或先完成 gcloud auth application-default login。"
            )
    if not re.fullmatch(r"[A-Za-z0-9._~+/=-]+", token):
        raise SystemExit("服务端 OAuth Access Token 格式无效。")
    return token


def sync_registration_token(project, serial, *, allow_offline=False):
    if not re.fullmatch(r"[a-zA-Z0-9._:-]{1,128}", serial):
        raise SystemExit("ADB 设备序列号无效。")
    cache = Path.home() / ".config" / "drawlife-fcm" / project / f"registration-token-{serial}.json"
    adb = ["adb", "-s", serial]
    try:
        state = subprocess.run(adb + ["get-state"], capture_output=True, text=True, timeout=6)
        online = state.returncode == 0 and state.stdout.strip() == "device"
    except (OSError, subprocess.TimeoutExpired):
        online = False
    if online:
        try:
            result = subprocess.run(
                adb + ["exec-out", "run-as", PACKAGE_NAME, "cat", "no_backup/drawlife-fcm-registration.json"],
                capture_output=True, text=True, timeout=10,
            )
            if result.returncode:
                raise ValueError
            snapshot = json.loads(result.stdout)
        except (ValueError, OSError, subprocess.TimeoutExpired):
            if allow_offline and sys.stdin.isatty():
                print("设备缓存尚不可读，可使用 Debug 版首页复制的 Token。", file=sys.stderr)
                return None
            raise SystemExit("设备上尚未读到 Token。请打开已配置 Firebase 的 Debug 版，联网后重试。")
    elif allow_offline and cache.is_file():
        try:
            snapshot = json.loads(cache.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            raise SystemExit("设备 Token 缓存无法读取，请连接设备后运行 sync-token。")
    elif allow_offline:
        return None
    else:
        raise SystemExit(f"ADB 无法连接 {serial}。")

    config = json.loads(CONFIG.read_text(encoding="utf-8"))
    app_ids = {c["client_info"]["mobilesdk_app_id"] for c in config["client"]
               if c["client_info"].get("android_client_info", {}).get("package_name") == PACKAGE_NAME}
    if (not isinstance(snapshot, dict) or snapshot.get("package_name") != PACKAGE_NAME
            or snapshot.get("project_id") != project or snapshot.get("app_id") not in app_ids
            or not isinstance(snapshot.get("token"), str) or not re.fullmatch(r"\S+", snapshot["token"])):
        raise SystemExit("Token 缓存与当前 Firebase 应用不匹配或内容无效。")
    if online:
        data = json.dumps(snapshot, sort_keys=True) + "\n"
        cache.parent.mkdir(parents=True, mode=0o700, exist_ok=True)
        if not cache.exists() or cache.read_text(encoding="utf-8") != data:
            with tempfile.NamedTemporaryFile(mode="w", dir=cache.parent, delete=False) as tmp:
                try:
                    tmp.write(data)
                    tmp.flush()
                    os.replace(tmp.name, cache)
                finally:
                    Path(tmp.name).unlink(missing_ok=True)
        print(f"已同步 {serial} 的 Token：{cache}", file=sys.stderr)
    else:
        print(f"{serial} 离线，使用上次同步的 Token。", file=sys.stderr)
    return snapshot["token"]


def registration_token(project, serial):
    if not sys.stdin.isatty():
        token = sys.stdin.readline().strip()
        if token:
            if any(char.isspace() for char in token):
                raise SystemExit("设备 FCM 注册 Token 格式无效。")
            return token
    cached = sync_registration_token(project, serial, allow_offline=True)
    if cached:
        return cached
    if not sys.stdin.isatty():
        raise SystemExit("请连接设备运行 sync-token，或从标准输入提供设备 Token。")
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", GetPassWarning)
            token = getpass("粘贴模拟器的 FCM 注册 Token：").strip()
    except (EOFError, GetPassWarning):
        raise SystemExit("无法读取设备 FCM 注册 Token。")
    if not token or any(char.isspace() for char in token):
        raise SystemExit("请提供一个有效的设备 FCM 注册 Token。")
    return token


def make_payload(args, token):
    message = {
        "token": token,
        "android": {
            "priority": args.priority.upper(),
            "restricted_package_name": PACKAGE_NAME,
        },
    }
    if args.mode == "notification":
        message["notification"] = {"title": args.title, "body": args.body}
    else:
        message["data"] = {"test_value": args.value}
    return {"validate_only": args.validate_only, "message": message}


def send(project, oauth_token, payload):
    headers = {
        "Authorization": f"Bearer {oauth_token}",
        "Content-Type": "application/json; charset=utf-8",
    }
    try:
        with closing(HTTPSConnection("fcm.googleapis.com", timeout=20)) as connection:
            connection.request(
                "POST",
                f"/v1/projects/{project}/messages:send",
                body=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
                headers=headers,
            )
            with connection.getresponse() as response:
                status = response.status
    except (OSError, HTTPException):
        raise SystemExit("无法连接 FCM HTTP v1，或未收到响应。请先确认设备是否已收到消息。")
    if status != 200:
        hint = {
            400: "检查通知内容和设备注册 Token。",
            401: "获取新的服务端 OAuth Access Token。",
            403: "检查 FCM API 是否启用，以及发送账号是否有 cloudmessaging.messages.create 权限。",
            404: "检查所选项目，并重新复制模拟器的注册 Token。",
        }.get(status, "稍后检查 FCM 服务状态。")
        raise SystemExit(f"FCM HTTP {status}。{hint}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--validate-only", action="store_true", help="让 FCM 校验请求而不投递")
    parser.add_argument("--serial", default="emulator-5554", help="同步 Token 的 ADB 设备，默认 emulator-5554")
    commands = parser.add_subparsers(dest="mode", required=True)
    commands.add_parser("sync-token", help="仅同步当前设备的注册 Token 到 Mac")
    notice = commands.add_parser("notification", help="发送可见的通知")
    notice.add_argument("--title", default="DrawLife FCM 测试")
    notice.add_argument("--body", default="这是一条服务端发送的测试通知。")
    data = commands.add_parser("data", help="发送纯数据，不在通知栏展示")
    data.add_argument("--value", default="drawlife-fcm-test")
    for command in (notice, data):
        command.add_argument(
            "--priority", choices=("normal", "high"), default="normal",
            help="Android 消息投递优先级（默认 normal）",
        )
    args = parser.parse_args()

    project = project_id()
    if args.mode == "sync-token":
        sync_registration_token(project, args.serial)
        return
    oauth_token = access_token(project)
    payload = make_payload(args, registration_token(project, args.serial))
    send(project, oauth_token, payload)
    if args.validate_only:
        print("FCM 校验通过，未投递消息。")
    else:
        print("FCM 已接受消息。请在目标模拟器上验证实际送达。")


if __name__ == "__main__":
    main()
