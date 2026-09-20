import { useEffect, useState } from "react";
import { BellOff, Check, CheckCheck } from "lucide-react";
import { api } from "../api";
import type { Account } from "./AccountPanel";

export type NotificationItem = {
  id: number;
  repository: string | null;
  verdict: string | null;
  risk_before: number | null;
  risk_after: number | null;
  scan_id: string | null;
  validation_status?: string | null;
  confidence?: string | null;
  created_at: string;
  read_at: string | null;
};
type NotificationPage = {
  items: NotificationItem[];
  next_cursor: number | null;
  unread_count: number;
};
type Channel = "IN_APP" | "EMAIL";
type Severity = "REVIEW" | "BLOCK";
type ChannelPreference = {
  channel: Channel;
  enabled: boolean;
  min_severity: Severity;
};
type Preferences = { channels: ChannelPreference[]; unsubscribed_all: boolean };

export function NotificationCenter({ user }: { user: Account | null }) {
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [nextCursor, setNextCursor] = useState<number | null>(null);
  const [unreadCount, setUnreadCount] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [available, setAvailable] = useState(true);
  const [preferences, setPreferences] = useState<Preferences | null>(null);

  async function load(cursor?: number) {
    setLoading(true);
    setError("");
    try {
      const response = await api(
        `/notifications${cursor ? `?cursor=${cursor}` : ""}`,
      );
      if (response.status === 404) {
        setAvailable(false);
        return;
      }
      if (!response.ok) throw new Error("Notifications unavailable");
      // Defensive: an unrelated response shape (a misrouted mock, a proxy
      // error page) should show "no notifications", not crash the panel.
      const page = (await response.json()) as Partial<NotificationPage>;
      const pageItems = Array.isArray(page.items) ? page.items : [];
      setItems((current) => (cursor ? [...current, ...pageItems] : pageItems));
      setNextCursor(page.next_cursor ?? null);
      setUnreadCount(page.unread_count ?? 0);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Notifications unavailable",
      );
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (!user) return;
    void load();
    api("/notifications/preferences")
      .then((response) => (response.ok ? response.json() : null))
      .then((value) => {
        const prefs = value as Partial<Preferences> | null;
        if (prefs && Array.isArray(prefs.channels))
          setPreferences(prefs as Preferences);
      })
      .catch(() => {
        // Preferences are a secondary control surface; a failed fetch just
        // leaves the form hidden rather than surfacing a second error state.
      });
  }, [user]);

  async function markRead(id: number) {
    const response = await api(`/notifications/${id}/read`, { method: "POST" });
    if (!response.ok) return;
    setItems((current) =>
      current.map((item) =>
        item.id === id ? { ...item, read_at: new Date().toISOString() } : item,
      ),
    );
    setUnreadCount((count) => Math.max(0, count - 1));
  }

  async function markAllRead() {
    const response = await api("/notifications/read-all", { method: "POST" });
    if (!response.ok) return;
    const now = new Date().toISOString();
    setItems((current) =>
      current.map((item) => ({ ...item, read_at: item.read_at ?? now })),
    );
    setUnreadCount(0);
  }

  async function updateChannel(
    channel: Channel,
    enabled: boolean,
    minSeverity: Severity,
  ) {
    const response = await api("/notifications/preferences", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ channel, enabled, min_severity: minSeverity }),
    });
    if (response.ok) setPreferences((await response.json()) as Preferences);
  }

  async function updateUnsubscribedAll(unsubscribed: boolean) {
    const response = await api("/notifications/preferences/unsubscribe-all", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ unsubscribed }),
    });
    if (response.ok) setPreferences((await response.json()) as Preferences);
  }

  if (!user || !available) return null;

  return (
    <section className="notification-center surface" aria-label="Notifications">
      <div className="surface-heading">
        <div>
          <p className="eyebrow">Review &amp; block decisions</p>
          <h2>Notifications</h2>
        </div>
        {unreadCount > 0 && <span className="count-badge">{unreadCount}</span>}
      </div>
      {loading && items.length === 0 ? (
        <div className="empty-surface" role="status">
          Loading notifications…
        </div>
      ) : error && items.length === 0 ? (
        <div className="empty-surface" role="alert">
          {error}
        </div>
      ) : items.length === 0 ? (
        <div className="empty-surface">No notifications yet.</div>
      ) : (
        <ul className="notification-list" aria-label="Notification list">
          {items.map((item) => (
            <li
              key={item.id}
              className={
                item.read_at ? "notification-read" : "notification-unread"
              }
            >
              <div className="notification-summary">
                <span
                  className={`certainty-badge ${item.verdict === "BLOCK" ? "certainty-unresolved" : "certainty-ambiguous"}`}
                >
                  {item.verdict ?? "—"}
                </span>
                <strong>{item.repository ?? "Unknown repository"}</strong>
                {item.risk_before != null && item.risk_after != null && (
                  <span className="notification-risk">
                    Risk {item.risk_before} → {item.risk_after}
                  </span>
                )}
                <span className="notification-risk">
                  {item.validation_status === "CONFIRMED" ? "Docker-confirmed" : "Possible"}
                  {item.confidence ? ` · ${item.confidence} confidence` : ""}
                </span>
              </div>
              {!item.read_at && (
                <button
                  className="secondary-button"
                  type="button"
                  onClick={() => void markRead(item.id)}
                >
                  <Check size={14} /> Mark read
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
      <div className="notification-actions">
        {unreadCount > 0 && (
          <button
            className="secondary-button"
            type="button"
            onClick={() => void markAllRead()}
          >
            <CheckCheck size={14} /> Mark all read
          </button>
        )}
        {nextCursor != null && (
          <button
            className="secondary-button"
            type="button"
            disabled={loading}
            onClick={() => void load(nextCursor)}
          >
            Load more
          </button>
        )}
      </div>
      {preferences && (
        <details className="notification-preferences">
          <summary>Manage notification preferences</summary>
          {preferences.channels.map((channel) => (
            <div className="notification-preference-row" key={channel.channel}>
              <label>
                <input
                  type="checkbox"
                  checked={channel.enabled}
                  onChange={(event) =>
                    void updateChannel(
                      channel.channel,
                      event.target.checked,
                      channel.min_severity,
                    )
                  }
                />
                {channel.channel === "IN_APP" ? "In-app" : "Email"}
              </label>
              <select
                value={channel.min_severity}
                disabled={!channel.enabled}
                onChange={(event) =>
                  void updateChannel(
                    channel.channel,
                    channel.enabled,
                    event.target.value as Severity,
                  )
                }
              >
                <option value="REVIEW">Review and block</option>
                <option value="BLOCK">Block only</option>
              </select>
            </div>
          ))}
          <label className="notification-preference-row">
            <input
              type="checkbox"
              checked={preferences.unsubscribed_all}
              onChange={(event) =>
                void updateUnsubscribedAll(event.target.checked)
              }
            />
            <BellOff size={14} /> Unsubscribe from all notifications
          </label>
        </details>
      )}
    </section>
  );
}
