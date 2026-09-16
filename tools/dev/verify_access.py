"""Exercise authenticated local platform access with persistent verification accounts."""

import json
import re
import secrets
import urllib.error
import urllib.request

from platform_session import ROOT, PlatformSession


def status(action):
    try:
        action()
        return 200
    except urllib.error.HTTPError as error:
        return error.code
    except RuntimeError as error:
        match = re.search(r"HTTP (\d{3}):", str(error))
        if match is None:
            raise
        return int(match.group(1))


def main():
    admin = PlatformSession()
    admin.login()
    existing = {account["username"] for account in admin.call("/admin/users")}
    path = ROOT / "tmp/local-auth/verification-users.json"
    users = (
        json.loads(path.read_text())
        if path.exists()
        else {
            role: dict(
                username="verify-" + role.lower() + "-" + secrets.token_hex(4),
                name="Verification " + role,
                role=role,
                password=secrets.token_urlsafe(24),
            )
            for role in ("DEVELOPER", "ANALYST")
        }
    )
    # Save before provisioning so interruptions never lose generated credentials.
    path.write_text(json.dumps(users) + "\n", encoding="utf-8", newline="\n")
    for user in users.values():
        if user["username"] not in existing:
            admin.call("/admin/users", user)
    checks = {}
    for role, user in users.items():
        client = PlatformSession(username=user["username"], password=user["password"])
        assert client.call("/auth/session")["user"]["role"] == role
        assert status(lambda client=client: client.call("/analyses/missing")) == 403
        assert status(lambda client=client: client.call("/analyses", {})) == (
            403 if role == "DEVELOPER" else 400
        )
        assert status(lambda client=client: client.call("/analyses/missing/validation", {})) == 403
        assert status(lambda client=client: client.call("/admin/users")) == 403
        checks[role] = "PASS"
    assert status(lambda: urllib.request.urlopen("http://localhost:8080/analyses/missing")) == 401
    request = urllib.request.Request(
        "http://localhost:8080/analyses", data=b"{}", headers={"Content-Type": "application/json"}
    )
    assert status(lambda: admin.opener.open(request)) == 403
    admin.call("/auth/logout", {})
    assert status(lambda: admin.opener.open("http://localhost:8080/analyses/missing")) == 401
    checks.update(
        anonymous_denied=True, csrf_enforced=True, logout_ends_session=True, accounts_persisted=True
    )
    output = ROOT / "tmp/evidence-bundle/platform-access.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(checks, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(json.dumps(checks, indent=2))


if __name__ == "__main__":
    main()
