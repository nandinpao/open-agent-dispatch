#!/usr/bin/env python3
"""Verify the configured Core Admin credential through the browser-facing auth proxy."""
from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request
from http.cookiejar import CookieJar


def request_json(opener: urllib.request.OpenerDirector, request: urllib.request.Request) -> tuple[int, object]:
    try:
        with opener.open(request, timeout=10) as response:
            payload = response.read().decode("utf-8", errors="replace")
            return response.status, json.loads(payload) if payload else {}
    except urllib.error.HTTPError as exc:
        payload = exc.read().decode("utf-8", errors="replace")
        try:
            body: object = json.loads(payload) if payload else {}
        except json.JSONDecodeError:
            body = payload
        return exc.code, body
    except urllib.error.URLError as exc:
        raise RuntimeError(f"authentication endpoint is unreachable: {exc.reason}") from exc


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--username", required=True)
    parser.add_argument("--password", required=True)
    args = parser.parse_args()

    base_url = args.base_url.rstrip("/")
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(CookieJar()))

    status, csrf = request_json(opener, urllib.request.Request(f"{base_url}/api/auth/csrf"))
    if status != 200 or not isinstance(csrf, dict) or not csrf.get("token") or not csrf.get("headerName"):
        raise RuntimeError(f"GET /api/auth/csrf failed with HTTP {status}: {csrf}")

    login_body = json.dumps({"username": args.username, "password": args.password}).encode("utf-8")
    login_request = urllib.request.Request(
        f"{base_url}/api/auth/login",
        data=login_body,
        method="POST",
        headers={
            "Content-Type": "application/json",
            str(csrf["headerName"]): str(csrf["token"]),
        },
    )
    status, login = request_json(opener, login_request)
    if status != 200:
        raise RuntimeError(
            f"POST /api/auth/login failed for username={args.username!r} with HTTP {status}: {login}. "
            "Check the effective Core container CORE_ADMIN_* environment and configured account startup log."
        )

    status, current = request_json(opener, urllib.request.Request(f"{base_url}/api/auth/me"))
    actual_username = current.get("username") if isinstance(current, dict) else None
    if status != 200 or actual_username != args.username:
        raise RuntimeError(
            f"GET /api/auth/me returned HTTP {status} username={actual_username!r}; expected {args.username!r}: {current}"
        )

    # Refresh CSRF after authentication because the security context/session may have changed.
    status, authenticated_csrf = request_json(opener, urllib.request.Request(f"{base_url}/api/auth/csrf"))
    if status == 200 and isinstance(authenticated_csrf, dict) and authenticated_csrf.get("token"):
        logout_request = urllib.request.Request(
            f"{base_url}/api/auth/logout",
            data=b"{}",
            method="POST",
            headers={
                "Content-Type": "application/json",
                str(authenticated_csrf["headerName"]): str(authenticated_csrf["token"]),
            },
        )
        logout_status, logout_body = request_json(opener, logout_request)
        if logout_status != 200:
            raise RuntimeError(f"POST /api/auth/logout failed with HTTP {logout_status}: {logout_body}")

    print(f"Core Admin login smoke passed for username={args.username}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:  # noqa: BLE001 - CLI must produce one actionable failure line.
        print(f"Core Admin login smoke failed: {exc}", file=sys.stderr)
        raise SystemExit(1)
