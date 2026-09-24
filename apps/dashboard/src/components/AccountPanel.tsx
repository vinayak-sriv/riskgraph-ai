import { useState } from "react";
import {
  AlertCircle,
  ArrowUpRight,
  CheckCircle2,
  GitBranch,
  LoaderCircle,
} from "lucide-react";
import { api, clearCsrfToken } from "../api";
export type Account = {
  username: string;
  name: string;
  role: "DEVELOPER" | "ANALYST" | "ADMIN";
};
export type GitHubConnectionState =
  | { status: "NOT_CONFIGURED" }
  | { status: "DISCONNECTED" }
  | { status: "CONNECTING" }
  | { status: "CONNECTED"; login: string; permissions: string[] }
  | { status: "ERROR"; message: string };

export function AccountPanel({
  user,
  onUserChange,
  onGithubChange,
}: {
  user: Account | null;
  onUserChange: (user: Account | null) => void;
  onGithubChange?: (connection: GitHubConnectionState) => void;
}) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [newUser, setNewUser] = useState({
    username: "",
    name: "",
    role: "DEVELOPER",
    password: "",
  });
  const [notificationEmail, setNotificationEmail] = useState({
    username: "",
    email: "",
    verified: true,
  });
  async function signIn() {
    setBusy(true);
    setMessage("");
    try {
      const response = await api("/auth/login", {
        method: "POST",
        body: new URLSearchParams({ username, password }),
      });
      if (!response.ok)
        throw new Error("Sign-in failed. Check your username and password.");
      clearCsrfToken();
      const session = await (await api("/auth/session")).json();
      onUserChange(session.user);
      if (session.github) onGithubChange?.(session.github);
    } catch (error) {
      setMessage(
        error instanceof Error ? error.message : "Sign-in unavailable",
      );
    } finally {
      setPassword("");
      setBusy(false);
    }
  }
  async function signOut() {
    setBusy(true);
    setMessage("");
    try {
      const response = await api("/auth/logout", { method: "POST" });
      if (!response.ok) throw new Error("Sign-out failed. Please try again.");
      clearCsrfToken();
      onUserChange(null);
      onGithubChange?.({ status: "DISCONNECTED" });
      setAccounts([]);
    } catch (error) {
      setMessage(
        error instanceof Error ? error.message : "Sign-out unavailable",
      );
    } finally {
      setBusy(false);
    }
  }
  async function listUsers() {
    try {
      const response = await api("/admin/users");
      if (!response.ok) throw new Error("Account list unavailable");
      setAccounts(await response.json());
    } catch (error) {
      setMessage(
        error instanceof Error ? error.message : "Account list unavailable",
      );
    }
  }
  async function createAccount() {
    setBusy(true);
    setMessage("");
    try {
      const response = await api("/admin/users", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(newUser),
      });
      if (!response.ok)
        throw new Error(
          (await response.json()).message ?? "Account creation failed",
        );
      setMessage("Account created.");
      setNewUser({ username: "", name: "", role: "DEVELOPER", password: "" });
      await listUsers();
    } catch (error) {
      setMessage(
        error instanceof Error ? error.message : "Account creation failed",
      );
    } finally {
      setNewUser((value) => ({ ...value, password: "" }));
      setBusy(false);
    }
  }
  async function saveNotificationEmail() {
    setBusy(true);
    setMessage("");
    try {
      const response = await api(
        `/admin/users/${encodeURIComponent(notificationEmail.username)}/email`,
        {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            email: notificationEmail.email,
            verified: notificationEmail.verified,
          }),
        },
      );
      if (!response.ok)
        throw new Error(
          (await response.json()).message ?? "Email update failed",
        );
      setMessage("Notification email updated.");
      setNotificationEmail({ username: "", email: "", verified: true });
    } catch (error) {
      setMessage(
        error instanceof Error ? error.message : "Email update failed",
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="account-panel surface" aria-label="Platform account">
      {user ? (
        <div className="account-summary">
          <span>
            Signed in as <strong>{user.name}</strong> ·{" "}
            {user.role === "ANALYST"
              ? "Security Analyst"
              : user.role === "ADMIN"
                ? "Admin"
                : "Developer (view only)"}
          </span>
          <button
            className="secondary-button"
            onClick={() => void signOut()}
            disabled={busy}
          >
            Sign out
          </button>
        </div>
      ) : (
        <form
          className="account-form"
          onSubmit={(event) => {
            event.preventDefault();
            void signIn();
          }}
        >
          <p>
            Sign in to access source scans. Explore demo fixtures from the
            scenario selector above.
          </p>
          <label>
            Username
            <input
              autoComplete="username"
              required
              value={username}
              onChange={(event) => setUsername(event.target.value)}
            />
          </label>
          <label>
            Password
            <input
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </label>
          <button className="primary-button" disabled={busy}>
            Sign in
          </button>
        </form>
      )}
      {user?.role === "ADMIN" && (
        <details
          onToggle={(event) => {
            if (event.currentTarget.open) void listUsers();
          }}
        >
          <summary>Manage accounts</summary>
          <ul>
            {accounts.map((account) => (
              <li key={account.username}>
                {account.name} · {account.username} · {account.role}
              </li>
            ))}
          </ul>
          <form
            className="account-form"
            onSubmit={(event) => {
              event.preventDefault();
              void createAccount();
            }}
          >
            <label>
              New username
              <input
                required
                pattern="[a-z][a-z0-9_.-]{2,63}"
                value={newUser.username}
                onChange={(event) =>
                  setNewUser({ ...newUser, username: event.target.value })
                }
              />
            </label>
            <label>
              Display name
              <input
                required
                maxLength={120}
                value={newUser.name}
                onChange={(event) =>
                  setNewUser({ ...newUser, name: event.target.value })
                }
              />
            </label>
            <label>
              Platform role
              <select
                value={newUser.role}
                onChange={(event) =>
                  setNewUser({ ...newUser, role: event.target.value })
                }
              >
                <option value="DEVELOPER">Developer (view only)</option>
                <option value="ANALYST">Security Analyst</option>
                <option value="ADMIN">Admin</option>
              </select>
            </label>
            <label>
              New password
              <input
                type="password"
                autoComplete="new-password"
                required
                minLength={12}
                maxLength={72}
                value={newUser.password}
                onChange={(event) =>
                  setNewUser({ ...newUser, password: event.target.value })
                }
              />
            </label>
            <button className="primary-button" disabled={busy}>
              Create account
            </button>
          </form>
          <form
            className="account-form"
            onSubmit={(event) => {
              event.preventDefault();
              void saveNotificationEmail();
            }}
          >
            <label>
              Account username
              <input
                required
                value={notificationEmail.username}
                onChange={(event) =>
                  setNotificationEmail({
                    ...notificationEmail,
                    username: event.target.value,
                  })
                }
              />
            </label>
            <label>
              Notification email
              <input
                type="email"
                required
                value={notificationEmail.email}
                onChange={(event) =>
                  setNotificationEmail({
                    ...notificationEmail,
                    email: event.target.value,
                  })
                }
              />
            </label>
            <label>
              <input
                type="checkbox"
                checked={notificationEmail.verified}
                onChange={(event) =>
                  setNotificationEmail({
                    ...notificationEmail,
                    verified: event.target.checked,
                  })
                }
              />
              Address verified by administrator
            </label>
            <button className="secondary-button" disabled={busy}>
              Save notification email
            </button>
          </form>
        </details>
      )}
      {message && <p role="status">{message}</p>}
    </section>
  );
}

export function GitHubConnectionCard({
  connection = { status: "NOT_CONFIGURED" },
  connectUrl,
}: {
  connection?: GitHubConnectionState;
  connectUrl: string;
}) {
  const icon =
    connection.status === "CONNECTED" ? (
      <CheckCircle2 size={18} />
    ) : connection.status === "CONNECTING" ? (
      <LoaderCircle className="spin" size={18} />
    ) : connection.status === "ERROR" ? (
      <AlertCircle size={18} />
    ) : (
      <GitBranch size={18} />
    );
  const label =
    connection.status === "NOT_CONFIGURED"
      ? "Backend setup required"
      : connection.status === "DISCONNECTED"
        ? "Not connected"
        : connection.status === "CONNECTING"
          ? "Connecting"
          : connection.status === "CONNECTED"
            ? "Connected"
            : "Connection error";
  return (
    <section
      className="surface github-connection"
      aria-labelledby="github-connection-title"
    >
      <div className="connection-card-heading">
        <span
          className={`connection-mark state-${connection.status.toLowerCase()}`}
        >
          {icon}
        </span>
        <div>
          <p className="eyebrow">Source connection</p>
          <h2 id="github-connection-title">GitHub</h2>
        </div>
        <span
          className={`certainty-badge certainty-${connection.status === "CONNECTED" ? "confirmed" : connection.status === "ERROR" ? "unresolved" : "ambiguous"}`}
        >
          {label}
        </span>
      </div>
      {connection.status === "CONNECTED" && (
        <>
          <p>
            Connected as <strong>@{connection.login}</strong>. The dashboard
            session and protected analysis services are synchronized.
          </p>
          <div
            className="permission-list"
            aria-label="GitHub connection capabilities"
          >
            {connection.permissions.map((permission) => (
              <span key={permission}>{permission}</span>
            ))}
          </div>
        </>
      )}
      {connection.status === "DISCONNECTED" && (
        <>
          <p>
            Connect your GitHub account to unlock source analysis, saved scans,
            and sandbox validation. If you already have an Analyst or Admin
            account, sign in locally first so GitHub links to that role.
          </p>
          <a className="primary-button" href={connectUrl}>
            <GitBranch size={16} /> Continue with GitHub{" "}
            <ArrowUpRight size={15} />
          </a>
        </>
      )}
      {connection.status === "CONNECTING" && (
        <p role="status">Waiting for GitHub authorization to complete.</p>
      )}
      {connection.status === "ERROR" && (
        <>
          <p role="alert">{connection.message}</p>
          <a className="secondary-button" href={connectUrl}>
            Retry connection <ArrowUpRight size={15} />
          </a>
        </>
      )}
      {connection.status === "NOT_CONFIGURED" && (
        <>
          <p>
            This RiskGraph installation has not configured GitHub sign-in. Add
            the GitHub App client ID and client secret to the platform
            environment, then restart the local services.
          </p>
          <button
            className="secondary-button"
            type="button"
            disabled
            title="Set GITHUB_CLIENT_ID and GITHUB_CLIENT_SECRET on the platform API"
          >
            <GitBranch size={16} /> Connect GitHub
          </button>
        </>
      )}
    </section>
  );
}
