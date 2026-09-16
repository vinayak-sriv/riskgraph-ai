import { useEffect, useState } from "react";
import {
  CheckCircle2,
  ChevronRight,
  Download,
  Moon,
  RefreshCw,
  ShieldCheck,
  Sun,
  UserRound,
} from "lucide-react";
import { Sidebar, ConnectionStatus } from "./components/WorkspaceLayout";
import { AnalysisView } from "./components/AnalysisView";
import { NewAnalysisPanel, SavedScansPanel } from "./components/AnalysisSetup";
import { AccountPanel, GitHubConnectionCard } from "./components/AccountPanel";
import { formatLabel, type Theme, type WorkspaceView } from "./view-model";
import { useAnalysis } from "./useAnalysis";
import { platformUrl } from "./api";

const viewLabels: Record<WorkspaceView, string> = {
  analysis: "Analysis",
  "new-analysis": "New analysis",
  "saved-scans": "Saved scans",
  account: "Account",
};

function currentView(): WorkspaceView {
  const hash = window.location.hash.slice(1);
  return hash === "new-analysis" || hash === "saved-scans" || hash === "account"
    ? hash
    : "analysis";
}

export default function App() {
  const {
    analysis,
    user,
    github,
    githubConnectionRequired,
    canRead,
    canRun,
    loading,
    operation,
    error,
    offline,
    staleResult,
    toast,
    setToast,
    handleUserChange,
    handleGithubChange,
    loadDemo,
    runSource,
    runValidation,
    openScan,
    history,
    historyCursor,
    historyLoading,
    historyError,
    loadHistory,
    refresh,
  } = useAnalysis();
  const [activeView, setActiveView] = useState<WorkspaceView>(currentView);
  const [theme, setTheme] = useState<Theme>(() =>
    document.documentElement.dataset.theme === "light" ? "light" : "dark",
  );
  const [graphFocus, setGraphFocus] = useState<{
    nodeId: string;
    requestId: number;
  } | null>(null);
  const [evidenceFocus, setEvidenceFocus] = useState<{
    evidence: string;
    requestId: number;
  } | null>(null);

  useEffect(() => {
    const update = () => setActiveView(currentView());
    window.addEventListener("hashchange", update);
    window.addEventListener("popstate", update);
    return () => {
      window.removeEventListener("hashchange", update);
      window.removeEventListener("popstate", update);
    };
  }, []);
  useEffect(() => {
    if (!toast) return;
    const timeout = window.setTimeout(() => setToast(null), 2200);
    return () => window.clearTimeout(timeout);
  }, [toast, setToast]);
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    try {
      localStorage.setItem("riskgraph-theme", theme);
    } catch {
      /* Theme remains usable when storage is unavailable. */
    }
  }, [theme]);
  useEffect(() => {
    document.title =
      activeView === "analysis"
        ? `${formatLabel(analysis.scenario.replaceAll("-", "_"))} · RiskGraph AI`
        : `${viewLabels[activeView]} · RiskGraph AI`;
  }, [activeView, analysis]);

  const navigate = (view: WorkspaceView) => {
    window.history.pushState(null, "", `#${view}`);
    setActiveView(view);
  };
  const showAnalysis = () => {
    navigate("analysis");
    window.scrollTo({
      top: 0,
      behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches
        ? "instant"
        : "smooth",
    });
  };
  const toggleTheme = () =>
    setTheme((current) => (current === "light" ? "dark" : "light"));
  const downloadResult = () => {
    const blob = new Blob([JSON.stringify(analysis, null, 2)], {
      type: "application/json",
    });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `${analysis.scenario || "riskgraph-analysis"}.json`;
    anchor.click();
    URL.revokeObjectURL(url);
    setToast("Analysis JSON exported");
  };

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        Skip to content
      </a>
      <Sidebar activeView={activeView} user={user} onNavigate={navigate} />
      <main
        id="main-content"
        className={`workspace${loading ? " is-refreshing" : ""}`}
      >
        <header className="workspace-header">
          <div className="breadcrumb">
            <ShieldCheck size={15} />
            <span>Workspace</span>
            <ChevronRight size={13} />
            <strong>{viewLabels[activeView]}</strong>
          </div>
          <div className="header-actions">
            <ConnectionStatus
              loading={loading}
              error={offline ? "Platform unavailable" : null}
            />
            <button
              className="icon-button"
              title={`Use ${theme === "light" ? "dark" : "light"} mode`}
              aria-label={`Use ${theme === "light" ? "dark" : "light"} mode`}
              onClick={toggleTheme}
            >
              {theme === "light" ? <Moon size={17} /> : <Sun size={17} />}
            </button>
            {activeView === "analysis" && (
              <>
                <button
                  className="icon-button"
                  title="Refresh analysis"
                  aria-label="Refresh analysis"
                  disabled={loading || (!!analysis.provenance && !canRun)}
                  onClick={() => void refresh()}
                >
                  <RefreshCw className={loading ? "spin" : ""} size={17} />
                </button>
                <button
                  className="secondary-button"
                  aria-label="Export JSON"
                  title="Export JSON"
                  onClick={downloadResult}
                >
                  <Download size={16} />
                  <span>Export JSON</span>
                </button>
              </>
            )}
          </div>
        </header>

        {activeView === "analysis" && (
          <AnalysisView
            analysis={analysis}
            loading={loading}
            operation={operation}
            error={error}
            offline={offline}
            staleResult={staleResult}
            canRun={canRun}
            theme={theme}
            graphFocus={graphFocus}
            evidenceFocus={evidenceFocus}
            onLoadDemo={loadDemo}
            onValidate={runValidation}
            onNotify={setToast}
            onGraphFocus={(nodeId) =>
              setGraphFocus({ nodeId, requestId: Date.now() })
            }
            onEvidenceFocus={(evidence) =>
              setEvidenceFocus({ evidence, requestId: Date.now() })
            }
          />
        )}
        {activeView === "new-analysis" && (
          <NewAnalysisPanel
            analysis={analysis}
            user={user}
            sourceAccessEnabled={canRead}
            githubConnectionRequired={githubConnectionRequired}
            loading={loading}
            requestError={offline ? null : error}
            onRun={runSource}
            onSuccess={showAnalysis}
          />
        )}
        {activeView === "saved-scans" && (
          <SavedScansPanel
            user={user}
            sourceAccessEnabled={canRead}
            githubConnectionRequired={githubConnectionRequired}
            loading={loading}
            requestError={offline ? null : error}
            onOpen={openScan}
            onSuccess={showAnalysis}
            history={history}
            nextCursor={historyCursor}
            historyLoading={historyLoading}
            historyError={historyError}
            onLoadHistory={loadHistory}
          />
        )}
        {activeView === "account" && (
          <section
            className="workspace-view"
            aria-labelledby="account-view-title"
          >
            <div className="page-heading compact-heading">
              <div>
                <p className="eyebrow">
                  <UserRound size={13} /> Account
                </p>
                <h1 id="account-view-title">Identity and connections</h1>
                <p>
                  Manage platform access and repository identity without mixing
                  account controls into analysis results.
                </p>
              </div>
            </div>
            <div className="account-view-grid">
              <AccountPanel
                user={user}
                onUserChange={handleUserChange}
                onGithubChange={handleGithubChange}
              />
              <GitHubConnectionCard
                connection={github}
                connectUrl={`${platformUrl}/auth/github/connect`}
              />
            </div>
          </section>
        )}

        <footer className="workspace-footer">
          <span>
            <ShieldCheck size={15} /> RiskGraph AI
          </span>
          <span>Evidence first. Every decision traceable.</span>
          <a href="#analysis">Back to analysis ↑</a>
        </footer>
      </main>
      {toast && (
        <div className="toast" role="status">
          <CheckCircle2 size={16} />
          {toast}
        </div>
      )}
    </div>
  );
}
