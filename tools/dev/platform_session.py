"""Cookie/CSRF client for trusted local verification. Credentials never go to remote hosts."""

import http.cookiejar
import json
import os
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


class PlatformSession:
    def __init__(self, base_url="http://localhost:8080", username=None, password=None):
        if base_url.rstrip("/") not in ("http://localhost:8080", "http://127.0.0.1:8080"):
            raise ValueError(
                "Local verification credentials may only be used with the local platform"
            )
        self.base_url = base_url.rstrip("/")
        self.username = username or os.environ.get("RISKGRAPH_USERNAME", "admin")
        self.password = password
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar())
        )
        self.csrf = None

    def token(self):
        with self.opener.open(self.base_url + "/auth/csrf", timeout=5) as response:
            self.csrf = json.load(response)

    def login(self):
        password = self.password
        if password is None:
            path = Path(
                os.environ.get(
                    "RISKGRAPH_PASSWORD_FILE", str(ROOT / "tmp/local-auth/admin.password")
                )
            )
            password = path.read_text(encoding="utf-8").strip()
        self.token()
        request = urllib.request.Request(
            self.base_url + "/auth/login",
            data=urllib.parse.urlencode(dict(username=self.username, password=password)).encode(),
            headers={
                self.csrf["headerName"]: self.csrf["token"],
                "Content-Type": "application/x-www-form-urlencoded",
            },
        )
        with self.opener.open(request, timeout=10):
            pass
        self.token()

    def call(self, route, payload=None):
        if self.csrf is None:
            self.login()
        headers = {"Content-Type": "application/json"}
        if payload is not None:
            headers[self.csrf["headerName"]] = self.csrf["token"]
        request = urllib.request.Request(
            self.base_url + route,
            data=None if payload is None else json.dumps(payload).encode(),
            headers=headers,
        )
        try:
            with self.opener.open(request, timeout=120) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            if error.code != 401:
                detail = error.read().decode("utf-8", errors="replace")[:2_000]
                raise RuntimeError(
                    f"Local platform request {route} failed with HTTP {error.code}: {detail}"
                ) from error
            # Platform restart invalidates sessions; reauthenticate once, never loop.
            self.login()
            if payload is not None:
                request.add_header(self.csrf["headerName"], self.csrf["token"])
            with self.opener.open(request, timeout=120) as response:
                return json.load(response)
