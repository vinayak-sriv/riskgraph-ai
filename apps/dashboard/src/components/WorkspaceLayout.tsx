import {
  ArrowUpRight,
  ChevronRight,
  CircleDot,
  Database,
  Fingerprint,
  GitBranch,
  History,
  LayoutDashboard,
  RefreshCw,
  ShieldCheck,
  SlidersHorizontal,
  UserRound,
  WifiOff,
} from "lucide-react";
import type { Account } from "./AccountPanel";
import type { WorkspaceView } from "../view-model";

export function Sidebar({
  activeView,
  user,
  onNavigate,
}: {
  activeView: WorkspaceView;
  user: Account | null;
  onNavigate?: (view: WorkspaceView) => void;
}) {
  const navigate = (
    event: React.MouseEvent<HTMLAnchorElement>,
    view: WorkspaceView,
  ) => {
    if (!onNavigate) return;
    event.preventDefault();
    onNavigate(view);
  };
  const items = [
    {
      href: "#analysis",
      view: "analysis" as const,
      label: "Analysis",
      compact: "Analysis",
      icon: <LayoutDashboard size={18} />,
    },
    {
      href: "#new-analysis",
      view: "new-analysis" as const,
      label: "New analysis",
      compact: "New",
      icon: <SlidersHorizontal size={18} />,
    },
    {
      href: "#saved-scans",
      view: "saved-scans" as const,
      label: "Open scan by ID",
      compact: "Open scan",
      icon: <History size={18} />,
    },
    {
      href: "#account",
      view: "account" as const,
      label: "Account",
      compact: "Account",
      icon: <UserRound size={18} />,
    },
  ];
  return (
    <aside className="sidebar">
      <a
        className="brand"
        href="#analysis"
        aria-label="RiskGraph home"
        onClick={(event) => navigate(event, "analysis")}
      >
        <span>
          <ShieldCheck size={23} />
        </span>
        <div>
          <strong>
            RiskGraph<span>AI</span>
          </strong>
          <small>Security intelligence</small>
        </div>
      </a>
      <div className="workspace-selector">
        <span className="workspace-avatar">
          <GitBranch size={18} />
        </span>
        <div>
          <strong>Local workspace</strong>
          <small>Java · Spring Boot</small>
        </div>
        <ChevronRight size={14} />
      </div>
      <p className="nav-label">Analysis workspace</p>
      <nav aria-label="Workspace navigation">
        {items.map((item, index) => (
          <a
            href={item.href}
            title={item.label}
            aria-label={item.label}
            className={activeView === item.view ? "active" : ""}
            aria-current={activeView === item.view ? "page" : undefined}
            key={item.href}
            onClick={(event) => navigate(event, item.view)}
          >
            {item.icon}
            <span className="nav-full">{item.label}</span>
            <span className="nav-compact" aria-hidden="true">
              {item.compact}
            </span>
            <small>0{index + 1}</small>
          </a>
        ))}
      </nav>
      <div className="sidebar-bottom">
        <div className="sidebar-note">
          <Fingerprint size={22} />
          <strong>Built on evidence</strong>
          <p>
            Deterministic analysis.
            <br />
            Transparent risk scoring.
          </p>
          <a href="#analysis-evidence">
            Explore the evidence <ArrowUpRight size={14} />
          </a>
        </div>
        <a
          href="#account"
          className="sidebar-footer"
          onClick={(event) => navigate(event, "account")}
          aria-label={`Platform account: ${user ? user.name : "Sign in for source scans"}`}
        >
          <span className="profile-avatar">
            {user ? (
              user.name.slice(0, 2).toUpperCase()
            ) : (
              <Database size={17} />
            )}
          </span>
          <div>
            <strong>{user ? user.username : "Demo workspace"}</strong>
            <small>
              {user
                ? user.role === "ADMIN"
                  ? "Administrator"
                  : user.role === "ANALYST"
                    ? "Security Analyst"
                    : "Developer · view only"
                : "Sign in for source scans"}
            </small>
          </div>
          <ChevronRight size={14} />
        </a>
      </div>
    </aside>
  );
}

export function ConnectionStatus({
  loading,
  error,
}: {
  loading: boolean;
  error: string | null;
}) {
  if (loading)
    return (
      <span className="connection-status checking">
        <RefreshCw className="spin" size={14} /> Checking platform
      </span>
    );
  if (error)
    return (
      <span className="connection-status unavailable" title={error}>
        <WifiOff size={14} /> Platform unavailable
      </span>
    );
  return (
    <span className="connection-status">
      <CircleDot size={14} /> Platform connected
    </span>
  );
}
