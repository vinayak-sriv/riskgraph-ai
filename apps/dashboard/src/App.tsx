import { lazy, Suspense, useEffect, useState } from "react";
import {
  AlertTriangle,
  ArrowUpRight,
  CheckCircle2,
  ChevronRight,
  Download,
  FileSearch,
  Fingerprint,
  GitBranch,
  GitCompareArrows,
  Moon,
  RefreshCw,
  ShieldCheck,
  SlidersHorizontal,
  Sun,
  UserRound,
} from "lucide-react";
import { Sidebar, ConnectionStatus } from "./components/WorkspaceLayout";
import { Overview } from "./components/Overview";
import { EvidencePanel } from "./components/EvidencePanel";
import { NewAnalysisPanel, SavedScansPanel } from "./components/AnalysisSetup";
import { AccountPanel, GitHubConnectionCard } from "./components/AccountPanel";
import {
  evidenceCertainty,
  formatLabel,
  type Theme,
  type WorkspaceView,
} from "./view-model";
import { useAnalysis, type Scenario } from "./useAnalysis";
import { CertaintyBadge } from "./components/ui";
import { platformUrl } from "./api";

const GraphComparison = lazy(() =>
  import("./components/GraphComparison").then((module) => ({
    default: module.GraphComparison,
  })),
);

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
    githubConnected,
    canRun,
    loading,
    operation,
    error,
    offline,
    toast,
    setToast,
    handleUserChange,
    handleGithubChange,
    loadDemo,
    runSource,
    runValidation,
    openScan,
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
            githubConnected={githubConnected}
            loading={loading}
            requestError={offline ? null : error}
            onRun={runSource}
            onSuccess={showAnalysis}
          />
        )}
        {activeView === "saved-scans" && (
          <SavedScansPanel
            analysis={analysis}
            user={user}
            githubConnected={githubConnected}
            loading={loading}
            requestError={offline ? null : error}
            onOpen={openScan}
            onSuccess={showAnalysis}
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

function AnalysisView({
  analysis,
  loading,
  operation,
  error,
  offline,
  canRun,
  theme,
  graphFocus,
  evidenceFocus,
  onLoadDemo,
  onValidate,
  onNotify,
  onGraphFocus,
  onEvidenceFocus,
}: {
  analysis: ReturnType<typeof useAnalysis>["analysis"];
  loading: boolean;
  operation: string;
  error: string | null;
  offline: boolean;
  canRun: boolean;
  theme: Theme;
  graphFocus: { nodeId: string; requestId: number } | null;
  evidenceFocus: { evidence: string; requestId: number } | null;
  onLoadDemo: (scenario: Scenario) => Promise<void>;
  onValidate: () => Promise<boolean | undefined>;
  onNotify: (message: string) => void;
  onGraphFocus: (nodeId: string) => void;
  onEvidenceFocus: (evidence: string) => void;
}) {
  const certainty = evidenceCertainty(analysis);
  return (
    <div className="workspace-view analysis-view">
      <div className="page-heading">
        <div>
          <p className="eyebrow">
            <GitCompareArrows size={13} /> Change intelligence
          </p>
          <h1>Security analysis</h1>
          <p>
            Decide from deterministic evidence, then inspect the exact path and
            provenance.
          </p>
        </div>
        <a className="primary-button" href="#new-analysis">
          <SlidersHorizontal size={16} /> New analysis{" "}
          <ArrowUpRight size={16} />
        </a>
      </div>
      <div className="analysis-context surface">
        <div className="context-identity">
          <span className="context-icon">
            <GitBranch size={20} />
          </span>
          <div>
            <strong>
              {analysis.provenance ? "Repository analysis" : "Spring Boot demo"}
            </strong>
            <span>
              {analysis.provenance
                ? `${analysis.provenance.old_commit.slice(0, 7)} → ${analysis.provenance.new_commit.slice(0, 7)}`
                : "Java / Spring Boot"}
            </span>
          </div>
        </div>
        <label className="scenario-control">
          <span>Demo scenario</span>
          <select
            aria-label="Demo scenario"
            value={analysis.provenance ? "" : analysis.scenario}
            disabled={loading}
            onChange={(event) =>
              void onLoadDemo(event.target.value as Scenario)
            }
          >
            {analysis.provenance && (
              <option value="" disabled>
                Choose a demo scenario
              </option>
            )}
            <option value="authorization-removal">Authorization removal</option>
            <option value="safe-change">Safe change</option>
            <option value="new-public-sensitive-endpoint">
              New public sensitive endpoint
            </option>
            <option value="sensitive-resource-exposure">
              Sensitive resource exposure
            </option>
          </select>
        </label>
        <span className="context-tag">
          <Fingerprint size={14} />{" "}
          {analysis.provenance ? "Source evidence" : "Demo fixture"}
        </span>
      </div>
      {error && (
        <div className="state-banner state-error" role="alert">
          <AlertTriangle size={17} />
          <div>
            <strong>
              {offline ? "Offline fixture active" : "Request failed"}
            </strong>
            <span>{error}</span>
          </div>
        </div>
      )}
      <div className="quality-strip" role="status">
        <span>
          <FileSearch size={15} />
          <strong>
            {analysis.provenance ? "Source analysis" : "Demonstration fixture"}
          </strong>
        </span>
        <span>
          <CertaintyBadge certainty={certainty} />
        </span>
        <span>
          {analysis.quality?.confidence ??
            (analysis.provenance ? "UNKNOWN" : "HIGH")}{" "}
          extraction confidence
        </span>
        <span>
          {analysis.quality
            ? `${Math.round(analysis.quality.coverage_ratio * 100)}% call-chain coverage`
            : analysis.provenance
              ? "Coverage unavailable"
              : "100% fixture coverage"}
        </span>
        <span>Validation: {analysis.validation_status ?? "NOT_RUN"}</span>
      </div>
      {analysis.status === "DEGRADED" && (
        <div className="state-banner state-degraded" role="status">
          <AlertTriangle size={17} />
          <div>
            <strong>Partial analysis available</strong>
            <span>
              Review diagnostics for unavailable stages. Deterministic results
              remain visible.
            </span>
          </div>
        </div>
      )}
      {loading && (
        <div className="state-banner state-refreshing" role="status">
          <RefreshCw className="spin" size={16} />
          <div>
            <strong>{operation}…</strong>
            <span>
              The current result remains visible while this request completes.
            </span>
          </div>
        </div>
      )}
      <Overview
        analysis={analysis}
        loading={loading}
        canRun={canRun}
        onValidate={() => void onValidate()}
      />
      <Suspense
        fallback={
          <section
            id="analysis-graph"
            className="section-block graph-loading surface"
            aria-label="Loading security graph"
            role="status"
          >
            <RefreshCw className="spin" size={18} />
            <div>
              <strong>Loading graph workspace</strong>
              <span>
                The decision and deterministic evidence remain available.
              </span>
            </div>
          </section>
        }
      >
        <GraphComparison
          analysis={analysis}
          theme={theme}
          onNotify={onNotify}
          focusTarget={graphFocus}
          onEvidenceFocus={onEvidenceFocus}
        />
      </Suspense>
      <EvidencePanel
        analysis={analysis}
        focusedEvidence={evidenceFocus?.evidence}
        onFocusNode={onGraphFocus}
        onNotify={onNotify}
      />
    </div>
  );
}
