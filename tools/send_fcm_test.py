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


def access_token():
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


def registration_token():
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", GetPassWarning)
            token = (
                getpass("粘贴模拟器的 FCM 注册 Token：")
                if sys.stdin.isatty()
                else sys.stdin.readline()
            ).strip()
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
    commands = parser.add_subparsers(dest="mode", required=True)
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
    oauth_token = access_token()
    payload = make_payload(args, registration_token())
    send(project, oauth_token, payload)
    if args.validate_only:
        print("FCM 校验通过，未投递消息。")
    else:
        print("FCM 已接受消息。请在目标模拟器上验证实际送达。")


if __name__ == "__main__":
    main()
