import { useEffect, useMemo, useRef, useState } from "react";
import {
  ArrowRight,
  Check,
  FileSearch,
  GitCompareArrows,
  History,
  LockKeyhole,
  Search,
  ShieldCheck,
  Terminal,
} from "lucide-react";
import type { Account } from "./AccountPanel";
import type { AnalysisResult } from "../types";
import type { SavedScanHistoryItem, SourceInput } from "../useAnalysis";

const SHA_PATTERN = /^[0-9a-fA-F]{40}$/;

export function NewAnalysisPanel({
  analysis,
  user,
  sourceAccessEnabled,
  githubConnectionRequired,
  loading,
  requestError,
  onRun,
  onSuccess,
}: {
  analysis: AnalysisResult;
  user: Account | null;
  sourceAccessEnabled: boolean;
  githubConnectionRequired: boolean;
  loading: boolean;
  requestError: string | null;
  onRun: (input: SourceInput) => Promise<boolean | undefined>;
  onSuccess: () => void;
}) {
  const [input, setInput] = useState<SourceInput>({
    repository_path: "",
    old_commit: "",
    new_commit: "",
  });
  const canRun = user?.role === "ADMIN" || user?.role === "ANALYST";
  useEffect(() => {
    if (analysis.provenance) {
      const { repository_path, old_commit, new_commit } = analysis.provenance;
      setInput({ repository_path, old_commit, new_commit });
    }
  }, [analysis.provenance]);
  useEffect(() => {
    if (!user)
      setInput({ repository_path: "", old_commit: "", new_commit: "" });
  }, [user]);

  const checks = useMemo(
    () => ({
      repository: input.repository_path.trim().length > 0,
      oldCommit: SHA_PATTERN.test(input.old_commit),
      newCommit: SHA_PATTERN.test(input.new_commit),
      distinct:
        input.old_commit.length === 0 ||
        input.new_commit.length === 0 ||
        input.old_commit.toLowerCase() !== input.new_commit.toLowerCase(),
    }),
    [input],
  );
  const formValid =
    checks.repository &&
    checks.oldCommit &&
    checks.newCommit &&
    checks.distinct;

  return (
    <section className="workspace-view" aria-labelledby="new-analysis-title">
      <div className="page-heading compact-heading">
        <div>
          <p className="eyebrow">
            <GitCompareArrows size={13} /> New analysis
          </p>
          <h1 id="new-analysis-title">Compare source revisions</h1>
          <p>
            Review the exact local repository and immutable commits before
            analysis starts.
          </p>
        </div>
        <span className="context-tag">
          <Terminal size={14} /> Local workspace
        </span>
      </div>
      {!sourceAccessEnabled ? (
        <SourceAccessRequired
          authenticated={!!user}
          githubConnectionRequired={githubConnectionRequired}
        />
      ) : (
        <form
          className="analysis-form surface analysis-form-refined"
          onSubmit={(event) => {
            event.preventDefault();
            if (canRun && formValid)
              void onRun(input).then((success) => {
                if (success) onSuccess();
              });
          }}
        >
          <div className="form-intro">
            <span className="context-icon">
              <GitCompareArrows size={20} />
            </span>
            <div>
              <p className="eyebrow">Source</p>
              <h2>Analysis inputs</h2>
              <p>
                Only allowlisted local repositories are accepted by the
                platform.
              </p>
            </div>
          </div>
          <label className="repository-input">
            Repository path
            <input
              aria-label="Repository path"
              required
              value={input.repository_path}
              onChange={(event) =>
                setInput({ ...input, repository_path: event.target.value })
              }
              placeholder="Allowlisted local repository"
              spellCheck={false}
              aria-describedby="repository-help"
            />
            <FieldStatus
              id="repository-help"
              valid={checks.repository}
              empty={!input.repository_path}
              validText="Repository path ready"
              emptyText="Enter an allowlisted local path"
            />
          </label>
          <label>
            Old commit SHA
            <input
              aria-label="Old commit SHA"
              required
              pattern="[0-9a-fA-F]{40}"
              value={input.old_commit}
              onChange={(event) =>
                setInput({ ...input, old_commit: event.target.value })
              }
              placeholder="40-character SHA"
              spellCheck={false}
              aria-describedby="old-commit-help"
            />
            <FieldStatus
              id="old-commit-help"
              valid={checks.oldCommit}
              empty={!input.old_commit}
              validText="Valid immutable revision"
              emptyText="Enter the base revision"
            />
          </label>
          <label>
            New commit SHA
            <input
              aria-label="New commit SHA"
              required
              pattern="[0-9a-fA-F]{40}"
              value={input.new_commit}
              onChange={(event) =>
                setInput({ ...input, new_commit: event.target.value })
              }
              placeholder="40-character SHA"
              spellCheck={false}
              aria-describedby="new-commit-help"
            />
            <FieldStatus
              id="new-commit-help"
              valid={checks.newCommit && checks.distinct}
              empty={!input.new_commit}
              validText="Valid comparison revision"
              emptyText="Enter the changed revision"
              invalidText={
                !checks.distinct
                  ? "Choose a revision different from the base"
                  : undefined
              }
            />
          </label>
          <aside
            className="submission-review"
            aria-label="Analysis submission review"
          >
            <div>
              <p className="eyebrow">Pre-submit review</p>
              <h3>Confirm analysis scope</h3>
            </div>
            <dl>
              <div>
                <dt>Repository</dt>
                <dd>{input.repository_path || "Not provided"}</dd>
              </div>
              <div>
                <dt>Revision pair</dt>
                <dd>
                  <code>{shortSha(input.old_commit)}</code>
                  <ArrowRight size={13} />
                  <code>{shortSha(input.new_commit)}</code>
                </dd>
              </div>
              <div>
                <dt>Execution</dt>
                <dd>Configured local environment</dd>
              </div>
            </dl>
          </aside>
          <div className="form-footer">
            <p>
              <ShieldCheck size={15} />
              {canRun
                ? "The platform will preserve provenance and return deterministic evidence."
                : "Security Analyst or Admin access is required."}
            </p>
            <button
              className="primary-button"
              disabled={loading || !canRun || !formValid}
              type="submit"
            >
              <GitCompareArrows size={16} />
              {loading ? "Analyzing…" : "Run Analysis"}
              <ArrowRight size={16} />
            </button>
          </div>
        </form>
      )}
      {requestError && (
        <p className="fixture-notice" role="alert">
          {requestError}
        </p>
      )}
    </section>
  );
}

export function SavedScansPanel({
  user,
  sourceAccessEnabled,
  githubConnectionRequired,
  loading,
  requestError,
  onOpen,
  onSuccess,
  history,
  nextCursor,
  historyLoading,
  historyError,
  onLoadHistory,
}: {
  user: Account | null;
  sourceAccessEnabled: boolean;
  githubConnectionRequired: boolean;
  loading: boolean;
  requestError: string | null;
  onOpen: (id: string) => Promise<boolean | undefined>;
  onSuccess: () => void;
  history: SavedScanHistoryItem[];
  nextCursor: string | null;
  historyLoading: boolean;
  historyError: string | null;
  onLoadHistory: (cursor?: string) => Promise<void>;
}) {
  const [savedScan, setSavedScan] = useState("");
  const historyRequested = useRef(false);
  useEffect(() => {
    if (!user) setSavedScan("");
  }, [user]);
  useEffect(() => {
    if (!sourceAccessEnabled) {
      historyRequested.current = false;
    } else if (!historyRequested.current) {
      historyRequested.current = true;
      void onLoadHistory();
    }
  }, [sourceAccessEnabled, onLoadHistory]);
  return (
    <section className="workspace-view" aria-labelledby="saved-scans-title">
      <div className="page-heading compact-heading">
        <div>
          <p className="eyebrow">
            <History size={13} /> Saved scans
          </p>
          <h1 id="saved-scans-title">Open previous analysis</h1>
          <p>Browse scan jobs and results shared with your account.</p>
        </div>
        <span className="runtime-badge">Direct lookup</span>
      </div>
      {!sourceAccessEnabled ? (
        <SourceAccessRequired
          authenticated={!!user}
          githubConnectionRequired={githubConnectionRequired}
        />
      ) : (
        <>
          <form
            className="surface saved-scan-search"
            onSubmit={(event) => {
              event.preventDefault();
              void onOpen(savedScan).then((success) => {
                if (success) onSuccess();
              });
            }}
          >
            <label>
              Saved scan ID
              <div className="search-input">
                <Search size={16} />
                <input
                  required
                  pattern="[0-9a-f]{64}"
                  value={savedScan}
                  placeholder="Enter a 64-character scan ID"
                  spellCheck={false}
                  onChange={(event) => setSavedScan(event.target.value)}
                />
              </div>
            </label>
            <button
              className="primary-button"
              disabled={loading || savedScan.length !== 64}
            >
              <FileSearch size={16} /> Open scan
            </button>
          </form>
          <div className="surface saved-scans-table-wrap">
            <div className="surface-heading">
              <div>
                <p className="eyebrow">Membership-filtered</p>
                <h2>Scan jobs</h2>
              </div>
              <span className="count-badge">{history.length}</span>
            </div>
            {historyLoading && history.length === 0 ? (
              <div className="empty-surface" role="status">
                Loading saved scans…
              </div>
            ) : historyError && history.length === 0 ? (
              <div className="empty-surface" role="alert">
                {historyError}
              </div>
            ) : history.length > 0 ? (
              <div
                className="source-table-wrap"
                role="region"
                aria-label="Saved scan history"
                tabIndex={0}
              >
                <table className="source-table saved-scans-table">
                  <thead>
                    <tr>
                      <th>Repository</th>
                      <th>Revisions</th>
                      <th>Verdict</th>
                      <th>Risk delta</th>
                      <th>Status</th>
                      <th>
                        <span className="sr-only">Action</span>
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {history.map((item) => (
                      <tr key={item.job_id}>
                        <td>{item.repository}</td>
                        <td>
                          <code>
                            {item.old_commit.slice(0, 7)} →{" "}
                            {item.new_commit.slice(0, 7)}
                          </code>
                        </td>
                        <td>{item.verdict ?? "—"}</td>
                        <td>
                          <code>
                            {item.risk_delta == null
                              ? "—"
                              : `${item.risk_delta > 0 ? "+" : ""}${item.risk_delta}`}
                          </code>
                        </td>
                        <td>{item.status}</td>
                        <td>
                          <button
                            className="secondary-button"
                            type="button"
                            disabled={!item.scan_id}
                            onClick={() =>
                              item.scan_id &&
                              void onOpen(item.scan_id).then((opened) => {
                                if (opened) onSuccess();
                              })
                            }
                          >
                            View
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                {nextCursor && (
                  <button
                    className="secondary-button"
                    type="button"
                    disabled={historyLoading}
                    onClick={() => void onLoadHistory(nextCursor)}
                  >
                    Load more
                  </button>
                )}
                {historyError && (
                  <div className="pagination-error" role="alert">
                    <span>Could not load more scans: {historyError}</span>
                    {nextCursor && (
                      <button
                        className="secondary-button"
                        type="button"
                        disabled={historyLoading}
                        onClick={() => void onLoadHistory(nextCursor)}
                      >
                        Retry
                      </button>
                    )}
                  </div>
                )}
              </div>
            ) : (
              <div className="empty-surface">
                <History size={20} />
                <strong>No saved scans are visible</strong>
                <p>
                  Completed and in-progress jobs appear after submission or
                  sharing.
                </p>
              </div>
            )}
          </div>
        </>
      )}
      {requestError && (
        <p className="fixture-notice" role="alert">
          {requestError}
        </p>
      )}
    </section>
  );
}

function SourceAccessRequired({
  authenticated,
  githubConnectionRequired,
}: {
  authenticated: boolean;
  githubConnectionRequired: boolean;
}) {
  const githubRequired = authenticated && githubConnectionRequired;
  return (
    <div className="state-panel state-empty github-required" role="status">
      <LockKeyhole size={20} />
      <div>
        <strong>
          {githubRequired
            ? "Connect your GitHub account to access this page"
            : "Sign in to access this page"}
        </strong>
        <p>
          {githubRequired
            ? "GitHub authorization is required before RiskGraph can open source scans, saved evidence, or validation services."
            : "A RiskGraph account is required before you can open source scans or saved evidence."}
        </p>
      </div>
      <a className="primary-button" href="#account">
        {githubRequired ? "Connect GitHub" : "Sign in"}
      </a>
    </div>
  );
}

function FieldStatus({
  id,
  valid,
  empty,
  validText,
  emptyText,
  invalidText = "Check the required format",
}: {
  id: string;
  valid: boolean;
  empty: boolean;
  validText: string;
  emptyText: string;
  invalidText?: string;
}) {
  return (
    <span
      id={id}
      className={`field-status ${valid ? "valid" : empty ? "empty" : "invalid"}`}
    >
      {valid && <Check size={13} />}
      {valid ? validText : empty ? emptyText : invalidText}
    </span>
  );
}

function shortSha(value: string) {
  return value ? value.slice(0, 7) + (value.length > 7 ? "…" : "") : "Pending";
}
